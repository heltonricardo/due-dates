@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.duely

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Red = Color(0xFFFF6B6B)
private val shortFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val fullFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.US)
private val monthYearFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val themeNames = listOf("Blue", "Green", "Orange", "Pink", "Purple", "Teal")

data class Ask(val o: Occ, val edit: EditData?)

data class MonthStat(val ym: YearMonth, val overdue: Int, val upcoming: Int, val done: Int)

fun defaultCount(f: Freq): Int = when (f) {
    Freq.WEEKLY -> 4
    Freq.MONTHLY -> 12
    Freq.YEARLY -> 5
    Freq.ONCE -> 1
}

@Composable
fun DuelyTheme(theme: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (theme < 0 && Build.VERSION.SDK_INT >= 31) {
        dynamicDarkColorScheme(context)
    } else if (theme < 0) {
        schemeFor(ReminderColors[0])
    } else {
        schemeFor(ReminderColors[theme.coerceIn(0, ReminderColors.size - 1)])
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun Root(vm: VM) {
    val context = LocalContext.current
    val reminders by vm.reminders.collectAsState()
    val states by vm.states.collectAsState()
    val settings by vm.settings.collectAsState()
    val today = LocalDate.now()
    val cur = YearMonth.from(today)
    val earliest = remember(reminders, states) { earliestMonth(reminders, states, cur) }
    val last = remember(reminders, states) { lastMonth(reminders, states, cur) }
    val openEnded = reminders.any { it.occurrenceCount == null }
    val previous = remember(reminders, states, earliest, today) {
        var count = 0
        var oldest: YearMonth? = null
        var m = earliest
        while (m < cur) {
            val c = occurrencesFor(m, reminders, states).count { it.overdue(today) }
            if (c > 0) {
                count += c
                if (oldest == null) oldest = m
            }
            m = m.plusMonths(1)
        }
        count to oldest
    }
    var shown by remember { mutableStateOf(cur) }
    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Occ?>(null) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showOverview by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; formOpen = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New reminder") }
            )
        }
    ) { pad ->
        key(earliest, last) {
            val curPage = ChronoUnit.MONTHS.between(earliest, cur).toInt()
            val total = ChronoUnit.MONTHS.between(earliest, last).toInt() + 1
            val pager = rememberPagerState(
                initialPage = ChronoUnit.MONTHS.between(earliest, shown).toInt().coerceIn(0, total - 1)
            ) { total }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { shown = earliest.plusMonths(it.toLong()) }
            }
            Column(Modifier.padding(pad).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Duely",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
                val oldest = previous.second
                if (previous.first > 0 && oldest != null) {
                    Card(
                        onClick = {
                            scope.launch { pager.animateScrollToPage(ChronoUnit.MONTHS.between(earliest, oldest).toInt()) }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Red.copy(alpha = 0.16f))
                    ) {
                        Text(
                            "${previous.first} overdue in previous months",
                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            color = Red,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                        MonthPage(
                            ym = earliest.plusMonths(page.toLong()),
                            cur = cur,
                            isFirst = page == 0,
                            isLast = page == total - 1,
                            openEnded = openEnded,
                            reminders = reminders,
                            states = states,
                            today = today,
                            onOpenOverview = { showOverview = true },
                            onToday = { scope.launch { pager.animateScrollToPage(curPage) } },
                            onToggle = { o, done -> vm.go { complete(o, done) } },
                            onEdit = { editing = it; formOpen = true },
                            onDelete = { o -> if (o.once) vm.go { deleteOne(o) } else ask = Ask(o, null) }
                        )
                    }
                    Row(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 16.dp)) {
                        FilledTonalIconButton(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            enabled = pager.currentPage > 0
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                            enabled = pager.currentPage < total - 1
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
                    }
                }
            }
            if (showOverview) {
                OverviewSheet(
                    earliest = earliest,
                    last = last,
                    cur = cur,
                    reminders = reminders,
                    states = states,
                    today = today,
                    onPick = { ym ->
                        showOverview = false
                        scope.launch { pager.scrollToPage(ChronoUnit.MONTHS.between(earliest, ym).toInt()) }
                    },
                    onDismiss = { showOverview = false }
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onTime = { h, m -> vm.setNotifyTime(h, m) },
            onTheme = { vm.setTheme(it) },
            onTest = { Notifier.runTest(context) },
            onDismiss = { showSettings = false }
        )
    }

    if (formOpen) {
        FormSheet(
            editing = editing,
            onDismiss = { formOpen = false },
            onCreate = { n ->
                formOpen = false
                vm.go { create(n) }
            },
            onEdit = { o, e ->
                formOpen = false
                if (o.once) vm.go { editOne(o, e) } else ask = Ask(o, e)
            }
        )
    }

    ask?.let { a ->
        val verb = if (a.edit == null) "Delete" else "Edit"
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text("$verb which reminders?") },
            text = { Text(a.o.title) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        ask = null
                        val e = a.edit
                        if (e == null) vm.go { deleteOne(a.o) } else vm.go { editOne(a.o, e) }
                    }) { Text("Only this") }
                    TextButton(onClick = {
                        ask = null
                        val e = a.edit
                        if (e == null) {
                            vm.go { splitFrom(a.o, null, null, true) }
                        } else {
                            val moved = if (e.date != a.o.dueDate) e.date else null
                            vm.go { splitFrom(a.o, e, moved, false) }
                        }
                    }) { Text("This and following") }
                }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun MonthPage(
    ym: YearMonth,
    cur: YearMonth,
    isFirst: Boolean,
    isLast: Boolean,
    openEnded: Boolean,
    reminders: List<Reminder>,
    states: List<OccurrenceState>,
    today: LocalDate,
    onOpenOverview: () -> Unit,
    onToday: () -> Unit,
    onToggle: (Occ, Boolean) -> Unit,
    onEdit: (Occ) -> Unit,
    onDelete: (Occ) -> Unit
) {
    val occ = remember(ym, reminders, states) { occurrencesFor(ym, reminders, states) }
    val overdue = occ.filter { it.overdue(today) }
    val upcoming = occ.filter { it.completedAt == null && !it.overdue(today) }
    val done = occ.filter { it.completedAt != null }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val summary = listOfNotNull(
        if (overdue.isNotEmpty()) "${overdue.size} overdue" else null,
        if (upcoming.isNotEmpty()) "${upcoming.size} upcoming" else null,
        if (done.isNotEmpty()) "${done.size} done" else null
    ).joinToString(" · ").ifEmpty { "Nothing scheduled" }
    val footer = listOfNotNull(
        if (isFirst) "Nothing before this month." else null,
        if (isLast) (if (openEnded) "Recurring reminders show up to 12 months ahead." else "Nothing scheduled after this month.") else null
    ).joinToString(" ")
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 128.dp, top = 12.dp, bottom = 8.dp)) {
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenOverview)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${ym.year}", style = MaterialTheme.typography.labelLarge, color = muted)
                    Icon(Icons.Default.ExpandMore, contentDescription = "Open overview", tint = muted, modifier = Modifier.size(18.dp))
                }
                Text(ym.format(monthFmt), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
            }
            Text(
                summary,
                style = MaterialTheme.typography.titleSmall,
                color = if (overdue.isNotEmpty()) Red else muted
            )
            if (ym != cur) {
                TextButton(onClick = onToday, contentPadding = PaddingValues(0.dp)) { Text("Back to ${cur.format(monthFmt)}") }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            section("Overdue", overdue, Red, today, onToggle, onEdit, onDelete)
            section("Upcoming", upcoming, primary, today, onToggle, onEdit, onDelete)
            section("Done", done, muted, today, onToggle, onEdit, onDelete)
            if (footer.isNotEmpty()) {
                item(key = "footer") {
                    Text(
                        footer,
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<Occ>,
    color: Color,
    today: LocalDate,
    onToggle: (Occ, Boolean) -> Unit,
    onEdit: (Occ) -> Unit,
    onDelete: (Occ) -> Unit
) {
    if (list.isEmpty()) return
    item(key = "h$title") {
        Text(
            title,
            Modifier.padding(start = 8.dp, top = 12.dp),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
    items(list, key = { it.key }) { OccCard(it, today, onToggle, onEdit, onDelete) }
}

@Composable
fun OccCard(o: Occ, today: LocalDate, onToggle: (Occ, Boolean) -> Unit, onEdit: (Occ) -> Unit, onDelete: (Occ) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val done = o.completedAt != null
    val late = o.overdue(today)
    val accent = colorFor(o.color)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val container = when {
        late -> Red.copy(alpha = 0.14f)
        done -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val sub = buildString {
        if (done) {
            append("Due ${o.dueDate.format(shortFmt)} · Done ${o.completedAt?.toLocalDate()?.format(shortFmt)}")
        } else {
            append(o.dueDate.format(shortFmt))
            if (late) {
                val days = ChronoUnit.DAYS.between(o.dueDate, today)
                append(" · $days ${if (days == 1L) "day" else "days"} overdue")
            }
            if (o.adjusted) append(" · adjusted")
        }
        if (!o.note.isNullOrBlank()) append(" · ${o.note}")
    }
    Box {
        Card(
            onClick = { menu = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = container)
        ) {
            Row(
                Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = if (done) 0.1f else 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconFor(o.icon), contentDescription = null, tint = if (done) accent.copy(alpha = 0.5f) else accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        o.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (late) Red else if (done) muted else Color.Unspecified
                    )
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Checkbox(checked = done, onCheckedChange = { onToggle(o, it) })
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!done) DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(o) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(o) })
        }
    }
}

@Composable
fun OverviewSheet(
    earliest: YearMonth,
    last: YearMonth,
    cur: YearMonth,
    reminders: List<Reminder>,
    states: List<OccurrenceState>,
    today: LocalDate,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    val rows = remember(earliest, last, reminders, states, today) {
        val stats = generateSequence(earliest) { it.plusMonths(1) }.takeWhile { it <= last }.map { m ->
            val occ = occurrencesFor(m, reminders, states)
            MonthStat(
                m,
                occ.count { it.overdue(today) },
                occ.count { it.completedAt == null && !it.overdue(today) },
                occ.count { it.completedAt != null }
            )
        }.toList()
        val out = mutableListOf<Any>()
        var year = -1
        stats.forEach {
            if (it.ym.year != year) {
                year = it.ym.year
                out += year
            }
            out += it
        }
        out
    }
    val startIndex = rows.indexOfFirst { it is MonthStat && it.ym == cur }.let { (it - 2).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Overview", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { if (it is MonthStat) it.ym.toString() else "y$it" }) { row ->
                if (row is MonthStat) {
                    MonthRow(row, row.ym == cur) { onPick(row.ym) }
                } else {
                    Text(
                        "$row",
                        Modifier.padding(start = 8.dp, top = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthRow(stat: MonthStat, current: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stat.ym.format(monthFmt), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stat.overdue + stat.upcoming + stat.done == 0) {
                    Text("—", color = muted)
                }
                if (stat.overdue > 0) Pill("${stat.overdue} overdue", Red)
                if (stat.upcoming > 0) Pill("${stat.upcoming} upcoming", MaterialTheme.colorScheme.primary)
                if (stat.done > 0) Pill("${stat.done} done", muted)
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun SettingsSheet(
    settings: AppSettings,
    onTime: (Int, Int) -> Unit,
    onTheme: (Int) -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit
) {
    var timeDialog by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val effective = if (settings.theme < 0 && Build.VERSION.SDK_INT < 31) 0 else settings.theme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Text("Notification time", style = MaterialTheme.typography.labelLarge)
            Card(
                onClick = { timeDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(LocalTime.of(settings.hour, settings.minute).format(timeFmt), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Notifications are approximate. Android may delay them by a few minutes or even hours to save battery, so one can arrive after this time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }
            Text("Theme color", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (Build.VERSION.SDK_INT >= 31) {
                    Box(
                        Modifier.size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .then(if (effective < 0) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { onTheme(-1) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Palette, contentDescription = "Wallpaper colors", modifier = Modifier.size(20.dp)) }
                }
                ReminderColors.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(38.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(if (effective == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { onTheme(i) }
                    )
                }
            }
            Text(
                if (effective < 0) "Wallpaper colors" else themeNames[effective.coerceIn(0, themeNames.size - 1)],
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
            OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text("Send test notification (in 10 s)") }
        }
    }
    if (timeDialog) {
        val st = rememberTimePickerState(initialHour = settings.hour, initialMinute = settings.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            confirmButton = { TextButton(onClick = { onTime(st.hour, st.minute); timeDialog = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Cancel") } },
            text = { TimePicker(state = st) }
        )
    }
}

@Composable
private fun DatePreview(dates: List<Pair<LocalDate, Boolean>>, first: LocalDate, openEnded: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = dates.take(12)
    val hidden = dates.drop(12)
    val hiddenAdjusted = hidden.count { it.second }
    val notes = listOfNotNull(
        if (shown.any { it.second }) "Red dates were moved to the last valid day." else null,
        if (hiddenAdjusted > 0) "$hiddenAdjusted more adjusted dates further ahead." else null,
        if (openEnded) "Continues indefinitely." else null
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Upcoming dates", style = MaterialTheme.typography.labelLarge, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { (d, adjusted) ->
                DateChip(d.format(if (d.year == first.year) shortFmt else fullFmt), adjusted, false)
            }
            if (hidden.isNotEmpty()) DateChip("+${hidden.size} more", false, true)
        }
        notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
    }
}

@Composable
private fun DateChip(text: String, adjusted: Boolean, subtle: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (adjusted) Red.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (adjusted) Red else if (subtle) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
        )
    }
}

@Composable
fun FormSheet(
    editing: Occ?,
    onDismiss: () -> Unit,
    onCreate: (NewReminder) -> Unit,
    onEdit: (Occ, EditData) -> Unit
) {
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var freq by remember { mutableStateOf(Freq.ONCE) }
    var limited by remember { mutableStateOf(false) }
    var countText by remember { mutableStateOf("") }
    var businessMode by remember { mutableStateOf(false) }
    var businessText by remember { mutableStateOf("5") }
    var weekends by remember { mutableStateOf(editing?.notifyWeekends ?: true) }
    var date by remember { mutableStateOf(editing?.dueDate ?: LocalDate.now()) }
    var icon by remember { mutableStateOf(editing?.icon ?: "bill") }
    var color by remember { mutableStateOf(editing?.color ?: 0) }
    var picker by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val count = if (limited && freq != Freq.ONCE) countText.toIntOrNull() else null
    val business = if (freq == Freq.MONTHLY && businessMode) (businessText.toIntOrNull() ?: 0).coerceIn(0, 23) else 0
    val valid = title.isNotBlank() &&
        (editing != null || !limited || freq == Freq.ONCE || (count ?: 0) >= 1) &&
        (editing != null || !(freq == Freq.MONTHLY && businessMode) || business >= 1)
    val accent = colorFor(color)
    val frequencies = listOf(Freq.ONCE to "Once", Freq.WEEKLY to "Weekly", Freq.MONTHLY to "Monthly", Freq.YEARLY to "Yearly")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (editing == null) "New reminder" else "Edit reminder",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            if (editing == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    frequencies.forEachIndexed { i, (f, label) ->
                        SegmentedButton(
                            selected = freq == f,
                            onClick = {
                                freq = f
                                countText = defaultCount(f).toString()
                                if (f != Freq.MONTHLY) businessMode = false
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, frequencies.size),
                            icon = {}
                        ) { Text(label, maxLines = 1) }
                    }
                }
                if (freq != Freq.ONCE) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !limited,
                            onClick = { limited = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {}
                        ) { Text("Forever", maxLines = 1) }
                        SegmentedButton(
                            selected = limited,
                            onClick = { limited = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {}
                        ) { Text("N times", maxLines = 1) }
                    }
                    if (limited) {
                        OutlinedTextField(
                            countText,
                            { countText = it.filter(Char::isDigit) },
                            label = { Text("Repetitions") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (freq == Freq.MONTHLY) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !businessMode,
                            onClick = { businessMode = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {}
                        ) { Text("Day of month", maxLines = 1) }
                        SegmentedButton(
                            selected = businessMode,
                            onClick = { businessMode = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {}
                        ) { Text("Business day", maxLines = 1) }
                    }
                    if (businessMode) {
                        OutlinedTextField(
                            businessText,
                            { businessText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Business day number (1–23)") },
                            supportingText = { Text("Counts Monday to Friday. Holidays are not considered.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (editing == null && freq == Freq.MONTHLY && businessMode) "Starting month: ${date.format(monthYearFmt)}"
                    else if (editing == null && freq != Freq.ONCE) "First due: ${date.format(fullFmt)}"
                    else "Due: ${date.format(fullFmt)}"
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notify on weekends", style = MaterialTheme.typography.bodyLarge)
                    Text("Off: skips Saturday and Sunday and notifies on Monday.", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Switch(checked = weekends, onCheckedChange = { weekends = it })
            }
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            ReminderIcons.chunked(6).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    rowItems.forEach { (k, img) ->
                        val sel = k == icon
                        Box(
                            Modifier.size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (sel) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .then(if (sel) Modifier.border(2.dp, accent, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { icon = k },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(img, contentDescription = k, tint = if (sel) accent else muted)
                        }
                    }
                }
            }
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ReminderColors.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(38.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(if (i == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = i }
                    )
                }
            }
            if (editing == null) {
                val preview = previewDates(date, freq, count, business)
                if (preview.isNotEmpty()) DatePreview(preview, date, !limited)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = valid,
                    onClick = {
                        val n = note.trim().ifEmpty { null }
                        if (editing == null) {
                            onCreate(NewReminder(title.trim(), n, freq, date, count, icon, color, business, weekends))
                        } else {
                            onEdit(editing, EditData(title.trim(), n, date, icon, color, weekends))
                        }
                    }
                ) { Text("Save") }
            }
        }
    }

    if (picker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } }
        ) { DatePicker(st) }
    }
}
