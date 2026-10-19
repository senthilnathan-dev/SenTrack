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

## Implementation status
### Phase 1 (done, verified on device)
- Vault: random 256-bit data key wrapped by passphrase (Argon2id 64 MiB x3), recovery code (HKDF) and optional biometric Keystore key (per-use auth, invalidated on new enrollment)
- Database: Room + SQLCipher with a raw key derived from the data key; verified on a Samsung A34 (Android 16): the file header is random bytes, no `SQLite format` marker, no plaintext strings
- Auto-lock on screen off and after 30 s in background; `FLAG_SECURE`; brute-force back-off after 5 failures
- Known limits: passphrase is held in a Compose String while typed (cannot be wiped); back-off counter is in memory only (offline attack resistance comes from Argon2id)
- File vault: per-file random key wrapped by a vault sub-key and bound to the file id; 64 KiB AES-GCM chunks with a final-chunk flag (detects tampering, truncation, extension, swapping); opaque UUID names
- Settings: configurable auto-lock (immediately, 30 s, 1 min, 5 min); change-passphrase screen re-wraps the key without re-encrypting data
