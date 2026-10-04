#!/usr/bin/env bash
# Builds the signed release bundle for the version in version.properties and attaches it, with the R8 mapping
# file, to the GitHub release for that version.
#
#   1. Merge the version bump to main. The Release workflow tags it and creates the release.
#   2. git checkout main && git pull
#   3. scripts/release.sh
#
# Needs: the upload key (keystore.properties, with the vault unlocked) and the GitHub CLI (gh), signed in.
set -euo pipefail
cd "$(dirname "$0")/.."

read_prop() { grep -E "^$1=" version.properties | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }
name=$(read_prop versionName)
code=$(read_prop versionCode)
tag="v${name}-${code}"

if ! gh release view "$tag" >/dev/null 2>&1; then
  echo "There is no GitHub release for $tag yet. Merge the version bump to main first and let the Release workflow run." >&2
  exit 1
fi

# The bundle must be built from the commit the tag points at, or the release lies about what is in the file.
git fetch --tags --quiet
if [ "$(git rev-parse "$tag^{commit}")" != "$(git rev-parse HEAD)" ]; then
  echo "HEAD is not the commit $tag points at. Check out the tag (git checkout $tag) and run this again." >&2
  exit 1
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "The working tree has uncommitted changes. Commit or stash them first." >&2
  exit 1
fi

./gradlew :app:bundleRelease --console=plain

aab="app/build/outputs/bundle/release/meanwhile-${name}-${code}-release.aab"
mapping="app/build/outputs/mapping/release/mapping.txt"
[ -f "$aab" ] || { echo "Expected $aab but it was not built." >&2; exit 1; }

# A named copy, so downloads from the release say which version they belong to.
named_mapping="$(mktemp -d)/meanwhile-${name}-${code}-mapping.txt"
cp "$mapping" "$named_mapping"

gh release upload "$tag" "$aab" "$named_mapping" --clobber
echo "Attached $(basename "$aab") and the mapping file to $tag."
