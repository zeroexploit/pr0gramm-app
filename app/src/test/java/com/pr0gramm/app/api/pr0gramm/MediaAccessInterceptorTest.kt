package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class MediaAccessInterceptorTest {
    @Test fun tokensAreReusedAndRenewedBeforeExpiration() {
        val fixture = Fixture()
        fixture.load("https://thumb.pr0gramm.com/image.jpg")
        fixture.load("https://vid.pr0gramm.com/video.mp4")
        assertEquals(1, fixture.tokenRequests)
        assertEquals("av=token-1", fixture.requests.last().header("Cookie"))

        fixture.time = 1080
        fixture.load("https://fullsize.pr0gramm.com/image.jpg")
        assertEquals(2, fixture.tokenRequests)
        assertEquals("av=token-2", fixture.requests.last().header("Cookie"))
    }

    @Test fun credentialsAreNotSentToOtherHostsOrUnencryptedUrls() {
        val fixture = Fixture()
        for (url in listOf("https://example.org/image.jpg", "https://img.pr0gramm.com.example.org/image.jpg",
            "http://img.pr0gramm.com/image.jpg", "https://pr0gramm.com/api/items/get")) {
            fixture.load(url)
        }
        assertEquals(0, fixture.tokenRequests)
        assertTrue(fixture.requests.all { it.header("Cookie") == null })
    }

    @Test fun switchingAccountsAndLoggingOutNeverReuseAnotherSessionsToken() {
        val fixture = Fixture()
        fixture.load("https://img.pr0gramm.com/image.jpg")
        fixture.session = "another-session"
        fixture.load("https://images.pr0gramm.com/image.jpg")
        assertEquals(2, fixture.tokenRequests)
        assertEquals("av=token-2", fixture.requests.last().header("Cookie"))

        fixture.session = null
        fixture.load("https://thumb.pr0gramm.com/image.jpg")
        assertNull(fixture.requests.last().header("Cookie"))
        assertEquals(2, fixture.tokenRequests)
    }

    @Test fun failingTokenEndpointDoesNotPreventSfwMediaAndIsThrottled() {
        val fixture = Fixture()
        fixture.tokenResponse = """{"error":"notAgeVerified"}"""
        repeat(3) { fixture.load("https://thumb.pr0gramm.com/image.jpg") }
        assertEquals(1, fixture.tokenRequests)
        assertNull(fixture.requests.last().header("Cookie"))

        fixture.time += 31
        fixture.load("https://thumb.pr0gramm.com/image.jpg")
        assertEquals(2, fixture.tokenRequests)
    }

    private class Fixture {
        var session: String? = "session"
        var time = 1000L
        var tokenResponse: String? = null
        var tokenRequests = 0
        val requests = mutableListOf<Request>()
        private val client = OkHttpClient.Builder()
            .addInterceptor(MediaAccessInterceptor({ session }, { Instant.ofEpochSeconds(time) }))
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val json = if (request.url.encodedPath == "/api/user/avToken") {
                    tokenRequests++
                    tokenResponse ?: """{"token":"token-$tokenRequests","validUntil":${time + 100}}"""
                } else "{}"
                ApiContractTest.response(request, json)
            }.build()

        fun load(url: String) {
            client.newCall(Request.Builder().url(url).build()).execute().close()
        }
    }
}
