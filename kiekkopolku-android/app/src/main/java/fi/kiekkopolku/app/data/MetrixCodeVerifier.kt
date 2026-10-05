package fi.kiekkopolku.app.data

import fi.kiekkopolku.app.domain.InvalidIntegrationCodeException
import fi.kiekkopolku.app.domain.MetrixConnectionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

fun interface IntegrationCodeVerifier { suspend fun verify(code: String) }

/** Only the documented my_competitions endpoint is used. No owner-ID inference or history scraping. */
class MetrixCodeVerifier : IntegrationCodeVerifier {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .build() // Deliberately no URL/body logging or disk HTTP cache: code is a credential query parameter.

    override suspend fun verify(code: String) {
        val request = Request.Builder().url("https://discgolfmetrix.com/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("content", "my_competitions").addQueryParameter("code", code).build())
            .header("Cache-Control", "no-store").build()
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(MetrixConnectionException())
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            if (it.code == 401 || it.code == 403) throw InvalidIntegrationCodeException()
            if (!it.isSuccessful) throw MetrixConnectionException()
            withContext(Dispatchers.IO) {
                try {
                    val source = it.body?.source() ?: throw MetrixConnectionException()
                    source.request(1_048_577L)
                    if (source.buffer.size > 1_048_576L) throw MetrixConnectionException()
                    validateMetrixCodeResponse(source.buffer.readUtf8())
                } catch (_: IOException) { throw MetrixConnectionException() }
            }
        }
    }
}

internal fun validateMetrixCodeResponse(body: String) {
    val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        ?: throw MetrixConnectionException()
    val errors = root["Errors"]
    if (errors != null && (errors !is JsonArray || errors.isNotEmpty())) throw InvalidIntegrationCodeException()
    // Live invalid-code response was {"Errors":[]}; absence of errors alone is NOT success.
    val ids = root["my_competitions"] as? JsonArray ?: throw InvalidIntegrationCodeException()
    if (ids.any { it !is JsonPrimitive || !it.content.matches(Regex("[0-9]+")) }) throw MetrixConnectionException()
}
