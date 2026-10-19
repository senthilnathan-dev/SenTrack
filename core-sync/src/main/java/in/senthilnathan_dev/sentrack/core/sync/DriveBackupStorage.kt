package `in`.senthilnathan_dev.sentrack.core.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** One backup file stored remotely. */
data class RemoteBackup(val id: String, val name: String, val sizeBytes: Long, val createdAtMillis: Long)

/** Where backups live. Drive today; the interface keeps the backup logic testable and replaceable. */
interface BackupStorage {
    /** Newest first. */
    fun list(): List<RemoteBackup>
    fun upload(file: File, remoteName: String): RemoteBackup
    fun download(id: String, target: File)
    fun delete(id: String)
}

/** The access token was rejected (expired, revoked or missing the Drive scope). Sign in again. */
class DriveAuthException(message: String) : IOException(message)

/** Drive answered with an error. [retryable] is true for rate limits and server errors. */
class DriveException(val code: Int, message: String) : IOException("Drive error $code: $message") {
    val retryable: Boolean get() = code == 429 || code >= 500 || code == 403 && message?.contains("rateLimit") == true
}

/**
 * Google Drive REST v3 client limited to the app's hidden `appDataFolder` (scope `drive.appdata`), using a
 * `SenTrackAppBackup` subfolder. Only plain HTTPS calls: no Google client library, so the dependency surface stays tiny.
 *
 * All methods are blocking: call them from a background thread.
 *
 * @param tokenProvider returns a valid OAuth access token each time it is called.
 * @param baseUrl overridable so tests can point at a local fake server.
 */
class DriveBackupStorage(
    private val tokenProvider: () -> String,
    private val baseUrl: String = "https://www.googleapis.com",
    private val folderName: String = "SenTrackAppBackup",
) : BackupStorage {

    override fun list(): List<RemoteBackup> {
        val folder = findFolder() ?: return emptyList()
        val query = "'$folder' in parents and trashed=false"
        val json = getJson(
            "/drive/v3/files?spaces=appDataFolder&orderBy=createdTime%20desc&pageSize=100" +
                "&fields=" + enc("files(id,name,size,createdTime)") + "&q=" + enc(query),
        )
        val files = json.optJSONArray("files") ?: JSONArray()
        return (0 until files.length()).map { files.getJSONObject(it).toRemoteBackup() }
    }

    override fun upload(file: File, remoteName: String): RemoteBackup {
        val folder = findFolder() ?: createFolder()
        // 1) Start a resumable upload session with the file's metadata.
        val metadata = JSONObject().put("name", remoteName).put("parents", JSONArray().put(folder)).toString()
        val start = open("POST", "/upload/drive/v3/files?uploadType=resumable&fields=" + enc("id,name,size,createdTime"))
        start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        start.setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
        start.setRequestProperty("X-Upload-Content-Length", file.length().toString())
        start.doOutput = true
        start.outputStream.use { it.write(metadata.toByteArray(Charsets.UTF_8)) }
        checkOk(start)
        val sessionUrl = start.getHeaderField("Location") ?: throw IOException("Drive gave no upload location")
        start.disconnect()

        // 2) Send the bytes, streamed from disk (never held in memory).
        val put = open("PUT", sessionUrl, absolute = true)
        put.setRequestProperty("Content-Type", "application/octet-stream")
        put.doOutput = true
        put.setFixedLengthStreamingMode(file.length())
        file.inputStream().use { input -> put.outputStream.use { output -> input.copyTo(output, 64 * 1024) } }
        checkOk(put)
        val body = put.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        put.disconnect()
        return JSONObject(body).toRemoteBackup()
    }

    override fun download(id: String, target: File) {
        val conn = open("GET", "/drive/v3/files/${enc(id)}?alt=media")
        checkOk(conn)
        conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
        conn.disconnect()
    }

    override fun delete(id: String) {
        val conn = open("DELETE", "/drive/v3/files/${enc(id)}")
        checkOk(conn)
        conn.disconnect()
    }

    // ---- internals

    private fun findFolder(): String? {
        val query = "name='$folderName' and mimeType='$FOLDER_MIME' and 'appDataFolder' in parents and trashed=false"
        val json = getJson("/drive/v3/files?spaces=appDataFolder&fields=" + enc("files(id)") + "&q=" + enc(query))
        return json.optJSONArray("files")?.takeIf { it.length() > 0 }?.getJSONObject(0)?.getString("id")
    }

    private fun createFolder(): String {
        val body = JSONObject().put("name", folderName).put("mimeType", FOLDER_MIME)
            .put("parents", JSONArray().put("appDataFolder")).toString()
        val conn = open("POST", "/drive/v3/files?fields=id")
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        checkOk(conn)
        val id = JSONObject(conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)).getString("id")
        conn.disconnect()
        return id
    }

    private fun getJson(path: String): JSONObject {
        val conn = open("GET", path)
        checkOk(conn)
        val text = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        conn.disconnect()
        return JSONObject(text)
    }

    private fun open(method: String, pathOrUrl: String, absolute: Boolean = false): HttpURLConnection {
        val conn = URL(if (absolute) pathOrUrl else baseUrl + pathOrUrl).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.setRequestProperty("Authorization", "Bearer ${tokenProvider()}")
        return conn
    }

    /** Throws a typed exception for any non-2xx answer. */
    private fun checkOk(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code in 200..299) return
        val message = (conn.errorStream ?: conn.inputStream).use { it.readBytes() }.toString(Charsets.UTF_8).take(300)
        conn.disconnect()
        if (code == 401) throw DriveAuthException("Google rejected the sign-in")
        throw DriveException(code, message)
    }

    private fun JSONObject.toRemoteBackup() = RemoteBackup(
        id = getString("id"),
        name = optString("name"),
        sizeBytes = optString("size", "0").toLongOrNull() ?: 0L,
        createdAtMillis = runCatching { Instant.parse(getString("createdTime")).toEpochMilli() }.getOrDefault(0L),
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}
