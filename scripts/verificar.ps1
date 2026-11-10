<#
.SYNOPSIS
Comprueba prerrequisitos y, con -Ejecutar, compila/verifica sin descargas.
.DESCRIPTION
Por defecto solo consulta archivos y herramientas. No instala dependencias,
inicia Docker ni aplica migraciones a la base de datos de la aplicacion.
La ejecucion backend usa las pruebas existentes con H2 en memoria.
Salidas: 0 correcto, 1 fallo, 2 incompleto por prerrequisitos ausentes.
#>
[CmdletBinding()]
param(
    [ValidateSet('Todos', 'Frontend', 'CMS', 'Backend')]
    [string]$Componente = 'Todos',
    [switch]$Ejecutar
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$results = New-Object 'System.Collections.Generic.List[object]'

function Invoke-Verification {
    param(
        [string]$Name,
        [string]$Directory,
        [string[]]$Tools,
        [string[]]$RequiredFiles,
        [string]$Command,
        [string[]]$CommandArguments
    )

    $missing = @()
    foreach ($tool in $Tools) {
        if (-not (Get-Command $tool -CommandType Application -ErrorAction SilentlyContinue)) {
            $missing += "herramienta: $tool"
        }
    }
    foreach ($file in $RequiredFiles) {
        if (-not (Test-Path -LiteralPath (Join-Path $Directory $file) -PathType Leaf)) {
            $missing += "archivo: $file"
        }
    }
    if ($missing.Count -gt 0) {
        $results.Add([pscustomobject]@{ Componente = $Name; Estado = 'INCOMPLETO'; Detalle = ($missing -join '; ') })
        return
    }
    if (-not $Ejecutar) {
        $results.Add([pscustomobject]@{ Componente = $Name; Estado = 'DISPONIBLE'; Detalle = 'Prerrequisitos detectados; compilacion y pruebas NO ejecutadas.' })
        return
    }

    Write-Host "Verificando $Name..."
    Push-Location -LiteralPath $Directory
    try {
        & $Command @CommandArguments
        $commandExitCode = $LASTEXITCODE
        if ($commandExitCode -ne 0) {
            throw "El comando termino con codigo $commandExitCode."
        }
        $results.Add([pscustomobject]@{ Componente = $Name; Estado = 'CORRECTO'; Detalle = "$Command $($CommandArguments -join ' ')" })
    }
    catch {
        $results.Add([pscustomobject]@{ Componente = $Name; Estado = 'FALLO'; Detalle = $_.Exception.Message })
    }
    finally {
        Pop-Location
    }
}

if ($Ejecutar) {
    Write-Host 'Modo ejecucion: genera artefactos locales. Sin instalaciones ni Docker.'
    Write-Host 'Maven offline: si faltan dependencias en cache, la verificacion fallara.'
}
else {
    Write-Host 'Modo consulta: no se ejecutan compilaciones ni pruebas. Use -Ejecutar para ello.'
}

if ($Componente -in @('Todos', 'Frontend')) {
    Invoke-Verification -Name 'Frontend' -Directory (Join-Path $projectRoot 'apps/public') `
        -Tools @('node', 'npm.cmd') `
        -RequiredFiles @('package.json', 'package-lock.json', 'node_modules/@angular/cli/bin/ng.js') `
        -Command 'npm.cmd' -CommandArguments @('--offline', 'run', 'build')
}
if ($Componente -in @('Todos', 'CMS')) {
    Invoke-Verification -Name 'CMS' -Directory (Join-Path $projectRoot 'apps/cms') `
        -Tools @('node', 'npm.cmd') `
        -RequiredFiles @('package.json', 'package-lock.json', 'node_modules/@angular/cli/bin/ng.js') `
        -Command 'npm.cmd' -CommandArguments @('--offline', 'run', 'build')
}
if ($Componente -in @('Todos', 'Backend')) {
    Invoke-Verification -Name 'Backend' -Directory (Join-Path $projectRoot 'backend') `
        -Tools @('java', 'mvn.cmd') -RequiredFiles @('pom.xml') `
        -Command 'mvn.cmd' -CommandArguments @('--offline', '-B', 'verify', '-Dapp.storage.root=target/verification-files')
}

$results | Format-Table -AutoSize -Wrap | Out-Host
if (@($results | Where-Object { $_.Estado -eq 'FALLO' }).Count -gt 0) { exit 1 }
if (@($results | Where-Object { $_.Estado -eq 'INCOMPLETO' }).Count -gt 0) { exit 2 }
exit 0
