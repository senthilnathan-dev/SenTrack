#!/usr/bin/env bash
# One-time: creates the Play upload key and the local keystore.properties that the release build reads.
#
#   bash tools/create-upload-key.sh
#
# Run it in Git Bash or PowerShell-with-bash on Windows (best), or in Linux/macOS. WSL also works if `keytool` or `keytool.exe`
# is reachable. The keystore is written OUTSIDE the repository (default: a `sentrack-keys` folder next to it). The
# keystore.properties file in the repository root is git-ignored. Passwords are typed here and never echoed or logged.
# You need `keytool`, which is part of any JDK (Android Studio's bundled JDK has it under jbr/bin).
set -euo pipefail
cd "$(dirname "$0")/.."
repo=$(pwd)

# Find keytool: native first, then the Windows one from inside WSL.
keytool_bin=$(command -v keytool || command -v keytool.exe || true)
if [[ -z "$keytool_bin" && -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then keytool_bin="$JAVA_HOME/bin/keytool"; fi
if [[ -z "$keytool_bin" ]]; then
  echo "keytool not found." >&2
  echo "  Windows: open Git Bash (not WSL) and run this script there; or add Android Studio's jbr/bin to PATH (for example D:\\Android\\Studio\\jbr\\bin)." >&2
  echo "  WSL/Linux: install a JDK (sudo apt install default-jre-headless) and run again, but note the release build itself must run on Windows." >&2
  exit 1
fi

# WSL: keytool.exe and Gradle (on Windows) need Windows-style paths, so convert when `wslpath` exists.
in_wsl=false; command -v wslpath >/dev/null 2>&1 && in_wsl=true
win_path() { if $in_wsl; then wslpath -m "$1"; else (cd "$1" && pwd -W 2>/dev/null) || echo "$1"; fi; }
tool_path() { if $in_wsl && [[ "$keytool_bin" == *.exe ]]; then wslpath -w "$1"; else echo "$1"; fi; }

default_dir="$(dirname "$repo")/sentrack-keys"
read -r -p "Folder for the keystore (outside the repository) [$default_dir]: " dir
dir="${dir:-$default_dir}"
mkdir -p "$dir"
case "$(cd "$dir" && pwd)/" in "$repo"/*) echo "Choose a folder OUTSIDE the repository." >&2; exit 1 ;; esac
store="$dir/sentrack-upload.jks"
if [[ -e "$store" ]]; then echo "$store already exists. Refusing to overwrite it." >&2; exit 1; fi

read -r -p "Your name for the certificate [Senthilnathan]: " cn
cn="${cn:-Senthilnathan}"
read -r -s -p "Choose a keystore password (12+ characters): " pass; echo
read -r -s -p "Repeat it: " pass2; echo
if [[ "$pass" != "$pass2" || ${#pass} -lt 12 ]]; then echo "Passwords differ or are shorter than 12 characters." >&2; exit 1; fi

"$keytool_bin" -genkeypair -v -keystore "$(tool_path "$store")" -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$pass" -keypass "$pass" -dname "CN=$cn"

# keystore.properties: forward slashes and a Windows-style drive path so Gradle on Windows can read it.
gradle_dir=$(win_path "$dir")
{
  echo "storeFile=$gradle_dir/sentrack-upload.jks"
  echo "storePassword=$pass"
  echo "keyAlias=upload"
  echo "keyPassword=$pass"
} > keystore.properties
chmod 600 keystore.properties 2>/dev/null || true

echo
echo "Created: $store"
echo "Wrote:   keystore.properties (git-ignored), pointing at $gradle_dir/sentrack-upload.jks"
echo
echo "IMPORTANT"
echo " 1. Back up the keystore file and the password in TWO separate places (for example a password manager and an encrypted drive)."
echo " 2. Enrol in Play App Signing in Play Console. Google then holds the real app-signing key; this upload key can be reset if lost."
echo " 3. For GitHub Actions releases, store these as repository secrets (Settings > Secrets and variables > Actions):"
echo "      KEYSTORE_BASE64   = output of:  base64 -w0 \"$store\""
echo "      KEYSTORE_PASSWORD = the password you just chose"
echo "      KEY_ALIAS         = upload"
echo "      KEY_PASSWORD      = the same password"
echo " 4. Build the release in Git Bash or PowerShell on Windows:  bash tools/build-release.sh"
