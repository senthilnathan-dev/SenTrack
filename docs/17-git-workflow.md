# Git workflow

## How history was made so far
The first 0.x releases were built by one developer working with an AI assistant, using **phase branches** merged into `main`
with fast-forward merges and annotated tags:

| Tag | Contents |
|---|---|
| `v0.1.0` | Security core (vault, lock, biometric, file vault) |
| `v0.2.0` | Encrypted Google Drive backup |
| `v0.3.0` | Finance |
| `v0.4.0` | Reminders, Personal, Work, Career, About, AI |
| `v0.5.0` | First internal-testing build (tagged when released) |

Commits used Conventional Commit messages (`feat(...)`, `fix(...)`, `docs`, `chore`) and AI-assisted commits carry a
`Co-Authored-By:` trailer. The repository had no remote until it was prepared for GitHub, so there is no PR history yet.

## Going forward: short-lived branches, pull requests, squash merge
`main` is always buildable and releasable. Everything else happens on a branch.

```
main ──●──────────●──────────●───────────●── (tag v0.5.0)  (tag v1.0.0)
        \        /  squash   \         /
         feat/x ─●─●─●        fix/y ──●
```

1. **Start**: `git switch main && git pull --ff-only && git switch -c feat/short-description`
2. **Work in small commits** (Conventional Commits; see [CONTRIBUTING.md](../CONTRIBUTING.md)). Push the branch to open a PR.
3. **Open a pull request** against `main` using the template. CI must be green (legal pages current, unit tests, lint, debug build).
4. **Review**: at least one approval (the owner reviews everything; `CODEOWNERS` lists sensitive paths).
5. **Squash and merge**. The PR title is the commit message on `main`, so it must be a valid Conventional Commit. Delete the branch.
6. **Never** commit directly to `main`, force-push `main`, or rewrite history that others may have.

### Keeping a branch current
`git fetch && git rebase origin/main` (before the PR is reviewed) or merge `main` in if others already commented on your commits.
Resolve conflicts in generated files by regenerating them (`bash tools/make-legal-pages.sh`), and in `core-data/schemas` by
keeping every version file. If two branches both bump the database version, the second one renumbers on top of the first.

### Working with an AI agent
One agent, one branch, one task. Give it the issue, tell it to read `AGENTS.md`, and review its PR like any other. Agents do not
push, tag or publish unless you explicitly ask; local commits on a feature branch are fine.

## Versions and releases
- Semantic versions `X.Y.Z`. `0.x` are testing builds; `1.0.0` is the first public release; `1.x` adds features and fixes.
- `bash tools/bump-version.sh X.Y.Z` sets `versionName`, `versionCode` (= X*10000 + Y*100 + Z) and a changelog stub. Fill in the
  changelog, regenerate pages, run tests, open a `chore(release): X.Y.Z` PR.
- After that PR merges: `git switch main && git pull --ff-only && git tag -a vX.Y.Z -m "SenTrack X.Y.Z" && git push origin vX.Y.Z`.
  The tag triggers the `Release` workflow, which builds the signed bundle and APK and drafts a GitHub release. Upload the bundle to
  Play Console ([13-release-checklist](13-release-checklist.md)).
- **Hotfix**: branch from the release tag (`git switch -c fix/urgent vX.Y.Z`), fix, PR to `main`, release `X.Y.(Z+1)`. If `main` has
  moved on, cherry-pick the fix there as well.

## Issues, labels and milestones
Use the issue forms. Labels: `bug`, `enhancement`, `docs`, `security`, `good first issue`, `help wanted`, `needs-info`, `blocked`,
`question`, plus module labels (`finance`, `personal`, `work`, `career`, `ai`, `backup`, `reminders`, `build`). Milestones: `0.5.0`
(internal testing), `1.0.0` (public), `1.x` (improvements and fixes). Link PRs with `Closes #N`.

## Recommended GitHub settings (when the repository is created)
- Default branch `main`; branch protection: require a pull request, require the `CI / check` status check, require linear
  history, block force pushes and deletion.
- Allow squash merging only; automatically delete merged branches.
- Enable Dependabot alerts and security updates, secret scanning with push protection, and private vulnerability reporting.
- Add the four release secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) under Settings, Secrets
  and variables, Actions. Never put them in the repository.
- Create the labels and milestones listed above.
- Replace the placeholder owner name `senthilnathan-dev` in `.github/CODEOWNERS` and `.github/ISSUE_TEMPLATE/config.yml` with the
  real GitHub account or organisation and repository name.
