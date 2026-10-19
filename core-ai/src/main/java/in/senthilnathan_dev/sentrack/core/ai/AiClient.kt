package `in`.senthilnathan_dev.sentrack.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** What came back from asking a provider. */
sealed interface AiResult {
    data class Text(val text: String) : AiResult
    data class Failure(val message: String) : AiResult
}

/**
 * Sends one request to the chosen provider with the user's key. Nothing is sent unless the user taps Send on the
 * assistant screen, and the screen shows the exact text first. No data is kept by SenTrack beyond the screen.
 */
object AiClient {
    suspend fun ask(provider: AiProvider, model: String, key: String, prompt: String): AiResult = withContext(Dispatchers.IO) {
        val spec = AiProtocol.request(provider, model, key, prompt)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                spec.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            connection.outputStream.use { it.write(spec.body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                AiProtocol.parse(provider, body)?.let { AiResult.Text(it) } ?: AiResult.Failure("The reply was not in the expected format.")
            } else {
                AiResult.Failure(friendly(code, AiProtocol.parseError(body)))
            }
        } catch (e: java.io.IOException) {
            AiResult.Failure("Could not reach ${provider.label}. Check your connection.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun friendly(code: Int, detail: String?): String = when (code) {
        401, 403 -> "The key was not accepted. Check it in AI settings."
        404 -> "That model name was not found. Check it in AI settings."
        429 -> "Too many requests or no quota left on this key."
        else -> "The service answered with an error ($code)${detail?.let { ": $it" } ?: ""}."
    }
}
