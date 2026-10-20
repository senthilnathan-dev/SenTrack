# About, user guide, privacy, terms, changelog

## Where the words live
One source for every document a user can read:

| File (docs/legal) | Shown in the app | Published page (docs/site) |
|---|---|---|
| `privacy.txt` | Settings, About, Privacy policy | `privacy-policy.html` |
| `terms.txt` | Settings, About, Terms of use | `terms.html` |
| `manual.txt` | Settings, User guide (and About) | `user-guide.html` |
| `changelog.txt` | About, What's new, and a pop-up once after each update | `changelog.html`, and `CHANGELOG.md` at the repository root |

The app packages `docs/legal` as assets (`app/build.gradle.kts`), so what the user reads in the app is exactly the file in
the repository. `tools/make-legal-pages.sh` turns the same files into the web pages and `CHANGELOG.md`. Format: `# Title`
starts a topic (a manual page, a release), `## Heading` a block, blank lines separate paragraphs, `- ` lines are bullets.
In the changelog a release is `# 0.5.0 | 2026-10-20 | note`.

## Rules that keep it honest (enforced by `LegalTextTest`)
* The **first changelog entry must equal `versionName`** in `app/build.gradle.kts`, and `CHANGELOG.md` must contain it.
* The generated **web pages must contain every line** of their source, so a forgotten `make-legal-pages.sh` fails the tests.
* The **privacy policy must name every network service** the app talks to (Google Drive, Gemini, OpenAI, Anthropic, Hugging
  Face) and **cover every permission** declared in any manifest. Adding a permission or a service fails the test until the
  policy and the test's map are updated, which forces a privacy review.

## Changing something
1. Edit the file in `docs/legal`.
2. Run `bash tools/make-legal-pages.sh`.
3. Run the unit tests. Commit the source, the generated pages and `CHANGELOG.md` together.

## What's-new pop-up
After an update, the first unlock shows the notes of the installed version once (`WhatsNew.kt`). It is stored as a plain
preference (`sentrack_ui`, `last_seen_version_code`), holds nothing private, and is skipped on a fresh install.

## Still to do before a public release
* Publish `docs/site/*.html` at public URLs (for example GitHub Pages) and use the privacy URL in Play Console.
* Have the privacy policy and terms reviewed; add the developer's legal name.
* Replace the hand-written open-source list with generated notices that include full licence texts.
