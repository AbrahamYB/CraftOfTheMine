# Runs the screenshot harness (the ride/plot/settings scene, and the wall scene with -Pwall) for the given versions, one after another,
# and copies the frames to tools/shots-out/<version>[-Pwall]. Usage: powershell -File tools\screenshots_all.ps1 1.21.8 26.1
# Set RUN_WALL=0 to skip the wall scene. Run no other Gradle task meanwhile. A run that takes over 8 minutes is killed.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$versions)
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$out = Join-Path $PSScriptRoot 'shots-out'
New-Item -ItemType Directory -Force $out | Out-Null
foreach ($v in $versions) {
  $modes = if ($env:RUN_WALL -eq '0') { @('') } else { @('', '-Pwall') }
  foreach ($mode in $modes) {
    $args = @(":${v}:runAutoClient", '--console=plain'); if ($mode) { $args += $mode }
    $p = Start-Process -FilePath .\gradlew.bat -ArgumentList $args -WindowStyle Hidden -RedirectStandardOutput "$out\log_${v}$mode.txt" -RedirectStandardError "$out\err_${v}$mode.txt" -PassThru
    if (-not $p.WaitForExit(480000)) {
      Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'net.minecraft.client.main.Main|org.gradle.appname=gradlew' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    }
    $keep = Join-Path $out "$v$mode"
    New-Item -ItemType Directory -Force $keep | Out-Null
    Copy-Item "versions\$v\run-auto\screenshots\*" $keep -Force -ErrorAction SilentlyContinue
    "$v $mode : $((Get-ChildItem $keep | Measure-Object).Count) frames"
  }
}
