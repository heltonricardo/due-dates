package app.duely

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Color
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VM(app: Application) : AndroidViewModel(app) {
    private val dao = Db.get(app).dao()
    private val repo = Repo(dao)
    private val _settings = MutableStateFlow(Prefs.load(app))
    val settings: StateFlow<AppSettings> = _settings
    val reminders = dao.reminders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val states = dao.states().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun go(block: suspend Repo.() -> Unit) {
        viewModelScope.launch { repo.block() }
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
