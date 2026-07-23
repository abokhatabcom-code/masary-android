package app.masary.core.network.notifications
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class StudentPushTokenApiTest {
 private val request=PushInstallationRequest("123e4567-e89b-42d3-a456-426614174000","x".repeat(32),"1.0",1,locale="ar-YE",timezone="Asia/Aden",permissionStatus="Granted")
 @Test fun `request serializes exactly as contract fixture snake case`() { val expected=checkNotNull(javaClass.classLoader?.getResource("push-token-request.json")).readText(); assertEquals(JsonParser.parseString(expected),JsonParser.parseString(Gson().toJson(request))) }
 @Test fun `request redacts token`() { assertFalse(request.toString().contains(request.fcmToken!!)) }
}
