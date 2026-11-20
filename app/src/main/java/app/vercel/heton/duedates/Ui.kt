@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.vercel.heton.duedates

import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
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
import java.text.Normalizer
import java.time.temporal.ChronoUnit

private val Red = Color(0xFFE5484D)

data class Ask(val o: Occ, val edit: EditData?)

data class MonthStat(val ym: YearMonth, val overdue: Int, val upcoming: Int, val done: Int)

fun defaultCount(f: Freq): Int = when (f) {
    Freq.DAILY -> 7
    Freq.WEEKLY -> 4
    Freq.MONTHLY -> 12
    Freq.YEARLY -> 5
    Freq.ONCE -> 1
}

private fun unitLabel(res: Resources, f: Freq, n: Int): String {
    val id = when (f) {
        Freq.DAILY -> R.plurals.unit_day
        Freq.WEEKLY -> R.plurals.unit_week
        Freq.MONTHLY -> R.plurals.unit_month
        Freq.YEARLY -> R.plurals.unit_year
        Freq.ONCE -> return ""
    }
    return res.getQuantityString(id, n)
}

@Composable
fun DueDatesTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun Root(vm: VM) {
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
    var redating by remember { mutableStateOf<Occ?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showOverview by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sUndo = stringResource(R.string.undo)
    val sMarkedDone = stringResource(R.string.marked_done)
    val sMarkedPending = stringResource(R.string.marked_pending)
    val sDeleted = stringResource(R.string.reminder_deleted)
    val sDeletedMany = stringResource(R.string.reminders_deleted)
    val sSaved = stringResource(R.string.changes_saved)
    val sCompletion = stringResource(R.string.completion_changed)
    val sBackupSaved = stringResource(R.string.backup_saved)
    val sExportFailed = stringResource(R.string.export_failed)
    val sBackupRestored = stringResource(R.string.backup_restored)
    val sInvalidBackup = stringResource(R.string.invalid_backup)
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                val r = snackbar.showSnackbar(e.message, actionLabel = sUndo, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.restore(e)
            }
        }
    }
    val toggle: (Occ, Boolean) -> Unit = { o, done ->
        vm.goUndoable(if (done) sMarkedDone else sMarkedPending) { complete(o, done) }
    }
    val delete: (Occ) -> Unit = { o ->
        if (o.once) vm.goUndoable(sDeleted) { deleteOne(o) } else ask = Ask(o, null)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; formOpen = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_reminder)) }
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
            var goTo by remember { mutableStateOf<YearMonth?>(null) }
            LaunchedEffect(goTo) {
                goTo?.let { m ->
                    pager.scrollToPage(ChronoUnit.MONTHS.between(earliest, m).toInt().coerceIn(0, total - 1))
                    goTo = null
                }
            }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }
            BackHandler(enabled = searching) {
                searching = false
                query = ""
            }
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (searching) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.search_reminders)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.weight(1f).focusRequester(focusRequester)
                        )
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row {
                            IconButton(onClick = { searching = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        }
                    }
                }
                if (searching) {
                    Box(Modifier.weight(1f)) {
                        SearchResults(
                            query = query,
                            earliest = earliest,
                            last = last,
                            reminders = reminders,
                            states = states,
                            today = today,
                            onToggle = toggle,
                            onEdit = { editing = it; formOpen = true },
                            onDelete = delete,
                            onChangeDone = { redating = it },
                            onGo = { o ->
                                goTo = YearMonth.from(o.dueDate)
                                searching = false
                                query = ""
                            }
                        )
                    }
                } else {
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
                            stringResource(R.string.overdue_previous, previous.first),
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
                            onToggle = toggle,
                            onEdit = { editing = it; formOpen = true },
                            onDelete = delete,
                            onChangeDone = { redating = it }
                        )
                    }
                    Row(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 16.dp)) {
                        FilledTonalIconButton(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            enabled = pager.currentPage > 0
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.previous_month)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                            enabled = pager.currentPage < total - 1
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.next_month)
                            )
                        }
                    }
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
            onDark = { vm.setDark(it) },
            onExport = { uri ->
                showSettings = false
                vm.exportTo(uri) { ok -> scope.launch { snackbar.showSnackbar(if (ok) sBackupSaved else sExportFailed) } }
            },
            onImport = { uri ->
                showSettings = false
                vm.importFrom(uri) { ok -> scope.launch { snackbar.showSnackbar(if (ok) sBackupRestored else sInvalidBackup) } }
            },
            onDismiss = { showSettings = false }
        )
    }

    if (formOpen) {
        val rule = editing?.let { e -> if (e.once) null else ruleDataFor(e, reminders) }
        FormSheet(
            editing = editing,
            rule = rule,
            onDismiss = { formOpen = false },
            onCreate = { n ->
                formOpen = false
                vm.go { create(n) }
            },
            onEdit = { o, e ->
                formOpen = false
                if (o.once) vm.goUndoable(sSaved) { editOne(o, e) } else ask = Ask(o, e)
            }
        )
    }

    redating?.let { o ->
        CompletionDialog(
            o = o,
            onDismiss = { redating = null },
            onConfirm = { d ->
                redating = null
                vm.goUndoable(sCompletion) { setCompletion(o, d) }
            }
        )
    }

    ask?.let { a ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(stringResource(if (a.edit == null) R.string.delete_which else R.string.edit_which)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.o.title)
                    if (a.edit?.rule != null) {
                        Text(
                            stringResource(R.string.repeat_only_following),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        ask = null
                        val e = a.edit
                        if (e == null) vm.goUndoable(sDeleted) { deleteOne(a.o) } else vm.goUndoable(sSaved) { editOne(a.o, e) }
                    }) { Text(stringResource(R.string.only_this)) }
                    TextButton(onClick = {
                        ask = null
                        val e = a.edit
                        if (e == null) {
                            vm.goUndoable(sDeletedMany) { splitFrom(a.o, null, null, true) }
                        } else {
                            val moved = if (e.date != a.o.dueDate) e.date else null
                            vm.goUndoable(sSaved) { splitFrom(a.o, e, moved, false) }
                        }
                    }) { Text(stringResource(R.string.this_and_following)) }
                }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun CompletionDialog(o: Occ, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val initial = o.completedAt?.toLocalDate() ?: LocalDate.now()
    val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(
            st,
            title = { Text(stringResource(R.string.completion_date), Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) }
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
    onDelete: (Occ) -> Unit,
    onChangeDone: (Occ) -> Unit
) {
    val res = LocalContext.current.resources
    val occ = remember(ym, reminders, states) { occurrencesFor(ym, reminders, states) }
    val overdue = occ.filter { it.overdue(today) }
    val dueToday = occ.filter { it.completedAt == null && it.dueDate == today }
    val upcoming = occ.filter { it.completedAt == null && it.dueDate.isAfter(today) }
    val done = occ.filter { it.completedAt != null }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val nothing = stringResource(R.string.nothing_scheduled)
    val summary = listOfNotNull(
        if (overdue.isNotEmpty()) res.plural(R.plurals.count_overdue, overdue.size) else null,
        if (dueToday.isNotEmpty()) res.plural(R.plurals.count_due_today, dueToday.size) else null,
        if (upcoming.isNotEmpty()) res.plural(R.plurals.count_upcoming, upcoming.size) else null,
        if (done.isNotEmpty()) res.plural(R.plurals.count_done, done.size) else null
    ).joinToString(" · ").ifEmpty { nothing }
    val sFirst = stringResource(R.string.footer_first)
    val sOpenEnded = stringResource(R.string.footer_open_ended)
    val sLast = stringResource(R.string.footer_last)
    val footer = listOfNotNull(
        if (isFirst) sFirst else null,
        if (isLast) (if (openEnded) sOpenEnded else sLast) else null
    ).joinToString(" ")
    val sOverdue = stringResource(R.string.section_overdue)
    val sDueToday = stringResource(R.string.section_due_today)
    val sUpcoming = stringResource(R.string.section_upcoming)
    val sDone = stringResource(R.string.section_done)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 128.dp, top = 12.dp, bottom = 8.dp)) {
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenOverview)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${ym.year}", style = MaterialTheme.typography.labelLarge, color = muted)
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.open_overview),
                        tint = muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(ym.format(dateFmt("MMMM")).cap(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
            }
            Text(
                summary,
                style = MaterialTheme.typography.titleSmall,
                color = if (overdue.isNotEmpty()) Red else muted
            )
            if (ym != cur) {
                TextButton(onClick = onToday, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.back_to, cur.format(dateFmt("MMMM"))))
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            section(sOverdue, overdue, Red, today, onToggle, onEdit, onDelete, onChangeDone)
            section(sDueToday, dueToday, tertiary, today, onToggle, onEdit, onDelete, onChangeDone)
            section(sUpcoming, upcoming, primary, today, onToggle, onEdit, onDelete, onChangeDone)
            section(sDone, done, muted, today, onToggle, onEdit, onDelete, onChangeDone)
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
    onDelete: (Occ) -> Unit,
    onChangeDone: (Occ) -> Unit
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
    items(list, key = { it.key }) { OccCard(it, today, onToggle, onEdit, onDelete, onChangeDone) }
}

@Composable
fun OccCard(
    o: Occ,
    today: LocalDate,
    onToggle: (Occ, Boolean) -> Unit,
    onEdit: (Occ) -> Unit,
    onDelete: (Occ) -> Unit,
    onChangeDone: (Occ) -> Unit,
    onGo: ((Occ) -> Unit)? = null
) {
    val res = LocalContext.current.resources
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
    val short = dateFmt("MMMd")
    val sub = run {
        val parts = mutableListOf<String>()
        if (done) {
            parts += res.getString(
                R.string.due_done,
                o.dueDate.format(short),
                o.completedAt?.toLocalDate()?.format(short) ?: ""
            )
        } else {
            parts += o.dueDate.format(short)
            if (late) parts += res.plural(R.plurals.days_overdue, ChronoUnit.DAYS.between(o.dueDate, today).toInt())
            if (o.adjusted) parts += res.getString(R.string.adjusted)
        }
        if (!o.note.isNullOrBlank()) parts += o.note
        parts.joinToString(" · ")
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
            if (!done) DropdownMenuItem(text = { Text(stringResource(R.string.menu_edit)) }, onClick = { menu = false; onEdit(o) })
            if (done) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_change_completion)) },
                    onClick = { menu = false; onChangeDone(o) }
                )
            }
            if (onGo != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_show_in_month)) },
                    onClick = { menu = false; onGo(o) }
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_delete)) }, onClick = { menu = false; onDelete(o) })
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
        Text(
            stringResource(R.string.overview),
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineSmall
        )
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
    val res = LocalContext.current.resources
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
            Text(stat.ym.format(dateFmt("MMMM")).cap(), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stat.overdue + stat.upcoming + stat.done == 0) {
                    Text("—", color = muted)
                }
                if (stat.overdue > 0) Pill(res.plural(R.plurals.count_overdue, stat.overdue), Red)
                if (stat.upcoming > 0) Pill(res.plural(R.plurals.count_upcoming, stat.upcoming), MaterialTheme.colorScheme.primary)
                if (stat.done > 0) Pill(res.plural(R.plurals.count_done, stat.done), muted)
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
    onDark: (Boolean) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var timeDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.notification_time), style = MaterialTheme.typography.labelLarge)
            Card(
                onClick = { timeDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        LocalTime.of(settings.hour, settings.minute).format(dateFmt("jm")),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        stringResource(R.string.notifications_approx),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !settings.dark,
                    onClick = { onDark(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {}
                ) { Text(stringResource(R.string.theme_light)) }
                SegmentedButton(
                    selected = settings.dark,
                    onClick = { onDark(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {}
                ) { Text(stringResource(R.string.theme_dark)) }
            }
            Text(stringResource(R.string.backup), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("backup-${LocalDate.now()}.json") },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.export)) }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.import_)) }
            }
            Text(stringResource(R.string.import_note), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.replace_title)) },
            text = { Text(stringResource(R.string.replace_text)) },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; onImport(uri) }) { Text(stringResource(R.string.replace)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (timeDialog) {
        val st = rememberTimePickerState(
            initialHour = settings.hour,
            initialMinute = settings.minute,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            confirmButton = {
                TextButton(onClick = { onTime(st.hour, st.minute); timeDialog = false }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = st) }
        )
    }
}

@Composable
private fun DatePreview(dates: List<Pair<LocalDate, Boolean>>, first: LocalDate, openEnded: Boolean) {
    val res = LocalContext.current.resources
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = dates.take(12)
    val hidden = dates.drop(12)
    val hiddenAdjusted = hidden.count { it.second }
    val notes = listOfNotNull(
        if (shown.any { it.second }) res.getString(R.string.adjusted_note) else null,
        if (hiddenAdjusted > 0) res.plural(R.plurals.more_adjusted, hiddenAdjusted) else null,
        if (openEnded) res.getString(R.string.continues) else null
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.upcoming_dates), style = MaterialTheme.typography.labelLarge, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { (d, adjusted) ->
                DateChip(d.format(dateFmt(if (d.year == first.year) "MMMd" else "yMMMd")), adjusted, false)
            }
            if (hidden.isNotEmpty()) DateChip(stringResource(R.string.more_dates, hidden.size), false, true)
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
    rule: RuleData?,
    onDismiss: () -> Unit,
    onCreate: (NewReminder) -> Unit,
    onEdit: (Occ, EditData) -> Unit
) {
    val res = LocalContext.current.resources
    val recurring = editing == null || rule != null
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var freq by remember { mutableStateOf(rule?.type ?: Freq.ONCE) }
    var everyText by remember { mutableStateOf((rule?.every ?: 1).toString()) }
    var weekdays by remember { mutableStateOf(rule?.weekdays ?: 0) }
    var limited by remember { mutableStateOf(rule?.count != null) }
    var countText by remember { mutableStateOf(rule?.count?.toString() ?: "") }
    var businessMode by remember { mutableStateOf((rule?.businessDay ?: 0) != 0) }
    var businessText by remember { mutableStateOf(rule?.businessDay?.takeIf { it > 0 }?.toString() ?: "5") }
    var lastBusiness by remember { mutableStateOf(rule?.businessDay == -1) }
    var weekends by remember { mutableStateOf(editing?.notifyWeekends ?: true) }
    var date by remember { mutableStateOf(editing?.dueDate ?: LocalDate.now()) }
    var icon by remember { mutableStateOf(editing?.icon ?: "bill") }
    var color by remember { mutableStateOf(editing?.color ?: 0) }
    var picker by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rec = recurring && freq != Freq.ONCE
    val every = (everyText.toIntOrNull() ?: 0).coerceIn(0, 999)
    val count = if (limited && rec) countText.toIntOrNull() else null
    val business = if (freq == Freq.MONTHLY && businessMode) {
        if (lastBusiness) -1 else (businessText.toIntOrNull() ?: 0).coerceIn(0, 23)
    } else {
        0
    }
    val valid = title.isNotBlank() &&
        (!rec || (
            every >= 1 &&
                (!limited || (count ?: 0) >= 1) &&
                (freq != Freq.WEEKLY || weekdays != 0) &&
                (freq != Freq.MONTHLY || !businessMode || business != 0)
            ))
    val accent = colorFor(color)
    val frequencies = (if (editing == null) listOf(Freq.ONCE to stringResource(R.string.freq_once)) else emptyList()) + listOf(
        Freq.DAILY to stringResource(R.string.freq_daily),
        Freq.WEEKLY to stringResource(R.string.freq_weekly),
        Freq.MONTHLY to stringResource(R.string.freq_monthly),
        Freq.YEARLY to stringResource(R.string.freq_yearly)
    )
    val initials = stringArrayResource(R.array.weekday_initials)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    val sheetScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { sheetScope.launch { sheetState.show() } },
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(if (editing == null) R.string.new_reminder else R.string.edit_reminder),
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                title,
                { title = it },
                label = { Text(stringResource(R.string.title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                note,
                { note = it },
                label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth()
            )
            if (recurring) {
                if (editing != null) {
                    Text(
                        stringResource(R.string.repeat_only_following),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    frequencies.forEach { (f, label) ->
                        FilterChip(
                            selected = freq == f,
                            onClick = {
                                if (f != freq) {
                                    freq = f
                                    countText = defaultCount(f).toString()
                                    if (f != Freq.MONTHLY) businessMode = false
                                    if (f == Freq.WEEKLY && weekdays == 0) weekdays = weekdayBit(date)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                if (freq != Freq.ONCE) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.every), style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(
                            everyText,
                            { everyText = it.filter(Char::isDigit).take(3) },
                            singleLine = true,
                            isError = every < 1,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp)
                        )
                        Text(unitLabel(res, freq, every), style = MaterialTheme.typography.bodyLarge)
                    }
                    if (freq == Freq.WEEKLY) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            initials.forEachIndexed { i, label ->
                                val on = (weekdays and (1 shl i)) != 0
                                Box(
                                    Modifier.size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { weekdays = weekdays xor (1 shl i) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        if (weekdays == 0) {
                            Text(stringResource(R.string.pick_day), style = MaterialTheme.typography.bodySmall, color = Red)
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !limited,
                            onClick = { limited = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {}
                        ) { Text(stringResource(R.string.forever), maxLines = 1) }
                        SegmentedButton(
                            selected = limited,
                            onClick = {
                                limited = true
                                if (countText.isBlank()) countText = defaultCount(freq).toString()
                            },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {}
                        ) { Text(stringResource(R.string.n_times), maxLines = 1) }
                    }
                    if (limited) {
                        OutlinedTextField(
                            countText,
                            { countText = it.filter(Char::isDigit).take(4) },
                            label = {
                                Text(stringResource(if (editing == null) R.string.repetitions else R.string.repetitions_from))
                            },
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
                        ) { Text(stringResource(R.string.day_of_month), maxLines = 1) }
                        SegmentedButton(
                            selected = businessMode,
                            onClick = { businessMode = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {}
                        ) { Text(stringResource(R.string.business_day), maxLines = 1) }
                    }
                    if (businessMode) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.last_business_day), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = lastBusiness, onCheckedChange = { lastBusiness = it })
                        }
                    }
                    if (businessMode && !lastBusiness) {
                        OutlinedTextField(
                            businessText,
                            { v ->
                                val digits = v.filter(Char::isDigit).take(2)
                                businessText = if ((digits.toIntOrNull() ?: 0) > 23) "23" else digits
                            },
                            label = { Text(stringResource(R.string.business_day_number)) },
                            supportingText = { Text(stringResource(R.string.business_day_support)) },
                            singleLine = true,
                            isError = business == 0,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (editing == null && freq == Freq.MONTHLY && businessMode) {
                        stringResource(R.string.starting_month, date.format(dateFmt("yMMM")))
                    } else if (editing == null && freq != Freq.ONCE) {
                        stringResource(R.string.first_due, date.format(dateFmt("yMMMd")))
                    } else {
                        stringResource(R.string.due_on, date.format(dateFmt("yMMMd")))
                    }
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.notify_weekends), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.notify_weekends_hint), style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Switch(checked = weekends, onCheckedChange = { weekends = it })
            }
            Text(stringResource(R.string.icon), style = MaterialTheme.typography.labelLarge)
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
            Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
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
            if (rec) {
                val preview = previewDates(date, freq, count, business, every, weekdays)
                if (preview.isNotEmpty()) DatePreview(preview, date, !limited)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = valid,
                    onClick = {
                        val n = note.trim().ifEmpty { null }
                        val mask = if (freq == Freq.WEEKLY) weekdays else 0
                        if (editing == null) {
                            onCreate(NewReminder(title.trim(), n, freq, date, count, icon, color, business, weekends, every, mask))
                        } else {
                            val r = if (rec) RuleData(freq, every, mask, count, business) else null
                            onEdit(editing, EditData(title.trim(), n, date, icon, color, weekends, r))
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (picker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        val nd = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (freq == Freq.WEEKLY && weekdays == weekdayBit(date)) weekdays = weekdayBit(nd)
                        date = nd
                    }
                    picker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(st) }
    }
}

private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

@Composable
fun SearchResults(
    query: String,
    earliest: YearMonth,
    last: YearMonth,
    reminders: List<Reminder>,
    states: List<OccurrenceState>,
    today: LocalDate,
    onToggle: (Occ, Boolean) -> Unit,
    onEdit: (Occ) -> Unit,
    onDelete: (Occ) -> Unit,
    onChangeDone: (Occ) -> Unit,
    onGo: (Occ) -> Unit
) {
    val res = LocalContext.current.resources
    val q = normalize(query.trim())
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val results = remember(q, reminders, states, earliest, last) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            val out = mutableListOf<Occ>()
            var m = earliest
            while (m <= last) {
                out += occurrencesFor(m, reminders, states).filter {
                    normalize(it.title).contains(q) || normalize(it.note ?: "").contains(q)
                }
                m = m.plusMonths(1)
            }
            out
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (q.isEmpty()) {
            item { Text(stringResource(R.string.type_to_search), color = muted) }
        } else if (results.isEmpty()) {
            item { Text(stringResource(R.string.no_results), color = muted) }
        } else {
            item {
                Text(
                    res.plural(R.plurals.results_count, results.size),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = muted
                )
            }
            items(results, key = { it.key }) { OccCard(it, today, onToggle, onEdit, onDelete, onChangeDone, onGo) }
        }
    }
}
