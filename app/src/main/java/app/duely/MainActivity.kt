package app.duely

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UndoEvent(val message: String, val reminders: List<Reminder>, val states: List<OccurrenceState>)

class VM(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val dao = db.dao()
    private val repo = Repo(dao)
    private val _settings = MutableStateFlow(Prefs.load(app))
    private val _events = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 8)
    val settings: StateFlow<AppSettings> = _settings
    val events: SharedFlow<UndoEvent> = _events
    val reminders = dao.reminders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val states = dao.states().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            combine(reminders, states) { _, _ -> Unit }.drop(1).collect { DueWidgetUpdater.update(app) }
        }
    }

    fun go(block: suspend Repo.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }

    fun goUndoable(message: String, block: suspend Repo.() -> Unit) {
        viewModelScope.launch {
            val r = dao.remindersNow()
            val s = dao.statesNow()
            repo.block()
            _events.emit(UndoEvent(message, r, s))
        }
    }

    private suspend fun replaceAll(r: List<Reminder>, s: List<OccurrenceState>) {
        db.withTransaction {
            dao.clearReminders()
            dao.clearStates()
            dao.insertReminders(r)
            dao.insertStates(s)
        }
    }

    fun restore(e: UndoEvent) {
        viewModelScope.launch { replaceAll(e.reminders, e.states) }
    }

    fun exportTo(uri: Uri, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = Backup.toJson(dao.remindersNow(), dao.statesNow())
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(json.toByteArray())
                    }
                }
            }.isSuccess
            done(ok)
        }
    }

    fun importFrom(uri: Uri, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                }
                val (r, s) = Backup.parse(text)
                replaceAll(r, s)
            }.isSuccess
            done(ok)
        }
    }

    fun setNotifyTime(hour: Int, minute: Int) {
        update(_settings.value.copy(hour = hour, minute = minute))
        Notifier.restart(getApplication<Application>())
    }

    fun setTheme(index: Int) = update(_settings.value.copy(theme = index))

    private fun update(s: AppSettings) {
        Prefs.save(getApplication<Application>(), s)
        _settings.value = s
    }
}

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Notifier.restart(this)
        setContent {
            val vm: VM = viewModel()
            val settings by vm.settings.collectAsState()
            DuelyTheme(settings.theme) {
                Root(vm)
            }
        }
    }
}
