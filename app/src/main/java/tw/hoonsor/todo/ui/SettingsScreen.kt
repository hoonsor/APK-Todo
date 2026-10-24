package tw.hoonsor.todo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as SysSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.hoonsor.agent.HoonsorAgent
import tw.hoonsor.todo.BuildConfig
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.agent.TodoAgentCatalog
import tw.hoonsor.todo.domain.Views
import tw.hoonsor.todo.reminder.Reminders
import tw.hoonsor.todo.sync.SyncStatus
import tw.hoonsor.todo.ui.theme.C
import tw.hoonsor.todo.update.UpdateCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun SearchScreen(state: AppState) {
    val all by state.tasks.collectAsState()
    val lists by state.lists.collectAsState()
    val steps by state.steps.collectAsState()
    var q by remember { mutableStateOf("") }
    val results = Views.search(q, all, steps.mapValues { (_, v) -> v.map { it.title } })
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(Glyph.Back, "返回") { state.back() }
            TextInput(q, { q = it }, "搜尋標題、步驟、備註", modifier = Modifier.weight(1f))
        }
        if (q.isNotBlank() && results.isEmpty()) Text("找不到「$q」", color = C.muted, modifier = Modifier.padding(24.dp))
        LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.id }) { t ->
                TaskCard(
                    t, taskMeta(t, state.today, steps[t.id].orEmpty(), lists.firstOrNull { it.id == t.listId }?.name, true),
                    C.listTitle(lists.firstOrNull { it.id == t.listId }?.color ?: 0), false, false,
                    onToggleDone = { state.launch { state.repo.setCompleted(t.id, !t.done) } },
                    onToggleStar = { state.launch { state.repo.setImportant(t.id, !t.important) } },
                    onClick = { state.go(Screen.Detail(t.id)) }, onLongClick = {},
                )
            }
        }
    }
}

/** 設定與健康檢查：帳號、同步、提醒權限、ColorOS、Agent API 測試台、App 內更新 */
@Composable
fun SettingsScreen(state: AppState) {
    val ctx = LocalContext.current
    val sync by state.sync.collectAsState()
    val pending by state.pending.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notifyOk = remember(tick) { ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val exactOk = remember(tick) { Reminders.canExact(ctx) }
    val batteryOk = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    var testMsg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Glyph.Back, "返回") { state.back() }
            Text("設定與健康檢查", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val (syncLabel, syncColor) = syncText(sync, pending)
            Item("帳號", Graph.api.session?.email ?: "尚未登入", if (Graph.api.signedIn) true else false) {
                PillButton("登出", color = C.overdue) { state.signOut() }
            }
            val last = Graph.settings.lastSyncAt.takeIf { it > 0 }?.let { SimpleDateFormat("M/d HH:mm:ss", Locale.TAIWAN).format(Date(it)) } ?: "尚未同步"
            Item(
                "同步", "${syncLabel.ifBlank { "待命" }} · 上次 $last" + (Graph.settings.lastSyncError?.let { "\n錯誤：$it" } ?: "") +
                    if (sync == SyncStatus.NotMember) "\n帳號尚未開通：請把這個帳號加進 Supabase 的 todo_members。使用者 ID：${Graph.api.session?.userId}" else "",
                when (sync) { is SyncStatus.Ok -> pending == 0; SyncStatus.Running, SyncStatus.Idle -> null; else -> false },
            ) { PillButton("立即同步", icon = Glyph.Refresh) { state.syncNow() } }
            Item("通知權限", if (notifyOk) "已允許" else "沒有通知權限，提醒不會出現", notifyOk) {
                if (!notifyOk) PillButton("允許", color = C.accent) { askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            Item("精準提醒", if (exactOk) "提醒會準時響" else "系統沒有允許精準鬧鐘，提醒可能晚幾分鐘", exactOk) {
                if (!exactOk) PillButton("去設定", color = C.accent) {
                    ctx.startActivity(Intent(SysSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            Item("電池最佳化", if (batteryOk) "已設為不最佳化" else "建議設為「不最佳化」，背景同步與提醒才不會被延後", batteryOk) {
                if (!batteryOk) PillButton("去設定", color = C.accent) {
                    runCatching {
                        ctx.startActivity(Intent(SysSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            Item(
                "ColorOS 自啟動與關聯啟動",
                "請到 手機管家 › 權限隱私 › 自啟動管理，打開待辦事項的「允許自啟動」與「允許關聯啟動」。語音總控呼叫待辦、開機後重排提醒都需要。",
                null,
            ) {
                PillButton("應用程式資訊") {
                    ctx.startActivity(Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            Item("桌面小工具", "把待辦清單放到桌面；標題的「清單名稱▾」可以切換要顯示的清單，每個小工具各自記住。", null) {
                PillButton("加入桌面", icon = Glyph.Plus, color = C.accent) {
                    val awm = ctx.getSystemService(android.appwidget.AppWidgetManager::class.java)
                    if (awm.isRequestPinAppWidgetSupported) {
                        awm.requestPinAppWidget(android.content.ComponentName(ctx, tw.hoonsor.todo.widget.TodoWidgetReceiver::class.java), null, null)
                    } else {
                        testMsg = "這個桌面不支援從 APP 加入，請長按桌面 › 小工具 › 待辦事項"
                    }
                }
            }
            Item("測試提醒", testMsg.ifBlank { "按下後 5 秒會跳出一則測試通知" }, null) {
                PillButton("測試", icon = Glyph.Bell) {
                    state.launch {
                        testMsg = "5 秒後會跳出通知…"
                        delay(5_000)
                        Reminders.show(ctx, "test-" + UUID.randomUUID(), "測試提醒", "看到這則通知，表示提醒正常")
                        testMsg = if (notifyOk) "已送出測試通知" else "沒有通知權限，看不到通知"
                    }
                }
            }
            AgentBench()
            UpdateCard()
            Text("版本 v${BuildConfig.VERSION_NAME}", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Item(title: String, desc: String, ok: Boolean?, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(when (ok) { true -> C.ok; false -> C.overdue; null -> C.star }))
            Text(title, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(desc, color = C.muted, fontSize = 13.sp, lineHeight = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/** Agent API 測試台：直接呼叫自己的 AgentProvider（跟語音總控走同一條路） */
@Composable
private fun AgentBench() {
    val ctx = LocalContext.current
    val names = TodoAgentCatalog.actions.map { it.getString("name") }
    var action by remember { mutableStateOf("todo_list") }
    var params by remember { mutableStateOf("{\"scope\":\"my_day\"}") }
    var dryRun by remember { mutableStateOf(true) }
    var out by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Agent API 測試台", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("語音總控呼叫的就是這些動作。", color = C.muted, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { n ->
                PillButton(n, color = if (n == action) C.accent else C.text, border = if (n == action) C.selectLine else C.lineHi) {
                    action = n
                    params = when (n) {
                        "todo_list" -> "{\"scope\":\"my_day\"}"
                        "todo_add" -> "{\"title\":\"測試待辦\",\"due\":\"${java.time.LocalDate.now()}\"}"
                        "todo_complete" -> "{\"title\":\"測試待辦\"}"
                        "todo_delete" -> "{\"id\":\"\"}"
                        else -> "{}"
                    }
                }
            }
        }
        TextInput(params, { params = it }, "params（JSON）", singleLine = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(if (dryRun) "試跑：開" else "試跑：關", color = if (dryRun) C.accent else C.muted) { dryRun = !dryRun }
            PillButton("執行", color = C.onAccent, bg = C.accent, border = C.accent) {
                out = "執行中…"
                val p = runCatching { JSONObject(params.ifBlank { "{}" }) }.getOrElse { out = "params 不是合法 JSON"; return@PillButton }
                val req = JSONObject().put("api", HoonsorAgent.API).put("requestId", "bench").put("action", action).put("params", p).put("dryRun", dryRun)
                Graph.scope.launch(Dispatchers.Main) {
                    out = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.contentResolver.call(Uri.parse("content://${ctx.packageName}.agent"), "execute", null, Bundle().apply { putString(HoonsorAgent.EXTRA_JSON, req.toString()) })
                                ?.getString(HoonsorAgent.EXTRA_JSON)?.let { JSONObject(it).toString(2) } ?: "沒有回應"
                        }.getOrElse { "錯誤：${it.message}" }
                    }
                }
            }
        }
        if (out.isNotBlank()) SelectionContainer {
            Text(out, color = C.textSoft, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
        }
    }
}
