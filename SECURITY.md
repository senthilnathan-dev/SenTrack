# Security policy

SenTrack stores private financial, personal, work and career information, so security reports are taken seriously.

## Reporting a vulnerability
**Do not open a public issue.** Report privately by either:
- GitHub: *Security* tab, *Report a vulnerability* (private advisory), or
- Email: **libertariansenthil@gmail.com** with the subject `SenTrack security`.

Please include: what you found, the steps to reproduce, the app version (Settings, About), your Android version and device,
and the impact you believe it has. Do not include real personal data.

You can expect an acknowledgement within **5 days** and a first assessment within **14 days**. Fixes ship in the next release
(urgent ones as a patch release), and you will be credited if you wish.

## Supported versions
| Version | Supported |
|---|---|
| Latest release | Yes |
| Earlier 0.x test builds | No: update to the latest |

## In scope
Weaknesses in encryption or key handling, lock bypass, data exposure outside the encrypted storage, backup or restore flaws,
unsafe file or link handling, the AI request or model download path, permission misuse, and anything that sends data off the
device without the user's choice.

## Out of scope
Attacks that need a fully compromised, rooted or unlocked-and-unlocked phone with the passphrase known, problems in Android,
Google Drive or the AI providers themselves, and denial of service by filling your own phone's storage.

## How the app is designed
See [docs/02-security-model.md](docs/02-security-model.md) and [docs/12-security-review.md](docs/12-security-review.md).

## For contributors
Never commit secrets, keystores, API keys or real user data. If you accidentally do, tell the maintainer immediately so the
secret can be revoked; deleting a commit does not remove it from history.
