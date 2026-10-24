package tw.hoonsor.todo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.data.ListEntity
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.domain.Views
import tw.hoonsor.todo.sync.ApiException
import tw.hoonsor.todo.sync.SyncStatus
import tw.hoonsor.todo.ui.theme.C

fun smartIcon(k: ListKey): Pair<ImageVector, Color> = when (k) {
    ListKey.MyDay -> Glyph.Sun to C.myDay
    ListKey.Important -> Glyph.Star to C.important
    ListKey.Planned -> Glyph.Calendar to C.planned
    else -> Glyph.Lines to C.accent
}

fun listColor(key: ListKey, lists: List<ListEntity>): Color = when (key) {
    is ListKey.Custom -> C.listTitle(lists.firstOrNull { it.id == key.id }?.color ?: 0)
    else -> smartIcon(key).second
}

fun syncText(s: SyncStatus, pending: Int): Pair<String, Color> = when (s) {
    SyncStatus.Running -> "同步中…" to C.muted
    is SyncStatus.Ok -> (if (pending > 0) "$pending 筆等待同步" else "已同步") to (if (pending > 0) C.muted else C.ok)
    is SyncStatus.Failed -> "離線中，$pending 筆等待同步" to C.star
    SyncStatus.NotMember -> "帳號尚未開通" to C.overdue
    SyncStatus.SignedOut -> "尚未登入" to C.overdue
    SyncStatus.Idle -> (if (pending > 0) "$pending 筆等待同步" else "") to C.muted
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListsScreen(state: AppState) {
    val lists by state.lists.collectAsState()
    val tasks by state.tasks.collectAsState()
    val sync by state.sync.collectAsState()
    val pending by state.pending.collectAsState()
    var editing by remember { mutableStateOf<ListEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val email = Graph.api.session?.email.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(C.listTint(0)).border(1.dp, C.selectLine, CircleShape), contentAlignment = Alignment.Center) {
                Text(email.take(1).uppercase().ifBlank { "倬" }, color = C.text, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("待辦事項", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val (t, c) = syncText(sync, pending)
                if (t.isNotEmpty()) Text(t, color = c, fontSize = 12.sp, modifier = Modifier.clickable { state.syncNow() })
            }
            RoundIconButton(Glyph.Search, "搜尋") { state.go(Screen.Search) }
            RoundIconButton(Glyph.Gear, "設定與健康檢查") { state.go(Screen.Settings) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(ListKey.SMART, key = { it.wire }) { k ->
                val (icon, color) = smartIcon(k)
                ListRow(
                    lead = { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) },
                    name = Views.title(k, lists), count = Views.count(k, tasks, state.today),
                    onClick = { state.go(Screen.Tasks(k)) }, onLongClick = {},
                )
            }
            item { Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(C.line)) }
            items(lists, key = { it.id }) { l ->
                ListRow(
                    lead = { Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(C.listTint(l.color)).border(1.dp, C.listDot(l.color), RoundedCornerShape(7.dp))) },
                    name = l.name, count = Views.count(ListKey.Custom(l.id), tasks, state.today),
                    onClick = { state.go(Screen.Tasks(ListKey.Custom(l.id))) }, onLongClick = { editing = l },
                )
            }
        }
        Box(Modifier.fillMaxWidth().background(C.bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
            PillButton("新增清單", Modifier.fillMaxWidth(), color = C.accent, bg = C.surface, icon = Glyph.Plus) { creating = true }
        }
    }

    if (creating) ListDialog(null, onDismiss = { creating = false }) { name, color ->
        creating = false
        state.launch {
            val l = state.repo.addList(name, color)
            state.go(Screen.Tasks(ListKey.Custom(l.id)))
        }
    }
    editing?.let { l ->
        ListDialog(l, onDismiss = { editing = null }, onDelete = if (l.isDefault) null else {
            {
                editing = null
                state.launch {
                    val ids = state.repo.deleteList(l.id)
                    state.showUndo("已刪除清單「${l.name}」") { state.repo.restoreList(l.id, ids) }
                }
            }
        }) { name, color ->
            editing = null
            state.launch { state.repo.updateList(l.id) { it.copy(name = name, color = color) } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(lead: @Composable () -> Unit, name: String, count: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        lead()
        Text(name, color = C.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (count > 0) Text(count.toString(), color = C.muted, fontSize = 13.sp)
    }
}

/** 新增或編輯清單：名稱＋顏色；預設清單不能刪 */
@Composable
fun ListDialog(list: ListEntity?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (String, Int) -> Unit) {
    var name by remember { mutableStateOf(list?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(list?.color ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.panel,
        title = { Text(if (list == null) "新增清單" else "編輯清單", color = C.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TextInput(name, { name = it.take(100) }, "清單名稱")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until C.listColorCount).forEach { i ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(C.listDot(i))
                                .border(3.dp, if (i == color) C.text else Color.Transparent, CircleShape)
                                .clickable { color = i }.semantics { contentDescription = "顏色 ${i + 1}" },
                        )
                    }
                }
                if (list?.isDefault == true) Text("這是預設清單，可以改名，但不能刪除。", color = C.muted, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color) }) { Text("儲存", color = C.accent) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("刪除清單", color = C.overdue) }
                TextButton(onClick = onDismiss) { Text("取消", color = C.muted) }
            }
        },
    )
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, placeholder: String, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.bg).border(1.dp, C.lineHi, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = C.muted, fontSize = 15.sp)
        BasicTextField(
            value, onChange, singleLine = singleLine, textStyle = TextStyle(color = C.text, fontSize = 15.sp), cursorBrush = SolidColor(C.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        )
    }
}

/** Email 驗證碼登入 */
@Composable
fun LoginScreen(state: AppState) {
    var email by remember { mutableStateOf(Graph.api.session?.email.orEmpty()) }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Color>?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("待辦事項", color = C.accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("用 Email 驗證碼登入。手機、網頁與語音總控會共用同一份待辦。", color = C.muted, fontSize = 14.sp)
        TextInput(email, { email = it.trim() }, "Email", KeyboardType.Email)
        if (sent) TextInput(code, { code = it.filter(Char::isDigit).take(8) }, "信裡的驗證碼", KeyboardType.Number)
        PillButton(
            when {
                busy -> "處理中…"
                sent -> "登入"
                else -> "寄驗證碼"
            },
            Modifier.fillMaxWidth(), color = C.onAccent, bg = C.accent, border = C.accent,
        ) {
            if (busy || email.isBlank()) return@PillButton
            busy = true
            msg = null
            state.launch {
                try {
                    if (!sent) {
                        Graph.api.sendOtp(email)
                        sent = true
                        msg = "驗證碼已寄出，請到信箱查看（可能在垃圾郵件）" to C.ok
                    } else {
                        Graph.api.verifyOtp(email, code)
                        state.onSignedIn()
                    }
                } catch (e: ApiException) {
                    msg = (e.message ?: "失敗") to C.overdue
                } finally {
                    busy = false
                }
            }
        }
        if (sent) Text("重新寄送", color = C.accent, fontSize = 14.sp, modifier = Modifier.clickable { sent = false; code = "" }.padding(vertical = 8.dp))
        msg?.let { (t, c) -> Text(t, color = c, fontSize = 14.sp) }
        Text("第一次登入後，帳號要開通才能同步（只有倬的帳號會被開通）。", color = C.muted, fontSize = 12.sp)
    }
}
