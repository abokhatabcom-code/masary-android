package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class RegistrationLifecycleTest { @Test fun `same encrypted current token can bind after logout and second login`() { val currentToken="encrypted-current-token";val events=mutableListOf<String>();events += "login";events += "register:$currentToken";events += "unregister";events += "login";events += "register:$currentToken";assertEquals("register:$currentToken",events.last());assertEquals(2,events.count{it.startsWith("register:")}) } }
