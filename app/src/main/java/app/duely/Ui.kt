@file:OptIn(ExperimentalMaterial3Api::class)

package app.duely

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
private val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.US)

data class Ask(val o: Occ, val edit: EditData?)

fun defaultCount(f: Freq): Int = when (f) {
    Freq.WEEKLY -> 4
    Freq.MONTHLY -> 12
    Freq.YEARLY -> 5
    Freq.ONCE -> 1
}

@Composable
fun DuelyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun Root(vm: VM) {
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
            ExtendedFloatingActionButton(
                onClick = { editing = null; formOpen = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New reminder") }
            )
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
                            reminders = reminders,
                            states = states,
                            today = today,
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
                        FilledTonalIconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                        }
                    }
                }
            }
        }
    }

    if (formOpen) {
        FormSheet(
            editing = editing,
            onDismiss = { formOpen = false },
            onCreate = { t, n, ty, d, c, ic, co ->
                formOpen = false
                vm.go { create(t, n, ty, d, c, ic, co) }
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
    val summary = listOfNotNull(
        if (overdue.isNotEmpty()) "${overdue.size} overdue" else null,
        if (upcoming.isNotEmpty()) "${upcoming.size} upcoming" else null,
        if (done.isNotEmpty()) "${done.size} done" else null
    ).joinToString(" · ").ifEmpty { "Nothing scheduled" }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 128.dp, top = 12.dp, bottom = 8.dp)) {
            Text("${ym.year}", style = MaterialTheme.typography.labelLarge, color = muted)
            Text(ym.format(monthFmt), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
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
fun FormSheet(
    editing: Occ?,
    onDismiss: () -> Unit,
    onCreate: (String, String?, Freq, LocalDate, Int?, String, Int) -> Unit,
    onEdit: (Occ, EditData) -> Unit
) {
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var freq by remember { mutableStateOf(Freq.ONCE) }
    var limited by remember { mutableStateOf(false) }
    var countText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(editing?.dueDate ?: LocalDate.now()) }
    var icon by remember { mutableStateOf(editing?.icon ?: "bill") }
    var color by remember { mutableStateOf(editing?.color ?: 0) }
    var picker by remember { mutableStateOf(false) }
    val count = if (limited && freq != Freq.ONCE) countText.toIntOrNull() else null
    val valid = title.isNotBlank() && (editing != null || !limited || freq == Freq.ONCE || (count ?: 0) >= 1)
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
            }
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (editing == null && freq != Freq.ONCE) "First due: ${date.format(fullFmt)}" else "Due: ${date.format(fullFmt)}")
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
                            Icon(img, contentDescription = k, tint = if (sel) accent else MaterialTheme.colorScheme.onSurfaceVariant)
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
                val preview = previewDates(date, freq, count)
                if (preview.isNotEmpty()) {
                    Text("Dates", style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = valid,
                    onClick = {
                        val n = note.trim().ifEmpty { null }
                        if (editing == null) {
                            onCreate(title.trim(), n, freq, date, count, icon, color)
                        } else {
                            onEdit(editing, EditData(title.trim(), n, date, icon, color))
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
