package com.alekseevich47.callmemo.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid glass tokens matching CodePen giladfeder/qErQmXO:
 * nearly clear fill (rgba 255,255,255,0.03), light blur, strong refraction.
 */
object LiquidGlassTokens {
    /** CodePen ::before uses ~0.03 white — milky white kills the liquid look. */
    val Fill = Color.White.copy(alpha = 0.03f)
    val TintCool = Color(0xFFE8EEF8).copy(alpha = 0.10f)
    val Specular = Color.White.copy(alpha = 0.22f)
    val ShadowTint = Color(0xFF1F2687).copy(alpha = 0.21f)
    val DefaultCorner = 28.dp
    /** CodePen backdrop-filter: blur(3px). */
    val BlurRadius = 3.dp
    val Elevation = 18.dp
    /** AGSL displacement strength ≈ SVG feDisplacementMap scale≈85 on phone UI. */
    const val DistortStrength = 42f
}

/**
 * Approx of CodePen:
 * feTurbulence fractalNoise baseFrequency 0.012, 2 octaves → feDisplacementMap scale 85.
 */
private const val LiquidDistortAgsl = """
uniform shader content;
uniform float2 size;
uniform float strength;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / size;
    // Low-frequency fractal-ish noise (2 octaves), not high-freq shimmer.
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
 * Glass plate. Backdrop is blurred + strongly refracted; interactive [content] stays sharp.
 */
@Composable
fun LiquidGlassPlate(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LiquidGlassTokens.DefaultCorner),
    elevation: Dp = LiquidGlassTokens.Elevation,
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
        // Backdrop layer: haze sample + liquid distortion (CodePen ::after).
        Box(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { shaderSize = it }
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = Color.Transparent,
                        tints = listOf(
                            HazeTint(LiquidGlassTokens.TintCool),
                            HazeTint(LiquidGlassTokens.Fill),
                        ),
                        blurRadius = LiquidGlassTokens.BlurRadius,
                        noiseFactor = 0.12f,
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
                ),
        )
        // Specular rim + faint gradient (CodePen ::before) — keep nearly clear.
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, LiquidGlassTokens.Specular, shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}
