package app.duely

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Occ(
    val groupId: String,
    val period: String,
    val title: String,
    val note: String?,
    val dueDate: LocalDate,
    val completedAt: LocalDateTime?,
    val isException: Boolean,
    val once: Boolean,
    val adjusted: Boolean,
    val icon: String,
    val color: Int
) {
    val key get() = "$groupId|$period"
    fun overdue(today: LocalDate) = completedAt == null && dueDate.isBefore(today)
}

private fun Reminder.clamp(ym: YearMonth): LocalDate = ym.atDay(minOf(anchorDay, ym.lengthOfMonth()))

fun Reminder.dateAt(k: Int): LocalDate {
    val s = LocalDate.parse(startDate)
    return when (type) {
        Freq.ONCE -> s
        Freq.WEEKLY -> s.plusWeeks(k.toLong())
        Freq.MONTHLY -> clamp(YearMonth.from(s).plusMonths(k.toLong()))
        Freq.YEARLY -> clamp(YearMonth.from(s).plusYears(k.toLong()))
    }
}

fun Reminder.isAdjusted(k: Int): Boolean =
    (type == Freq.MONTHLY || type == Freq.YEARLY) && dateAt(k).dayOfMonth < anchorDay

fun Reminder.indexOf(date: LocalDate): Int {
    val s = LocalDate.parse(startDate)
    return when (type) {
        Freq.ONCE -> 0
        Freq.WEEKLY -> (ChronoUnit.DAYS.between(s, date) / 7).toInt()
        Freq.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(s), YearMonth.from(date)).toInt()
        Freq.YEARLY -> date.year - s.year
    }
}

fun Reminder.occurrencesIn(from: LocalDate, to: LocalDate): List<Int> {
    val s = LocalDate.parse(startDate)
    val count = occurrenceCount
    fun ok(k: Int) = k >= 0 && (count == null || k < count)
    val ks = mutableListOf<Int>()
    when (type) {
        Freq.ONCE -> if (ok(0)) ks += 0
        Freq.WEEKLY -> {
            var k = maxOf(0, (ChronoUnit.DAYS.between(s, from) / 7).toInt() - 1)
            while (ok(k) && !dateAt(k).isAfter(to)) {
                ks += k
                k++
            }
        }
        else -> {
            var ym = YearMonth.from(from)
            val end = YearMonth.from(to)
            while (ym <= end) {
                val k = if (type == Freq.MONTHLY) {
                    ChronoUnit.MONTHS.between(YearMonth.from(s), ym).toInt()
                } else if (ym.monthValue == s.monthValue) {
                    ym.year - s.year
                } else {
                    -1
                }
                if (ok(k)) ks += k
                ym = ym.plusMonths(1)
            }
        }
    }
    return ks.filter { val d = dateAt(it); !d.isBefore(from) && !d.isAfter(to) }
}

fun occurrencesFor(ym: YearMonth, reminders: List<Reminder>, states: List<OccurrenceState>): List<Occ> {
    val from = ym.atDay(1)
    val to = ym.atEndOfMonth()
    val saved = states.map { "${it.groupId}|${it.period}" }.toSet()
    val onceGroups = reminders.filter { it.type == Freq.ONCE }.map { it.groupId }.toSet()
    val fromRules = reminders.flatMap { r ->
        r.occurrencesIn(from, to).mapNotNull { k ->
            val d = r.dateAt(k)
            if ("${r.groupId}|$d" in saved) null else Occ(
                groupId = r.groupId,
                period = d.toString(),
                title = r.title,
                note = r.note,
                dueDate = d,
                completedAt = null,
                isException = false,
                once = r.type == Freq.ONCE,
                adjusted = r.isAdjusted(k),
                icon = r.icon,
                color = r.color
            )
        }
    }
    val fromStates = states.filter { !it.deleted && it.dueDate.startsWith(ym.toString()) }.map {
        Occ(
            groupId = it.groupId,
            period = it.period,
            title = it.title,
            note = it.note,
            dueDate = LocalDate.parse(it.dueDate),
            completedAt = it.completedAt?.let { c -> LocalDateTime.parse(c) },
            isException = it.isException,
            once = it.groupId in onceGroups,
            adjusted = false,
            icon = it.icon,
            color = it.color
        )
    }
    return (fromRules + fromStates).sortedWith(compareBy({ it.dueDate }, { it.title }))
}

fun earliestMonth(reminders: List<Reminder>, states: List<OccurrenceState>, current: YearMonth): YearMonth =
    (reminders.map { YearMonth.from(LocalDate.parse(it.startDate)) } +
        states.map { YearMonth.from(LocalDate.parse(it.period)) } +
        states.map { YearMonth.from(LocalDate.parse(it.dueDate)) } +
        current).min()

fun pendingDue(reminders: List<Reminder>, states: List<OccurrenceState>, today: LocalDate): List<Occ> {
    val current = YearMonth.from(today)
    val out = mutableListOf<Occ>()
    var m = earliestMonth(reminders, states, current)
    while (m <= current) {
        out += occurrencesFor(m, reminders, states).filter { it.completedAt == null && !it.dueDate.isAfter(today) }
        m = m.plusMonths(1)
    }
    return out
}

fun previewDates(first: LocalDate, type: Freq, count: Int?): List<Pair<LocalDate, Boolean>> {
    if (type == Freq.ONCE) return emptyList()
    val r = Reminder(
        groupId = "",
        title = "",
        note = null,
        type = type,
        startDate = first.toString(),
        anchorDay = first.dayOfMonth,
        occurrenceCount = null,
        icon = "bill",
        color = 0
    )
    val n = if (count == null) 12 else count.coerceIn(0, 120)
    return (0 until n).map { r.dateAt(it) to r.isAdjusted(it) }
}

data class EditData(val title: String, val note: String?, val date: LocalDate, val icon: String, val color: Int)

class Repo(private val dao: ReminderDao) {
    suspend fun create(title: String, note: String?, type: Freq, first: LocalDate, count: Int?, icon: String, color: Int) {
        dao.insert(
            Reminder(
                groupId = UUID.randomUUID().toString(),
                title = title,
                note = note,
                type = type,
                startDate = first.toString(),
                anchorDay = first.dayOfMonth,
                occurrenceCount = if (type == Freq.ONCE) 1 else count,
                icon = icon,
                color = color
            )
        )
    }

    private suspend fun stateOf(o: Occ): OccurrenceState =
        dao.statesNow().firstOrNull { it.groupId == o.groupId && it.period == o.period }
            ?: OccurrenceState(
                groupId = o.groupId,
                period = o.period,
                title = o.title,
                note = o.note,
                dueDate = o.dueDate.toString(),
                completedAt = null,
                deleted = false,
                isException = false,
                icon = o.icon,
                color = o.color
            )

    suspend fun complete(o: Occ, done: Boolean) {
        val s = stateOf(o)
        if (done) {
            dao.upsert(s.copy(completedAt = LocalDateTime.now().toString()))
        } else if (s.isException) {
            dao.upsert(s.copy(completedAt = null))
        } else if (s.id != 0L) {
            dao.deleteState(s)
        }
    }

    suspend fun deleteOne(o: Occ) = dao.upsert(stateOf(o).copy(deleted = true))

    suspend fun editOne(o: Occ, e: EditData) =
        dao.upsert(
            stateOf(o).copy(
                title = e.title,
                note = e.note,
                dueDate = e.date.toString(),
                isException = true,
                icon = e.icon,
                color = e.color
            )
        )

    suspend fun splitFrom(o: Occ, e: EditData?, newDate: LocalDate?, remove: Boolean) {
        val rows = dao.group(o.groupId)
        val p = LocalDate.parse(o.period)
        val r = rows.filter { !LocalDate.parse(it.startDate).isAfter(p) }
            .maxByOrNull { LocalDate.parse(it.startDate) } ?: return
        val ends = rows.map { x -> x.occurrenceCount?.let { x.dateAt(it - 1) } }
        val end: LocalDate? = if (ends.any { it == null }) null else ends.filterNotNull().maxOrNull()
        val k = r.indexOf(p)
        val rStart = LocalDate.parse(r.startDate)
        rows.filter { it.id != r.id && LocalDate.parse(it.startDate).isAfter(rStart) }.forEach { dao.delete(it) }
        if (k > 0) dao.update(r.copy(occurrenceCount = k)) else dao.delete(r)
        if (!remove) {
            val rest = end?.let { (r.indexOf(it) - k + 1).coerceAtLeast(1) }
            dao.insert(
                r.copy(
                    id = 0,
                    title = e?.title ?: r.title,
                    note = e?.note,
                    startDate = (newDate ?: p).toString(),
                    anchorDay = newDate?.dayOfMonth ?: r.anchorDay,
                    occurrenceCount = rest,
                    icon = e?.icon ?: r.icon,
                    color = e?.color ?: r.color
                )
            )
        }
        dao.clearFuture(o.groupId, o.period)
    }
}
