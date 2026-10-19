# Security Model (draft, expanded in Phase 1)

## Assets
Finance records, tax documents, notes and attachments, work records, location stamps, AI API keys.

## Threats considered
Lost or stolen phone, malware or other apps, shoulder surfing and screenshots, cloud backup exposure, rooted device, forgotten passphrase, tampered backups.

## Controls
| Area | Control |
|---|---|
| Data at rest | SQLCipher (AES-256) database; per-file AES-256-GCM for attachments |
| Keys | Random data key, wrapped by a hardware-backed Android Keystore key and by an Argon2id key derived from the user passphrase |
| Recovery | One-time recovery code shown at setup, held by the user only; never stored in Drive |
| Access | Biometric/passphrase lock, auto-lock on background, `FLAG_SECURE` against screenshots |
| Backup | Android auto-backup disabled; Drive backup is encrypted on the device before upload and uses only the private `appDataFolder` |
| Network | Offline by default; TLS only; no analytics or trackers |
| AI | Local model by default; cloud providers opt-in, user-held API keys stored encrypted, user controls what data is sent |
| Destructive actions | Archive by default; hard delete needs authentication, typed confirmation and a 24 h cooling-off period |
| Supply chain | Pinned versions via the version catalog, dependency vulnerability scanning in CI |

## Out of scope
Protection against a fully compromised OS or an attacker with both the unlocked phone and the passphrase.
