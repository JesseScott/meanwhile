#!/usr/bin/env bash
# Bumps the version in version.properties for the next release: versionCode goes up by one, and so does the last
# part of versionName (0.1.11 becomes 0.1.12). Give a versionName to set it instead, for example for a minor release:
#
#   scripts/bump-version.sh          # 0.1.11 (11) -> 0.1.12 (12)
#   scripts/bump-version.sh 0.2.0    # 0.1.12 (12) -> 0.2.0 (13)
#
# Only the two values change; the comments and line endings are left as they are.
set -euo pipefail
cd "$(dirname "$0")/.."

file=version.properties
read_prop() { grep -E "^$1=" "$file" | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }
old_name=$(read_prop versionName)
old_code=$(read_prop versionCode)

if ! [[ "$old_code" =~ ^[0-9]+$ ]]; then echo "versionCode in $file is not a whole number: $old_code" >&2; exit 1; fi
new_code=$((old_code + 1))

if [ $# -gt 1 ]; then echo "Usage: $0 [versionName]" >&2; exit 1; fi
if [ $# -eq 1 ]; then
  new_name=$1
else
  if ! [[ "$old_name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "versionName in $file does not look like 1.2.3: $old_name. Give the new one: $0 <versionName>" >&2
    exit 1
  fi
  new_name="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((10#${BASH_REMATCH[3]} + 1))"
fi
if ! [[ "$new_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then echo "versionName must look like 1.2.3: $new_name" >&2; exit 1; fi

updated=$(sed -e "s/^versionName=.*/versionName=$new_name/" -e "s/^versionCode=.*/versionCode=$new_code/" "$file")
printf '%s\n' "$updated" > "$file"

echo "versionName: $old_name -> $new_name"
echo "versionCode: $old_code -> $new_code"
