package `in`.senthilnathan_dev.sentrack.ui.about

/*
 * The words of the About, Privacy, Terms, Licenses and Changelog pages, kept apart from the screens so they can be
 * reviewed and edited without touching UI code. Anything here that describes what the app does must stay true to the
 * code: if a feature changes what data is collected or sent, update the privacy text in the same commit.
 */

/** A titled block of text. */
internal data class TextBlock(val heading: String?, val body: String)

internal object AboutContent {
    const val CONTACT = "libertariansenthil@gmail.com"

    val privacy = listOf(
        TextBlock(
            null,
            "SenTrack is a personal tracker for your finances, personal life, work and career. This page explains what it does with your information. The short version: your information stays on your phone, encrypted, and you decide if and where a backup goes.",
        ),
        TextBlock(
            "What SenTrack stores",
            "Everything you enter: accounts and transactions, notes, habits, plans, important dates, work records such as attendance, leave, tasks and appreciation, career records such as learning, certificates, job applications and resumes, reminders, and the files you attach. It is stored only on this phone.",
        ),
        TextBlock(
            "How it is protected",
            "Data is kept in an encrypted database and attached files in an encrypted file vault. Both are unlocked by a key that only your passphrase (or your fingerprint or face, if you turn that on) can open. SenTrack locks itself when the screen turns off or after the time you choose, and hides its contents from screenshots and the recent-apps view. SenTrack cannot read your data without your passphrase, and neither can we. If you lose both your passphrase and your recovery code, the data cannot be recovered.",
        ),
        TextBlock(
            "What leaves your phone",
            "Nothing, unless you choose it. SenTrack has no account, no advertising, no analytics and no tracking, and it does not send your data to the developer. The only network feature is the optional Google Drive backup. When you switch it on, SenTrack uploads an already-encrypted backup file to a private, hidden area of your own Google Drive that only SenTrack can open. Google receives that encrypted file and your Google account details under Google's own terms and privacy policy. Files you export or share yourself leave SenTrack in readable form, so share them with care.",
        ),
        TextBlock(
            "Permissions SenTrack may ask for",
            "Notifications and alarms, to ring your reminders (reminder text is generic on the lock screen unless you choose otherwise). Location, only at the moment you tap Punch in or Punch out, and only if you allow it; it is saved with that record on your phone and never tracked in the background. Network access, for the optional Drive backup. Biometrics, only to unlock with your fingerprint or face. SenTrack does not read your SMS, contacts or call history.",
        ),
        TextBlock(
            "Apps and sites you add to Workspace",
            "SenTrack remembers the names and addresses you add and how many times you opened each from SenTrack. It does not watch what you do inside those apps and sites.",
        ),
        TextBlock(
            "Your control",
            "You can export your attachments, back up and restore, archive items, or delete items and companies permanently. Uninstalling SenTrack removes its data from the phone; a Drive backup stays in your Drive until you remove it there.",
        ),
        TextBlock(
            "Changes and contact",
            "If this policy changes, the new version ships with the app update that needs it, and the changelog says so. Questions: $CONTACT.",
        ),
    )

    val terms = listOf(
        TextBlock(null, "By using SenTrack you agree to these terms. SenTrack is a personal record-keeping tool provided by its developer for your own use."),
        TextBlock(
            "Your data, your responsibility",
            "You are responsible for keeping your passphrase and recovery code safe. They are the only way to open your data, and SenTrack cannot reset them. Keep backups if the data matters to you.",
        ),
        TextBlock(
            "Not advice",
            "Calculations, summaries and reminders (including finance totals, gains and losses, tax-year grouping and leave balances) are aids to your own record-keeping. They are not financial, investment, tax, legal or employment advice, and may contain mistakes. Check important figures against official records before relying on them.",
        ),
        TextBlock(
            "Acceptable use",
            "Use SenTrack lawfully and only for information you have the right to keep. Do not try to break its protections or interfere with other people's use of Google services through it.",
        ),
        TextBlock(
            "No warranty",
            "SenTrack is provided as is, without warranties of any kind. To the fullest extent the law allows, the developer is not liable for lost data, missed reminders, incorrect figures or any indirect loss arising from its use. Phones can silence alarms or kill background work to save battery; do not rely on a reminder alone for anything critical.",
        ),
        TextBlock(
            "Third-party services and software",
            "The optional Google Drive backup is subject to Google's terms. SenTrack includes open-source software under its own licenses, listed under Open-source licenses.",
        ),
        TextBlock("Changes", "These terms may be updated with new versions of the app. Continuing to use SenTrack after an update means you accept the updated terms."),
        TextBlock("Contact", CONTACT),
    )

    /** One release note. [date] is free text so unreleased work can say "Unreleased". */
    data class Release(val version: String, val date: String, val notes: List<String>)

    val changelog = listOf(
        Release(
            "Unreleased", "in development",
            listOf(
                "Career: learning, certificates with renewal reminders, job hunt with interview reminders, resume versions, workspace launcher and onboarding/offboarding checklists.",
                "Work: companies, punch in and out with one-tap location, leave policy and calendar, tasks with a daily summary, appreciation with proof, permissions.",
                "Personal: plans, wishlist and bucket list, achievements, important dates with reminders, a unified timeline, habits with streaks, and a modern notes editor.",
                "Reminders that ring even while the app is locked.",
            ),
        ),
        Release("0.3.0", "Phase 3", listOf("Finance: accounts, transactions, budgets, goals, investments with gains, dashboard, tax filings and documents.")),
        Release("0.2.0", "Phase 2", listOf("Encrypted backup and restore with Google Drive, manual and scheduled.")),
        Release("0.1.0", "Phase 1", listOf("Security core: passphrase, recovery code, biometric unlock, encrypted database and file vault, auto-lock.")),
    )

    /** A third-party component and the license it comes under. */
    data class Library(val name: String, val by: String, val license: String)

    val libraries = listOf(
        Library("Kotlin and kotlinx.coroutines", "JetBrains", "Apache License 2.0"),
        Library("Jetpack Compose and Material 3", "Google (AndroidX)", "Apache License 2.0"),
        Library("Material Icons Extended", "Google", "Apache License 2.0"),
        Library("AndroidX Core, Activity, Fragment, Lifecycle", "Google (AndroidX)", "Apache License 2.0"),
        Library("Room", "Google (AndroidX)", "Apache License 2.0"),
        Library("WorkManager", "Google (AndroidX)", "Apache License 2.0"),
        Library("AndroidX Biometric", "Google (AndroidX)", "Apache License 2.0"),
        Library("SQLCipher for Android", "Zetetic LLC", "BSD-style license"),
        Library("Bouncy Castle (bcprov)", "The Legion of the Bouncy Castle", "MIT-style license"),
        Library("Google Play services Auth", "Google", "Android Software Development Kit License / Play services terms"),
    )

    const val APACHE_NOTICE =
        "Components marked Apache License 2.0 are licensed under the Apache License, Version 2.0 (the \"License\"); you may not use them except in compliance with the License. You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
}
