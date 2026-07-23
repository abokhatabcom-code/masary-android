package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class LogoutPreparationTest { @Test fun `storage failure is reported and not silently scheduled`() { var attempted=false;val saved=LogoutPreparation{attempted=true;false}.save(PendingLogout("access","refresh"));assertTrue(attempted);assertFalse(saved) } }
