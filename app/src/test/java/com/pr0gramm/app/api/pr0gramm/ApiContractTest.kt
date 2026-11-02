package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.MoshiInstance
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.decodeBase64
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

class ApiContractTest {
    @Test fun feedUsesBooleanFiltersAndPreservesPagination() = runBlocking {
        val fixture = Fixture("""{"atStart":false,"atEnd":true,"error":null,"items":[]}""")
        val feed = fixture.api.itemsGet(true, false, 42, null, null, 1, "katze", null, null, null, false)

        val url = fixture.requests.single().url
        assertEquals("1", url.queryParameter("promoted"))
        assertEquals("0", url.queryParameter("following"))
        assertEquals("0", url.queryParameter("show_junk"))
        assertEquals("42", url.queryParameter("older"))
        assertEquals("katze", url.queryParameter("tags"))
        assertTrue(feed.isAtEnd)
        assertTrue(feed.items.isEmpty())
    }

    @Test fun votesSendCsrfHeaderInsteadOfLegacyFormField() = runBlocking {
        val fixture = Fixture()
        fixture.api.vote(Api.Nonce("0123456789abcdef-more-session-data"), 42, -1)

        val request = fixture.requests.single()
        assertEquals("0123456789abcdef", request.header("X-XSRF-Token"))
        assertEquals(mapOf("id" to "42", "vote" to "-1"), request.fields())
    }

    @Test fun collectionVisibilityUsesBooleans() = runBlocking {
        val fixture = Fixture()
        fixture.api.collectionsCreate(null, "Sammlung", true, false)
        fixture.api.collectionsEdit(null, 12, "Sammlung", false, true)
        assertEquals("1", fixture.requests[0].fields()["isPublic"])
        assertEquals("0", fixture.requests[0].fields()["isDefault"])
        assertEquals("0", fixture.requests[1].fields()["isPublic"])
        assertEquals("1", fixture.requests[1].fields()["isDefault"])
    }

    @Test fun markingOneMessageReadUsesGetAndMessageType() = runBlocking {
        val fixture = Fixture()
        fixture.api.markAsRead(null, "comment", 123)
        val request = fixture.requests.single()
        assertEquals("GET", request.method)
        assertEquals("/api/inbox/read", request.url.encodedPath)
        assertEquals("comment", request.url.queryParameter("messageType"))
        assertEquals("123", request.url.queryParameter("messageId"))
        assertNull(request.body)
    }

    @Test fun updatedEndpointsAndDefaultBookmarkFlagAreUsed() = runBlocking {
        val fixture = Fixture()
        fixture.api.requestPasswordRecovery("name@example.org")
        fixture.api.resetPassword("name", "token", "password")
        fixture.api.defaultBookmarks()
        assertEquals("/api/user/sendPasswordResetMail", fixture.requests[0].url.encodedPath)
        assertEquals("/api/user/resetPassword", fixture.requests[1].url.encodedPath)
        assertEquals("1", fixture.requests[2].url.queryParameter("default"))
    }

    @Test fun favoritesUseCaseSensitiveEndpointAndDecodeComments() = runBlocking {
        val fixture = Fixture("""{"user":{"id":1,"name":"name","mark":2},"comments":[],"hasOlder":false,"hasNewer":false}""")
        assertTrue(fixture.api.userCommentsLike("name", 100, 1).comments.isEmpty())
        assertEquals("/api/profile/commentLikes", fixture.requests.single().url.encodedPath)
    }

    @Test fun uploadLimitReadsRemainingUploads() = runBlocking {
        val fixture = Fixture("""{"left":0}""")
        assertEquals(0, fixture.api.ratelimited().left)
        assertEquals("/api/items/rateLimited", fixture.requests.single().url.encodedPath)
    }

    @Test fun settingsUsePartialUpdateEndpoint() = runBlocking {
        val fixture = Fixture()
        @Suppress("UNCHECKED_CAST")
        val settings = mapOf("junkSeparation" to 0) as GenericSettings
        fixture.api.storeSettings(null, settings)
        assertEquals("/api/user/siteSetting", fixture.requests.single().url.encodedPath)
        assertEquals(mapOf("junkSeparation" to "0"), fixture.requests.single().fields())
    }

    @Test fun uploadsEncodeDisabledSimilarityCheckAsZero() = runBlocking {
        val fixture = Fixture("""{"item":{"id":42},"success":true}""")
        assertEquals(42L, fixture.api.post(null, "sfw", "katze", false, "upload-key", 1).itemId)
        assertEquals("0", fixture.requests.single().fields()["checkSimilar"])
    }

    @Test fun unsubscribeExplicitlyStopsFollowingByDefault() = runBlocking {
        val fixture = Fixture()
        fixture.api.profileUnsubscribe(null, "name")
        assertEquals("0", fixture.requests.single().fields()["keepFollow"])
    }

    @Test fun handoverCanPostWithoutLegacyNonceFormBody() = runBlocking {
        val fixture = Fixture("""{"token":"handover-token"}""")
        assertEquals("handover-token", fixture.api.handoverToken(Api.Nonce("0123456789abcdef-session")).token)
        val request = fixture.requests.single()
        assertEquals("POST", request.method)
        assertEquals("0123456789abcdef", request.header("X-XSRF-Token"))
        assertEquals(0L, request.body!!.contentLength())
    }

    @Test fun seenBitsReadIdentifierFromCurrentResponse() {
        // Android's Base64 implementation is unavailable in local JVM tests.
        val moshi = Moshi.Builder().add(ByteArray::class.java, object : JsonAdapter<ByteArray>() {
            override fun fromJson(reader: JsonReader) = reader.nextString().decodeBase64()!!.toByteArray()
            override fun toJson(writer: JsonWriter, value: ByteArray?) = error("unused")
        }).build()
        val bits = moshi.adapter<Api.SeenBits>().fromJson("""{"identifier":"user-id","value":"AQID","version":7}""")!!
        assertEquals("user-id", bits.userIdentifier)
        assertArrayEquals(byteArrayOf(1, 2, 3), bits.value)
        assertEquals(7, bits.version)
    }

    @Test fun sessionHeaderAlsoCoversMultipartAndBinaryPosts() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(ApiSessionInterceptor({ Api.Nonce("0123456789abcdef-session") }, {}))
            .addInterceptor { chain ->
                requests += chain.request()
                response(chain.request(), "{}")
            }.build()
        for (path in listOf("items/upload", "seen/update", "contact/send")) {
            client.newCall(Request.Builder().url("https://pr0gramm.com/api/$path")
                .post("payload".toRequestBody()).build()).execute().close()
        }
        assertTrue(requests.all { it.header("X-XSRF-Token") == "0123456789abcdef" })
        assertTrue(requests.all { "application/problem+json" in it.header("Accept").orEmpty() })
    }

    @Test fun expiredSessionsAreRecognizedWithoutLoggingOutOnOtherForbiddenErrors() {
        for ((body, invalid) in listOf(
            """{"error":"forbidden","code":403,"msg":"Nicht eingeloggt"}""" to true,
            """{"title":"Forbidden","status":403,"detail":"Nicht eingeloggt"}""" to true,
            """{"error":"forbidden","code":403,"msg":"Not logged in"}""" to true,
            """{"detail":"Requires pr0mium"}""" to false,
            "<html>Forbidden</html>" to false,
        )) {
            var loggedOut = false
            val client = OkHttpClient.Builder()
                .addInterceptor(ApiSessionInterceptor({ null }, { loggedOut = true }))
                .addInterceptor { chain -> response(chain.request(), body, 403) }.build()
            client.newCall(Request.Builder().url("https://pr0gramm.com/api/user/sync").build())
                .execute().use { assertEquals(body, it.body!!.string()) }
            assertEquals(invalid, loggedOut)
        }
    }

    private class Fixture(private val json: String = "{}") {
        val requests = mutableListOf<Request>()
        val api = Retrofit.Builder().baseUrl("https://pr0gramm.com/")
            .addConverterFactory(ApiBooleanConverterFactory)
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                response(chain.request(), json)
            }.build()).validateEagerly(true).build().create<Api>()
    }

    private fun Request.fields(): Map<String, String> {
        val form = body as FormBody
        return (0 until form.size).associate { form.name(it) to form.value(it) }
    }

    companion object {
        fun response(request: Request, json: String, code: Int = 200): Response = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(json.toResponseBody("application/json".toMediaType())).build()
    }
}
