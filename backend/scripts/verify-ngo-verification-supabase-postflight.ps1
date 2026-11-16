$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Local .env is unavailable; no datasource selected.' }

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
$flag = Read-EnvValue 'NGO_VERIFICATION_ENABLED'
$parsed = [regex]::Match($jdbcUrl, '^jdbc:postgresql://(?<host>[^/:?]+)(?::(?<port>[0-9]+))?/(?<database>[^?]+)')
if (-not $parsed.Success -or [string]::IsNullOrWhiteSpace($supabaseUrl)) { throw 'Datasource configuration is incomplete; no connection attempted.' }
$dbHost = $parsed.Groups['host'].Value
$database = $parsed.Groups['database'].Value
$port = if ($parsed.Groups['port'].Success) { [int]$parsed.Groups['port'].Value } else { 5432 }
$apiHost = ([Uri]$supabaseUrl).Host
$projectRef = $apiHost.Split('.')[0]
$directMatch = ($dbHost -replace '^db\.', '') -eq $apiHost
$poolerMatch = ($dbHost -match '\.pooler\.supabase\.com$') -and ($dbUser -match ('(^|\.)' + [regex]::Escape($projectRef) + '$'))
if ($database -ne 'postgres' -or -not ($directMatch -or $poolerMatch)) {
    throw 'The configured target is not the preflight-confirmed Supabase TEST project; no connection attempted.'
}

$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
if (-not (Test-Path -LiteralPath $psql)) { throw 'Local psql executable unavailable; no connection attempted.' }
$sql = @'
BEGIN READ ONLY;
SET LOCAL statement_timeout='30s';
SELECT 'target' AS check_name,current_database() AS database_name,current_user AS runtime_role,
       current_setting('transaction_read_only') AS read_only;
SELECT 'migration_tables' AS check_name,count(*) AS present,
       count(*)=10 AS complete
  FROM information_schema.tables
 WHERE table_schema='public' AND table_name IN (
  'ngo_representative_verification_config','ngo_representative_verification_retention_approval_events',
  'ngo_representative_verification_legacy_exemptions','ngo_representative_verification_applications',
  'ngo_representative_verification_submissions','ngo_representative_verification_evidence',
  'ngo_representative_verification_events','ngo_representative_verification_evidence_disposal_events',
  'ngo_representative_verification_audit_metadata_redactions','ngo_representative_verification_disposal_metadata_redactions');
SELECT 'migration_rls' AS check_name,count(*) AS enabled_tables,
       count(*)=10 AS all_enabled
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relname LIKE 'ngo_representative_verification_%'
   AND c.relkind='r' AND c.relrowsecurity;
SELECT 'rest_direct_table_privileges' AS check_name,count(*) AS unexpected_grants
  FROM pg_roles role
 CROSS JOIN unnest(ARRAY[
  'ngo_representative_verification_config','ngo_representative_verification_retention_approval_events',
  'ngo_representative_verification_legacy_exemptions','ngo_representative_verification_applications',
  'ngo_representative_verification_submissions','ngo_representative_verification_evidence',
  'ngo_representative_verification_events','ngo_representative_verification_evidence_disposal_events',
  'ngo_representative_verification_audit_metadata_redactions','ngo_representative_verification_disposal_metadata_redactions'
 ]) AS object_name
 WHERE role.rolname IN ('anon','authenticated','service_role')
   AND has_table_privilege(role.rolname,'public.'||object_name,
       'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER');
SELECT 'storage_bucket' AS check_name,id,public,file_size_limit,allowed_mime_types
  FROM storage.buckets WHERE id='ngo-representative-evidence';
SELECT 'storage_client_policies' AS check_name,count(*) AS policies
  FROM pg_policy p WHERE p.polrelid='storage.objects'::regclass
   AND (0=ANY(p.polroles) OR EXISTS (
    SELECT 1 FROM unnest(p.polroles) AS policy_role(role_oid)
      JOIN pg_roles r ON r.oid=policy_role.role_oid
     WHERE r.rolname IN ('anon','authenticated')));
SELECT 'account_deletion_fk_actions' AS check_name,r.relname AS table_name,c.conname,
       CASE c.confdeltype WHEN 'n' THEN 'SET NULL' WHEN 'r' THEN 'RESTRICT'
         WHEN 'c' THEN 'CASCADE' WHEN 'a' THEN 'NO ACTION' ELSE c.confdeltype::text END AS on_delete
  FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid JOIN pg_namespace n ON n.oid=r.relnamespace
 WHERE n.nspname='public' AND c.conname IN (
  'fk_ngo_verification_retention_approver','fk_ngo_verification_retention_approval_actor',
  'fk_ngo_verification_application_applicant','fk_ngo_verification_submission_user',
  'fk_ngo_verification_evidence_uploader','fk_ngo_verification_event_actor',
  'fk_ngo_verification_disposal_actor') ORDER BY r.relname,c.conname;
SELECT 'retained_fixture_counts' AS check_name,'legacy_exemptions' AS table_name,count(*) AS rows
  FROM public.ngo_representative_verification_legacy_exemptions
UNION ALL SELECT 'retained_fixture_counts','applications',count(*) FROM public.ngo_representative_verification_applications
UNION ALL SELECT 'retained_fixture_counts','submissions',count(*) FROM public.ngo_representative_verification_submissions
UNION ALL SELECT 'retained_fixture_counts','evidence',count(*) FROM public.ngo_representative_verification_evidence
UNION ALL SELECT 'retained_fixture_counts','decision_events',count(*) FROM public.ngo_representative_verification_events
UNION ALL SELECT 'retained_fixture_counts','disposal_events',count(*) FROM public.ngo_representative_verification_evidence_disposal_events;
SELECT 'retention_policy' AS check_name,retention_policy_status,
       proposed_evidence_retention_months,proposed_audit_metadata_retention_years
  FROM public.ngo_representative_verification_config WHERE id=1;
SELECT 'trigger_state' AS check_name,count(*) AS enabled_triggers,
       bool_and(tgenabled<>'D') AS all_enabled
  FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relname LIKE 'ngo_representative_verification_%' AND NOT t.tgisinternal;
ROLLBACK;
'@

$old = @{ PGPASSWORD=$env:PGPASSWORD; PGSSLMODE=$env:PGSSLMODE; PGOPTIONS=$env:PGOPTIONS }
try {
    $env:PGPASSWORD = $dbPassword
    $env:PGSSLMODE = 'require'
    $env:PGOPTIONS = '-c default_transaction_read_only=on'
    $flagState = if ([string]::IsNullOrWhiteSpace($flag)) { 'false by application default' } else { $flag.ToLowerInvariant() }
    Write-Output ("Read-only postflight target confirmed; credentials omitted. NGO_VERIFICATION_ENABLED={0}." -f $flagState)
    $sql | & $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $port -U $dbUser -d $database -P pager=off -P footer=off
    if ($LASTEXITCODE -ne 0) { throw 'Read-only postflight failed; no writes were attempted.' }
} finally {
    $env:PGPASSWORD = $old.PGPASSWORD
    $env:PGSSLMODE = $old.PGSSLMODE
    $env:PGOPTIONS = $old.PGOPTIONS
}
