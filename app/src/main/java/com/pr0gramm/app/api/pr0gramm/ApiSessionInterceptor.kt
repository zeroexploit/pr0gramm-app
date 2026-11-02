package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.MoshiInstance
import com.squareup.moshi.adapter
import okhttp3.Interceptor
import okhttp3.Response

/** Applies session authentication to every API POST, including multipart and binary uploads. */
class ApiSessionInterceptor(
    private val nonce: () -> Api.Nonce?,
    private val onInvalidSession: () -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/problem+json,application/json")
        if (chain.request().method == "POST") {
            nonce()?.let { request.header("X-XSRF-Token", it.toString()) }
        }

        val response = chain.proceed(request.build())
        if (response.code == 403) {
            val error = response.peekBody(16 * 1024).use { body ->
                runCatching { MoshiInstance.adapter<Api.Error>().fromJson(body.source()) }.getOrNull()
            }
            val message = error?.detail ?: error?.msg
            if (message == "Not logged in" || message == "Nicht eingeloggt") {
                onInvalidSession()
            }
        }
        return response
    }
}
