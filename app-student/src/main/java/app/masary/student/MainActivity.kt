package app.masary.student

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.masary.core.ui.MasaryTheme
import app.masary.student.auth.data.StudentAuthFactory
import app.masary.student.auth.domain.LoginStudent
import app.masary.student.auth.ui.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MasaryTheme {
                LoginScreen(loginStudent = LoginStudent(StudentAuthFactory.create()))
            }
        }
    }
}

@Composable
private fun AppScreen() {
    LoginScreen(
        loginStudent = LoginStudent { _, password -> password.isNotEmpty() },
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun AppScreenPreview() {
    MasaryTheme { AppScreen() }
}
