#!/usr/bin/env bash
# Install for Claude Code / opencode / Cursor. Usage: bash scripts/install.sh [--opencode] [--project]
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
name="android-production-ultimate"
install_into() {
  local dst="$1/$name"
  mkdir -p "$dst/references"
  cp "$root/SKILL.md" "$dst/"
  cp "$root"/references/*.md "$dst/references/"
  echo "installed -> $dst"
}
install_into "$HOME/.claude/skills"
for arg in "$@"; do
  [[ "$arg" == "--opencode" ]] && install_into "$HOME/.config/opencode/skills"
  [[ "$arg" == "--project"  ]] && install_into "$PWD/.cursor/skills"
done
echo "restart your agent to activate the skill"
