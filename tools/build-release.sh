#!/usr/bin/env bash
# Builds the release artifacts after checking everything a release needs.
#
#   bash tools/build-release.sh
#
# Produces (in app/build/outputs):
#   bundle/release/app-release.aab   the file you upload to Google Play
#   apk/release/app-release.apk      the same app as an installable APK (signed only if keystore.properties exists)
#
# Steps: the working tree must be clean -> the web pages and CHANGELOG.md must be up to date -> the version is consistent ->
# unit tests -> release build. Install the signed APK on your phone and test it before uploading anything.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "STOP: $*" >&2; exit 1; }

[[ -z "$(git status --porcelain)" ]] || fail "uncommitted changes. Commit or stash them first."

bash tools/make-legal-pages.sh >/dev/null
[[ -z "$(git status --porcelain)" ]] || fail "docs/site or CHANGELOG.md were out of date and have just been regenerated. Review and commit them, then run again."

version=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
code=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
if [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  expected=$((BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3]))
  [[ "$code" == "$expected" ]] || fail "versionCode $code does not match $version (expected $expected). Use tools/bump-version.sh."
else
  fail "versionName '$version' is not X.Y.Z."
fi
first=$(sed -n 's/^# \([0-9][0-9.]*\) |.*/\1/p' docs/legal/changelog.txt | head -1)
[[ "$first" == "$version" ]] || fail "the newest changelog entry ($first) is not $version."

if [[ ! -f keystore.properties ]]; then
  echo "NOTE: keystore.properties not found. The release build will be UNSIGNED and cannot be uploaded to Play."
  echo "      Create the key once with: bash tools/create-upload-key.sh"
  read -r -p "Continue anyway? [y/N] " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]] || exit 1
fi

echo "== Unit tests"
./gradlew test --console=plain
echo "== Release build"
./gradlew :app:bundleRelease :app:assembleRelease --console=plain

aab=app/build/outputs/bundle/release/app-release.aab
apk=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1 || true)
echo
echo "Version $version (build $code)"
[[ -f "$aab" ]] && { echo "AAB: $aab"; (sha256sum "$aab" 2>/dev/null || shasum -a 256 "$aab") | cut -d' ' -f1 | sed 's/^/     sha256 /'; }
[[ -n "$apk" ]] && echo "APK: $apk"
echo
echo "Next: install the APK on your phone (adb install -r <apk>), test every tab, then upload the AAB (docs/13-release-checklist.md)."
