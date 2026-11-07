package com.alekseevich47.callmemo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alekseevich47.callmemo.R

val CallMemoBlue = Color(0xFF1F6FEB)
val CallMemoBlueSoft = Color(0xFF4C8DFF)
val CallMemoSky = Color(0xFFEAF3FF)
val CallMemoMist = Color(0xFFF5F8FC)
val CallMemoInk = Color(0xFF152033)
val CallMemoMuted = Color(0xFF6B7A90)
val CallMemoGlass = Color(0xD9FFFFFF)
val CallMemoDanger = Color(0xFFE5484D)
val CallMemoSuccess = Color(0xFF2F9E44)

val CallMemoFontFamily = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
)

private val LightColors = lightColorScheme(
    primary = CallMemoBlue,
    onPrimary = Color.White,
    secondary = CallMemoBlueSoft,
    background = CallMemoMist,
    surface = Color.White,
    onSurface = CallMemoInk,
    onBackground = CallMemoInk,
    surfaceVariant = CallMemoSky,
    onSurfaceVariant = CallMemoMuted,
    error = CallMemoDanger,
)

@Composable
fun CallMemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Mock is light-first; keep light until dark designs arrive.
    @Suppress("UNUSED_VARIABLE")
    val ignored = darkTheme
    val typography = MaterialTheme.typography.copy(
        displayLarge = TextStyle(fontFamily = CallMemoFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp),
        headlineLarge = TextStyle(fontFamily = CallMemoFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
        headlineMedium = TextStyle(fontFamily = CallMemoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = CallMemoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = CallMemoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = TextStyle(fontFamily = CallMemoFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = CallMemoFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        labelLarge = TextStyle(fontFamily = CallMemoFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    )
    MaterialTheme(
        colorScheme = LightColors,
        typography = typography,
        content = content,
    )
}
