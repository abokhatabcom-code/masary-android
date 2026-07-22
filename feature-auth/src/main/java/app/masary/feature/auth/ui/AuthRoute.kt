package app.masary.feature.auth.ui
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.StudentSession
import app.masary.feature.auth.domain.*

@Composable fun AuthRoute(repository:AuthRepository,registrationRepository:RegistrationRepository,sessionManager:SessionManager,deviceName:String,onAuthenticated:(StudentSession)->Unit={},authenticatedContent:(@Composable (StudentSession,()->Unit)->Unit)?=null){
 val login:LoginViewModel=viewModel(factory=LoginFactory(repository,sessionManager,deviceName));val state by login.state.collectAsStateWithLifecycle();var registering by rememberSaveable{mutableStateOf(false)}
 LaunchedEffect(state){(state as? LoginUiState.Success)?.session?.let(onAuthenticated)}
 if(registering){val registration:RegistrationViewModel=viewModel(factory=RegistrationFactory(registrationRepository,sessionManager,deviceName){ registering=false; onAuthenticated(it) });val rs by registration.state.collectAsStateWithLifecycle();RegistrationScreen(rs,registration::account,registration::city,registration::academic,registration::submit,registration::back){registering=false}}
 else if(state is LoginUiState.Success && authenticatedContent!=null)authenticatedContent((state as LoginUiState.Success).session,login::logout)
 else AuthScreen(state,login::login,login::logout){registering=true}
}
private class LoginFactory(val r:AuthRepository,val s:SessionManager,val d:String):ViewModelProvider.Factory{override fun<T:ViewModel>create(c:Class<T>):T=@Suppress("UNCHECKED_CAST")(LoginViewModel(r,s,d) as T)}
private class RegistrationFactory(val r:RegistrationRepository,val s:SessionManager,val d:String,val done:(StudentSession)->Unit):ViewModelProvider.Factory{override fun<T:ViewModel>create(c:Class<T>):T=@Suppress("UNCHECKED_CAST")(RegistrationViewModel(r,s,d,done) as T)}
