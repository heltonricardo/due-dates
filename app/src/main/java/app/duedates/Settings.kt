package app.duedates

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

data class AppSettings(val hour: Int = 0, val minute: Int = 0, val theme: Int = -1)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("duedates", Context.MODE_PRIVATE)

    fun load(c: Context): AppSettings =
        sp(c).let { AppSettings(it.getInt("hour", 0), it.getInt("minute", 0), it.getInt("theme", -1)) }

    fun save(c: Context, s: AppSettings) {
        sp(c).edit().putInt("hour", s.hour).putInt("minute", s.minute).putInt("theme", s.theme).apply()
    }
}

private fun hsl(h: Float, s: Float, l: Float): Color = Color(ColorUtils.HSLToColor(floatArrayOf(h, s, l)))

fun schemeFor(accent: Color): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    val h = hsl[0]
    val h2 = (h + 60f) % 360f
    return darkColorScheme(
        primary = hsl(h, 0.85f, 0.78f),
        onPrimary = hsl(h, 0.9f, 0.15f),
        primaryContainer = hsl(h, 0.45f, 0.28f),
        onPrimaryContainer = hsl(h, 0.9f, 0.9f),
        secondary = hsl(h, 0.3f, 0.75f),
        onSecondary = hsl(h, 0.3f, 0.15f),
        secondaryContainer = hsl(h, 0.2f, 0.28f),
        onSecondaryContainer = hsl(h, 0.3f, 0.9f),
        tertiary = hsl(h2, 0.6f, 0.78f),
        onTertiary = hsl(h2, 0.6f, 0.15f),
        tertiaryContainer = hsl(h2, 0.3f, 0.28f),
        onTertiaryContainer = hsl(h2, 0.6f, 0.9f),
        background = hsl(h, 0.12f, 0.08f),
        onBackground = hsl(h, 0.1f, 0.9f),
        surface = hsl(h, 0.12f, 0.08f),
        onSurface = hsl(h, 0.1f, 0.9f),
        surfaceVariant = hsl(h, 0.1f, 0.22f),
        onSurfaceVariant = hsl(h, 0.1f, 0.78f),
        surfaceContainerLowest = hsl(h, 0.12f, 0.05f),
        surfaceContainerLow = hsl(h, 0.12f, 0.1f),
        surfaceContainer = hsl(h, 0.12f, 0.12f),
        surfaceContainerHigh = hsl(h, 0.12f, 0.15f),
        surfaceContainerHighest = hsl(h, 0.12f, 0.19f),
        outline = hsl(h, 0.08f, 0.55f),
        outlineVariant = hsl(h, 0.1f, 0.3f)
    )
}
