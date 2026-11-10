<#
.SYNOPSIS
Respalda PostgreSQL y archivos, prueba restauracion y una migracion en PostgreSQL aislado.
.DESCRIPTION
No reinicia el entorno existente ni aplica migraciones sobre su base.
Use -Migration V3__commercial_followup.sql para comprobar el salto V2 a V3.
El respaldo contiene datos privados: permanece en .local-backups, fuera de Git.
La instancia de comprobacion usa --network none y no publica puertos.
#>
[CmdletBinding()]
param([ValidateSet('V2__administration.sql','V3__commercial_followup.sql','V4__editorial_content.sql','V5__appointments.sql','V6__media_library.sql','V7__account_recovery_oidc.sql')][string]$Migration='V2__administration.sql')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmssZ')
$backupRoot = Join-Path $projectRoot ".local-backups/admin-$stamp"
$checkContainer = "deportecuador-restore-$stamp"
$databaseContainer = 'deportecuador-db-1'
$backendContainer = 'deportecuador-backend-1'

function Invoke-Docker {
    param([string[]]$Arguments)
    & docker.exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker fallo durante: $($Arguments[0])" }
}

New-Item -ItemType Directory -Path $backupRoot | Out-Null
# Restrict the local Windows backup to the current user.
$backupOwner = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $backupRoot /grant:r "*${backupOwner}:(OI)(CI)F" /inheritance:r | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudo restringir el acceso al respaldo.' }

Invoke-Docker -Arguments @('exec',$databaseContainer,'pg_dump','-U','deportecuador','-d','deportecuador','-Fc','-f',"/tmp/admin-$stamp.dump")
Invoke-Docker -Arguments @('cp',"${databaseContainer}:/tmp/admin-$stamp.dump",(Join-Path $backupRoot 'database.dump'))
Invoke-Docker -Arguments @('exec',$backendContainer,'tar','-czf',"/tmp/admin-$stamp-files.tar.gz",'-C','/data/files','.')
Invoke-Docker -Arguments @('cp',"${backendContainer}:/tmp/admin-$stamp-files.tar.gz",(Join-Path $backupRoot 'files.tar.gz'))

$created = $false
try {
    Invoke-Docker -Arguments @('run','-d','--name',$checkContainer,'--network','none','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:17-alpine')
    $created = $true
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        & docker.exe exec $checkContainer pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL de comprobacion no inicio.' }
    Invoke-Docker -Arguments @('cp',(Join-Path $backupRoot 'database.dump'),"${checkContainer}:/tmp/database.dump")
    Invoke-Docker -Arguments @('exec',$checkContainer,'createdb','-U','postgres','restore_check')
    Invoke-Docker -Arguments @('exec',$checkContainer,'pg_restore','-U','postgres','-d','restore_check','--no-owner','--no-acl','--exit-on-error','/tmp/database.dump')
    $before = & docker.exe exec $checkContainer psql -U postgres -d restore_check -Atc 'SELECT count(*) FROM commercial_request'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar el respaldo.' }
    $usersBefore = ''
    $crmBefore = @{}
    if ($Migration -in @('V4__editorial_content.sql','V5__appointments.sql','V6__media_library.sql','V7__account_recovery_oidc.sql')) {
        $checkedTables = @('commercial_request','crm_activity','crm_notice','notification_outbox')
        if ($Migration -in @('V5__appointments.sql','V6__media_library.sql','V7__account_recovery_oidc.sql')) { $checkedTables += @('cms_document','cms_revision','cms_event') }
        if ($Migration -in @('V6__media_library.sql','V7__account_recovery_oidc.sql')) { $checkedTables += @('appointment_agenda','assistant_settings','appointment_slot','whatsapp_session','whatsapp_reply') }
        if ($Migration -eq 'V7__account_recovery_oidc.sql') { $checkedTables += @('media_asset','cms_revision_media') }
        foreach ($table in $checkedTables) {
            $crmBefore[$table] = & docker.exe exec $checkContainer psql -U postgres -d restore_check -Atc "SELECT md5(coalesce(string_agg(row_to_json(t)::text,chr(10) ORDER BY row_to_json(t)::text),'')) FROM $table t"
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo verificar el contenido comercial del respaldo.' }
        }
    }
    if ($Migration -ne 'V2__administration.sql') {
        $usersBefore = & docker.exe exec $checkContainer psql -U postgres -d restore_check -Atc 'SELECT md5(string_agg((to_jsonb(u)-''auth_version'')::text,chr(10) ORDER BY id)) FROM admin_user u'
        if ($LASTEXITCODE -ne 0) { throw 'No se pudieron comprobar las cuentas del respaldo.' }
    }
    Invoke-Docker -Arguments @('cp',(Join-Path $projectRoot "backend/src/main/resources/db/migration/$Migration"),"${checkContainer}:/tmp/migration.sql")
    Invoke-Docker -Arguments @('exec',$checkContainer,'psql','-U','postgres','-d','restore_check','-v','ON_ERROR_STOP=1','--single-transaction','-f','/tmp/migration.sql')
    if ($Migration -in @('V4__editorial_content.sql','V5__appointments.sql','V6__media_library.sql','V7__account_recovery_oidc.sql')) {
        foreach ($table in $crmBefore.Keys) {
            $crmAfter = & docker.exe exec $checkContainer psql -U postgres -d restore_check -Atc "SELECT md5(coalesce(string_agg(row_to_json(t)::text,chr(10) ORDER BY row_to_json(t)::text),'')) FROM $table t"
            if ($LASTEXITCODE -ne 0 -or "$crmAfter" -ne "$($crmBefore[$table])") { throw 'El contenido comercial cambio durante la migracion de prueba.' }
        }
    }
    if ($Migration -ne 'V2__administration.sql') {
        $usersAfter = & docker.exe exec $checkContainer psql -U postgres -d restore_check -Atc 'SELECT md5(string_agg((to_jsonb(u)-''auth_version'')::text,chr(10) ORDER BY id)) FROM admin_user u'
        if ($LASTEXITCODE -ne 0 -or "$usersBefore" -ne "$usersAfter") { throw 'Las cuentas cambiaron durante la prueba.' }
    }
    Invoke-Docker -Arguments @('cp',(Join-Path $backupRoot 'files.tar.gz'),"${checkContainer}:/tmp/files.tar.gz")
    Invoke-Docker -Arguments @('exec',$checkContainer,'mkdir','/tmp/restored-files')
    Invoke-Docker -Arguments @('exec',$checkContainer,'tar','-xzf','/tmp/files.tar.gz','-C','/tmp/restored-files')
    if ($Migration -ne 'V2__administration.sql') {
        Invoke-Docker -Arguments @('exec',$checkContainer,'test','-s','/tmp/restored-files/.admin-secrets/encryption.key')
    }
    $after = & docker.exe exec $checkContainer psql -U postgres -d restore_check -Atc 'SELECT count(*) FROM commercial_request'
    if ($LASTEXITCODE -ne 0 -or "$before" -ne "$after") { throw 'El numero de solicitudes cambio durante la prueba.' }
    $report = "Restauracion PostgreSQL 17, archivos y $Migration correctas. Solicitudes conservadas: $after. Fecha: $stamp. Base original sin migrar."
    Set-Content -LiteralPath (Join-Path $backupRoot 'VERIFICADO.txt') -Value $report -Encoding UTF8
    Write-Output $report
    Write-Output "Respaldo privado: $backupRoot"
}
finally {
    # Only the disposable container created by this run is removed, with its volumes.
    if ($created) { Invoke-Docker -Arguments @('rm','-f','-v',$checkContainer) }
}
