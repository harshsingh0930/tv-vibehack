# Build only; does not connect to a TV or change installed packages.
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkPath = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }),
    [string]$GradleUserHome = $env:GRADLE_USER_HOME,
    [string]$AndroidUserHome = $env:ANDROID_USER_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $SdkPath -or -not (Test-Path -LiteralPath (Join-Path $SdkPath 'platforms/android-34/android.jar'))) {
    throw 'Set ANDROID_HOME or pass -SdkPath with Android SDK platform 34 and build-tools 34.0.0 installed.'
}
if ($JavaHome -and -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'The specified JDK does not contain bin/java.exe. Use JDK 17.'
}
if (-not $JavaHome -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Set JAVA_HOME, pass -JavaHome, or put JDK 17 on PATH.'
}
$savedEnvironment = @{}
foreach ($name in @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'ANDROID_USER_HOME', 'GRADLE_USER_HOME')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    $env:ANDROID_HOME = (Resolve-Path -LiteralPath $SdkPath).Path
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    if ($GradleUserHome) { $env:GRADLE_USER_HOME = $GradleUserHome }
    if ($AndroidUserHome) { $env:ANDROID_USER_HOME = $AndroidUserHome }
    Set-Content -LiteralPath (Join-Path $PSScriptRoot 'local.properties') -Encoding ascii -Value (
        'sdk.dir=' + $env:ANDROID_HOME.Replace([char]92, '/')
    )
    Push-Location $PSScriptRoot
    try {
        & (Join-Path $PSScriptRoot 'gradlew.bat') --no-daemon --console=plain assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "Launcher build failed: exit $LASTEXITCODE" }
    } finally { Pop-Location }
    Get-Item -LiteralPath (Join-Path $PSScriptRoot 'app/build/outputs/apk/debug/app-debug.apk') |
        Select-Object FullName, Length
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
}
