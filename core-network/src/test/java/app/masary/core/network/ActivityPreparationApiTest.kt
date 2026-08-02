package app.masary.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ActivityPreparationApiTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `start status sends idempotency key in header without query parameters`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(resource("activity-start-success.json")),
        )
        val api = MasaryNetwork.studentActivityPreparationApi(server.url("/"))
        val key = "12345678-1234-1234-1234-123456789012"

        api.startStatus("Bearer access-token", key)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/student/activity/start-status", request.requestUrl?.encodedPath)
        assertNull(request.requestUrl?.query)
        assertEquals(key, request.getHeader("Idempotency-Key"))
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
    }

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)).readText()
}
