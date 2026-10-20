package `in`.senthilnathan_dev.sentrack.ui.about

/*
 * What is left in code of the About pages. The privacy policy, terms, user guide and changelog are plain text files in
 * docs/legal (see LegalText), shared with the published web pages. If a change alters what data is stored, collected or
 * sent, update docs/legal/privacy.txt in the same commit and run tools/make-legal-pages.sh.
 */
internal object AboutContent {
    const val CONTACT = "libertariansenthil@gmail.com"

    /** A third-party component and the licence it comes under. */
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
        Library("LiteRT-LM (on-device AI runtime)", "Google", "Apache License 2.0"),
        Library("Gemma 4 E2B model (optional download)", "Google", "Apache License 2.0"),
        Library("Qwen 3 0.6B model (optional download)", "Alibaba Cloud", "Apache License 2.0"),
    )

    const val APACHE_NOTICE =
        "Components marked Apache License 2.0 are licensed under the Apache License, Version 2.0 (the \"License\"); you may not use them except in compliance with the License. You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
}
