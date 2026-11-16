$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Local .env is not available; no datasource was selected.' }

function Read-EnvValue([string]$key) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $match = [regex]::Match($line, '^\s*(?:export\s+)?' + [regex]::Escape($key) + '\s*=\s*(.*?)\s*$')
        if (-not $match.Success) { continue }
        $value = $match.Groups[1].Value.Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        return $value
    }
    return $null
}

$jdbcUrl = Read-EnvValue 'DB_URL'
$dbUser = Read-EnvValue 'DB_USER'
$dbPassword = Read-EnvValue 'DB_PASSWORD'
$supabaseUrl = Read-EnvValue 'SUPABASE_URL'
$supabaseSecretKey = Read-EnvValue 'SUPABASE_SECRET_KEY'
$verificationBucket = Read-EnvValue 'SUPABASE_BUCKET_VERIFICACAO'
$verificationFlag = Read-EnvValue 'NGO_VERIFICATION_ENABLED'
if ([string]::IsNullOrWhiteSpace($jdbcUrl) -or [string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPassword)) {
    throw 'Effective database settings are incomplete; no connection was attempted.'
}
$match = [regex]::Match($jdbcUrl, '^jdbc:postgresql://(?<host>[^/:?]+)(?::(?<port>[0-9]+))?/(?<database>[^?]+)')
if (-not $match.Success) { throw 'DB_URL format is not a PostgreSQL JDBC URL; no connection was attempted.' }
$hostName = $match.Groups['host'].Value
$port = if ($match.Groups['port'].Success) { [int]$match.Groups['port'].Value } else { 5432 }
$database = $match.Groups['database'].Value
if ($hostName -notmatch '\.supabase\.(co|com)$' -or $database -ne 'postgres') {
    throw 'The effective DB_URL is not the configured Supabase postgres database; no connection was attempted.'
}
if ([string]::IsNullOrWhiteSpace($supabaseUrl)) { throw 'SUPABASE_URL is absent; database/storage project consistency could not be checked.' }
$apiUri = [Uri]$supabaseUrl
$databaseProjectHost = $hostName -replace '^db\.', ''
$poolerProjectMatches = $hostName -match '\.pooler\.supabase\.com$' -and
    ($dbUser -match ('(^|\.)' + [regex]::Escape($apiUri.Host.Split('.')[0]) + '$'))
if (($databaseProjectHost -ne $apiUri.Host) -and -not $poolerProjectMatches) {
    throw 'DB_URL and SUPABASE_URL do not identify the same Supabase project; no connection was attempted.'
}

$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
if (-not (Test-Path -LiteralPath $psql)) { throw 'The local PostgreSQL client executable was not found; no connection was attempted.' }

$sql = @'
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';
SELECT 'target' AS check_name, current_database() AS database_name, current_user AS runtime_role,
       current_setting('transaction_read_only') AS read_only;
WITH expected(name) AS (VALUES
 ('ngo_representative_verification_config'),
 ('ngo_representative_verification_retention_approval_events'),
 ('ngo_representative_verification_legacy_exemptions'),
 ('ngo_representative_verification_applications'),
 ('ngo_representative_verification_submissions'),
 ('ngo_representative_verification_evidence'),
 ('ngo_representative_verification_events'),
 ('ngo_representative_verification_evidence_disposal_events'),
 ('ngo_representative_verification_audit_metadata_redactions'),
 ('ngo_representative_verification_disposal_metadata_redactions')),
present AS (SELECT count(*) AS n FROM expected WHERE to_regclass('public.'||name) IS NOT NULL)
SELECT 'migration_object_state' AS check_name,
       CASE WHEN n=0 THEN 'ABSENT' WHEN n=10 THEN 'TABLES_COMPLETE_REQUIRES_FULL_CHECK' ELSE 'PARTIAL' END AS state,
       n AS verification_tables_present FROM present;
SELECT 'dependency_column' AS check_name, table_name, column_name, data_type, is_nullable
  FROM information_schema.columns
 WHERE table_schema='public'
   AND ((table_name='oscs' AND column_name IN ('id','cnpj','status','arquivada'))
     OR (table_name='usuarios' AND column_name='id'))
 ORDER BY table_name, ordinal_position;
SELECT 'dependency_table_owner' AS check_name, c.relname, pg_get_userbyid(c.relowner) AS owner,
       pg_has_role(current_user,c.relowner,'MEMBER') AS runtime_owner_or_member
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relname IN ('oscs','usuarios') ORDER BY c.relname;
SELECT 'cnpj_unique_index' AS check_name, EXISTS (
    SELECT 1 FROM pg_index i
     WHERE i.indrelid='public.oscs'::regclass AND i.indisunique AND i.indisvalid AND i.indnkeyatts=1
       AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid=i.indrelid AND a.attnum=i.indkey[0] AND a.attname='cnpj')
) AS present;
SELECT 'migration_function' AS check_name, p.proname AS name,
       pg_get_userbyid(p.proowner) AS owner,
       has_function_privilege(current_user,p.oid,'EXECUTE') AS runtime_execute,
       has_function_privilege('anon',p.oid,'EXECUTE') AS anon_execute,
       has_function_privilege('authenticated',p.oid,'EXECUTE') AS authenticated_execute,
       has_function_privilege('service_role',p.oid,'EXECUTE') AS service_role_execute
  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND (p.proname LIKE 'ngo_representative_%' OR p.proname LIKE 'guard_ngo_%representative%')
 ORDER BY p.proname;
SELECT 'migration_relation' AS check_name, c.relname AS name, c.relkind, c.relrowsecurity,
       pg_get_userbyid(c.relowner) AS owner,
       has_table_privilege(current_user,c.oid,'SELECT') AS runtime_select,
       has_table_privilege(current_user,c.oid,'INSERT') AS runtime_insert,
       has_table_privilege(current_user,c.oid,'UPDATE') AS runtime_update,
       has_table_privilege('anon',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE') AS anon_table_access,
       has_table_privilege('authenticated',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE') AS authenticated_table_access,
       has_table_privilege('service_role',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE') AS service_role_table_access
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relname LIKE 'ngo_representative_verification_%'
 ORDER BY c.relname;
SELECT 'migration_executor_prerequisites' AS check_name,
       has_schema_privilege(current_user,'public','CREATE') AS public_schema_create,
       has_table_privilege(current_user,'public.oscs','SELECT') AS oscs_select,
       has_table_privilege(current_user,'public.oscs','UPDATE') AS oscs_update_for_legacy_lock,
       has_table_privilege(current_user,'public.oscs','REFERENCES') AS oscs_references,
       has_table_privilege(current_user,'public.usuarios','SELECT') AS usuarios_select,
       has_table_privilege(current_user,'public.usuarios','REFERENCES') AS usuarios_references;
SELECT 'storage_table' AS check_name, c.relname AS name, c.relrowsecurity, c.relforcerowsecurity,
       pg_get_userbyid(c.relowner) AS owner, r.rolsuper AS runtime_is_superuser,
       pg_has_role(current_user,c.relowner,'MEMBER') AS runtime_is_owner_or_member,
       has_table_privilege(current_user,c.oid,'SELECT') AS runtime_select,
       has_table_privilege(current_user,c.oid,'INSERT') AS runtime_insert,
       has_table_privilege(current_user,c.oid,'UPDATE') AS runtime_update,
       has_table_privilege(current_user,c.oid,'DELETE') AS runtime_delete
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.rolname=current_user
 WHERE n.nspname='storage' AND c.relname IN ('buckets','objects') ORDER BY c.relname;
SELECT 'storage_api_role_privilege' AS check_name, role_name,
       r.rolsuper, r.rolbypassrls,
       has_table_privilege(role_name,'storage.objects','SELECT') AS can_select,
       has_table_privilege(role_name,'storage.objects','INSERT') AS can_insert,
       has_table_privilege(role_name,'storage.objects','UPDATE') AS can_update,
       has_table_privilege(role_name,'storage.objects','DELETE') AS can_delete
  FROM (VALUES ('anon'),('authenticated'),('service_role')) roles(role_name)
  JOIN pg_roles r ON r.rolname=role_name;
SELECT 'migration_index' AS check_name, i.relname AS name, x.indisunique, x.indisvalid,
       pg_get_indexdef(x.indexrelid) AS definition
  FROM pg_index x JOIN pg_class i ON i.oid=x.indexrelid JOIN pg_namespace n ON n.oid=i.relnamespace
 WHERE n.nspname='public' AND i.relname LIKE '%ngo_verification%'
 ORDER BY i.relname;
SELECT 'migration_trigger' AS check_name, c.relname AS table_name, t.tgname, p.proname AS function_name,
       t.tgenabled, t.tgdeferrable, t.tginitdeferred
  FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
  JOIN pg_proc p ON p.oid=t.tgfoid
 WHERE n.nspname='public' AND c.relname LIKE 'ngo_representative_verification_%' AND NOT t.tgisinternal
 ORDER BY c.relname,t.tgname;
SELECT 'migration_storage_bucket' AS check_name, id, public, file_size_limit, allowed_mime_types
  FROM storage.buckets WHERE id='ngo-representative-evidence';
SELECT 'storage_policy' AS check_name, p.polname, p.polcmd, p.polpermissive,
       array(SELECT r.rolname FROM unnest(p.polroles) role_id JOIN pg_roles r ON r.oid=role_id ORDER BY r.rolname) AS roles,
       pg_get_expr(p.polqual,p.polrelid) AS using_expression,
       pg_get_expr(p.polwithcheck,p.polrelid) AS check_expression
  FROM pg_policy p WHERE p.polrelid='storage.objects'::regclass ORDER BY p.polname;
SELECT 'storage_bucket_policy' AS check_name, p.polname, p.polcmd, p.polpermissive,
       array(SELECT r.rolname FROM unnest(p.polroles) role_id JOIN pg_roles r ON r.oid=role_id ORDER BY r.rolname) AS roles,
       pg_get_expr(p.polqual,p.polrelid) AS using_expression,
       pg_get_expr(p.polwithcheck,p.polrelid) AS check_expression
  FROM pg_policy p WHERE p.polrelid='storage.buckets'::regclass ORDER BY p.polname;SELECT 'storage_runtime_privileges' AS check_name,
       has_table_privilege(current_user,'storage.buckets','SELECT,INSERT,UPDATE') AS runtime_bucket_access,
       has_table_privilege(current_user,'storage.objects','SELECT,INSERT,UPDATE,DELETE') AS runtime_object_access,
       has_table_privilege('anon','storage.objects','SELECT,INSERT,UPDATE,DELETE') AS anon_object_access,
       has_table_privilege('authenticated','storage.objects','SELECT,INSERT,UPDATE,DELETE') AS authenticated_object_access;
SELECT 'storage_schema_usage' AS check_name, r.rolbypassrls AS runtime_bypass_rls,
       has_schema_privilege(current_user,'storage','USAGE') AS runtime_schema_usage,
       has_schema_privilege(current_user,'storage','CREATE') AS runtime_schema_create,
       has_table_privilege(current_user,'storage.buckets','INSERT') AS runtime_bucket_insert,
       has_table_privilege(current_user,'storage.buckets','UPDATE') AS runtime_bucket_update,
       row_security_active('storage.buckets'::regclass) AS runtime_subject_to_bucket_rls
  FROM pg_roles r WHERE r.rolname=current_user;
ROLLBACK;
'@

$oldValues = @{ PGPASSWORD=$env:PGPASSWORD; PGSSLMODE=$env:PGSSLMODE; PGOPTIONS=$env:PGOPTIONS }
try {
    $env:PGPASSWORD = $dbPassword
    $env:PGSSLMODE = 'require'
    $env:PGOPTIONS = '-c default_transaction_read_only=on'
    Write-Output ("Target confirmed: DB_URL and SUPABASE_URL identify the same Supabase project; database name={0}; SSL=require; credentials omitted." -f $database)
    $secretKeyState = if ([string]::IsNullOrWhiteSpace($supabaseSecretKey)) { 'absent' } else { 'present' }
    $bucketState = if ([string]::IsNullOrWhiteSpace($verificationBucket)) { 'unset; application default applies' } elseif ($verificationBucket -eq 'ngo-representative-evidence') { 'configured as expected' } else { 'configured to a different value' }
    $flagState = if ([string]::IsNullOrWhiteSpace($verificationFlag)) { 'false by application default' } else { $verificationFlag.ToLowerInvariant() }
    Write-Output ("Storage configuration: service key {0}; evidence bucket {1}; NGO_VERIFICATION_ENABLED={2}. No secret value is displayed." -f $secretKeyState,$bucketState,$flagState)
    $sql | & $psql -X -v ON_ERROR_STOP=1 -h $hostName -p $port -U $dbUser -d $database -P pager=off -P footer=off
    if ($LASTEXITCODE -ne 0) { throw "Read-only preflight failed with psql exit code $LASTEXITCODE." }
} finally {
    $env:PGPASSWORD = $oldValues.PGPASSWORD
    $env:PGSSLMODE = $oldValues.PGSSLMODE
    $env:PGOPTIONS = $oldValues.PGOPTIONS
}
