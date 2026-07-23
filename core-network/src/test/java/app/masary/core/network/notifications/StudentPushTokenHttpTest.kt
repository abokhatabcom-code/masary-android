package app.masary.core.network.notifications
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
class StudentPushTokenHttpTest {
 @Test fun `unregister sends DELETE with contract JSON body`()=runBlocking {
  val server=MockWebServer();server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type","application/json").setBody("{\"success\":true,\"data\":{\"registered\":false},\"error\":null}"))
  server.start();try { val request=PushInstallationRequest("123e4567-e89b-42d3-a456-426614174000","x".repeat(32),"1.0",1,locale="ar-YE",timezone="Asia/Aden",permissionStatus="Granted")
   Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(StudentPushTokenApi::class.java).unregister("Bearer test",request);val recorded=server.takeRequest();assertEquals("DELETE",recorded.method)
   val expected=checkNotNull(javaClass.classLoader?.getResource("push-token-request.json")).readText();assertEquals(JsonParser.parseString(expected),JsonParser.parseString(recorded.body.readUtf8()))
  } finally {server.shutdown()}
 }
}
