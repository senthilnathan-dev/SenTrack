package `in`.senthilnathan_dev.sentrack.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.NavRow
import `in`.senthilnathan_dev.sentrack.core.ui.Section

private enum class AboutPage { MAIN, PRIVACY, TERMS, LICENSES, CHANGELOG, MANUAL, MANUAL_TOPIC }

/** Where About opens: its main page or straight to the user guide. */
enum class AboutStart { MAIN, MANUAL }

/** The app's version, read from the installed package so it can never disagree with the build. */
internal data class VersionInfo(val name: String, val code: Long)

@Composable
internal fun rememberVersion(): VersionInfo {
    val context = LocalContext.current
    return remember { currentVersion(context) }
}

internal fun currentVersion(context: android.content.Context): VersionInfo = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    VersionInfo(info.versionName ?: "?", info.longVersionCode)
}.getOrDefault(VersionInfo("?", 0))

/**
 * About SenTrack, the user guide and the legal pages (privacy policy, terms, open-source licenses, changelog). Keeps
 * its own page state; [onBack] leaves for the screen it was opened from. The texts are read from the packaged files in
 * docs/legal.
 */
@Composable
fun AboutHost(onBack: () -> Unit, start: AboutStart = AboutStart.MAIN) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(if (start == AboutStart.MANUAL) AboutPage.MANUAL else AboutPage.MAIN) }
    var topic by rememberSaveable { mutableIntStateOf(0) }
    val version = rememberVersion()
    val home = if (start == AboutStart.MANUAL) AboutPage.MANUAL else AboutPage.MAIN
    BackHandler {
        when (page) {
            AboutPage.MANUAL_TOPIC -> page = AboutPage.MANUAL
            home -> onBack()
            else -> page = AboutPage.MAIN
        }
    }
    val manual = remember { LegalText.load(context, "manual.txt") }

    when (page) {
        AboutPage.MAIN -> AppScreen("About SenTrack", onBack = onBack) {
            Section("SenTrack") {
                Text("Version ${version.name} (build ${version.code})", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A private tracker for your money, your life, your work and your career. Your data stays on your phone, encrypted, and works without a connection.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Made by Senthilnathan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section("Help and legal") {
                NavRow("User guide", "How to use every part of SenTrack", { page = AboutPage.MANUAL }, Icons.AutoMirrored.Filled.MenuBook)
                NavRow("What's new", "Release notes for every version", { page = AboutPage.CHANGELOG }, Icons.Filled.History)
                NavRow("Privacy policy", "What is stored and what leaves your phone", { page = AboutPage.PRIVACY }, Icons.Filled.Lock)
                NavRow("Terms of use", null, { page = AboutPage.TERMS }, Icons.Filled.Gavel)
                NavRow("Open-source licenses", "Software SenTrack is built on", { page = AboutPage.LICENSES }, Icons.Filled.Policy)
            }
            Section("Contact") {
                Text(AboutContent.CONTACT, style = MaterialTheme.typography.bodyLarge)
            }
        }

        AboutPage.PRIVACY -> DocumentPage("privacy.txt", "Privacy policy", onBack = { page = AboutPage.MAIN })
        AboutPage.TERMS -> DocumentPage("terms.txt", "Terms of use", onBack = { page = AboutPage.MAIN })

        AboutPage.MANUAL -> AppScreen("User guide", onBack = { if (start == AboutStart.MANUAL) onBack() else page = AboutPage.MAIN }) {
            if (manual.isEmpty()) Section("User guide") { Text("The guide could not be loaded.") }
            Section("Topics") {
                manual.forEachIndexed { i, t ->
                    NavRow(t.title, t.blocks.mapNotNull { it.heading }.take(3).joinToString(", "), { topic = i; page = AboutPage.MANUAL_TOPIC })
                }
            }
        }

        AboutPage.MANUAL_TOPIC -> {
            val t = manual.getOrNull(topic)
            AppScreen(t?.title ?: "User guide", onBack = { page = AboutPage.MANUAL }) {
                t?.blocks?.forEach { b -> Section(b.heading ?: t.title) { Text(b.text, style = MaterialTheme.typography.bodyMedium) } }
            }
        }

        AboutPage.LICENSES -> AppScreen("Open-source licenses", onBack = { page = AboutPage.MAIN }) {
            Section("Built on", "Thank you to the people behind these projects.") {
                AboutContent.libraries.forEachIndexed { i, lib ->
                    if (i > 0) HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(lib.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${lib.by} · ${lib.license}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Section("License notice") { Text(AboutContent.APACHE_NOTICE, style = MaterialTheme.typography.bodySmall) }
        }

        AboutPage.CHANGELOG -> {
            val releases = remember { LegalText.load(context, "changelog.txt").map { Release.from(it) } }
            AppScreen("What's new", onBack = { page = AboutPage.MAIN }) {
                releases.forEach { r ->
                    Section(listOf(r.version, r.date).filter { it.isNotBlank() }.joinToString("  ·  "), r.label.ifBlank { null }) {
                        r.sections.forEach { b ->
                            b.heading?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                            Text(b.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** A document with one topic (privacy policy, terms), shown as a card per heading. */
@Composable
private fun DocumentPage(file: String, fallbackTitle: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val doc = remember(file) { LegalText.load(context, file).firstOrNull() }
    AppScreen(doc?.title ?: fallbackTitle, onBack = onBack) {
        if (doc == null) Section(fallbackTitle) { Text("This page could not be loaded.") }
        doc?.blocks?.forEach { b -> Section(b.heading ?: doc.title) { Text(b.text, style = MaterialTheme.typography.bodyMedium) } }
    }
}
