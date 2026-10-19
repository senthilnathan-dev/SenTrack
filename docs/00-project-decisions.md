# SenTrack: Project Decisions (as of 2026-10-19)

Personal tracker for finance, personal life and work. Single user, Android only.

## Fixed decisions
- Stack: Kotlin + Jetpack Compose, clean architecture, MVVM, Hilt, Room on SQLCipher
- Package ID: `in.senthilnathan_dev.sentrack` (immutable after publishing)
- Target: Android 16 (API 36); test device Samsung A34 5G, Wi-Fi debugging
- Offline-first; Google Drive `appDataFolder` encrypted backup/restore, manual and scheduled
- Sync model: single active device (no multi-device merge)
- Currency INR; manual ledger only (no live prices, no SMS/bank reading, no Excel import)
- Tax filings: stored as encrypted PDFs with metadata, not parsed
- Notes: rich text, images, video, attachments, folders and tags
- Punch location captured only on tap (no background location)
- Workspace: manually chosen apps/sites, usage counted from inside the app
- Recovery: user-held recovery code; key never leaves the device unencrypted
- Deletion of companies/major records: archive by default; hard delete needs biometric/passphrase, typed name, 24h cooling-off
- AI: provider abstraction; local model default; Gemini/OpenAI/Claude optional, off by default, user-supplied keys, user-controlled data scope
- UI: light/dark/system, English only

## Phases
0 Setup, 1 Security core, 2 Sync, 3 Finance, 4 Personal, 5 Work-company, 6 Work-career, 7 Alerts, 8 AI, 9 Legal/About, 10 Hardening, 11 Release

## Tool locations (all on D:)
- Android Studio: `D:\Android\Studio`
- Android SDK: `D:\Android\Sdk`
- Gradle cache: `D:\Android\.gradle` (env `GRADLE_USER_HOME`)
- Project: `D:\Projects\SenTrack`
