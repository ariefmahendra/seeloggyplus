# Stops any running SeeLoggyPlus application instance.
#
# `gradlew runDev` launches the app as com.seeloggyplus.app.Main (sometimes
# com.seeloggyplus.app.Launcher) and, on Windows, that process keeps file locks on
# build/resources/main. If `gradlew test` / `gradlew build` fails with
# "Failed to clean up stale outputs", run this script (and `gradlew --stop`
# if needed), then retry the build.
#
# Usage: powershell -ExecutionPolicy Bypass -File scripts/stop-app.ps1

$ErrorActionPreference = 'SilentlyContinue'

$targets = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object {
        $_.CommandLine -match 'com\.seeloggyplus\.app\.(Main|Launcher)' -and
        $_.CommandLine -notmatch 'GradleDaemon|gradle-launcher|GradleWrapperMain'
    }

if (-not $targets) {
    Write-Host "No SeeLoggyPlus app process is running."
    exit 0
}

foreach ($process in $targets) {
    Write-Host "Stopping SeeLoggyPlus (PID $($process.ProcessId))..."
    Stop-Process -Id $process.ProcessId -Force
}

Start-Sleep -Seconds 2

$remaining = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -match 'com\.seeloggyplus\.app\.(Main|Launcher)' }

if ($remaining) {
    Write-Warning "Some SeeLoggyPlus processes could not be stopped."
    exit 1
}

Write-Host "Done."
exit 0
