package app.masary.student.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.masary.student.R
import app.masary.student.auth.domain.LoginResult
import app.masary.student.auth.domain.LoginStudent

@Composable
fun LoginScreen(loginStudent: LoginStudent, modifier: Modifier = Modifier) {
    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LoginResult?>(null) }

    fun submit() {
        result = loginStudent(studentId, password.toCharArray())
        password = ""
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = studentId,
                onValueChange = { studentId = it; result = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.student_id)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                isError = result == LoginResult.InvalidStudentId,
                supportingText = if (result == LoginResult.InvalidStudentId) {
                    { Text(stringResource(R.string.student_id_required)) }
                } else null,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; result = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                isError = result == LoginResult.InvalidPassword || result == LoginResult.Rejected,
                supportingText = when (result) {
                    LoginResult.InvalidPassword -> { { Text(stringResource(R.string.password_required)) } }
                    LoginResult.Rejected -> { { Text(stringResource(R.string.login_rejected)) } }
                    else -> null
                },
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.login_action))
            }
            if (result == LoginResult.Success) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.login_success), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
