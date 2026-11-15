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
    val adjusted: Boolean
) {
    val key get() = "$groupId|$period"
    fun overdue(today: LocalDate) = completedAt == null && dueDate.isBefore(today)
}

fun Reminder.covers(ym: YearMonth): Boolean {
    val d = ChronoUnit.MONTHS.between(YearMonth.parse(startMonth), ym)
    return d >= 0 && (occurrenceCount == null || d < occurrenceCount)
}

fun occurrencesFor(ym: YearMonth, reminders: List<Reminder>, states: List<OccurrenceState>): List<Occ> {
    val ymKey = ym.toString()
    val saved = states.filter { it.period == ymKey }.map { it.groupId }.toSet()
    val onceGroups = reminders.filter { it.type == ReminderType.ONCE }.map { it.groupId }.toSet()
    val fromRules = reminders.filter { it.covers(ym) && it.groupId !in saved }.map {
        Occ(
            groupId = it.groupId,
            period = ymKey,
            title = it.title,
            note = it.note,
            dueDate = ym.atDay(minOf(it.dayOfMonth, ym.lengthOfMonth())),
            completedAt = null,
            isException = false,
            once = it.type == ReminderType.ONCE,
            adjusted = it.dayOfMonth > ym.lengthOfMonth()
        )
    }
    val fromStates = states.filter { !it.deleted && it.dueDate.startsWith(ymKey) }.map {
        Occ(
            groupId = it.groupId,
            period = it.period,
            title = it.title,
            note = it.note,
            dueDate = LocalDate.parse(it.dueDate),
            completedAt = it.completedAt?.let { c -> LocalDateTime.parse(c) },
            isException = it.isException,
            once = it.groupId in onceGroups,
            adjusted = false
        )
    }
    return (fromRules + fromStates).sortedWith(compareBy({ it.dueDate }, { it.title }))
}

fun pendingDue(reminders: List<Reminder>, states: List<OccurrenceState>, today: LocalDate): List<Occ> {
    val current = YearMonth.from(today)
    val first = (reminders.map { YearMonth.parse(it.startMonth) } + states.map { YearMonth.parse(it.period) } + current).min()
    val out = mutableListOf<Occ>()
    var m = first
    while (m <= current) {
        out += occurrencesFor(m, reminders, states).filter { it.completedAt == null && !it.dueDate.isAfter(today) }
        m = m.plusMonths(1)
    }
    return out
}

fun previewDates(first: LocalDate, type: ReminderType, count: Int?): List<Pair<LocalDate, Boolean>> {
    val n = when (type) {
        ReminderType.ONCE -> 0
        ReminderType.MONTHLY_FOREVER -> 12
        ReminderType.MONTHLY_COUNT -> (count ?: 0).coerceIn(0, 120)
    }
    val start = YearMonth.from(first)
    return (0 until n).map {
        val ym = start.plusMonths(it.toLong())
        ym.atDay(minOf(first.dayOfMonth, ym.lengthOfMonth())) to (first.dayOfMonth > ym.lengthOfMonth())
    }
}

class Repo(private val dao: ReminderDao) {
    suspend fun create(title: String, note: String?, type: ReminderType, first: LocalDate, count: Int?) {
        dao.insert(
            Reminder(
                groupId = UUID.randomUUID().toString(),
                title = title,
                note = note,
                type = type,
                startMonth = YearMonth.from(first).toString(),
                dayOfMonth = first.dayOfMonth,
                occurrenceCount = when (type) {
                    ReminderType.ONCE -> 1
                    ReminderType.MONTHLY_COUNT -> count
                    ReminderType.MONTHLY_FOREVER -> null
                }
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
                isException = false
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

    suspend fun editOne(o: Occ, title: String, note: String?, date: LocalDate) =
        dao.upsert(stateOf(o).copy(title = title, note = note, dueDate = date.toString(), isException = true))

    suspend fun splitFrom(o: Occ, title: String, note: String?, day: Int?, remove: Boolean) {
        val rows = dao.group(o.groupId)
        val p = YearMonth.parse(o.period)
        val r = rows.filter { YearMonth.parse(it.startMonth) <= p }.maxByOrNull { YearMonth.parse(it.startMonth) } ?: return
        val ends = rows.map { x -> x.occurrenceCount?.let { YearMonth.parse(x.startMonth).plusMonths(it - 1L) } }
        val end: YearMonth? = if (ends.any { it == null }) null else ends.filterNotNull().maxOrNull()
        val rs = YearMonth.parse(r.startMonth)
        val k = ChronoUnit.MONTHS.between(rs, p).toInt()
        rows.filter { it.id != r.id && YearMonth.parse(it.startMonth) > rs }.forEach { dao.delete(it) }
        if (k > 0) dao.update(r.copy(type = ReminderType.MONTHLY_COUNT, occurrenceCount = k)) else dao.delete(r)
        if (!remove) {
            val rest = end?.let { ChronoUnit.MONTHS.between(p, it).toInt() + 1 }
            dao.insert(
                r.copy(
                    id = 0,
                    title = title,
                    note = note,
                    dayOfMonth = day ?: r.dayOfMonth,
                    startMonth = o.period,
                    occurrenceCount = rest,
                    type = if (rest == null) ReminderType.MONTHLY_FOREVER else ReminderType.MONTHLY_COUNT
                )
            )
        }
        dao.clearFuture(o.groupId, o.period)
    }
}
