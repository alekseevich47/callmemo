package com.alekseevich47.callmemo.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable liquid-glass tokens (CodePen qErQmXO / giladfeder).
 *
 * Visual recipe for future chrome blocks:
 * - soft blue-tinted outer shadow
 * - translucent white frost layer (+ blur wash on API 31+)
 * - specular 1dp rim
 * - light diagonal highlight gradient
 * - crisp content drawn *above* the frost (never blurred)
 */
object LiquidGlassTokens {
    val Fill = Color.White.copy(alpha = 0.46f)
    val Specular = Color.White.copy(alpha = 0.78f)
    val ShadowTint = Color(0xFF1F2687).copy(alpha = 0.20f)
    val DefaultCorner = 28.dp
    val BlurRadius = 10.dp
    val Elevation = 16.dp
}

/**
 * Glass plate container. Put interactive content in [content] so icons/text stay sharp.
 */
@Composable
fun LiquidGlassPlate(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LiquidGlassTokens.DefaultCorner),
    elevation: Dp = LiquidGlassTokens.Elevation,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = LiquidGlassTokens.ShadowTint,
                spotColor = LiquidGlassTokens.ShadowTint,
            )
            .clip(shape),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(LiquidGlassTokens.Fill)
                .then(
                    if (Build.VERSION.SDK_INT >= 31) {
                        Modifier
                            .blur(LiquidGlassTokens.BlurRadius)
                            .background(Color.White.copy(alpha = 0.20f))
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.16f))
                    },
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, LiquidGlassTokens.Specular, shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}
