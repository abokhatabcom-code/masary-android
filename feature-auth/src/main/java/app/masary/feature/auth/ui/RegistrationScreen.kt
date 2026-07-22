package app.masary.feature.auth.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.masary.feature.auth.domain.*

@Composable fun RegistrationScreen(state:RegistrationState,onAccount:(RegistrationDraft)->Unit,onCity:(RegistrationCity)->Unit,onAcademic:(RegistrationCity,AcademicOption?,AcademicOption)->Unit,onSubmit:(Boolean)->Unit,onBack:()->Unit,onCancel:()->Unit){
 var d by remember(state.step){mutableStateOf(state.draft)};var accepted by remember{mutableStateOf(false)}
 Surface{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("إنشاء حساب طالب — الخطوة ${state.step} من 3",style=MaterialTheme.typography.headlineSmall);state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 when(state.step){1->{Field("الاسم الكامل",d.fullName){d=d.copy(fullName=it)};Field("اسم المستخدم",d.username){d=d.copy(username=it)};Field("الهاتف (اختياري)",d.phone){d=d.copy(phone=it)};Field("البريد (اختياري)",d.email){d=d.copy(email=it)};Field("كلمة المرور",d.password,true){d=d.copy(password=it)};Field("تأكيد كلمة المرور",d.passwordConfirmation,true){d=d.copy(passwordConfirmation=it)};Choice("الجنس",listOf("male" to "ذكر","female" to "أنثى"),d.gender){d=d.copy(gender=it)};Button({onAccount(d)},Modifier.fillMaxWidth()){Text("التالي")}}
 2->{Choice("المدينة",state.cities.map{it.id.toString() to it.name},d.city?.id?.toString().orEmpty()){id->state.cities.first{it.id.toString()==id}.let{d=d.copy(city=it,school=null);onCity(it)}};if(d.city?.requiresSchool==true)Choice("المدرسة",state.schools.map{it.id.toString() to it.name},d.school?.id?.toString().orEmpty()){id->d=d.copy(school=state.schools.first{it.id.toString()==id})};Choice("الصف",state.grades.map{it.id.toString() to it.name},d.grade?.id?.toString().orEmpty()){id->d=d.copy(grade=state.grades.first{it.id.toString()==id})};Button({d.city?.let{c->d.grade?.let{g->onAcademic(c,d.school,g)}}},Modifier.fillMaxWidth()){Text("المراجعة")}}
 else->{Text("الاسم: ${d.fullName}\nالمستخدم: ${d.username}\nالمدينة: ${d.city?.name}\nالمدرسة: ${d.school?.name?:"غير مطلوبة"}\nالصف: ${d.grade?.name}");Row{Checkbox(accepted,{accepted=it});Text("أوافق على سياسة الخصوصية")};Button({onSubmit(accepted)},Modifier.fillMaxWidth(),enabled=!state.loading){Text(if(state.loading)"جارٍ الإنشاء…" else "إنشاء الحساب")}}
 };Row{if(state.step>1)TextButton(onBack){Text("السابق")};TextButton(onCancel){Text("العودة لتسجيل الدخول")}}}}
@Composable private fun Field(label:String,value:String,password:Boolean=false,onChange:(String)->Unit){OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={Text(label)},visualTransformation=if(password)PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)}
@Composable private fun Choice(label:String,items:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit){Text(label);items.forEach{(id,name)->Row{RadioButton(selected==id,{onSelect(id)});Text(name)}}}
