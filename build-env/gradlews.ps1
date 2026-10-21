$steps = @(
    'clean build',
    'runGameTestServer',
    'generateDocs',
    'collectJars'
)

$results = [System.Collections.Generic.List[object]]::new()
foreach ($step in $steps) {
    Write-Host "=== gradlew $step ===" -ForegroundColor Cyan
    & .\gradlew.bat @($step -split ' ') | Out-Host
    $results.Add([pscustomobject]@{ Step = $step; ExitCode = $LASTEXITCODE })
}

Write-Host ''
Write-Host '=== Summary ===' -ForegroundColor Cyan
foreach ($r in $results) {
    if ($r.ExitCode -eq 0) {
        Write-Host ("PASS  {0}" -f $r.Step) -ForegroundColor Green
    } else {
        Write-Host ("FAIL  {0} (exit {1})" -f $r.Step, $r.ExitCode) -ForegroundColor Red
    }
}

if ($results | Where-Object { $_.ExitCode -ne 0 }) { exit 1 }
