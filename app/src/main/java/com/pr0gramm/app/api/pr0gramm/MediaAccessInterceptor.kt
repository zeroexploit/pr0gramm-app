package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant
import com.pr0gramm.app.MoshiInstance
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.adapter
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Supplies the short-lived age verification credential required by the media servers. */
class MediaAccessInterceptor(
    private val verifiedSession: () -> String?,
    private val now: () -> Instant = { Instant.now() },
) : Interceptor {
    private var session: String? = null
    private var token: Api.AgeVerificationToken? = null
    private var retryAfter = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isMediaUrl(request.url)) return chain.proceed(request)

        val sessionId = verifiedSession() ?: return chain.proceed(request)
        val credential = synchronized(this) {
            if (session != sessionId) {
                session = sessionId
                token = null
                retryAfter = 0
            }

            val time = now().epochSeconds
            if ((token?.validUntil?.epochSeconds ?: 0) <= time + 30) {
                token = null
                if (time >= retryAfter) {
                    // Avoid a burst of failing token requests when loading a grid of thumbnails.
                    retryAfter = time + 30
                    token = fetchToken(chain)
                }
            }
            token?.takeIf { verifiedSession() == sessionId && it.validUntil.epochSeconds > time + 30 }
        }

        if (credential == null) return chain.proceed(request)

        val cookie = Cookie.Builder().name("av").value(credential.token)
            .hostOnlyDomain(request.url.host).secure().build()
        val cookies = listOfNotNull(request.header("Cookie"), "${cookie.name}=${cookie.value}")
        return chain.proceed(request.newBuilder().header("Cookie", cookies.joinToString("; ")).build())
    }

    private fun fetchToken(chain: Interceptor.Chain): Api.AgeVerificationToken? {
        val request = Request.Builder().url("https://pr0gramm.com/api/user/avToken")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .build()
        return try {
            chain.proceed(request).use { response ->
                if (!response.isSuccessful) return null
                response.body?.source()?.let { MoshiInstance.adapter<Api.AgeVerificationToken>().fromJson(it) }
            }
        } catch (_: IOException) {
            null
        } catch (_: JsonDataException) {
            null
        }
    }

    private fun isMediaUrl(url: HttpUrl): Boolean {
        if (!url.isHttps) return false
        return url.host in MEDIA_HOSTS ||
                (url.host in setOf("pr0gramm.com", "pr0.app") && url.encodedPath.startsWith("/data/"))
    }

    private companion object {
        val MEDIA_HOSTS = setOf("thumb", "img", "vid", "full", "images", "videos", "fullsize")
            .flatMap { subdomain -> listOf("$subdomain.pr0gramm.com", "$subdomain.pr0.app") }.toSet()
    }
}
