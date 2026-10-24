package tw.hoonsor.todo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tw.hoonsor.todo.BuildConfig
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.reminder.Reminders
import tw.hoonsor.todo.ui.theme.C
import tw.hoonsor.todo.ui.theme.TodoTheme

class MainActivity : ComponentActivity() {
    private val state: AppState by viewModels()
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handle(intent)
        setContent {
            TodoTheme {
                BackHandler(enabled = state.canGoBack) { state.back() }
                Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
                    when (val s = state.screen) {
                        Screen.Login -> LoginScreen(state)
                        Screen.Lists -> ListsScreen(state)
                        is Screen.Tasks -> TasksScreen(state, s.key, s.quickAdd)
                        is Screen.Detail -> DetailScreen(state, s.taskId)
                        Screen.Search -> SearchScreen(state)
                        Screen.Settings -> SettingsScreen(state)
                    }
                    UndoBar(state, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        state.refreshToday()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** 從通知、小工具進來：開待辦詳細或快速新增 */
    private fun handle(intent: Intent?) {
        intent ?: return
        // LAB 版測試入口：adb shell am start -n tw.hoonsor.todo.lab/tw.hoonsor.todo.ui.MainActivity --ez preview true
        if (BuildConfig.FLAVOR == "lab" && intent.getBooleanExtra("preview", false) && state.screen == Screen.Login) state.enterPreview()
        intent.getStringExtra(EXTRA_TASK)?.let { id ->
            Reminders.dismiss(this, id)
            state.openTask(id)
        }
        intent.getStringExtra(EXTRA_QUICK_ADD)?.let { key ->
            if (state.screen != Screen.Login) state.go(Screen.Tasks(ListKey.parse(key), quickAdd = true))
        }
    }

    companion object {
        const val EXTRA_TASK = Reminders.EXTRA_TASK
        const val EXTRA_QUICK_ADD = "quickAdd"
    }
}
