#!/usr/bin/env bash
# Builds the published web pages and CHANGELOG.md from the plain-text documents in docs/legal.
#
#   docs/legal/privacy.txt   -> docs/site/privacy-policy.html
#   docs/legal/terms.txt     -> docs/site/terms.html
#   docs/legal/manual.txt    -> docs/site/user-guide.html
#   docs/legal/changelog.txt -> docs/site/changelog.html and CHANGELOG.md (repository root)
#
# The same text files are packaged inside the app, so the app and the web never differ. Run this after editing any file
# in docs/legal, then commit the result. A unit test (ReleaseConsistencyTest) fails if the pages are out of date.
#
# Text format: "# Title" starts a topic, "## Heading" a block, blank lines separate paragraphs, "- " lines are bullets.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p docs/site

html_page() {   # html_page <input.txt> <output.html> <page title>
  awk -v title="$3" '
    function esc(s) { gsub(/&/, "\\&amp;", s); gsub(/</, "\\&lt;", s); gsub(/>/, "\\&gt;", s); return s }
    function close_blocks() { if (inpara) { print "</p>"; inpara = 0 } if (inlist) { print "</ul>"; inlist = 0 } }
    BEGIN {
      print "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">"
      print "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
      print "<title>" title " - SenTrack</title>"
      print "<style>body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.6;color:#1b1f23;background:#fff;margin:0}"
      print "main{max-width:46rem;margin:0 auto;padding:1.5rem 1.25rem 4rem}h1{font-size:1.8rem;margin:1.5rem 0 .5rem}"
      print "h2{font-size:1.2rem;margin:2rem 0 .4rem;color:#0b3c49}p,li{margin:.5rem 0}ul{padding-left:1.4rem}"
      print "footer{margin-top:3rem;font-size:.85rem;color:#57606a}@media(prefers-color-scheme:dark){body{background:#111418;color:#e6e8ea}h2{color:#7fd0e3}footer{color:#9aa4ad}}</style>"
      print "</head>\n<body>\n<main>"
    }
    { sub(/\r$/, "") }
    /^## / { close_blocks(); print "<h2>" esc(substr($0, 4)) "</h2>"; next }
    /^# /  { close_blocks(); print "<h1>" esc(substr($0, 3)) "</h1>"; next }
    /^[[:space:]]*$/ { close_blocks(); next }
    /^- / {
      if (inpara) { print "</p>"; inpara = 0 }
      if (!inlist) { print "<ul>"; inlist = 1 }
      print "<li>" esc(substr($0, 3)) "</li>"; next
    }
    {
      if (inlist) { print "</ul>"; inlist = 0 }
      if (!inpara) { printf "<p>%s", esc($0); inpara = 1 } else { printf " %s", esc($0) }
      printf "\n"
    }
    END {
      close_blocks()
      print "<footer>SenTrack. Contact: libertariansenthil@gmail.com</footer>\n</main>\n</body>\n</html>"
    }
  ' "$1" > "$2"
}

html_page docs/legal/privacy.txt   docs/site/privacy-policy.html "Privacy policy"
html_page docs/legal/terms.txt     docs/site/terms.html          "Terms of use"
html_page docs/legal/manual.txt    docs/site/user-guide.html     "User guide"
html_page docs/legal/changelog.txt docs/site/changelog.html      "What's new"

# CHANGELOG.md in the Keep a Changelog layout.
{
  printf '# Changelog\n\nAll notable changes to SenTrack. Generated from docs/legal/changelog.txt by tools/make-legal-pages.sh; edit that file, not this one.\n\n'
  awk '
    { sub(/\r$/, "") }
    /^# / {
      n = split(substr($0, 3), p, " \\| ")
      printf "## [%s] - %s", p[1], p[2]; if (n >= 3 && p[3] != "") printf " (%s)", p[3]; printf "\n"; next
    }
    /^## / { printf "### %s\n", substr($0, 4); next }
    { print }
  ' docs/legal/changelog.txt
} > CHANGELOG.md

echo "Updated docs/site/*.html and CHANGELOG.md"
