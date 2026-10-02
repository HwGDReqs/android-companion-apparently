package com.malikhw.hwgdreqs.network

import com.malikhw.hwgdreqs.data.AuthPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class ApiException(val code: String) : Exception(code)

sealed interface AuthCheck {
    data object Valid : AuthCheck
    data object Rejected : AuthCheck
    data class Failed(val message: String) : AuthCheck
}

class HwGDReqsApi(private val prefs: AuthPreferences) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val pairClient = client.newBuilder()
        .readTimeout(70, TimeUnit.SECONDS)
        .build()

    private val pollClient = client.newBuilder()
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    fun generatePin(): String = Random.nextInt(1000, 10_000).toString()

    suspend fun pair(baseUrl: String, pin: String): Result<String> = withContext(Dispatchers.IO) {
        safe {
            val body = JSONObject()
                .put("client", CLIENT_ID)
                .put("device_id", prefs.deviceId)
                .put("device_name", prefs.deviceName)
                .put("pin", pin)
            val request = Request.Builder()
                .url("$baseUrl/mobile/pair")
                .mobileHeaders(null)
                .post(body.toString().toRequestBody(JSON))
                .build()

            pairClient.newCall(request).await().use { r ->
                val json = parseObject(r.body?.string().orEmpty())
                val token = json.optString("token")
                if (r.isSuccessful && json.optBoolean("ok", false) && token.isNotEmpty()) {
                    token
                } else {
                    throw ApiException(json.optString("error").ifEmpty { "http_${r.code}" })
                }
            }
        }
    }

    suspend fun verifyAuth(baseUrl: String, token: String): AuthCheck = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("client", CLIENT_ID)
                .put("device_id", prefs.deviceId)
                .put("token", token)
            val request = Request.Builder()
                .url("$baseUrl/mobile/auth")
                .mobileHeaders(token)
                .post(body.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).await().use { r ->
                when {
                    r.isSuccessful -> AuthCheck.Valid
                    r.code == 401 -> AuthCheck.Rejected
                    else -> AuthCheck.Failed("HTTP ${r.code}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthCheck.Failed(e.message ?: "network error")
        }
    }

    suspend fun getQueue(
        baseUrl: String,
        token: String?,
        actionPassword: String?,
    ): Result<List<QueueEntry>> =
        withContext(Dispatchers.IO) {
            safe {
                val request = Request.Builder()
                    .url(
                        Request.Builder().url("$baseUrl/queue").build().url.newBuilder()
                            .apply { actionPassword?.let { addQueryParameter("auth", it) } }
                            .build()
                    )
                    .mobileHeaders(token)
                    .get()
                    .build()

                pollClient.newCall(request).await().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        val error = parseObject(text).optString("error")
                            .ifEmpty { if (r.code == 401) "unauthorized" else "http_${r.code}" }
                        throw ApiException(error)
                    }
                    parseQueue(text)
                }
            }
        }

    suspend fun deleteLevel(
        baseUrl: String,
        token: String?,
        levelId: String,
        actionPassword: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        safe {
            val body = JSONObject().put("id", levelId)
            actionPassword?.let { body.put("auth", it) }
            val request = Request.Builder()
                .url("$baseUrl/delete")
                .mobileHeaders(token)
                .post(body.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).await().use { r ->
                val text = r.body?.string().orEmpty()
                val json = parseObject(text)
                if (!r.isSuccessful || !json.optBoolean("ok", false)) {
                    val error = json.optString("error")
                        .ifEmpty { "http_${r.code}" }
                    throw ApiException(error)
                }
            }
        }
    }


    private fun Request.Builder.mobileHeaders(token: String?): Request.Builder = apply {
        header("X-Client-Id", CLIENT_ID)
        header("X-Device-Id", prefs.deviceId)
        header("User-Agent", "$CLIENT_ID/1.0")
        if (!token.isNullOrEmpty()) header("X-Auth-Token", token)
    }

    private fun parseObject(text: String): JSONObject =
        try {
            JSONObject(text)
        } catch (_: JSONException) {
            JSONObject()
        }

    private fun parseQueue(text: String): List<QueueEntry> {
        val arr = parseObject(text).optJSONArray("levels") ?: JSONArray()
        return List(arr.length()) { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            val details = buildList {
                val keys = o.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = o.opt(key)
                    add(key to if (value == JSONObject.NULL) "" else value.toString())
                }
            }
            QueueEntry(
                id = o.optString("id"),
                name = o.optString("name"),
                author = o.optString("author"),
                requester = o.optString("requester"),
                details = details,
            )
        }
    }

    private inline fun <T> safe(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val CLIENT_ID = "hwgdreqs-mobile"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
