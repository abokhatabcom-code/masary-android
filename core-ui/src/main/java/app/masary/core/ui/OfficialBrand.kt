package app.masary.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scalable brand mark used by the native app until the official raster/vector
 * asset pipeline is added to all Android density buckets.
 * The proportions, navy field, white arch, gold path and star follow the approved identity.
 */
@Composable
fun MasaryBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val corner = w * 0.22f

        drawRoundRect(
            color = MasaryColors.logoNavy,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = Offset(w * 0.035f, h * 0.035f),
            size = Size(w * 0.93f, h * 0.93f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.86f, corner * 0.86f),
            style = Stroke(width = w * 0.014f),
        )

        val arch = Path().apply {
            moveTo(w * 0.22f, h * 0.76f)
            lineTo(w * 0.22f, h * 0.45f)
            cubicTo(w * 0.22f, h * 0.25f, w * 0.35f, h * 0.22f, w * 0.48f, h * 0.22f)
            moveTo(w * 0.78f, h * 0.76f)
            lineTo(w * 0.78f, h * 0.45f)
            cubicTo(w * 0.78f, h * 0.25f, w * 0.65f, h * 0.22f, w * 0.54f, h * 0.22f)
        }
        drawPath(
            path = arch,
            color = Color.White,
            style = Stroke(
                width = w * 0.135f,
                cap = StrokeCap.Square,
                join = StrokeJoin.Round,
            ),
        )

        val road = Path().apply {
            moveTo(w * 0.25f, h * 0.79f)
            cubicTo(w * 0.35f, h * 0.59f, w * 0.54f, h * 0.51f, w * 0.515f, h * 0.27f)
            cubicTo(w * 0.52f, h * 0.22f, w * 0.53f, h * 0.19f, w * 0.535f, h * 0.16f)
            cubicTo(w * 0.555f, h * 0.35f, w * 0.61f, h * 0.55f, w * 0.49f, h * 0.79f)
            close()
        }
        drawPath(path = road, color = MasaryColors.brandGoldBright)

        val dashColor = Color.White.copy(alpha = 0.95f)
        drawLine(dashColor, Offset(w * 0.37f, h * 0.68f), Offset(w * 0.405f, h * 0.61f), w * 0.025f, StrokeCap.Square)
        drawLine(dashColor, Offset(w * 0.44f, h * 0.54f), Offset(w * 0.47f, h * 0.47f), w * 0.022f, StrokeCap.Square)
        drawLine(dashColor, Offset(w * 0.495f, h * 0.40f), Offset(w * 0.51f, h * 0.34f), w * 0.018f, StrokeCap.Square)

        val star = starPath(
            center = Offset(w * 0.535f, h * 0.125f),
            outer = w * 0.09f,
            inner = w * 0.035f,
        )
        drawPath(star, MasaryColors.brandGoldBright)
    }
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path = Path().apply {
    repeat(16) { index ->
        val radius = if (index % 2 == 0) outer else inner
        val angle = -PI / 2 + index * PI / 8
        val point = Offset(
            x = center.x + cos(angle).toFloat() * radius,
            y = center.y + sin(angle).toFloat() * radius,
        )
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

@Composable
fun MasaryWordmark(
    modifier: Modifier = Modifier,
    inverse: Boolean = false,
    centered: Boolean = false,
) {
    val titleColor = if (inverse) Color.White else MasaryColors.logoNavy
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = "مساري",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = titleColor,
            textAlign = textAlign,
        )
        Text(
            text = "التعليمية",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MasaryColors.brandGoldBright,
            textAlign = textAlign,
        )
    }
}

@Composable
fun MasaryBrandLockup(
    modifier: Modifier = Modifier,
    logoSize: Dp = 58.dp,
    inverse: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MasaryBrandMark(size = logoSize)
        Spacer(Modifier.width(12.dp))
        MasaryWordmark(inverse = inverse)
    }
}

@Composable
fun MasaryOrbitDecoration(
    modifier: Modifier = Modifier,
    color: Color = MasaryColors.brandGoldBright,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val navy = MasaryColors.logoNavy
        val line = Path().apply {
            moveTo(0f, h * 0.72f)
            cubicTo(w * 0.28f, h * 0.05f, w * 0.52f, h * 0.98f, w, h * 0.28f)
        }
        drawPath(line, color.copy(alpha = 0.45f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))

        val lineTwo = Path().apply {
            moveTo(0f, h * 0.88f)
            cubicTo(w * 0.30f, h * 0.24f, w * 0.64f, h * 0.92f, w, h * 0.46f)
        }
        drawPath(lineTwo, navy.copy(alpha = 0.18f), style = Stroke(width = 1.8f, cap = StrokeCap.Round))

        drawCircle(color, radius = 4.5f, center = Offset(w * 0.18f, h * 0.42f))
        drawCircle(navy.copy(alpha = 0.5f), radius = 4f, center = Offset(w * 0.64f, h * 0.66f))
        drawPath(starPath(Offset(w * 0.86f, h * 0.22f), 9f, 3.5f), color)
    }
}
