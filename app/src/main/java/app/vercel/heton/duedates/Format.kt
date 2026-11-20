package app.vercel.heton.duedates

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val cache = ConcurrentHashMap<String, DateTimeFormatter>()

fun dateFmt(skeleton: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    return cache.getOrPut("${locale.toLanguageTag()}|$skeleton") {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

fun String.cap(): String = replaceFirstChar { it.titlecase(Locale.getDefault()) }

fun Resources.plural(id: Int, n: Int): String = getQuantityString(id, n, n)

fun localized(base: Context): Context {
    val lang = Prefs.load(base).lang
    val locale = if (lang.isEmpty()) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(lang)
    Locale.setDefault(locale)
    val cfg = Configuration(base.resources.configuration)
    cfg.setLocale(locale)
    return base.createConfigurationContext(cfg)
}
