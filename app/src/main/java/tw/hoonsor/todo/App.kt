package tw.hoonsor.todo

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.hoonsor.todo.data.KeyVault
import tw.hoonsor.todo.data.Repository
import tw.hoonsor.todo.data.Settings
import tw.hoonsor.todo.data.TodoDb
import tw.hoonsor.todo.reminder.Reminders
import tw.hoonsor.todo.sync.RealtimeClient
import tw.hoonsor.todo.sync.Supabase
import tw.hoonsor.todo.sync.SyncEngine
import tw.hoonsor.todo.sync.SyncStatus
import tw.hoonsor.todo.sync.SyncWorker
import tw.hoonsor.todo.widget.TodoWidget

/** 全 APP 共用的單例 */
object Graph {
    lateinit var app: Context
        private set
    lateinit var settings: Settings
        private set
    lateinit var api: Supabase
        private set
    lateinit var db: TodoDb
        private set
    lateinit var repo: Repository
        private set
    lateinit var sync: SyncEngine
        private set
    lateinit var realtime: RealtimeClient
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pullJob: Job? = null

    fun init(context: Context) {
        app = context.applicationContext
        settings = Settings(app)
        api = Supabase(KeyVault(app))
        db = TodoDb.create(app)
        repo = Repository(db) { ids ->
            // 本機修改後：排同步、更新小工具、重排受影響的提醒
            SyncWorker.syncSoon(app)
            TodoWidget.refresh(app)
            Reminders.rescheduleAll(app)
        }
        sync = SyncEngine(api, db.dao(), settings) { changed ->
            if (api.signedIn) runCatching { repo.ensureDefaultList() }
            if (changed) {
                TodoWidget.refresh(app)
                Reminders.rescheduleAll(app)
            }
        }
        realtime = RealtimeClient(api, scope) { pullSoon() }
    }

    /** Realtime 通知很密集時合併成一次拉取 */
    fun pullSoon() {
        pullJob?.cancel()
        pullJob = scope.launch {
            delay(800)
            sync.sync()
        }
    }

    suspend fun signOut() {
        realtime.stop()
        api.signOut()
        repo.wipeLocal()
        settings.clearSync()
        TodoWidget.refresh(app)
        Reminders.rescheduleAll(app)
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Reminders.createChannel(this)
        SyncWorker.schedulePeriodic(this)
        // 前景時連 Realtime 並同步一次；背景時斷線（省電，背景交給 WorkManager）
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Graph.scope.launch {
                    if (Graph.sync.sync() !is SyncStatus.SignedOut) Graph.realtime.start()
                }
            }

            override fun onStop(owner: LifecycleOwner) = Graph.realtime.stop()
        })
    }
}
