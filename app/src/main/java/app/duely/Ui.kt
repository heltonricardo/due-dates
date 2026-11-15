@file:OptIn(ExperimentalMaterial3Api::class)

package app.duely

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Red = Color(0xFFFF6B6B)
private val shortFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val fullFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

data class Ask(val o: Occ, val edit: Triple<String, String?, LocalDate>?)

@Composable
fun DuelyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
fun Root(vm: VM) {
    val context = LocalContext.current
    val reminders by vm.reminders.collectAsState()
    val states by vm.states.collectAsState()
    val today = LocalDate.now()
    val cur = YearMonth.from(today)
    val earliest = remember(reminders, states) { earliestMonth(reminders, states, cur) }
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
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { Notifier.runTest(context) }) { Text("T") }
                FloatingActionButton(onClick = { editing = null; formOpen = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add reminder")
                }
            }
        }
    ) { pad ->
        key(earliest) {
            val curPage = ChronoUnit.MONTHS.between(earliest, cur).toInt()
            val total = curPage + 1200
            val pager = rememberPagerState(
                initialPage = ChronoUnit.MONTHS.between(earliest, shown).toInt().coerceIn(0, total - 1)
            ) { total }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { shown = earliest.plusMonths(it.toLong()) }
            }
            Column(Modifier.padding(pad).fillMaxSize()) {
                val oldest = previous.second
                if (previous.first > 0 && oldest != null) {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            scope.launch { pager.animateScrollToPage(ChronoUnit.MONTHS.between(earliest, oldest).toInt()) }
                        },
                        colors = CardDefaults.cardColors(containerColor = Red.copy(alpha = 0.18f))
                    ) {
                        Text("${previous.first} overdue in previous months", Modifier.padding(12.dp), color = Red)
                    }
                }
                Box(Modifier.weight(1f)) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                        MonthPage(
                            ym = earliest.plusMonths(page.toLong()),
                            cur = cur,
                            reminders = reminders,
                            states = states,
                            today = today,
                            onToday = { scope.launch { pager.animateScrollToPage(curPage) } },
                            onToggle = { o, done -> vm.go { complete(o, done) } },
                            onEdit = { editing = it; formOpen = true },
                            onDelete = { o -> if (o.once) vm.go { deleteOne(o) } else ask = Ask(o, null) }
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            enabled = pager.currentPage > 0
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                        IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                        }
                    }
                }
            }
        }
    }

    if (formOpen) {
        FormDialog(
            editing = editing,
            onDismiss = { formOpen = false },
            onCreate = { t, n, ty, d, c ->
                formOpen = false
                vm.go { create(t, n, ty, d, c) }
            },
            onEdit = { o, t, n, d ->
                formOpen = false
                if (o.once) vm.go { editOne(o, t, n, d) } else ask = Ask(o, Triple(t, n, d))
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
                        if (e == null) vm.go { deleteOne(a.o) } else vm.go { editOne(a.o, e.first, e.second, e.third) }
                    }) { Text("Only this") }
                    TextButton(onClick = {
                        ask = null
                        val e = a.edit
                        if (e == null) {
                            vm.go { splitFrom(a.o, a.o.title, a.o.note, null, true) }
                        } else {
                            val moved = if (e.third != a.o.dueDate) e.third else null
                            vm.go { splitFrom(a.o, e.first, e.second, moved, false) }
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
    reminders: List<Reminder>,
    states: List<OccurrenceState>,
    today: LocalDate,
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
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(ym.format(monthFmt), style = MaterialTheme.typography.titleLarge)
            if (overdue.isNotEmpty()) {
                Badge(Modifier.padding(start = 8.dp), containerColor = Red) { Text("${overdue.size}") }
            }
        }
        if (ym != cur) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Go to current month") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp)) {
            if (occ.isEmpty()) {
                item { Text("No reminders this month", color = muted) }
            }
            section("Overdue", overdue, Red, today, onToggle, onEdit, onDelete)
            section("Upcoming", upcoming, primary, today, onToggle, onEdit, onDelete)
            section("Done", done, muted, today, onToggle, onEdit, onDelete)
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
        Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp), color = color, style = MaterialTheme.typography.titleSmall)
    }
    items(list, key = { it.key }) { OccRow(it, today, onToggle, onEdit, onDelete) }
}

@Composable
fun OccRow(o: Occ, today: LocalDate, onToggle: (Occ, Boolean) -> Unit, onEdit: (Occ) -> Unit, onDelete: (Occ) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val done = o.completedAt != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = done, onCheckedChange = { onToggle(o, it) })
        Column(Modifier.weight(1f)) {
            Text(
                o.title,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (o.overdue(today)) Red else Color.Unspecified
            )
            val sub = buildString {
                append(o.dueDate.format(shortFmt))
                if (o.adjusted) append(" (adjusted)")
                if (!o.note.isNullOrBlank()) append(" · ${o.note}")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!done) DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(o) })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(o) })
            }
        }
    }
}

@Composable
fun FormDialog(
    editing: Occ?,
    onDismiss: () -> Unit,
    onCreate: (String, String?, Freq, LocalDate, Int?) -> Unit,
    onEdit: (Occ, String, String?, LocalDate) -> Unit
) {
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var freq by remember { mutableStateOf(Freq.ONCE) }
    var limited by remember { mutableStateOf(false) }
    var countText by remember { mutableStateOf("12") }
    var date by remember { mutableStateOf(editing?.dueDate ?: LocalDate.now()) }
    var picker by remember { mutableStateOf(false) }
    val count = if (limited && freq != Freq.ONCE) countText.toIntOrNull() else null
    val valid = title.isNotBlank() && (editing != null || !limited || freq == Freq.ONCE || (count ?: 0) >= 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "New reminder" else "Edit reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                if (editing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(freq == Freq.ONCE, { freq = Freq.ONCE }, label = { Text("Once") })
                        FilterChip(freq == Freq.WEEKLY, { freq = Freq.WEEKLY }, label = { Text("Weekly") })
                        FilterChip(freq == Freq.MONTHLY, { freq = Freq.MONTHLY }, label = { Text("Monthly") })
                        FilterChip(freq == Freq.YEARLY, { freq = Freq.YEARLY }, label = { Text("Yearly") })
                    }
                    if (freq != Freq.ONCE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!limited, { limited = false }, label = { Text("Forever") })
                            FilterChip(limited, { limited = true }, label = { Text("N times") })
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
                }
                OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (editing == null && freq != Freq.ONCE) "First due: ${date.format(fullFmt)}" else "Due: ${date.format(fullFmt)}")
                }
                if (editing == null) {
                    val preview = previewDates(date, freq, count)
                    if (preview.isNotEmpty()) {
                        Text("Dates", style = MaterialTheme.typography.titleSmall)
                        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            preview.forEach { (d, adjusted) ->
                                Text(
                                    if (adjusted) "${d.format(fullFmt)} (adjusted from day ${date.dayOfMonth})" else d.format(fullFmt),
                                    color = if (adjusted) Red else Color.Unspecified
                                )
                            }
                        }
                        if (!limited) {
                            Text("Continues indefinitely", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val n = note.trim().ifEmpty { null }
                    if (editing == null) onCreate(title.trim(), n, freq, date, count) else onEdit(editing, title.trim(), n, date)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

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
