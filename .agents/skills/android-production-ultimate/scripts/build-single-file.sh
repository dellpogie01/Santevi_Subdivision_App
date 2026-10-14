#!/usr/bin/env bash
# Build the portable single-file corpus (ULTIMATE-SKILL.md) from SKILL.md + parts.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/ULTIMATE-SKILL.md"
cp "$root/SKILL.md" "$out"
for p in part-a part-b part-c part-d part-e part-f; do
  printf '\n\n' >> "$out"
  cat "$root/references/$p.md" >> "$out"
done
echo "built ULTIMATE-SKILL.md  $(wc -c < "$out") bytes"
