# Build the portable single-file corpus (ULTIMATE-SKILL.md) from SKILL.md + parts.
# Usage:  powershell -File scripts/build-single-file.ps1
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$enc  = New-Object System.Text.UTF8Encoding($false)
$out  = Join-Path $root "ULTIMATE-SKILL.md"
[System.IO.File]::WriteAllText($out, (Get-Content (Join-Path $root "SKILL.md") -Raw -Encoding UTF8), $enc)
foreach ($p in 'part-a','part-b','part-c','part-d','part-e','part-f') {
    $t = Get-Content (Join-Path $root "references\$p.md") -Raw -Encoding UTF8
    [System.IO.File]::AppendAllText($out, "`n`n" + $t, $enc)
}
Write-Host ("built ULTIMATE-SKILL.md  " + [math]::Round((Get-Item $out).Length/1KB) + " KB")
