package app.duely

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VM(app: Application) : AndroidViewModel(app) {
    private val dao = Db.get(app).dao()
    private val repo = Repo(dao)
    val reminders = dao.reminders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val states = dao.states().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun go(block: suspend Repo.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }
}

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Notifier.restart(this)
        setContent {
            DuelyTheme {
                Root(viewModel())
            }
        }
    }
}
