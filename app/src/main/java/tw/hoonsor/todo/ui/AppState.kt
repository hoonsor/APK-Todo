package tw.hoonsor.todo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.data.ListEntity
import tw.hoonsor.todo.data.StepEntity
import tw.hoonsor.todo.data.TaskEntity
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.sync.SyncStatus
import java.time.LocalDate

sealed interface Screen {
    data object Login : Screen
    data object Lists : Screen
    data class Tasks(val key: ListKey, val quickAdd: Boolean = false) : Screen
    data class Detail(val taskId: String) : Screen
    data object Search : Screen
    data object Settings : Screen
}

/** 畫面底部的「復原」提示（約 10 秒） */
data class Undo(val text: String, val undo: suspend () -> Unit)

class AppState : ViewModel() {
    private val dao = Graph.repo.dao
    val repo get() = Graph.repo

    val lists: StateFlow<List<ListEntity>> = dao.listsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tasks: StateFlow<List<TaskEntity>> = dao.tasksFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val steps: StateFlow<Map<String, List<StepEntity>>> = dao.allStepsFlow()
        .map { all -> all.groupBy { it.taskId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val pending: StateFlow<Int> = dao.pendingCountFlow().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val sync: StateFlow<SyncStatus> get() = Graph.sync.status

    var today by mutableStateOf(LocalDate.now())
        private set

    private val stack = mutableStateListOf<Screen>(if (Graph.api.signedIn) Screen.Tasks(ListKey.MyDay) else Screen.Login)
    val screen: Screen get() = stack.last()
    val canGoBack get() = stack.size > 1

    var undo by mutableStateOf<Undo?>(null)
        private set
    private var undoJob: Job? = null

    init {
        // 開啟時預設顯示「我的一天」，按返回到清單總覽（同 To Do）
        if (Graph.api.signedIn) stack.add(0, Screen.Lists)
    }

    fun refreshToday() { today = LocalDate.now() }

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceAll(vararg s: Screen) { stack.clear(); stack.addAll(s) }

    fun openTask(id: String) {
        if (screen != Screen.Login) go(Screen.Detail(id))
    }

    fun showUndo(text: String, action: suspend () -> Unit) {
        undoJob?.cancel()
        undo = Undo(text, action)
        undoJob = viewModelScope.launch {
            delay(10_000)
            undo = null
        }
    }

    fun doUndo() {
        val u = undo ?: return
        undo = null
        undoJob?.cancel()
        viewModelScope.launch { u.undo() }
    }

    fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun syncNow() = launch { Graph.sync.sync() }

    /** LAB 版測試用：跳過登入，只操作本機資料（不會同步；正式登入時會先清掉） */
    fun enterPreview() {
        replaceAll(Screen.Lists, Screen.Tasks(ListKey.MyDay))
        launch {
            val work = repo.ensureDefaultList()
            if (repo.dao.tasks().isNotEmpty()) return@launch
            // 與設計稿相同的示範資料（只在 LAB 預覽出現）
            val life = repo.addList("日常生活", 1)
            val invest = repo.addList("投資理財", 2)
            val t = LocalDate.now()
            val at3pm = t.atTime(15, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            fun n(list: String, title: String, due: LocalDate? = null, important: Boolean = false, myDay: Boolean = false, remind: Long? = null, repeat: tw.hoonsor.todo.domain.Repeat? = null, note: String = "") =
                tw.hoonsor.todo.data.NewTask(list, title, due, remind, important, myDay, repeat, note)
            listOf(
                n(work.id, "教師敘薪資料送審", t.minusDays(2)),
                n(work.id, "寄出退休人員名冊給主計", t.minusDays(1)),
                n(work.id, "交 10 月人事月報", t, important = true, myDay = true, remind = at3pm, repeat = tw.hoonsor.todo.domain.Repeat(tw.hoonsor.todo.domain.Repeat.Freq.MONTHLY), note = "月報表要附上新進與離職人員異動，主計室說最晚 3 點前要收到。"),
                n(work.id, "差勤系統請假單審核", t, myDay = true),
                n(work.id, "核對 11 月薪資異動清冊", t.plusDays(1), important = true),
                n(work.id, "研習時數登錄（10 月）", t.plusDays(4), repeat = tw.hoonsor.todo.domain.Repeat(tw.hoonsor.todo.domain.Repeat.Freq.MONTHLY)),
                n(work.id, "考績委員會開會通知", t.plusDays(6), important = true),
                n(work.id, "準備新進人員報到資料袋", t.plusDays(8)),
                n(work.id, "整理兼職人員勞健保資料", t.plusDays(10)),
                n(work.id, "確認寒假值班表", t.plusDays(22)),
                n(work.id, "更新職務代理人名單"),
                n(work.id, "回覆教育部函釋查詢", note = "附上去年同類案件的處理方式"),
                n(work.id, "檢查公務人員保險異動"),
                n(life.id, "繳 10 月電費", t.minusDays(1), important = true),
                n(life.id, "買牛奶和雞蛋", myDay = true),
                n(life.id, "預約牙醫洗牙", t.plusDays(1)),
                n(life.id, "機車換機油", t.plusDays(9)),
                n(life.id, "備份手機照片", repeat = tw.hoonsor.todo.domain.Repeat(tw.hoonsor.todo.domain.Repeat.Freq.WEEKLY, 1, setOf(7))),
                n(invest.id, "看 ETF 季配息公告", t, myDay = true),
                n(invest.id, "整理年度股利收入"),
                n(invest.id, "檢查定期定額扣款", t.plusDays(3)),
            ).forEach { repo.addTask(it) }
            repo.dao.tasks().firstOrNull { it.title.startsWith("差勤") }?.let { task ->
                repo.addStep(task.id, "匯出差勤系統 10 月資料")
                repo.addStep(task.id, "核對新進、離職人數")
                repo.addStep(task.id, "主任簽章後送主計室")
            }
        }
    }

    fun onSignedIn() {
        replaceAll(Screen.Lists, Screen.Tasks(ListKey.MyDay))
        launch {
            // 剛登入時本機不該有正式資料；先清掉（例如 LAB 預覽留下的），再以伺服器為準
            repo.wipeLocal()
            Graph.settings.clearSync()
            Graph.sync.sync()
            Graph.realtime.start()
        }
    }

    fun signOut() = launch {
        Graph.signOut()
        replaceAll(Screen.Login)
    }
}
