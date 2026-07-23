package app.masary.feature.notifications
import org.junit.Assert.assertNotEquals
import org.junit.Test
class NotificationPendingIntentPolicyTest {
 @Test fun `different message ids remain unique`() { assertNotEquals(code("a",1,"home"),code("b",1,"home")) }
 @Test fun `missing ids same destination remain unique by sent time`() { assertNotEquals(code(null,1,"home"),code(null,2,"home")) }
 @Test fun `missing ids different destinations remain unique`() { assertNotEquals(code(null,1,"home"),code(null,1,"profile")) }
 private fun code(id:String?,time:Long,destination:String)=NotificationPendingIntentPolicy.requestCode(id,time,destination,"body")
}
