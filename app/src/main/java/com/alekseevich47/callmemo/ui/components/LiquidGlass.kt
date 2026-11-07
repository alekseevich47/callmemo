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
 * Reusable liquid-glass tokens (CodePen qErQmXO / giladfeder).
 *
 * Requires a [HazeState] sourced on the content behind the plate so text under
 * the glass is blurred + distorted (real backdrop sampling).
 */
object LiquidGlassTokens {
    /** ~10% less opaque than the previous 0.46 fill. */
    val Fill = Color.White.copy(alpha = 0.36f)
    val Specular = Color.White.copy(alpha = 0.70f)
    val ShadowTint = Color(0xFF1F2687).copy(alpha = 0.18f)
    val DefaultCorner = 28.dp
    val BlurRadius = 14.dp
    val Elevation = 14.dp
}

/** AGSL approx of CodePen feTurbulence + feDisplacementMap on the glass layer. */
private const val LiquidDistortAgsl = """
uniform shader content;
uniform float2 size;
uniform float strength;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / size;
    float n1 = sin(uv.y * 36.0 + uv.x * 10.0) * cos(uv.x * 28.0);
    float n2 = sin(uv.x * 52.0 - uv.y * 18.0) * 0.55;
    float n3 = cos((uv.x + uv.y) * 22.0) * 0.35;
    float2 offset = float2(n1 + n3, n2 - n1 * 0.4) * strength;
    return content.eval(fragCoord + offset);
}
"""

/**
 * Glass plate container. Put interactive content in [content] so icons/text stay sharp.
 * Pass [hazeState] from a parent that applied `Modifier.hazeSource(state)` on the backdrop.
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
        Box(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { shaderSize = it }
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = LiquidGlassTokens.Fill,
                        tints = listOf(HazeTint(LiquidGlassTokens.Fill)),
                        blurRadius = LiquidGlassTokens.BlurRadius,
                        noiseFactor = 0.08f,
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
                            distortShader.setFloatUniform("strength", 5.5f)
                            renderEffect = RenderEffect
                                .createRuntimeShaderEffect(distortShader, "content")
                                .asComposeRenderEffect()
                        }
                    } else {
                        Modifier
                    },
                )
                .background(LiquidGlassTokens.Fill.copy(alpha = 0.12f)),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, LiquidGlassTokens.Specular, shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.26f),
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}
