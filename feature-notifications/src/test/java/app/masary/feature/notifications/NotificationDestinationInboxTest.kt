package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class NotificationDestinationInboxTest {
 @Test fun `cold start consumes and removes extra once`() { val i=NotificationDestinationInbox();var removed=false;i.receive("subjects");assertEquals("subjects",i.consume(true,false){removed=true});assertTrue(removed);assertNull(i.consume(true,false)) }
 @Test fun `warm start replaces pending destination`() { val i=NotificationDestinationInbox();i.receive("subjects");i.receive("profile");assertEquals("profile",i.consume(true,false)) }
 @Test fun `configuration recreation cannot replay removed extra`() { val old=NotificationDestinationInbox();var extra:String?="subjects";old.receive(extra);old.consume(true,false){extra=null};val recreated=NotificationDestinationInbox();recreated.receive(extra);assertNull(recreated.consume(true,false)) }
}
