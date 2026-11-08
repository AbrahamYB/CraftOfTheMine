# Logs the running Minecraft's heap use every minute and saves a class histogram every 30 minutes (and whenever the heap passes 80%).
# Compare an early histogram with a late one: the classes whose bytes grew are the leak. Output goes to tools/memwatch-out/.
param([int]$ProcId = 0, [int]$MaxHeapMb = 7000)

$jcmd = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\jcmd.exe"
if ($ProcId -eq 0) { $ProcId = (Get-Process javaw | Sort-Object StartTime -Descending | Select-Object -First 1).Id }
$out = Join-Path $PSScriptRoot "memwatch-out"
New-Item -ItemType Directory -Force $out | Out-Null
$csv = Join-Path $out "heap.csv"
"time,heap_used_mb,heap_committed_mb,working_set_mb" | Set-Content $csv
$lastHisto = [datetime]::MinValue
while (Get-Process -Id $ProcId -ErrorAction SilentlyContinue) {
    $info = & $jcmd $ProcId GC.heap_info 2>$null | Select-String 'garbage-first heap\s+total (\d+)K, used (\d+)K'
    if ($info) {
        $committed = [int]($info.Matches[0].Groups[1].Value) / 1024
        $used = [int]($info.Matches[0].Groups[2].Value) / 1024
        $ws = [int]((Get-Process -Id $ProcId).WorkingSet64 / 1MB)
        "{0},{1:F0},{2:F0},{3}" -f (Get-Date -Format s), $used, $committed, $ws | Add-Content $csv
        if ($used -gt 0.8 * $MaxHeapMb -or ((Get-Date) - $lastHisto).TotalMinutes -ge 30) {
            $lastHisto = Get-Date
            & $jcmd $ProcId GC.class_histogram 2>$null | Select-Object -First 60 | Set-Content (Join-Path $out ("histo-" + (Get-Date -Format "HHmm") + ".txt"))
        }
    }
    Start-Sleep -Seconds 60
}
