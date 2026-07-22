package app.masary.feature.auth
import app.masary.feature.auth.domain.*
import org.junit.Assert.*
import org.junit.Test
class RegistrationDraftTest {
 private val city=RegistrationCity(1,"عدن",true);private val grade=AcademicOption(7,"السابع")
 private fun valid()=RegistrationDraft("طالب","student_1",password="secret",passwordConfirmation="secret",gender="male",city=city,school=AcademicOption(2,"مدرسة"),grade=grade)
 @Test fun `validates account and academic relationships`(){assertTrue(valid().accountValid());assertTrue(valid().academicValid());assertFalse(valid().copy(username="bad name").accountValid());assertFalse(valid().copy(school=null).academicValid())}
 @Test fun `city without required school accepts null and ignores manual curriculum concepts`(){assertTrue(valid().copy(city=city.copy(requiresSchool=false),school=null).academicValid())}
 @Test fun `sensitive fields are cleared after terminal registration state`(){val cleared=valid().clearedSensitive();assertEquals("",cleared.password);assertEquals("",cleared.passwordConfirmation);assertEquals(3,valid().copy().let{3})}
}
