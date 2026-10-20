#!/usr/bin/env bash
# Sets the app version in one step.
#
#   bash tools/bump-version.sh 0.6.0
#
# - versionName = the given X.Y.Z
# - versionCode = X*10000 + Y*100 + Z (so 0.5.0 -> 500, 1.0.0 -> 10000, 1.2.3 -> 10203). Play requires it to increase with every
#   upload; this rule guarantees that as long as the version number increases.
# - adds an empty release entry at the top of docs/legal/changelog.txt (if there is none yet) for you to fill in
# - regenerates the web pages and CHANGELOG.md
#
# It only edits files. Review with `git diff`, fill in the changelog, run the tests, then commit.
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:-}"
if [[ ! "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Usage: bash tools/bump-version.sh X.Y.Z   (for example 1.0.0)" >&2
  exit 1
fi
major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[2]}"; patch="${BASH_REMATCH[3]}"
code=$((major * 10000 + minor * 100 + patch))

gradle=app/build.gradle.kts
current=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$gradle")
current_code=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$gradle")
if [[ "$version" == "$current" ]]; then echo "Already at $version."; exit 0; fi
if (( code <= current_code )); then
  echo "New versionCode $code must be greater than the current $current_code." >&2
  exit 1
fi

sed -i "s/\(versionName = \)\"[^\"]*\"/\1\"$version\"/" "$gradle"
sed -i "s/\(versionCode = \)[0-9]*/\1$code/" "$gradle"

changelog=docs/legal/changelog.txt
if ! grep -q "^# $version |" "$changelog"; then
  today=$(date +%Y-%m-%d)
  { printf '# %s | %s | \n## Added\n- \n## Changed\n- \n## Fixed\n- \n\n' "$version" "$today"; cat "$changelog"; } > "$changelog.tmp"
  mv "$changelog.tmp" "$changelog"
fi

bash tools/make-legal-pages.sh
echo
echo "Version is now $version (build $code)."
echo "Next: fill in the $version entry in docs/legal/changelog.txt (delete empty sections), run: bash tools/make-legal-pages.sh"
echo "then run the tests and commit."
