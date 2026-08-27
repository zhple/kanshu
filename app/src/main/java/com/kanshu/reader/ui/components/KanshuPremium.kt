package com.kanshu.reader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val PremiumShape = RoundedCornerShape(22.dp)
private val GlassShape = RoundedCornerShape(20.dp)

/** 玻璃质感卡片：柔阴影 + 高光描边 + 半透明底。 */
@Composable
fun Modifier.kanshuPremiumCard(): Modifier {
    val infinite = rememberInfiniteTransition(label = "premiumEdge")
    val edge by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "premiumEdgeVal"
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surface
    val corner = 22.dp

    return this
        .drawBehind {
            val r = corner.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.07f),
                topLeft = Offset(6f, 10f),
                size = size,
                cornerRadius = CornerRadius(r, r)
            )
        }
        .clip(PremiumShape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.78f),
                    surface.copy(alpha = 0.62f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.65f),
                    primary.copy(alpha = 0.12f + edge * 0.14f),
                    secondary.copy(alpha = 0.08f + edge * 0.1f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(400f, 200f)
            ),
            shape = PremiumShape
        )
}

@Composable
fun KanshuPremiumHero(
    title: String,
    subtitle: String,
    bookCount: Int,
    moodLabel: String,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "heroPremium")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroDrift"
    )
    val line by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroLine"
    )

    val sea = MaterialTheme.colorScheme.primary
    val coffee = MaterialTheme.colorScheme.secondary
    val foam = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShape)
            .drawBehind {
                val r = 20.dp.toPx()
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.12f),
                    topLeft = Offset(8f, 14f),
                    size = size,
                    cornerRadius = CornerRadius(r, r)
                )
            }
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        lerp(sea, coffee, drift * 0.4f),
                        lerp(coffee, foam, 0.35f + drift * 0.2f),
                        lerp(foam, sea, drift * 0.25f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(0.45f),
                        Color.White.copy(0.08f)
                    )
                ),
                GlassShape
            )
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.1f + drift * 0.08f),
                radius = w * 0.35f,
                center = Offset(w * (0.1f + drift * 0.75f), h * 0.35f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = w * 0.25f,
                center = Offset(w * 0.85f, h * 0.2f)
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumChip(label = "$bookCount 册藏书")
                PremiumChip(label = moodLabel)
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(line * 0.35f + 0.25f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
private fun PremiumChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.28f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun PremiumBookCover(
    icon: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier,
    shimmer: Boolean = true
) {
    val infinite = rememberInfiniteTransition(label = "bookGloss")
    val gloss by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bookGlossVal"
    )

    val coverModifier = if (shimmer) modifier.kanshuCoverShimmer() else modifier

    Box(
        modifier = coverModifier
            .size(64.dp)
            .drawBehind {
                drawRoundRect(
                    color = Color.Black.copy(0.15f),
                    topLeft = Offset(4f, 6f),
                    size = size,
                    cornerRadius = CornerRadius(14.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(0.45f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gx = w * gloss
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(0.35f),
                        Color.Transparent
                    ),
                    start = Offset(gx - w * 0.15f, 0f),
                    end = Offset(gx + w * 0.15f, h)
                )
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun KanshuEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "emptyRing")
    val ring by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "emptyRingVal"
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            primary.copy(0.35f),
                            secondary.copy(0.25f),
                            primary.copy(0.1f),
                            secondary.copy(0.35f)
                        )
                    ),
                    radius = size.width * 0.48f,
                    center = c,
                    alpha = 0.5f + ring * 0.3f
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                primary.copy(0.16f),
                                secondary.copy(0.12f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(0.35f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
