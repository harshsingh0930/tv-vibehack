param([switch]$SkipChecks)
$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
if(!$SkipChecks){
    Push-Location (Join-Path $projectRoot 'cloud')
    try {
        npm run build
        if($LASTEXITCODE -ne 0){throw 'Dashboard build failed'}
        npm test
        if($LASTEXITCODE -ne 0){throw 'Server tests failed'}
    } finally { Pop-Location }
}
if(!(Test-Path -LiteralPath (Join-Path $projectRoot 'cloud/dist/index.html'))){throw 'Build the dashboard before packaging'}
$releaseRoot=Join-Path $projectRoot ('review/oracle-package-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
# Explicit allowlist: no credentials, photos, node_modules, TV files, build caches or local state.
$entries=@('cloud/dist','cloud/server','cloud/photo-layout.mjs','cloud/effects.mjs','cloud/package.json','cloud/package-lock.json','deploy','docs/ORACLE.md','docs/DEPLOYMENT.md')
foreach($relative in $entries){
    $source=Join-Path $projectRoot $relative
    $destination=Join-Path $releaseRoot $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination -Recurse
}
$checksums=Get-ChildItem -LiteralPath $releaseRoot -File -Recurse | Sort-Object FullName | ForEach-Object {
    $relative=$_.FullName.Substring($releaseRoot.Length+1).Replace('\','/')
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$relative
}
[System.IO.File]::WriteAllText((Join-Path $releaseRoot 'SHA256SUMS'), ($checksums -join "`n")+"`n", [System.Text.UTF8Encoding]::new($false))
$archive=Join-Path $projectRoot 'review/occasion-studio-oracle.tar.gz'
& tar.exe -czf $archive -C $releaseRoot .
if($LASTEXITCODE -ne 0){throw 'Release archive failed'}
[pscustomobject]@{archive=$archive; bytes=(Get-Item -LiteralPath $archive).Length; sha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash} | ConvertTo-Json
Write-Output 'Server-only package ready. This packaging step does not upload or deploy anything.'
