package app.vercel.heton.duedates

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
