package app.masary.feature.auth.data

import app.masary.core.models.auth.*
import app.masary.core.network.auth.*
import app.masary.feature.auth.domain.*

class NetworkRegistrationRepository(private val api: StudentAuthApi) : RegistrationRepository {
 override suspend fun cities()=runCatching{api.cities().data?.cities?.map{RegistrationCity(it.id,it.name,it.requiresSchool)}?:error("تعذر تحميل المدن")}
 override suspend fun grades()=runCatching{api.grades().data?.grades?.map{AcademicOption(it.id,it.name)}?:error("تعذر تحميل الصفوف")}
 override suspend fun schools(cityId:Long)=runCatching{api.schools(cityId).data?.schools?.map{AcademicOption(it.id,it.name)}?:error("تعذر تحميل المدارس")}
 override suspend fun register(d:RegistrationDraft,deviceName:String,idempotencyKey:String)=runCatching{
  val r=api.register(idempotencyKey,StudentRegistrationRequestDto(d.fullName,d.username,d.phone.ifBlank{null},d.email.ifBlank{null},d.password,d.passwordConfirmation,d.gender,d.personality.ifBlank{null},d.city!!.id,d.school?.id,d.grade!!.id,d.privacyAccepted,deviceName));val x=r.data?:error(r.error?.message?:"تعذر إنشاء الحساب")
  AuthenticatedStudent(Student(x.student.id,x.student.username,x.student.displayName),AuthTokens(x.accessToken,x.refreshToken,x.expiresIn))
 }
}
