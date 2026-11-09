package tw.hoonsor.todo.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.data.ListEntity
import tw.hoonsor.todo.data.NewTask
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.domain.Views
import tw.hoonsor.todo.ui.DueDatePicker
import tw.hoonsor.todo.ui.Glyph
import tw.hoonsor.todo.ui.MainActivity
import tw.hoonsor.todo.ui.PillButton
import tw.hoonsor.todo.ui.ReminderPicker
import tw.hoonsor.todo.ui.dayLabel
import tw.hoonsor.todo.ui.listColor
import tw.hoonsor.todo.ui.listDotColor
import tw.hoonsor.todo.ui.localDateOf
import tw.hoonsor.todo.ui.syncText
import tw.hoonsor.todo.ui.theme.C
import tw.hoonsor.todo.ui.theme.TodoTheme
import tw.hoonsor.todo.ui.timeLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 小工具按鈕跳出的底部浮動面板（仿 Microsoft To Do）：疊在桌面上，不開整個 APP。
 * - [MODE_ADD]：「＋」快速新增，可選清單、到期日、提醒；Enter 或↑送出後清空，可連續新增
 * - [MODE_LISTS]：點「清單名稱⌄」切換這個小工具要顯示的清單
 * - [MODE_SETTINGS]：齒輪；顯示的清單、同步狀態、開啟 APP 完整設定
 * 點面板外的暗處就關閉。
 */
class WidgetSheetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(EXTRA_WIDGET, -1)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LISTS
        setContent {
            TodoTheme {
                val lists by Graph.repo.dao.listsFlow().collectAsState(initial = emptyList())
                var current by remember { mutableStateOf(ListKey.parse(Graph.settings.widgetList(widgetId))) }
                fun choose(k: ListKey) {
                    Graph.settings.setWidgetList(widgetId, k.wire)
                    current = k
                    TodoWidget.refresh(applicationContext)
                }
                Box(
                    Modifier.fillMaxSize().background(C.scrim)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(C.panel)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { } // 點面板本身不關閉
                            .navigationBarsPadding().imePadding().padding(top = 8.dp, bottom = 8.dp),
                    ) {
                        when (mode) {
                            MODE_ADD -> AddSheet(current, lists) { finish() }
                            MODE_SETTINGS -> SettingsSheet(current, lists, ::choose) { finish() }
                            else -> {
                                SheetTitle("小工具要顯示哪個清單？")
                                ListOptions(current, lists) { choose(it); finish() }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_WIDGET = "widgetId"
        const val EXTRA_MODE = "mode"
        const val MODE_ADD = "add"
        const val MODE_LISTS = "lists"
        const val MODE_SETTINGS = "settings"

        fun intent(context: Context, widgetId: Int, mode: String): Intent =
            Intent(context, WidgetSheetActivity::class.java).putExtra(EXTRA_WIDGET, widgetId).putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .setData(android.net.Uri.parse("todo://widget/$widgetId/$mode")) // 每個小工具、每種面板的 Intent 都不同
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

/** 清單選項：智慧清單＋自訂清單，色點用各清單顏色 */
@Composable
private fun ColumnScope.ListOptions(current: ListKey, lists: List<ListEntity>, onPick: (ListKey) -> Unit) {
    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        (ListKey.SMART + lists.map { ListKey.Custom(it.id) }).forEach { k ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(k) }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(listDotColor(k, lists)))
                Text(Views.title(k, lists), color = C.text, fontSize = 16.sp, fontWeight = if (k == current) FontWeight.Bold else null, modifier = Modifier.weight(1f))
                if (k == current) Icon(Glyph.Check, "目前", tint = listColor(k, lists), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 一個可點的選項膠囊：圖示＋文字；有值時亮起並可按 × 清除 */
@Composable
private fun OptionChip(icon: ImageVector, text: String, on: Boolean, accent: Color, onClear: (() -> Unit)?, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) C.selectBg else Color.Transparent)
            .border(1.dp, if (on) accent.copy(alpha = 0.6f) else C.lineHi, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(start = 12.dp, end = if (onClear != null) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (on) accent else C.textSoft, modifier = Modifier.size(18.dp))
        Text(text, color = if (on) accent else C.textSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onClear != null) Box(Modifier.size(32.dp).clickable(onClick = onClear).semantics { contentDescription = "清除$text" }, contentAlignment = Alignment.Center) {
            Icon(Glyph.Close, null, tint = C.muted, modifier = Modifier.size(14.dp))
        }
    }
}

/** 「＋」快速新增：標題、清單、到期日、提醒 */
@Composable
private fun AddSheet(widgetKey: ListKey, lists: List<ListEntity>, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var title by remember { mutableStateOf("") }
    // 小工具在自訂清單就加到那個清單；在智慧清單就加到預設清單（同 APP 內的規則）
    var listId by remember { mutableStateOf((widgetKey as? ListKey.Custom)?.id) }
    var due by remember { mutableStateOf(if (widgetKey == ListKey.Planned) today else null) }
    var remindAt by remember { mutableStateOf<Long?>(null) }
    var listMenu by remember { mutableStateOf(false) }
    var dueMenu by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    var pickRemind by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<String?>(null) }
    val list = lists.firstOrNull { it.id == listId } ?: lists.firstOrNull { it.isDefault } ?: lists.firstOrNull()
    val accent = if (list != null) listColor(ListKey.Custom(list.id), lists) else C.accent
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val submit = submit@{
        val t = title.trim()
        if (t.isEmpty()) return@submit
        title = ""
        scope.launch {
            val target = list?.id ?: Graph.repo.ensureDefaultList().id
            Graph.repo.addTask(
                NewTask(
                    listId = target, title = t, due = due, remindAt = remindAt,
                    myDay = widgetKey == ListKey.MyDay, important = widgetKey == ListKey.Important, source = "widget",
                ),
            )
            added = "已新增「$t」"
        }
        Unit
    }

    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, C.ring, CircleShape))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).heightIn(min = 52.dp), contentAlignment = Alignment.CenterStart) {
            if (title.isEmpty()) Text("新增工作", color = C.muted, fontSize = 17.sp)
            BasicTextField(
                value = title, onValueChange = { title = it.take(500) },
                textStyle = TextStyle(color = C.text, fontSize = 17.sp), cursorBrush = SolidColor(accent), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (title.isBlank()) close() else submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "新增工作" },
            )
        }
        val ready = title.isNotBlank()
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (ready) accent else C.surfaceHi)
                .clickable(enabled = ready) { submit() }.semantics { contentDescription = "新增" },
            contentAlignment = Alignment.Center,
        ) { Icon(Glyph.ArrowUp, null, tint = if (ready) C.onAccent else C.muted, modifier = Modifier.size(22.dp)) }
    }
    added?.let { Text(it, color = C.ok, fontSize = 12.sp, modifier = Modifier.padding(start = 52.dp, bottom = 2.dp)) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            OptionChip(Glyph.Lines, list?.name ?: "清單", true, accent, null) { listMenu = true }
            DropdownMenu(listMenu, onDismissRequest = { listMenu = false }, containerColor = C.panel) {
                lists.forEach { l ->
                    DropdownMenuItem(
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(C.listDot(l.color))) },
                        text = { Text(l.name, color = C.text) },
                        onClick = { listId = l.id; listMenu = false },
                    )
                }
            }
        }
        Box {
            OptionChip(
                Glyph.Calendar, due?.let { "${dayLabel(it, today)}到期" } ?: "設定到期日", due != null, accent,
                if (due != null) ({ due = null }) else null,
            ) { dueMenu = true }
            DropdownMenu(dueMenu, onDismissRequest = { dueMenu = false }, containerColor = C.panel) {
                listOf("今天" to today, "明天" to today.plusDays(1), "下週一" to today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))).forEach { (label, d) ->
                    DropdownMenuItem(text = { Text("$label（${d.monthValue}/${d.dayOfMonth}）", color = C.text) }, onClick = { due = d; dueMenu = false })
                }
                DropdownMenuItem(text = { Text("選擇日期…", color = C.accent) }, onClick = { dueMenu = false; pickDue = true })
            }
        }
        OptionChip(
            Glyph.Bell, remindAt?.let { r -> "${if (localDateOf(r) == today) "" else dayLabel(localDateOf(r), today, weekday = false) + " "}${timeLabel(r)}" } ?: "提醒我",
            remindAt != null, accent, if (remindAt != null) ({ remindAt = null }) else null,
        ) { pickRemind = true }
    }
    if (pickDue) DueDatePicker(due ?: today, onDismiss = { pickDue = false }) { due = it; pickDue = false }
    if (pickRemind) ReminderPicker(remindAt, today, onDismiss = { pickRemind = false }) { remindAt = it; pickRemind = false }
}

/** 齒輪：小工具設定（顯示的清單、同步）＋開啟 APP 完整設定 */
@Composable
private fun ColumnScope.SettingsSheet(current: ListKey, lists: List<ListEntity>, choose: (ListKey) -> Unit, close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val sync by Graph.sync.status.collectAsState()
    val pending by Graph.repo.dao.pendingCountFlow().collectAsState(initial = 0)
    var pickList by remember { mutableStateOf(false) }

    Text("小工具設定", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    SettingRow("顯示的清單", Views.title(current, lists), listDotColor(current, lists)) { pickList = !pickList }
    if (pickList) ListOptions(current, lists) { choose(it); pickList = false }
    val (label, color) = syncText(sync, pending)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("同步", color = C.text, fontSize = 16.sp)
            Text(label.ifBlank { "待命" }, color = color, fontSize = 13.sp)
        }
        if (Graph.api.signedIn) PillButton("立即同步", icon = Glyph.Refresh) {
            scope.launch { Graph.sync.sync(); TodoWidget.refresh(context.applicationContext) }
        }
    }
    SettingRow("待辦事項設定", "帳號、提醒權限、健康檢查、更新", null) {
        context.startActivity(
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        close()
    }
}

@Composable
private fun SettingRow(title: String, value: String, dot: Color?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Text(value, color = C.muted, fontSize = 13.sp)
            }
        }
        Icon(Glyph.ChevronRight, null, tint = C.muted, modifier = Modifier.size(20.dp))
    }
}
