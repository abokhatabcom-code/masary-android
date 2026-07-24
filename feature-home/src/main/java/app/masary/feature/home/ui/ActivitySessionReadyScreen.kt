package app.masary.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masary.core.ui.MasaryColors
import kotlinx.serialization.Serializable

@Serializable
internal data class ActivitySessionDestination(
    val sessionId: String,
    val destination: String,
    val expiresAt: String,
)

@Composable
internal fun ActivitySessionReadyScreen(
    destination: ActivitySessionDestination,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MasaryColors.brandGold,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    text = "تم تجهيز النشاط",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MasaryColors.brandNavy,
                )
                Text(
                    text = "أُنشئت جلسة آمنة بنجاح. ستُربط واجهة الأسئلة بهذه الجلسة في المرحلة المختصة.",
                    color = MasaryColors.muted,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "رقم الجلسة: ${destination.sessionId.take(12)}…",
                    color = MasaryColors.muted,
                )
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("العودة إلى المواد")
                }
            }
        }
    }
}
