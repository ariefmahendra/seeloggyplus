param(
    [ValidateSet('before','after')][string]$Phase = 'after',
    [ValidateSet('soak','kernel')][string]$Kind = 'soak',
    [int]$SoakSeconds = 1800,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (!$JavaHome) { $JavaHome = Split-Path (Split-Path (Get-Command java.exe).Source -Parent) -Parent }
$javaExecutable = Join-Path $JavaHome 'bin/java.exe'
& .\gradlew.bat --no-daemon performanceClasspath
if ($LASTEXITCODE -ne 0) { throw 'Could not compile performance harness' }
$performanceRoot = Join-Path $projectRoot 'build/performance'
$artifactName = if ($Phase -eq 'before') {'baseline'} else {'optimized'}
$artifact = Join-Path $performanceRoot $artifactName
if (!(Test-Path -LiteralPath $artifact)) {
    New-Item -ItemType Directory -Path $artifact | Out-Null
    Copy-Item -LiteralPath 'build/classes/java/main' -Destination (Join-Path $artifact 'classes') -Recurse
    Copy-Item -LiteralPath 'build/resources/main' -Destination (Join-Path $artifact 'resources') -Recurse
    $manifest = rg --files src/main | ForEach-Object {
        [pscustomobject]@{path=$_; sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}
    }
    $manifest | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $artifact 'manifest.json')
}
$outputName = if ($Kind -eq 'soak') {$Phase} else {"$Phase-kernel"}
$output = Join-Path $performanceRoot $outputName
if (Test-Path -LiteralPath (Join-Path $output 'pid.txt')) {
    $profileProcessId = [int](Get-Content -LiteralPath (Join-Path $output 'pid.txt'))
    if (Get-Process -Id $profileProcessId -ErrorAction SilentlyContinue) { throw "Profiler $profileProcessId is already running" }
}
if ((Test-Path -LiteralPath (Join-Path $output 'result.json')) -or (Test-Path -LiteralPath (Join-Path $output 'kernel.json'))) {
    throw "Completed measurements already exist in $output"
}
New-Item -ItemType Directory -Force -Path $output | Out-Null
$runtimeClasspath = (Get-Content -LiteralPath (Join-Path $performanceRoot 'runtime-classpath.txt') -Raw).Trim()
$runtimeClasspath = $runtimeClasspath.Replace((Join-Path $projectRoot 'build/classes/java/main').Replace('/','\'),(Join-Path $artifact 'classes').Replace('/','\'))
$runtimeClasspath = $runtimeClasspath.Replace((Join-Path $projectRoot 'build/resources/main').Replace('/','\'),(Join-Path $artifact 'resources').Replace('/','\'))
$runner = if ($Kind -eq 'soak') {'com.seeloggyplus.performance.PerformanceProfile'} else {'com.seeloggyplus.performance.SearchKernelProfile'}
$jvmArguments = @('-Xms128m','-Xmx4g','-XX:+UseG1GC','-XX:NativeMemoryTracking=summary',
    '-Dseeloggyplus.disableUpdateCheck=true',"-Dseeloggyplus.dataDir=$performanceRoot/data-$Phase",
    '-Dtestfx.headless=true','-Dglass.platform=Monocle','-Dmonocle.platform=Headless',
    '-Dheadless.geometry=1920x1080','-Dprism.order=sw','-Djava.awt.headless=true',
    '--class-path',$runtimeClasspath,$runner,$output,"$SoakSeconds")
$argumentFile = Join-Path $performanceRoot "$outputName.args"
$quotedArguments = $jvmArguments | ForEach-Object {'"' + $_.Replace('\','\\').Replace('"','\"') + '"'}
[System.IO.File]::WriteAllLines($argumentFile, [string[]]$quotedArguments, [System.Text.UTF8Encoding]::new($false))
$process = Start-Process -FilePath $javaExecutable -ArgumentList ('@"' + $argumentFile + '"') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output 'console.log') -RedirectStandardError (Join-Path $output 'errors.log')
$process.Id | Set-Content -LiteralPath (Join-Path $output 'pid.txt')
[pscustomobject]@{pid=$process.Id;phase=$Phase;kind=$Kind;java=$javaExecutable;output=$output;artifact=$artifact} | ConvertTo-Json
