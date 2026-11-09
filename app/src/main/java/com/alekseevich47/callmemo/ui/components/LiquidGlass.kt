package com.alekseevich47.callmemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid glass tokens — frosted white plate + backdrop blur.
 *
 * Note: AGSL RuntimeShader distortion was removed from the default path.
 * On Tecno/HiOS it poisoned bottom-of-screen compositing: a hard horizontal
 * seam appeared and opaque siblings (dialpad, FAB, sticky player) bled the
 * hazeSource list through their lower half.
 */
object LiquidGlassTokens {
    /** Denser fill so list text does not read through the chrome plate. */
    val Fill = Color.White.copy(alpha = 0.72f)
    val TintCool = Color(0xFFE8EEF8).copy(alpha = 0.20f)
    val Specular = Color.White.copy(alpha = 0.45f)
    val ShadowTint = Color(0xFF1F2687).copy(alpha = 0.22f)
    val DefaultCorner = 28.dp
    val BlurRadius = 22.dp
    val HaloBlurRadius = 16.dp
    val HaloExtent = 22.dp
    val Elevation = 16.dp
}

/**
 * Glass plate with optional outer progressive blur halo.
 * Interactive [content] stays sharp above the frosted backdrop.
 *
 * [halo] defaults off for chrome bars: the padded halo rectangle shifts the
 * chip plate upward and created the dialpad open/close menu jump.
 */
@Composable
fun LiquidGlassPlate(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LiquidGlassTokens.DefaultCorner),
    elevation: Dp = LiquidGlassTokens.Elevation,
    halo: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    // Offscreen layer: isolate glass from hazeSource siblings so OEM GPUs
    // cannot punch list pixels through neighboring opaque chrome.
    Box(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        if (halo) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = Color.Transparent,
                            tints = emptyList(),
                            blurRadius = LiquidGlassTokens.HaloBlurRadius,
                            noiseFactor = 0.04f,
                        ),
                    ) {
                        progressive = HazeProgressive.RadialGradient(
                            centerIntensity = 1f,
                            radiusIntensity = 0f,
                        )
                    },
            )
        }

        Box(
            modifier = Modifier
                .then(if (halo) Modifier.padding(LiquidGlassTokens.HaloExtent) else Modifier)
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = LiquidGlassTokens.ShadowTint,
                    spotColor = LiquidGlassTokens.ShadowTint,
                )
                .clip(shape),
        ) {
            // Frost: Haze blur only — no AGSL RenderEffect (see file note).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = LiquidGlassTokens.Fill,
                            tints = listOf(
                                HazeTint(LiquidGlassTokens.Fill),
                                HazeTint(LiquidGlassTokens.TintCool),
                            ),
                            blurRadius = LiquidGlassTokens.BlurRadius,
                            noiseFactor = 0.08f,
                        ),
                    )
                    .background(LiquidGlassTokens.Fill),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, LiquidGlassTokens.Specular, shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                            tileMode = TileMode.Clamp,
                        ),
                    ),
            )
            content()
        }
    }
}
