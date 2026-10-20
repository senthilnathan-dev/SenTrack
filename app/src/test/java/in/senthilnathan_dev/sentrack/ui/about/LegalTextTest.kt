package `in`.senthilnathan_dev.sentrack.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the words the user reads honest and in step with the build:
 * the parser reads the shared text files; the published pages and CHANGELOG.md are up to date; the changelog's newest
 * release is the app's version; and the privacy policy mentions every permission and network service the app uses.
 */
class LegalTextTest {
    // Unit tests run with the app module as the working directory.
    private val root = File("..").canonicalFile
    private fun read(path: String) = File(root, path).readText()

    @Test
    fun parser_readsTopicsBlocksAndBullets() {
        val topics = LegalText.parse("# One\n\nIntro line\n\n## Heading\nfirst\nsecond\n\n- a\n- b\n\n# Two\n## Only heading\n")
        assertEquals(listOf("One", "Two"), topics.map { it.title })
        val blocks = topics[0].blocks
        assertEquals(null, blocks[0].heading)
        assertEquals("Intro line", blocks[0].text)
        assertEquals("Heading", blocks[1].heading)
        assertEquals("first\nsecond\n\n• a\n• b", blocks[1].text)
        assertEquals("Only heading", topics[1].blocks[0].heading)
    }

    @Test
    fun changelog_newestReleaseIsTheAppVersion() {
        val gradle = read("app/build.gradle.kts")
        val versionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(gradle)!!.groupValues[1]
        val releases = LegalText.parse(read("docs/legal/changelog.txt")).map { Release.from(it) }
        assertEquals("The first changelog entry must be the current app version", versionName, releases.first().version)
        assertTrue("CHANGELOG.md is out of date: run tools/make-legal-pages.sh", read("CHANGELOG.md").contains("## [$versionName]"))
    }

    @Test
    fun webPages_containEveryLineOfTheirSource() {
        val pairs = mapOf(
            "docs/legal/privacy.txt" to "docs/site/privacy-policy.html",
            "docs/legal/terms.txt" to "docs/site/terms.html",
            "docs/legal/manual.txt" to "docs/site/user-guide.html",
            "docs/legal/changelog.txt" to "docs/site/changelog.html",
        )
        pairs.forEach { (source, page) ->
            val html = read(page)
            read(source).lines().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { line ->
                val text = line.removePrefix("## ").removePrefix("# ").removePrefix("- ")
                val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                assertTrue("$page is out of date (missing: $text). Run tools/make-legal-pages.sh", html.contains(escaped))
            }
        }
    }

    @Test
    fun privacyPolicy_namesEveryNetworkServiceTheAppTalksTo() {
        val policy = read("docs/legal/privacy.txt")
        listOf("Google Drive", "Gemini", "OpenAI", "Anthropic", "Hugging Face").forEach {
            assertTrue("privacy policy does not mention $it", policy.contains(it))
        }
    }

    @Test
    fun privacyPolicy_coversEveryPermissionInTheManifests() {
        val policy = read("docs/legal/privacy.txt").lowercase()
        val keywordFor = mapOf(
            "INTERNET" to "network access",
            "ACCESS_NETWORK_STATE" to "network access",
            "POST_NOTIFICATIONS" to "notifications",
            "SCHEDULE_EXACT_ALARM" to "alarms",
            "RECEIVE_BOOT_COMPLETED" to "reminders",
            "VIBRATE" to "reminders",
            "ACCESS_FINE_LOCATION" to "location",
            "ACCESS_COARSE_LOCATION" to "location",
            "USE_BIOMETRIC" to "biometrics",
        )
        val manifests = listOf(
            "app/src/main/AndroidManifest.xml", "core-alerts/src/main/AndroidManifest.xml", "feature-work/src/main/AndroidManifest.xml",
            "core-security/src/main/AndroidManifest.xml", "core-sync/src/main/AndroidManifest.xml",
        ).map { File(root, it) }.filter { it.exists() }
        val permissions = manifests.flatMap { m ->
            Regex("""<uses-permission[^>]*android:name="android\.permission\.([A-Z_]+)"""").findAll(m.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        permissions.forEach { p ->
            val keyword = keywordFor[p]
            assertTrue("Permission $p is not covered in the privacy test map: describe it in docs/legal/privacy.txt and add it here", keyword != null)
            assertTrue("privacy policy does not mention '$keyword' (needed for $p)", policy.contains(keyword!!))
        }
    }
}
