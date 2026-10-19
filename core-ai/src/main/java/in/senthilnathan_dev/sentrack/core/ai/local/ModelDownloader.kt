package `in`.senthilnathan_dev.sentrack.core.ai.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads a model file, resumably, and checks its SHA-256 before it is ever loaded.
 *
 * - A partly downloaded file (`name.part`) is continued with an HTTP Range request, so a dropped connection or a
 *   cancelled setup does not start from zero.
 * - Redirects (Hugging Face sends you to a CDN) are followed by hand, on HTTPS only.
 * - Nothing is installed under the final name until the hash matches; a mismatch deletes the download.
 */
object ModelDownloader {
    private const val MAX_REDIRECTS = 6
    private const val BUFFER = 256 * 1024

    /** Downloads (or resumes) [option] into [dir] and returns the verified file. [onProgress] gets (bytes done, total bytes). */
    suspend fun fetch(
        option: ModelOption,
        dir: File,
        onDownloading: (done: Long, total: Long) -> Unit,
        onVerifying: (done: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = File(dir, option.fileName)
        if (target.length() == option.sizeBytes && sha256(target, onVerifying) == option.sha256) return@withContext target

        val part = File(dir, option.fileName + ".part")
        if (part.length() > option.sizeBytes) part.delete()
        if (part.length() < option.sizeBytes) download(option, part, onDownloading)

        val actual = sha256(part, onVerifying)
        if (actual != option.sha256) {
            part.delete()
            throw IOException("The downloaded file did not match its checksum, so it was discarded. Please try again.")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not store the model.")
        target
    }

    private suspend fun download(option: ModelOption, part: File, onProgress: (Long, Long) -> Unit) {
        var restarted = false
        while (true) {
            val have = part.length()
            val connection = open(option.url, DownloadMath.rangeHeader(have))
            try {
                when (val code = connection.responseCode) {
                    200 -> if (have > 0) part.delete()           // the server ignored Range: start over
                    206 -> {}
                    416 -> {                                      // our partial file does not fit; start clean once
                        if (restarted) throw IOException("The server rejected the resume request.")
                        part.delete(); restarted = true
                        continue
                    }
                    else -> throw IOException("The download server answered with error $code.")
                }
                RandomAccessFile(part, "rw").use { file ->
                    file.seek(part.length().let { if (connection.responseCode == 206) it else 0L })
                    var done = file.filePointer
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(BUFFER)
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            file.write(buffer, 0, n)
                            done += n
                            if (done - lastReport >= BUFFER) { onProgress(done, option.sizeBytes); lastReport = done }
                        }
                        onProgress(done, option.sizeBytes)
                    }
                }
                if (part.length() < option.sizeBytes) throw IOException("The connection dropped before the download finished. Tap Resume to continue.")
                return
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Opens [url], following up to a few HTTPS redirects, sending a Range header when resuming. */
    private fun open(url: String, range: String?): HttpURLConnection {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            val c = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                range?.let { setRequestProperty("Range", it) }
            }
            val code = c.responseCode
            if (code in intArrayOf(301, 302, 303, 307, 308)) {
                val next = URL(current, c.getHeaderField("Location") ?: throw IOException("Bad redirect from the download server."))
                c.disconnect()
                if (next.protocol != "https") throw IOException("Refusing a non-secure redirect.")
                current = next
            } else {
                return c
            }
        }
        throw IOException("Too many redirects from the download server.")
    }

    /** Hex SHA-256 of a file, streaming, reporting progress. */
    suspend fun sha256(file: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val total = file.length()
        var done = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                done += n
                onProgress(done, total)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
