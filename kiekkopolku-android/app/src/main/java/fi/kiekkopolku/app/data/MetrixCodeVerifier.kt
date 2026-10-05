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
class MetrixCodeVerifier(private val api: MetrixApi = MetrixHttpApi()) : IntegrationCodeVerifier {
    override suspend fun verify(code: String) { validateMetrixCodeResponse(api.get("my_competitions", code).toString()) }
}

fun interface MetrixApi {
    suspend fun get(content: String, code: String, id: String?): JsonObject
    suspend fun publicEvent(id: String): String? = null
}
suspend fun MetrixApi.get(content: String, code: String): JsonObject = get(content, code, null)

class MetrixHttpApi : MetrixApi {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .build() // Deliberately no URL/body logging or disk HTTP cache: code is a credential query parameter.

    override suspend fun get(content: String, code: String, id: String?): JsonObject {
        val request = Request.Builder().url("https://discgolfmetrix.com/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("content", content).addQueryParameter("code", code)
            .apply { if (id != null) addQueryParameter("id", id) }.build())
            .header("Cache-Control", "no-store").build()
        val raw = body(request)
        return runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: throw MetrixConnectionException()
    }

    override suspend fun publicEvent(id: String): String? {
        require(id.matches(Regex("[0-9]+")))
        // Public metadata only: no integration code, login cookie or redirect to another host.
        return body(Request.Builder().url("https://discgolfmetrix.com/$id?locale=en")
            .header("Accept-Language", "en").header("Cache-Control", "no-store").build())
    }

    private suspend fun body(request: Request): String {
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
        return response.use {
            if (it.code == 401 || it.code == 403) throw InvalidIntegrationCodeException()
            if (!it.isSuccessful) throw MetrixConnectionException()
            withContext(Dispatchers.IO) {
                try {
                    val source = it.body?.source() ?: throw MetrixConnectionException()
                    source.request(8_388_609L)
                    if (source.buffer.size > 8_388_608L) throw MetrixConnectionException()
                    source.buffer.readUtf8()
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
