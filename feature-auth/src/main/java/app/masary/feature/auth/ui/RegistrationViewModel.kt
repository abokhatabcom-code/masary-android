package app.masary.feature.auth.ui
import androidx.lifecycle.*
import app.masary.core.datastore.SessionManager
import app.masary.feature.auth.domain.*
import app.masary.core.models.auth.StudentSession
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RegistrationState(val step:Int=1,val draft:RegistrationDraft=RegistrationDraft(),val cities:List<RegistrationCity> = emptyList(),val grades:List<AcademicOption> = emptyList(),val schools:List<AcademicOption> = emptyList(),val loading:Boolean=false,val error:String?=null)
class RegistrationViewModel(private val repo:RegistrationRepository,private val sessions:SessionManager,private val device:String,private val success:(StudentSession)->Unit):ViewModel(){
 private val _state=MutableStateFlow(RegistrationState());val state=_state.asStateFlow();private var key=UUID.randomUUID().toString()
 init{viewModelScope.launch{val c=repo.cities();val g=repo.grades();_state.value=_state.value.copy(cities=c.getOrDefault(emptyList()),grades=g.getOrDefault(emptyList()),error=c.exceptionOrNull()?.message?:g.exceptionOrNull()?.message)}}
 fun account(d:RegistrationDraft){if(!d.accountValid()){_state.value=_state.value.copy(error="تحقق من بيانات الحساب");return};_state.value=_state.value.copy(step=2,draft=d,error=null)}
 fun academic(city:RegistrationCity,school:AcademicOption?,grade:AcademicOption){val d=_state.value.draft.copy(city=city,school=if(city.requiresSchool)school else null,grade=grade);if(!d.academicValid()){_state.value=_state.value.copy(error="اختر البيانات الأكاديمية المطلوبة");return};_state.value=_state.value.copy(step=3,draft=d,error=null)}
 fun city(city:RegistrationCity){_state.value=_state.value.copy(draft=_state.value.draft.copy(city=city,school=null),schools=emptyList());if(city.requiresSchool)viewModelScope.launch{repo.schools(city.id).onSuccess{_state.value=_state.value.copy(schools=it)}}}
 fun back(){_state.value=_state.value.copy(step=(_state.value.step-1).coerceAtLeast(1),error=null)}
 fun submit(accepted:Boolean){val d=_state.value.draft.copy(privacyAccepted=accepted);if(!accepted)return run{_state.value=_state.value.copy(error="يجب الموافقة على الخصوصية")};_state.value=_state.value.copy(loading=true,draft=d,error=null);viewModelScope.launch{repo.register(d,device,key).onSuccess{sessions.save(it);_state.value=_state.value.copy(draft=d.clearedSensitive());success(StudentSession(it.student.id,it.student.username,it.student.displayName))}.onFailure{_state.value=_state.value.copy(loading=false,draft=d.clearedSensitive(),error=it.message);key=UUID.randomUUID().toString()}}}
}
