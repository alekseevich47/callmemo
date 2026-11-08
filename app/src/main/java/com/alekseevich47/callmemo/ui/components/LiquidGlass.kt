package com.alekseevich47.callmemo.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid glass tokens — Telegram/CodePen style: white 25% plate + strong blur/refraction,
 * with a soft progressive haze halo around the plate.
 */
object LiquidGlassTokens {
    /** Solid white fill at 25% opacity on the plate itself. */
    val Fill = Color.White.copy(alpha = 0.25f)
    val TintCool = Color(0xFFE8EEF8).copy(alpha = 0.14f)
    val Specular = Color.White.copy(alpha = 0.45f)
    val ShadowTint = Color(0xFF1F2687).copy(alpha = 0.22f)
    val DefaultCorner = 28.dp
    val BlurRadius = 22.dp
    val HaloBlurRadius = 16.dp
    val HaloExtent = 22.dp
    val Elevation = 16.dp
    /** AGSL displacement — stronger liquid refraction under the plate. */
    const val DistortStrength = 56f
}

/**
 * Approx of CodePen feTurbulence + feDisplacementMap (scale ~85).
 */
private const val LiquidDistortAgsl = """
uniform shader content;
uniform float2 size;
uniform float strength;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / size;
    float n1 = sin(uv.x * 7.5 + uv.y * 5.2) * cos(uv.y * 6.1 - uv.x * 3.4);
    float n2 = sin(uv.x * 15.0 - uv.y * 11.0) * cos(uv.x * 9.0 + uv.y * 13.0) * 0.55;
    float n3 = sin((uv.x + uv.y) * 4.2) * cos((uv.x - uv.y) * 5.8) * 0.35;
    float nx = n1 + n2 * 0.65 + n3;
    float ny = n2 - n1 * 0.45 + n3 * 0.7;
    float2 offset = float2(nx, ny) * strength;
    return content.eval(fragCoord + offset);
}
"""

/**
 * Glass plate with optional outer progressive blur halo.
 * Interactive [content] stays sharp above the distorted backdrop.
 */
@Composable
fun LiquidGlassPlate(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LiquidGlassTokens.DefaultCorner),
    elevation: Dp = LiquidGlassTokens.Elevation,
    halo: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val distortShader = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { RuntimeShader(LiquidDistortAgsl) }.getOrNull()
        } else {
            null
        }
    }
    var shaderSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier.then(
            if (halo) Modifier.padding(LiquidGlassTokens.HaloExtent) else Modifier,
        ),
    ) {
        if (halo) {
            // Soft blur that fades out around the plate (content near the chip softens).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(-LiquidGlassTokens.HaloExtent)
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
                    .onSizeChanged { shaderSize = it }
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = LiquidGlassTokens.Fill,
                            tints = listOf(
                                HazeTint(LiquidGlassTokens.Fill),
                                HazeTint(LiquidGlassTokens.TintCool),
                            ),
                            blurRadius = LiquidGlassTokens.BlurRadius,
                            noiseFactor = 0.10f,
                        ),
                    )
                    .then(
                        if (distortShader != null && shaderSize.width > 0 && shaderSize.height > 0) {
                            Modifier.graphicsLayer {
                                distortShader.setFloatUniform(
                                    "size",
                                    shaderSize.width.toFloat(),
                                    shaderSize.height.toFloat(),
                                )
                                distortShader.setFloatUniform(
                                    "strength",
                                    LiquidGlassTokens.DistortStrength,
                                )
                                renderEffect = RenderEffect
                                    .createRuntimeShaderEffect(distortShader, "content")
                                    .asComposeRenderEffect()
                            }
                        } else {
                            Modifier
                        },
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
                                Color.White.copy(alpha = 0.08f),
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
