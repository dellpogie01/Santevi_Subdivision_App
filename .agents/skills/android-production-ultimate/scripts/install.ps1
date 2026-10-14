# Install this skill for your AI agent(s). Usage:
#   .\scripts\install.ps1            → install to ~/.claude/skills (Claude Code + external-skill scanners)
#   .\scripts\install.ps1 -Opencode  → also install to ~/.config/opencode/skills
#   .\scripts\install.ps1 -Project   → also install to ./.cursor/skills of current dir
param([switch]$Opencode, [switch]$Project)
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$name = 'android-production-ultimate'
function Install-Into($dir) {
    $dst = Join-Path $dir $name
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Copy-Item (Join-Path $root 'SKILL.md') $dst -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $dst 'references') | Out-Null
    Copy-Item (Join-Path $root 'references\*') (Join-Path $dst 'references') -Force
    Write-Host "installed -> $dst"
}
Install-Into (Join-Path $HOME '.claude\skills')
if ($Opencode)  { Install-Into (Join-Path $HOME '.config\opencode\skills') }
if ($Project)   { Install-Into (Join-Path (Get-Location) '.cursor\skills') }
Write-Host 'restart your agent to activate the skill'
