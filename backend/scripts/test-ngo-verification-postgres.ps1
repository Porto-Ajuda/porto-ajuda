$ErrorActionPreference = 'Stop'

$postgresRoot = Join-Path $env:ProgramFiles 'PostgreSQL'
$bin = Get-ChildItem -LiteralPath $postgresRoot -Directory -ErrorAction Stop |
    Where-Object { $_.Name -match '^\d+$' } |
    Sort-Object { [int]$_.Name } -Descending |
    ForEach-Object { Join-Path $_.FullName 'bin' } |
    Where-Object {
        (Test-Path (Join-Path $_ 'initdb.exe')) -and
        (Test-Path (Join-Path $_ 'pg_ctl.exe')) -and
        (Test-Path (Join-Path $_ 'createdb.exe')) -and
        (Test-Path (Join-Path $_ 'psql.exe'))
    } | Select-Object -First 1
if (-not $bin) { throw 'PostgreSQL initdb/pg_ctl/createdb/psql binaries were not found in the existing Windows installation.' }

$temporaryParent = Join-Path $env:LOCALAPPDATA 'Temp'
$taskRoot = Join-Path $temporaryParent ('ngo-verification-test-' + [guid]::NewGuid().ToString('N'))
$dataDirectory = Join-Path $taskRoot 'data'
$passwordFile = Join-Path $taskRoot 'init-password.txt'
$runtimeSetupFile = Join-Path $taskRoot 'runtime-role-setup.sql'
$databaseName = 'ngo_representative_verification_test'
$databaseUser = 'ngo_verification_test_admin'
$port = 55447
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$baseline = Join-Path $backendRoot 'src\test\resources\db\ngo_representative_verification_baseline.sql'
$migration = Join-Path $backendRoot 'src\main\resources\db\manual\20261113_ngo_representative_verification.sql'
$aiJobsMigration = Join-Path $backendRoot 'src\main\resources\db\manual\20261114_ngo_representative_ai_analysis_jobs.sql'
$serverStarted = $false
$mavenExit = 1
$oldEnvironment = @{
    PGPASSWORD = $env:PGPASSWORD
    DB_URL = $env:DB_URL
    DB_USER = $env:DB_USER
    DB_PASSWORD = $env:DB_PASSWORD
    NGO_VERIFICATION_TEST_DATABASE_CONFIRMED = $env:NGO_VERIFICATION_TEST_DATABASE_CONFIRMED
}

New-Item -ItemType Directory -Path $taskRoot | Out-Null
try {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
    try { $listener.Start(); $listener.Stop() }
    catch { try { $listener.Stop() } catch { }; throw 'Disposable PostgreSQL port 55447 is occupied; no process was touched.' }

    $randomBytes = New-Object byte[] 32
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $randomGenerator.GetBytes($randomBytes) } finally { $randomGenerator.Dispose() }
    $password = [Convert]::ToBase64String($randomBytes)
    [IO.File]::WriteAllText($passwordFile, $password, [Text.UTF8Encoding]::new($false))
    & (Join-Path $bin 'initdb.exe') -D $dataDirectory -U $databaseUser --auth-local=trust `
        --auth-host=scram-sha-256 --pwfile=$passwordFile --encoding=UTF8 --no-locale
    if ($LASTEXITCODE -ne 0) { throw "initdb failed with exit code $LASTEXITCODE" }

    Add-Content -LiteralPath (Join-Path $dataDirectory 'postgresql.conf') -Encoding ascii `
        -Value "`nlisten_addresses = '127.0.0.1'`nport = $port`nmax_connections = 30`n"
    $env:PGPASSWORD = $password
    & (Join-Path $bin 'pg_ctl.exe') -D $dataDirectory -l (Join-Path $taskRoot 'postgres.log') `
        -o "-h 127.0.0.1 -p $port" -w start
    if ($LASTEXITCODE -ne 0) { throw "pg_ctl start failed with exit code $LASTEXITCODE" }
    $serverStarted = $true

    & (Join-Path $bin 'createdb.exe') -h 127.0.0.1 -p $port -U $databaseUser $databaseName
    if ($LASTEXITCODE -ne 0) { throw "createdb failed with exit code $LASTEXITCODE" }
    & (Join-Path $bin 'psql.exe') -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $port -U $databaseUser `
        -d $databaseName -f $baseline
    if ($LASTEXITCODE -ne 0) { throw "Synthetic baseline failed with exit code $LASTEXITCODE" }
    $runtimeSetup = @"
CREATE ROLE ngo_verification_runtime LOGIN PASSWORD '$password' BYPASSRLS;
GRANT CREATE ON SCHEMA public TO ngo_verification_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON TABLE public.usuarios TO ngo_verification_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON TABLE public.oscs TO ngo_verification_runtime;
GRANT USAGE, SELECT ON SEQUENCE public.oscs_id_seq TO ngo_verification_runtime;
GRANT USAGE ON SCHEMA storage TO ngo_verification_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE storage.buckets TO ngo_verification_runtime;
"@
    [IO.File]::WriteAllText($runtimeSetupFile, $runtimeSetup, [Text.UTF8Encoding]::new($false))
    & (Join-Path $bin 'psql.exe') -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $port -U $databaseUser `
        -d $databaseName -f $runtimeSetupFile
    if ($LASTEXITCODE -ne 0) { throw "Synthetic runtime role setup failed with exit code $LASTEXITCODE" }
    Remove-Item -LiteralPath $runtimeSetupFile -Force
    & (Join-Path $bin 'psql.exe') -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $port -U ngo_verification_runtime `
        -d $databaseName -f $migration
    if ($LASTEXITCODE -ne 0) { throw "Migration failed with exit code $LASTEXITCODE" }

    $oldErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & (Join-Path $bin 'psql.exe') -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $port `
            -U ngo_verification_runtime -d $databaseName -f $migration 1>$null 2>(Join-Path $taskRoot 'second-migration-output.txt')
        $secondRunExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldErrorActionPreference }
    if ($secondRunExitCode -eq 0) { throw 'The one-shot migration unexpectedly succeeded on a second execution.' }
    $preservedObjects = & (Join-Path $bin 'psql.exe') -X -tA -h 127.0.0.1 -p $port -U ngo_verification_runtime `
        -d $databaseName -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'ngo_representative_verification_%'"
    if ($LASTEXITCODE -ne 0 -or [int]($preservedObjects | Select-Object -Last 1) -lt 10) {
        throw 'The rejected second execution did not preserve the first migration state.'
    }
    $preservedBucket = & (Join-Path $bin 'psql.exe') -X -tA -h 127.0.0.1 -p $port -U ngo_verification_runtime `
        -d $databaseName -c "SELECT EXISTS (SELECT 1 FROM storage.buckets WHERE id='ngo-representative-evidence')"
    if ($LASTEXITCODE -ne 0 -or ($preservedBucket | Select-Object -Last 1).Trim() -ne 't') {
        throw 'The rejected second execution did not preserve the dedicated bucket.'
    }

    & (Join-Path $bin 'psql.exe') -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $port -U ngo_verification_runtime `
        -d $databaseName -f $aiJobsMigration
    if ($LASTEXITCODE -ne 0) { throw "Synthetic AI job migration failed with exit code $LASTEXITCODE" }

    $env:NGO_VERIFICATION_TEST_DATABASE_CONFIRMED = 'true'
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$port/$databaseName"
    $env:DB_USER = 'ngo_verification_runtime'
    $env:DB_PASSWORD = $password
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd '-Dtest=VerificacaoRepresentanteServiceTest,VerificacaoRepresentanteSecurityTest,VerificacaoRepresentantePostgresIntegrationTest,VerificacaoAnaliseOrchestratorTest,VerificacaoAiJobPostgresIntegrationTest,GeminiAnaliseRepresentacaoDocumentalTest,GeminiRequestBudgetTest,GeminiLiveSyntheticSmokeTest' test
        $mavenExit = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:PGPASSWORD = $oldEnvironment.PGPASSWORD
    $env:DB_URL = $oldEnvironment.DB_URL
    $env:DB_USER = $oldEnvironment.DB_USER
    $env:DB_PASSWORD = $oldEnvironment.DB_PASSWORD
    $env:NGO_VERIFICATION_TEST_DATABASE_CONFIRMED = $oldEnvironment.NGO_VERIFICATION_TEST_DATABASE_CONFIRMED

    if ($serverStarted) {
        & (Join-Path $bin 'pg_ctl.exe') -D $dataDirectory -m fast -w stop
        if ($LASTEXITCODE -ne 0) {
            throw "Could not stop the task-created PostgreSQL instance. Its isolated files remain at $taskRoot."
        }
    }
    if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile -Force }
    if (Test-Path -LiteralPath $taskRoot) {
        $resolvedTaskRoot = (Resolve-Path -LiteralPath $taskRoot).Path
        $resolvedTemp = (Resolve-Path -LiteralPath $temporaryParent).Path
        if (-not $resolvedTaskRoot.StartsWith($resolvedTemp, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Split-Path $resolvedTaskRoot -Leaf).StartsWith('ngo-verification-test-', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing cleanup because the path is outside the task-specific temporary directory.'
        }
        Remove-Item -LiteralPath $resolvedTaskRoot -Recurse -Force
    }
}

if ($mavenExit -ne 0) { exit $mavenExit }
Write-Output 'Disposable PostgreSQL migration and NGO verification integration tests completed.'
