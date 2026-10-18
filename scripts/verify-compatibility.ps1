param(
    [ValidateSet('fabric', 'neoforge')]
    [string]$Loader = 'fabric',
    [ValidateSet('1.21.1', '1.21.11', '26.1.2', '26.2', '26.3')]
    [string[]]$MinecraftVersions = @('1.21.1', '1.21.11', '26.1.2', '26.2', '26.3'),
    [ValidateSet('mojang', 'yarn')]
    [string]$Mappings = 'mojang'
)

$ErrorActionPreference = 'Stop'
if ($Mappings -eq 'yarn') {
    if ($Loader -ne 'fabric') { throw 'Yarn mappings require the Fabric fixture.' }
    if (!$PSBoundParameters.ContainsKey('MinecraftVersions')) { $MinecraftVersions = @('1.21.1', '1.21.11') }
    if ($MinecraftVersions | Where-Object { $_ -notin @('1.21.1', '1.21.11') }) {
        throw 'The Yarn fixture supports Minecraft 1.21.1 and 1.21.11.'
    }
}
$compatibilityRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$fixture = Join-Path $compatibilityRoot "integration/compatibility/$Loader"
$output = Join-Path $compatibilityRoot "build/reports/minedriver/compatibility/$Loader"
if ($Mappings -eq 'yarn') { $output = Join-Path $output 'yarn' }
New-Item -ItemType Directory -Force -Path $output | Out-Null
$lockFile = Join-Path $compatibilityRoot 'build/reports/minedriver/compatibility/verification.lock'
try {
    $compatibilityLock = [IO.File]::Open($lockFile, 'OpenOrCreate', 'ReadWrite', 'None')
} catch [IO.IOException] {
    throw 'A compatibility matrix is already running. Run loaders sequentially because their composite builds share compiler outputs.'
}
try {
    $records = @()
    Set-Content -Encoding utf8 -LiteralPath (Join-Path $output 'results.json') -Value '[]'
    foreach ($minecraftVersion in $MinecraftVersions) {
        $log = Join-Path $output "$minecraftVersion.log"
        $arguments = @('-p', $fixture, 'mineDriverCheck', "-PmcVersion=$minecraftVersion", '--console=plain')
        if ($Loader -eq 'fabric') { $arguments += "-Pmappings=$Mappings" }
        if ($Loader -eq 'neoforge' -and $minecraftVersion -eq '26.2') {
            # This exact Cloth metadata warning was diagnosed in docs/validation.md.
            $arguments += '-PacceptedWarningsFile=../../neoforge/fixtures/accepted-cloth26.2.json'
        }
        & (Join-Path $compatibilityRoot 'gradlew.bat') @arguments *> $log
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw "Compatibility failed: $Loader / $minecraftVersion, exit $exitCode. Log: $log"
        }
        $pointer = Get-Content -Raw (Join-Path $fixture 'build/reports/minedriver/mineDriverCheck/latest.json') | ConvertFrom-Json
        $resultFile = Join-Path $pointer.directory 'result.json'
        if (!(Test-Path -LiteralPath $resultFile)) {
            throw "Compatibility failed: $Loader / $minecraftVersion, exit $exitCode. Log: $log"
        }
        $result = Get-Content -Raw -LiteralPath $resultFile | ConvertFrom-Json
        if ($result.runId -ne $pointer.runId -or !$result.complete -or $result.status -ne 'PASS') {
            throw "Compatibility result is not a fresh complete PASS: $Loader / $minecraftVersion. Report: $resultFile"
        }
        if ($result.environment.minecraft -ne $minecraftVersion -or $result.environment.loader -ne $Loader) {
            throw "Compatibility runtime differs from the requested target: $Loader / $minecraftVersion. Report: $resultFile"
        }
        $expectedNamespace = if ($Mappings -eq 'yarn') { 'named' } else { 'mojang' }
        if ($result.environment.mappingNamespace -ne $expectedNamespace) {
            throw "Compatibility mapping namespace differs from the requested target: $Mappings. Report: $resultFile"
        }
        $records += [PSCustomObject]@{
            loader = $Loader
            minecraft = $minecraftVersion
            mappings = $Mappings
            runId = $result.runId
            complete = $result.complete
            status = $result.status
            result = $resultFile
            log = $log
        }
        ConvertTo-Json -Depth 8 -InputObject $records | Set-Content -Encoding utf8 (Join-Path $output 'results.json')
        Write-Output "PASS $Loader / $minecraftVersion / $Mappings / $($result.runId)"
    }
} finally {
    $compatibilityLock.Dispose()
}
