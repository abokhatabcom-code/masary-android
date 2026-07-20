package app.masary.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masary.core.models.auth.StudentSession
import app.masary.core.ui.MasaryBrandMark
import app.masary.core.ui.MasaryColors
import app.masary.core.ui.MasaryOrbitDecoration
import app.masary.core.ui.MasaryWordmark
import app.masary.feature.auth.R

@Composable
fun AuthScreen(
    state: LoginUiState,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (state) {
            LoginUiState.Restoring -> RestoringSessionScreen()
            is LoginUiState.Success -> StudentScreen(state.session, onLogout)
            else -> LoginScreen(state, onLogin)
        }
    }
}

@Composable
private fun BrandBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MasaryColors.brandNavyDeep,
                        MasaryColors.brandNavy,
                        Color(0xFF0A2B61),
                    ),
                ),
            ),
    ) {
        MasaryOrbitDecoration(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(210.dp),
            color = MasaryColors.brandGoldBright,
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 72.dp, start = 24.dp)
                .size(9.dp),
            shape = CircleShape,
            color = MasaryColors.brandGoldBright,
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 90.dp, end = 26.dp)
                .size(7.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.45f),
        ) {}
        content()
    }
}

@Composable
private fun RestoringSessionScreen() {
    BrandBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MasaryBrandMark(size = 92.dp)
            Spacer(Modifier.height(18.dp))
            MasaryWordmark(inverse = true, centered = true)
            Spacer(Modifier.height(30.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = MasaryColors.brandGoldBright,
                trackColor = Color.White.copy(alpha = 0.16f),
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.restoring_session),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoginScreen(
    state: LoginUiState,
    onLogin: (String, String) -> Unit,
) {
    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val loading = state == LoginUiState.Loading
    val canSubmit = studentId.isNotBlank() && password.isNotBlank() && !loading

    fun submit() {
        if (canSubmit) {
            onLogin(studentId.trim(), password)
            password = ""
        }
    }

    BrandBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MasaryBrandMark(size = 90.dp)
                Spacer(Modifier.height(14.dp))
                MasaryWordmark(inverse = true, centered = true)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.login_card_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MasaryColors.brandNavy,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.login_card_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MasaryColors.muted,
                        )
                        Spacer(Modifier.height(18.dp))

                        OutlinedTextField(
                            value = studentId,
                            onValueChange = { studentId = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            label = { Text(stringResource(R.string.student_id)) },
                            placeholder = { Text(stringResource(R.string.student_id_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = brandTextFieldColors(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            label = { Text(stringResource(R.string.password)) },
                            placeholder = { Text(stringResource(R.string.password_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            trailingIcon = {
                                TextButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = !loading,
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (passwordVisible) R.string.hide_password else R.string.show_password,
                                        ),
                                        color = MasaryColors.brandNavy,
                                    )
                                }
                            },
                            colors = brandTextFieldColors(),
                        )

                        if (state is LoginUiState.Error) {
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                color = Color(0xFFFFE8E5),
                            ) {
                                Text(
                                    text = state.message,
                                    modifier = Modifier.padding(13.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF8B241C),
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { submit() },
                            enabled = canSubmit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MasaryColors.brandGoldBright,
                                contentColor = MasaryColors.brandNavyDeep,
                                disabledContainerColor = MasaryColors.border,
                                disabledContentColor = MasaryColors.muted,
                            ),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(23.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MasaryColors.brandNavy,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.login_action),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = MasaryColors.success,
                    ) {}
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.login_secure_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun brandTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MasaryColors.brandGold,
    unfocusedBorderColor = MasaryColors.border,
    focusedLabelColor = MasaryColors.brandNavy,
    unfocusedLabelColor = MasaryColors.muted,
    focusedTextColor = MasaryColors.brandNavyDeep,
    unfocusedTextColor = MasaryColors.brandNavyDeep,
    cursorColor = MasaryColors.brandNavy,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
)

@Composable
private fun StudentScreen(
    session: StudentSession,
    onLogout: () -> Unit,
) {
    BrandBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MasaryBrandMark(size = 76.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.student_screen_title, session.displayName),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MasaryColors.brandNavy,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.student_screen_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MasaryColors.muted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MasaryColors.brandNavy,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.logout))
                    }
                }
            }
        }
    }
}
