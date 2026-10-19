package `in`.senthilnathan_dev.sentrack.core.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder

/** Exercises [DriveBackupStorage] against a tiny in-memory imitation of the Drive v3 endpoints it uses. */
class DriveBackupStorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class Entry(val id: String, val name: String, val parent: String, val mime: String, val bytes: ByteArray, val created: String)

    private lateinit var server: HttpServer
    private val entries = LinkedHashMap<String, Entry>()
    private val sessions = HashMap<String, JSONObject>()
    private var nextId = 1
    private var rejectAuth = false
    private val seenAuth = mutableListOf<String?>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex -> handle(ex) }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun storage() = DriveBackupStorage({ "token-123" }, "http://127.0.0.1:${server.address.port}")

    private fun handle(ex: HttpExchange) {
        seenAuth += ex.requestHeaders.getFirst("Authorization")
        if (rejectAuth) return reply(ex, 401, "{}")
        val path = ex.requestURI.path
        val query = ex.requestURI.rawQuery.orEmpty().split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val body = ex.requestBody.readBytes()
        when {
            ex.requestMethod == "GET" && path == "/drive/v3/files" -> {
                val q = query["q"].orEmpty()
                val hits = if ("mimeType" in q) entries.values.filter { it.mime.endsWith("folder") && it.parent == "appDataFolder" }
                else {
                    val parent = Regex("'([^']+)' in parents").find(q)!!.groupValues[1]
                    entries.values.filter { it.parent == parent }.sortedByDescending { it.created }
                }
                val arr = hits.joinToString(",") { """{"id":"${it.id}","name":"${it.name}","size":"${it.bytes.size}","createdTime":"${it.created}"}""" }
                reply(ex, 200, """{"files":[$arr]}""")
            }
            ex.requestMethod == "POST" && path == "/drive/v3/files" -> {
                val j = JSONObject(String(body))
                val e = Entry("id${nextId++}", j.getString("name"), j.getJSONArray("parents").getString(0), j.getString("mimeType"), ByteArray(0), "2026-01-01T00:00:00.000Z")
                entries[e.id] = e
                reply(ex, 200, """{"id":"${e.id}"}""")
            }
            ex.requestMethod == "POST" && path == "/upload/drive/v3/files" -> {
                val sid = "s${nextId++}"
                sessions[sid] = JSONObject(String(body))
                ex.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/session/$sid")
                reply(ex, 200, "{}")
            }
            ex.requestMethod == "PUT" && path.startsWith("/session/") -> {
                val meta = sessions.remove(path.removePrefix("/session/"))!!
                val created = "2026-10-%02dT10:00:00.000Z".format(entries.size + 1)
                val e = Entry("id${nextId++}", meta.getString("name"), meta.getJSONArray("parents").getString(0), "application/octet-stream", body, created)
                entries[e.id] = e
                reply(ex, 200, """{"id":"${e.id}","name":"${e.name}","size":"${body.size}","createdTime":"$created"}""")
            }
            ex.requestMethod == "GET" && path.startsWith("/drive/v3/files/") -> {
                val e = entries[path.removePrefix("/drive/v3/files/")]
                if (e == null) reply(ex, 404, "{}") else reply(ex, 200, e.bytes)
            }
            ex.requestMethod == "DELETE" && path.startsWith("/drive/v3/files/") -> {
                entries.remove(path.removePrefix("/drive/v3/files/"))
                ex.sendResponseHeaders(204, -1); ex.close()
            }
            else -> reply(ex, 400, "unexpected ${ex.requestMethod} $path")
        }
    }

    private fun reply(ex: HttpExchange, code: Int, text: String) = reply(ex, code, text.toByteArray())

    private fun reply(ex: HttpExchange, code: Int, bytes: ByteArray) {
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
        ex.close()
    }

    private fun file(size: Int): File = File(tmp.root, "b$size.stbk").also { f -> f.writeBytes(ByteArray(size) { (it % 200).toByte() }) }

    @Test
    fun list_isEmpty_whenNoFolderYet() {
        assertEquals(emptyList<RemoteBackup>(), storage().list())
    }

    @Test
    fun upload_createsFolderOnce_storesBytes_sendsBearerToken() {
        val s = storage()
        val data = file(300_000)
        val r1 = s.upload(data, "one.stbk")
        val r2 = s.upload(file(10), "two.stbk")
        assertEquals("one.stbk", r1.name)
        assertEquals(300_000, r1.sizeBytes)
        assertEquals(1, entries.values.count { it.mime.endsWith("folder") })
        assertArrayEquals(data.readBytes(), entries[r1.id]!!.bytes)
        assertTrue(seenAuth.all { it == "Bearer token-123" })
        assertEquals(listOf(r2.name, r1.name), s.list().map { it.name }) // newest first
    }

    @Test
    fun download_roundTrips() {
        val s = storage()
        val data = file(100_000)
        val id = s.upload(data, "x.stbk").id
        val out = File(tmp.root, "down.bin")
        s.download(id, out)
        assertArrayEquals(data.readBytes(), out.readBytes())
    }

    @Test
    fun delete_removesRemoteFile() {
        val s = storage()
        val id = s.upload(file(50), "x.stbk").id
        s.delete(id)
        assertEquals(emptyList<String>(), s.list().map { it.name })
    }

    @Test
    fun rejectedToken_raisesAuthException() {
        rejectAuth = true
        try {
            storage().list(); fail()
        } catch (_: DriveAuthException) {
        }
    }

    @Test
    fun missingFile_raisesDriveException() {
        try {
            storage().download("nope", File(tmp.root, "x")); fail()
        } catch (e: DriveException) {
            assertEquals(404, e.code)
            assertTrue(!e.retryable)
        }
    }
}
