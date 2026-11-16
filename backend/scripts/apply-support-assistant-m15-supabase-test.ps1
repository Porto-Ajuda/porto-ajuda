$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env'
$migration = Join-Path $repoRoot 'backend\src\main\resources\db\manual\20261115_support_assistant.sql'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Local .env unavailable; no database selected.' }
if (-not (Test-Path -LiteralPath $psql)) { throw 'PostgreSQL client unavailable; no connection attempted.' }

function Read-EnvValue([string]$key) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $m = [regex]::Match($line, '^\s*(?:export\s+)?' + [regex]::Escape($key) + '\s*=\s*(.*?)\s*$')
        if ($m.Success) {
            $v = $m.Groups[1].Value.Trim()
            if ($v.Length -ge 2 -and (($v.StartsWith('"') -and $v.EndsWith('"')) -or ($v.StartsWith("'") -and $v.EndsWith("'")))) { $v = $v.Substring(1,$v.Length-2) }
            return $v
        }
    }
    return $null
}

$jdbc = Read-EnvValue 'DB_URL'; $dbUser = Read-EnvValue 'DB_USER'; $dbPassword = Read-EnvValue 'DB_PASSWORD'; $supabaseUrl = Read-EnvValue 'SUPABASE_URL'
$m = [regex]::Match($jdbc, '^jdbc:postgresql://(?<host>[^/:?]+)(?::(?<port>[0-9]+))?/(?<db>[^?]+)')
if (-not $m.Success -or [string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPassword) -or [string]::IsNullOrWhiteSpace($supabaseUrl)) { throw 'Datasource incomplete; no connection attempted.' }
$dbHost=$m.Groups['host'].Value; $dbName=$m.Groups['db'].Value; $port=if($m.Groups['port'].Success){[int]$m.Groups['port'].Value}else{5432}
$api=[Uri]$supabaseUrl
$projectHost=$dbHost -replace '^db\.',''
$poolerMatch=$dbHost -match '\.pooler\.supabase\.com$' -and $dbUser -match ('(^|\.)'+[regex]::Escape($api.Host.Split('.')[0])+'$')
if ($dbHost -notmatch '\.supabase\.(co|com)$' -or $dbName -ne 'postgres' -or (($projectHost -ne $api.Host) -and -not $poolerMatch)) { throw 'Configured datasource is not the matching Supabase TEST database; no connection attempted.' }

$preflight = @'
BEGIN READ ONLY;
SET LOCAL statement_timeout='30s';
SELECT current_database() AS database_name, current_user AS migration_role, current_setting('transaction_read_only') AS read_only;
WITH names(name) AS (VALUES ('support_conversations'),('support_messages'),('support_conversation_events'),('support_retention_authorizations'))
SELECT count(*) FILTER (WHERE to_regclass('public.'||name) IS NOT NULL) AS present_tables, count(*) AS expected_tables FROM names;
SELECT count(*) AS support_functions FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname IN ('guard_support_conversation_identity','prevent_support_history_mutation','purge_expired_support_conversation');
SELECT count(*) AS support_triggers FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND t.tgname LIKE 'trg_support_%' AND NOT t.tgisinternal;
SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' AND ((table_name='usuarios' AND column_name='id') OR (table_name='admin_assignments' AND column_name='usuario_id')) ORDER BY table_name,column_name;
ROLLBACK;
'@
$applyDecision = @'
DO $check$
DECLARE table_count integer; function_count integer; trigger_count integer;
BEGIN
 SELECT count(*) INTO table_count FROM (VALUES ('support_conversations'),('support_messages'),('support_conversation_events'),('support_retention_authorizations')) x(name) WHERE to_regclass('public.'||name) IS NOT NULL;
 SELECT count(*) INTO function_count FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname IN ('guard_support_conversation_identity','prevent_support_history_mutation','purge_expired_support_conversation');
 SELECT count(*) INTO trigger_count FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND t.tgname LIKE 'trg_support_%' AND NOT t.tgisinternal;
 IF table_count<>0 OR function_count<>0 OR trigger_count<>0 THEN RAISE EXCEPTION 'M15 objects are partially or fully present; do not rerun the one-shot migration.'; END IF;
END $check$;
'@
$postflight = @'
BEGIN READ ONLY;
SET LOCAL statement_timeout='30s';
SELECT 'relations' AS check_name,count(*) AS present,count(*) FILTER (WHERE c.relrowsecurity) AS rls_enabled
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname IN ('support_conversations','support_messages','support_conversation_events','support_retention_authorizations') AND c.relkind='r';
SELECT 'triggers' AS check_name,c.relname AS table_name,t.tgname,p.proname AS function_name,t.tgenabled
 FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_proc p ON p.oid=t.tgfoid
 WHERE n.nspname='public' AND t.tgname LIKE 'trg_support_%' AND NOT t.tgisinternal ORDER BY c.relname,t.tgname;
SELECT 'indexes' AS check_name,count(*) AS required_present FROM pg_class WHERE relname IN ('idx_support_conversations_owner_updated','idx_support_conversations_queue','idx_support_conversations_retention','uq_support_message_idempotency','idx_support_messages_conversation_time','idx_support_events_conversation_time');
SELECT 'constraints' AS check_name,count(*) AS validated_present FROM pg_constraint WHERE conname IN ('ck_support_conversation_state','ck_support_conversation_version','ck_support_conversation_assignment','ck_support_conversation_closed_at','ck_support_conversation_purged','ck_support_message_sender','ck_support_message_body','ck_support_message_idempotency','ck_support_message_sender_identity','ck_support_event_actor','ck_support_event_type');
SELECT 'service_role' AS check_name,has_table_privilege('service_role','public.support_conversations','SELECT,INSERT,UPDATE') AS conversation_access,has_table_privilege('service_role','public.support_messages','SELECT,INSERT') AS message_access,has_table_privilege('service_role','public.support_conversation_events','SELECT,INSERT') AS event_access,has_function_privilege('service_role','public.purge_expired_support_conversation(uuid,timestamptz)','EXECUTE') AS purge_execute,has_table_privilege('service_role','public.support_retention_authorizations','SELECT,INSERT,UPDATE,DELETE') AS authorization_table_access;
SELECT 'anon' AS check_name,has_table_privilege('anon','public.support_messages','SELECT,INSERT,UPDATE,DELETE') AS message_access,has_function_privilege('anon','public.purge_expired_support_conversation(uuid,timestamptz)','EXECUTE') AS purge_execute;
ROLLBACK;
'@

$old = @{ PGPASSWORD=$env:PGPASSWORD; PGSSLMODE=$env:PGSSLMODE; PGOPTIONS=$env:PGOPTIONS }
try {
    $env:PGPASSWORD=$dbPassword; $env:PGSSLMODE='require'; $env:PGOPTIONS='-c default_transaction_read_only=on'
    Write-Output ("Preflight target confirmed against matching SUPABASE_URL; database={0}; SSL=require; credentials omitted." -f $dbName)
    $preflight | & $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $port -U $dbUser -d $dbName -P pager=off -P footer=off
    if ($LASTEXITCODE -ne 0) { throw 'Read-only preflight failed; migration not applied.' }
    $env:PGOPTIONS=''
    $applyDecision | & $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $port -U $dbUser -d $dbName -P pager=off -P footer=off
    if ($LASTEXITCODE -ne 0) { throw 'Pre-application object-state check failed; migration not applied.' }
    & $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $port -U $dbUser -d $dbName -f $migration -P pager=off -P footer=off
    if ($LASTEXITCODE -ne 0) { throw 'M15 failed; its explicit transaction rolled back. Stop and inspect; do not rerun blindly.' }
    $env:PGOPTIONS='-c default_transaction_read_only=on'
    $postflight | & $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $port -U $dbUser -d $dbName -P pager=off -P footer=off
    if ($LASTEXITCODE -ne 0) { throw 'Postflight failed; preserve state and diagnose before any repair.' }
} finally { $env:PGPASSWORD=$old.PGPASSWORD; $env:PGSSLMODE=$old.PGSSLMODE; $env:PGOPTIONS=$old.PGOPTIONS }
