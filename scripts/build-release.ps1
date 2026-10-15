[CmdletBinding()]
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$versionLines = @(Get-Content -LiteralPath (Join-Path $releaseRoot 'gradle.properties') |
    Where-Object { $_ -match '^version=' })
if ($versionLines.Count -ne 1) { throw 'Expected exactly one version in gradle.properties.' }
$releaseVersion = $versionLines[0].Substring('version='.Length).Trim()
if ($releaseVersion -cnotmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
    throw "Expected a stable SemVer release: $releaseVersion"
}
if (-not (Select-String -LiteralPath (Join-Path $releaseRoot 'CHANGELOG.md') -Quiet `
        -Pattern ('^## \[' + [regex]::Escape($releaseVersion) + '\] - \d{4}-\d{2}-\d{2}$'))) {
    throw "Missing dated changelog section for $releaseVersion"
}
if (-not $SkipBuild) {
    $wrapper = Join-Path $releaseRoot $(if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' })
    & $wrapper check build --no-parallel --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "Release verification failed: $LASTEXITCODE" }
}
$releaseInputs = @(
    "api/build/libs/minedriver-api-$releaseVersion.jar",
    "protocol/build/libs/minedriver-protocol-$releaseVersion.jar",
    "hooks/build/libs/minedriver-hooks-$releaseVersion.jar",
    "agent/build/libs/minedriver-agent-$releaseVersion.jar",
    "gradle-plugin/build/libs/minedriver-gradle-plugin-$releaseVersion.jar",
    "cli/build/distributions/minedriver-$releaseVersion.zip",
    "cli/build/distributions/minedriver-$releaseVersion.tar"
)
$releaseFiles = foreach ($relative in $releaseInputs) {
    $path = Join-Path $releaseRoot $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing release input: $path" }
    Get-Item -LiteralPath $path
}
# Keep other versions and prior audit evidence; replace only this version's known output files.
$releaseDirectory = Join-Path $releaseRoot "build/release/$releaseVersion"
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
foreach ($file in $releaseFiles) { Copy-Item -LiteralPath $file.FullName -Destination $releaseDirectory -Force }
$releaseHashes = foreach ($file in $releaseFiles) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $releaseDirectory $file.Name) -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($file.Name)"
}
[IO.File]::WriteAllLines((Join-Path $releaseDirectory 'SHA256SUMS.txt'), [string[]]$releaseHashes)
Write-Host "Collected $($releaseFiles.Count) version $releaseVersion files in $releaseDirectory"
