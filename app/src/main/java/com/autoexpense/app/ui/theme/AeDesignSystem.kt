package com.autoexpense.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AeColors {
    val Primary900 = Color(0xFF0F1729)
    val Primary800 = Color(0xFF1A1F36)
    val Primary700 = Color(0xFF2D3561)
    val Primary600 = Color(0xFF3D4785)
    val Primary500 = Color(0xFF5B7FFF)
    val Primary400 = Color(0xFF7B9AFF)
    val Primary100 = Color(0xFFDEE8FF)

    val Purple = Color(0xFF8B5CF6)
    val Teal = Color(0xFF06B6D4)
    val Emerald = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Expense = Color(0xFFFF6B6B)

    val LightBackground = Color(0xFFF5F7FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceAlt = Color(0xFFF9FAFB)
    val LightTextPrimary = Color(0xFF1F2937)
    val LightTextSecondary = Color(0xFF6B7280)
    val LightTextTertiary = Color(0xFF9CA3AF)
    val LightBorder = Color(0xFFE5E7EB)

    val DarkBackground = Color(0xFF0F1729)
    val DarkSurface = Color(0xFF1A1F36)
    val DarkSurfaceAlt = Color(0xFF202844)
    val DarkTextPrimary = Color(0xFFF8FAFC)
    val DarkTextSecondary = Color(0xFFCBD5E1)
    val DarkTextTertiary = Color(0xFF94A3B8)
    val DarkBorder = Color(0xFF2D3561)

    val CategoryFood = Color(0xFFFF6B6B)
    val CategoryTransport = Color(0xFF4ECDC4)
    val CategoryShopping = Color(0xFF45B7D1)
    val CategoryEntertainment = Color(0xFFFFA07A)
    val CategoryBills = Color(0xFF98D8C8)
    val CategoryHealth = Color(0xFFFF85A2)
    val CategoryFitness = Color(0xFF95E1D3)
    val CategoryEducation = Color(0xFFA8E6CF)
    val CategoryOther = Color(0xFF9CA3AF)
}

object AeSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 48.dp
}

object AeRadius {
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Full = 999.dp
}

object AeElevation {
    val Sm = 2.dp
    val Md = 4.dp
    val Lg = 8.dp
}

object AeMotion {
    const val Short = 180
    const val Medium = 250
    const val Long = 750

    fun <T> standard(durationMillis: Int = Medium) = tween<T>(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing
    )
}

object AeGradients {
    val Hero = Brush.linearGradient(listOf(AeColors.Primary600, AeColors.Primary800))
    val Accent = Brush.linearGradient(listOf(AeColors.Primary500, AeColors.Purple))
    val Success = Brush.linearGradient(listOf(AeColors.Emerald, AeColors.Teal))
}

@Immutable
data class AePalette(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val primary: Color,
    val primarySoft: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val expense: Color
)

@Composable
fun aePalette(darkTheme: Boolean = isSystemInDarkTheme()): AePalette {
    return if (darkTheme) {
        AePalette(
            background = AeColors.DarkBackground,
            surface = AeColors.DarkSurface,
            surfaceAlt = AeColors.DarkSurfaceAlt,
            textPrimary = AeColors.DarkTextPrimary,
            textSecondary = AeColors.DarkTextSecondary,
            textTertiary = AeColors.DarkTextTertiary,
            border = AeColors.DarkBorder,
            primary = AeColors.Primary500,
            primarySoft = AeColors.Primary500.copy(alpha = 0.16f),
            success = AeColors.Emerald,
            warning = AeColors.Warning,
            error = AeColors.Error,
            expense = AeColors.Expense
        )
    } else {
        AePalette(
            background = AeColors.LightBackground,
            surface = AeColors.LightSurface,
            surfaceAlt = AeColors.LightSurfaceAlt,
            textPrimary = AeColors.LightTextPrimary,
            textSecondary = AeColors.LightTextSecondary,
            textTertiary = AeColors.LightTextTertiary,
            border = AeColors.LightBorder,
            primary = AeColors.Primary500,
            primarySoft = AeColors.Primary100,
            success = AeColors.Emerald,
            warning = AeColors.Warning,
            error = AeColors.Error,
            expense = AeColors.Expense
        )
    }
}

fun aeColorScheme(palette: AePalette, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            background = palette.background,
            surface = palette.surface,
            onPrimary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            secondary = palette.textSecondary,
            error = palette.error
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            background = palette.background,
            surface = palette.surface,
            onPrimary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            secondary = palette.textSecondary,
            error = palette.error
        )
    }
}

object AeType {
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val BodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val Body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val BodyStrong = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val Title = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val Section = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val ScreenTitle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
    val Hero = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun AeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = aePalette(darkTheme)
    MaterialTheme(
        colorScheme = aeColorScheme(palette, darkTheme),
        typography = MaterialTheme.typography,
        content = content
    )
}

fun categoryColor(category: String): Color {
    val lower = category.lowercase()
    return when {
        lower.contains("food") || lower.contains("dining") -> AeColors.CategoryFood
        lower.contains("transport") || lower.contains("car") || lower.contains("fuel") -> AeColors.CategoryTransport
        lower.contains("shopping") || lower.contains("grocery") -> AeColors.CategoryShopping
        lower.contains("entertainment") || lower.contains("movie") -> AeColors.CategoryEntertainment
        lower.contains("bill") || lower.contains("rent") || lower.contains("home") -> AeColors.CategoryBills
        lower.contains("health") || lower.contains("medical") -> AeColors.CategoryHealth
        lower.contains("fitness") -> AeColors.CategoryFitness
        lower.contains("education") || lower.contains("school") -> AeColors.CategoryEducation
        else -> AeColors.CategoryOther
    }
}
