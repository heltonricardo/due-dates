package app.vercel.heton.duedates

import android.content.Context

data class AppSettings(val hour: Int = 0, val minute: Int = 0, val dark: Boolean = true)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("duedates", Context.MODE_PRIVATE)

    fun load(c: Context): AppSettings =
        sp(c).let { AppSettings(it.getInt("hour", 0), it.getInt("minute", 0), it.getBoolean("dark", true)) }

    fun save(c: Context, s: AppSettings) {
        sp(c).edit().putInt("hour", s.hour).putInt("minute", s.minute).putBoolean("dark", s.dark).apply()
    }
}
