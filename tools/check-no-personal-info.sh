#!/usr/bin/env bash
# Fails if the repository contains a personal email address, a device serial or similar identifying detail.
#
#   bash tools/check-no-personal-info.sh
#
# Checks (1) every tracked text file and (2) the author and committer email of recent commits. Allowed addresses are GitHub
# no-reply addresses, the reserved example/invalid domains, and an explicit allow-list below. Run by CI on every pull request.
set -euo pipefail
cd "$(dirname "$0")/.."

# Email-like text that is fine: GitHub no-reply addresses and reserved placeholder domains.
allowed='@users\.noreply\.github\.com|@example\.(com|org|net)|@[a-z0-9.-]*\.invalid|^noreply@'

status=0

echo "== Tracked files: email addresses"
matches=$(git grep -nIE '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}' -- . ':(exclude)gradle/wrapper/*' ':(exclude)*.jar' \
  | grep -viE "$allowed" || true)
if [[ -n "$matches" ]]; then
  echo "$matches" | sed 's/^/  /'
  echo "Remove these (use a GitHub no-reply address or no address at all)." >&2
  status=1
else
  echo "  none"
fi

echo "== Tracked files: device serials, local paths, IDE and assistant settings"
other=$(git grep -nIiE 'serial=[A-Z0-9]{8,}|C:\\\\Users\\\\|/Users/[A-Za-z]+/|/home/[a-z]+/|adb-[A-Za-z0-9]+-[A-Za-z0-9]+\._adb' -- . ':(exclude)tools/check-no-personal-info.sh' || true)
tracked=$(git ls-files | grep -E '^(\.idea|\.claude)/|local\.properties$|keystore\.properties$|\.(jks|keystore|p12|pem)$' || true)
if [[ -n "$other$tracked" ]]; then
  [[ -n "$other" ]] && echo "$other" | sed 's/^/  /'
  [[ -n "$tracked" ]] && echo "$tracked" | sed 's/^/  tracked file that must not be committed: /'
  status=1
else
  echo "  none"
fi

echo "== Recent commits: author and committer emails"
bad=$(git log -n 500 --format='%ae%n%ce' | sort -u | grep -viE "$allowed" | grep -v '^$' || true)
if [[ -n "$bad" ]]; then
  echo "$bad" | sed 's/^/  /'
  echo "Commits carry a personal email. Set a repo-local no-reply identity: git config user.email \"<id>+<name>@users.noreply.github.com\"" >&2
  status=1
else
  echo "  only no-reply addresses"
fi

[[ $status -eq 0 ]] && echo "OK: no personal information found." || echo "FAILED." >&2
exit $status
