package tw.hoonsor.todo.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.data.Settings
import tw.hoonsor.todo.data.TodoDao
import java.util.concurrent.TimeUnit

/** 同步狀態（健康檢查頁與清單總覽顯示） */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Ok(val at: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
    data object NotMember : SyncStatus
    data object SignedOut : SyncStatus
}

/**
 * 推送 outbox → 拉取三張表的變更。規則見 docs/DATA_MODEL.md「同步（Android APP）」。
 */
class SyncEngine(
    private val api: Supabase,
    private val dao: TodoDao,
    private val settings: Settings,
    private val afterPull: suspend (changed: Boolean) -> Unit,
) {
    private val lock = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    suspend fun sync(): SyncStatus = lock.withLock {
        if (!api.signedIn) return SyncStatus.SignedOut.also { _status.value = it }
        _status.value = SyncStatus.Running
        val result = try {
            push()
            val changed = pull()
            afterPull(changed)
            settings.lastSyncAt = System.currentTimeMillis()
            settings.lastSyncError = null
            SyncStatus.Ok(settings.lastSyncAt)
        } catch (e: ApiException) {
            Log.w(TAG, "sync failed", e)
            settings.lastSyncError = e.message
            when {
                e.notMember -> SyncStatus.NotMember
                !api.signedIn -> SyncStatus.SignedOut
                else -> SyncStatus.Failed(e.message ?: "同步失敗")
            }
        } catch (e: Exception) {
            Log.w(TAG, "sync failed", e)
            settings.lastSyncError = e.message
            SyncStatus.Failed("${e.javaClass.simpleName}：${e.message}")
        }
        _status.value = result
        result
    }

    private suspend fun push() {
        while (true) {
            val batch = dao.pending()
            if (batch.isEmpty()) return
            for (op in batch) {
                try {
                    val body = JSONObject(op.payload)
                    if (op.op == "insert") api.insert(op.tableName, body) else api.update(op.tableName, op.rowId, body)
                    dao.done(op.seq)
                } catch (e: ApiException) {
                    if (e.conflict && op.tableName == Mapping.TASKS && op.op == "insert" && JSONObject(op.payload).optString("repeat_root").isNotBlank()) {
                        // 重複待辦的下一筆已由另一端建立（唯一索引擋下）：丟掉本機這筆
                        dao.dropFor(op.rowId)
                        dao.hardDeleteSteps(op.rowId)
                        dao.hardDeleteTask(op.rowId)
                        continue
                    }
                    dao.failed(op.seq, e.message ?: "")
                    throw e
                }
            }
        }
    }

    /** 回傳是否有收到任何變更 */
    private suspend fun pull(): Boolean {
        var changed = false
        for (table in Mapping.TABLES) {
            var cursor = settings.cursor(table)
            while (true) {
                val q = buildString {
                    append("select=*&order=updated_at.asc&limit=$PAGE")
                    if (cursor != null) append("&updated_at=gte.").append(api.enc(cursor))
                }
                val rows: JSONArray = api.select(table, q)
                var newest = cursor
                for (i in 0 until rows.length()) {
                    val j = rows.getJSONObject(i)
                    val id = j.getString("id")
                    newest = Mapping.maxTime(newest, j.optString("updated_at"))
                    if (dao.pendingFor(id) > 0) continue // 本機還有沒送出的修改：等推送後再以伺服器版本為準
                    when (table) {
                        Mapping.LISTS -> Mapping.listFrom(j).let { e -> if (dao.list(id) != e) { dao.upsertList(e); changed = true } }
                        Mapping.TASKS -> Mapping.taskFrom(j).let { e -> if (dao.task(id) != e) { dao.upsertTask(e); changed = true } }
                        Mapping.STEPS -> Mapping.stepFrom(j).let { e -> if (dao.step(id) != e) { dao.upsertStep(e); changed = true } }
                    }
                }
                if (newest != null && newest != cursor) settings.setCursor(table, newest)
                // 用 gte 會重收游標那一刻的列（冪等）；一頁收不滿或游標沒前進就結束
                if (rows.length() < PAGE || newest == cursor) break
                cursor = newest
            }
        }
        return changed
    }

    companion object {
        private const val TAG = "SyncEngine"
        private const val PAGE = 500
    }
}

/** WorkManager：每 15 分鐘＋本機修改後立即同步（有網路才跑） */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = when (Graph.sync.sync()) {
        is SyncStatus.Failed -> if (runAttemptCount < 5) Result.retry() else Result.failure()
        else -> Result.success()
    }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(net).build(),
            )
        }

        fun syncSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(net)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }
    }
}

/**
 * Supabase Realtime：只在 APP 前景時連線，收到任何 todo_* 變更就安排一次拉取。
 * 連不上不影響功能（還有定時同步）。
 */
class RealtimeClient(private val api: Supabase, private val scope: CoroutineScope, private val onChange: () -> Unit) {
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var ref = 0

    fun start() {
        if (socket != null || !api.signedIn) return
        scope.launch {
            val token = runCatching { api.accessToken() }.getOrNull() ?: return@launch
            val wsUrl = api.baseUrl.replaceFirst("https://", "wss://") + "/realtime/v1/websocket?apikey=${api.enc(api.apiKey)}&vsn=1.0.0"
            socket = api.http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val changes = JSONArray()
                    Mapping.TABLES.forEach { t -> changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", t)) }
                    send("realtime:todo", "phx_join", JSONObject()
                        .put("config", JSONObject().put("postgres_changes", changes))
                        .put("access_token", token))
                    heartbeat = scope.launch {
                        while (true) {
                            delay(25_000)
                            send("phoenix", "heartbeat", JSONObject())
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val event = runCatching { JSONObject(text).optString("event") }.getOrNull()
                    if (event == "postgres_changes") onChange()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w("Realtime", "failed: ${t.message}")
                    stop()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = stop()
            })
        }
    }

    private fun send(topic: String, event: String, payload: JSONObject) {
        socket?.send(JSONObject().put("topic", topic).put("event", event).put("payload", payload).put("ref", (++ref).toString()).toString())
    }

    fun stop() {
        heartbeat?.cancel()
        heartbeat = null
        socket?.close(1000, null)
        socket = null
    }
}
