#!/usr/bin/env bash
# One-time: creates the Play upload key and the local keystore.properties that the release build reads.
#
#   bash tools/create-upload-key.sh
#
# The keystore is written OUTSIDE the repository (default: ~/sentrack-keys). keystore.properties in the repository root is
# git-ignored. Passwords are typed here and never echoed or logged. You need `keytool` (it is part of any JDK; Android Studio's
# bundled JDK has it under jbr/bin).
set -euo pipefail
cd "$(dirname "$0")/.."

keytool_bin=$(command -v keytool || true)
if [[ -z "$keytool_bin" && -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then keytool_bin="$JAVA_HOME/bin/keytool"; fi
if [[ -z "$keytool_bin" ]]; then
  echo "keytool not found. Install a JDK, or add Android Studio's jbr/bin to PATH (for example D:\\Android\\Studio\\jbr\\bin)." >&2
  exit 1
fi

default_dir="$HOME/sentrack-keys"
read -r -p "Folder for the keystore [$default_dir]: " dir
dir="${dir:-$default_dir}"
mkdir -p "$dir"
store="$dir/sentrack-upload.jks"
if [[ -e "$store" ]]; then echo "$store already exists. Refusing to overwrite it." >&2; exit 1; fi

read -r -p "Your name for the certificate [Senthilnathan]: " cn
cn="${cn:-Senthilnathan}"
read -r -s -p "Choose a keystore password (12+ characters): " pass; echo
read -r -s -p "Repeat it: " pass2; echo
if [[ "$pass" != "$pass2" || ${#pass} -lt 12 ]]; then echo "Passwords differ or are shorter than 12 characters." >&2; exit 1; fi

"$keytool_bin" -genkeypair -v -keystore "$store" -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$pass" -keypass "$pass" -dname "CN=$cn"

# keystore.properties: forward slashes so the same file works on Windows and Linux.
path_for_gradle=$(cd "$dir" && pwd -W 2>/dev/null || pwd)
{
  echo "storeFile=$path_for_gradle/sentrack-upload.jks"
  echo "storePassword=$pass"
  echo "keyAlias=upload"
  echo "keyPassword=$pass"
} > keystore.properties
chmod 600 keystore.properties 2>/dev/null || true

echo
echo "Created: $store"
echo "Wrote:   keystore.properties (git-ignored)"
echo
echo "IMPORTANT"
echo " 1. Back up $store and the password in TWO separate places (for example a password manager and an encrypted drive)."
echo " 2. Enrol in Play App Signing in Play Console. Google then holds the real app-signing key; this upload key can be reset if lost."
echo " 3. For GitHub Actions releases, store these as repository secrets (Settings > Secrets and variables > Actions):"
echo "      KEYSTORE_BASE64   = output of:  base64 -w0 \"$store\""
echo "      KEYSTORE_PASSWORD = the password you just chose"
echo "      KEY_ALIAS         = upload"
echo "      KEY_PASSWORD      = the same password"
