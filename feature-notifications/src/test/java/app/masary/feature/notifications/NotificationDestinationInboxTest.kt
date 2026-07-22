package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class NotificationDestinationInboxTest {
 @Test fun `cold start destination is consumed once`() { val i=NotificationDestinationInbox();i.receive("subjects");assertEquals("subjects",i.consume(true,false));assertNull(i.consume(true,false)) }
 @Test fun `warm start replaces pending destination and applies allowlist`() { val i=NotificationDestinationInbox();i.receive("subjects");i.receive("evil");assertEquals("home",i.consume(true,false)) }
 @Test fun `active test falls back without forced navigation`() { val i=NotificationDestinationInbox();i.receive("subjects");assertEquals("home",i.consume(true,true)) }
}
