package tw.hoonsor.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tw.hoonsor.todo.data.TaskEntity
import tw.hoonsor.todo.domain.Repeat
import tw.hoonsor.todo.ui.theme.C
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@Composable
fun DetailScreen(state: AppState, taskId: String) {
    val task by remember(taskId) { state.repo.dao.taskFlow(taskId) }.collectAsState(initial = null)
    val steps by remember(taskId) { state.repo.dao.stepsFlow(taskId) }.collectAsState(initial = emptyList())
    val lists by state.lists.collectAsState()
    val t = task ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("載入中…", color = C.muted) }
        return
    }
    if (t.deletedAt != null) {
        LaunchedEffect(Unit) { state.back() }
        return
    }
    val today = state.today
    val list = lists.firstOrNull { it.id == t.listId }
    val accent = C.listTitle(list?.color ?: 0)
    fun update(change: (TaskEntity) -> TaskEntity) = state.launch { state.repo.updateTask(t.id) { change(it) } }

    var dueMenu by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    var pickRemind by remember { mutableStateOf(false) }
    var repeatDialog by remember { mutableStateOf(false) }
    var movePick by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Glyph.Back, "返回") { state.back() }
            Text(list?.name.orEmpty(), color = accent, fontSize = 14.sp, modifier = Modifier.clickable { movePick = true }.padding(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 標題
            Block {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(t.done, accent, 22.dp, if (t.done) "取消完成" else "完成") { state.launch { state.repo.setCompleted(t.id, !t.done) } }
                    EditableText(t.title, Modifier.weight(1f), fontSize = 20, bold = true, done = t.done) { v -> if (v.isNotBlank()) update { it.copy(title = v.take(500)) } }
                    Box(Modifier.size(44.dp).clickable { update { it.copy(important = !it.important) } }, contentAlignment = Alignment.Center) {
                        Icon(if (t.important) Glyph.StarFilled else Glyph.Star, if (t.important) "取消重要" else "標為重要", tint = if (t.important) C.star else C.ring, modifier = Modifier.size(22.dp))
                    }
                }
            }
            // 步驟
            Block(Modifier.padding(horizontal = 4.dp)) {
                steps.forEach { s ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clickable { state.launch { state.repo.updateStep(s.id) { it.copy(done = !it.done) } } }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(if (s.done) accent else Color.Transparent).border(2.dp, if (s.done) accent else C.ring, CircleShape))
                        }
                        EditableText(s.title, Modifier.weight(1f), fontSize = 14, done = s.done) { v ->
                            state.launch { if (v.isBlank()) state.repo.deleteStep(s.id) else state.repo.updateStep(s.id) { it.copy(title = v.take(300)) } }
                        }
                        Box(Modifier.size(40.dp).clickable { state.launch { state.repo.deleteStep(s.id) } }, contentAlignment = Alignment.Center) {
                            Icon(Glyph.Close, "刪除步驟「${s.title}」", tint = C.muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                NewStepField(accent) { v -> state.launch { state.repo.addStep(t.id, v) } }
            }
            // 屬性
            Block(Modifier.padding(0.dp)) {
                val inMyDay = t.inMyDay(today)
                PropRow(Glyph.Sun, if (inMyDay) "已加入我的一天" else "加入我的一天", null, inMyDay, onClear = if (inMyDay) ({ update { it.copy(myDayDate = null) } }) else null) {
                    update { it.copy(myDayDate = today.toString()) }
                }
                PropRow(
                    Glyph.Bell, t.remindAt?.let { "${dayLabel(localDateOf(it), today)} ${timeLabel(it)} 提醒我" } ?: "提醒我", null, t.remindAt != null,
                    onClear = if (t.remindAt != null) ({ update { it.copy(remindAt = null) } }) else null,
                ) { pickRemind = true }
                Box {
                    PropRow(
                        Glyph.Calendar, t.due?.let { "${dayLabel(it, today)}到期" } ?: "新增到期日",
                        t.due?.let { d -> "${d.year} 年 ${d.monthValue} 月 ${d.dayOfMonth} 日 星期${Repeat.WEEKDAY_NAMES[d.dayOfWeek.value - 1]}" },
                        t.dueDate != null, onClear = if (t.dueDate != null) ({ update { it.copy(dueDate = null) } }) else null,
                    ) { dueMenu = true }
                    DropdownMenu(dueMenu, onDismissRequest = { dueMenu = false }, containerColor = C.panel) {
                        listOf("今天" to today, "明天" to today.plusDays(1), "下週一" to today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))).forEach { (label, d) ->
                            DropdownMenuItem(text = { Text("$label（${d.monthValue}/${d.dayOfMonth}）", color = C.text) }, onClick = { dueMenu = false; update { it.copy(dueDate = d.toString()) } })
                        }
                        DropdownMenuItem(text = { Text("選擇日期…", color = C.accent) }, onClick = { dueMenu = false; pickDue = true })
                    }
                }
                val rep = Repeat.parse(t.repeat)
                PropRow(Glyph.Repeat, rep?.label() ?: "重複", null, rep != null, onClear = if (rep != null) ({ update { it.copy(repeat = null) } }) else null) { repeatDialog = true }
                PropRow(Glyph.Move, "清單：${list?.name.orEmpty()}", null, false, onClear = null) { movePick = true }
            }
            // 備註
            Block {
                EditableText(t.note, Modifier.fillMaxWidth().heightIn(min = 72.dp), fontSize = 14, multiline = true, placeholder = "新增備註") { v -> update { it.copy(note = v) } }
            }
            Spacer(Modifier.heightIn(min = 24.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(C.bg).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val created = SimpleDateFormat("M/d HH:mm", Locale.TAIWAN).format(Date(t.createdAt))
            val src = mapOf("voice" to "由語音總控建立", "web" to "由網頁建立", "widget" to "由小工具建立")[t.source] ?: "建立於"
            Text("$src · $created", color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            RoundIconButton(Glyph.Trash, "刪除待辦", tint = C.overdue) {
                state.launch { state.repo.deleteTasks(listOf(t.id)) }
                state.back()
                state.showUndo("已刪除「${t.title}」") { state.repo.restoreTasks(listOf(t.id)) }
            }
        }
    }

    if (pickDue) DueDatePicker(t.due ?: today, onDismiss = { pickDue = false }) { d -> pickDue = false; update { it.copy(dueDate = d.toString()) } }
    if (pickRemind) ReminderPicker(t.remindAt, today, onDismiss = { pickRemind = false }) { ms -> pickRemind = false; update { it.copy(remindAt = ms) } }
    if (repeatDialog) RepeatDialog(Repeat.parse(t.repeat), t.due ?: today, onDismiss = { repeatDialog = false }) { r ->
        repeatDialog = false
        // 設定重複但沒有到期日時，以今天為第一次（同 To Do）
        update { it.copy(repeat = r.toJson(), dueDate = it.dueDate ?: today.toString()) }
    }
    if (movePick) ListPicker(lists, "移動到哪個清單？", onDismiss = { movePick = false }) { l -> movePick = false; update { it.copy(listId = l.id) } }
}

@Composable
private fun Block(modifier: Modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(12.dp)).then(modifier),
    ) { content() }
}

@Composable
private fun PropRow(icon: ImageVector, label: String, sub: String?, on: Boolean, onClear: (() -> Unit)?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = if (on) C.accent else C.muted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (on) C.accent else C.text, fontSize = 14.sp)
            if (sub != null) Text(sub, color = C.muted, fontSize = 12.sp)
        }
        if (onClear != null) Box(Modifier.size(44.dp).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
            Icon(Glyph.Close, "移除「$label」", tint = C.muted, modifier = Modifier.size(16.dp))
        } else Spacer(Modifier.size(14.dp))
    }
}

/** 可直接編輯的文字；停止輸入 0.8 秒或按完成後儲存 */
@Composable
private fun EditableText(value: String, modifier: Modifier, fontSize: Int, bold: Boolean = false, done: Boolean = false, multiline: Boolean = false, placeholder: String = "", onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(text) {
        if (text != value) {
            delay(800)
            onSave(text.trim())
        }
    }
    Box(modifier.padding(vertical = if (multiline) 12.dp else 8.dp, horizontal = if (multiline) 14.dp else 0.dp)) {
        if (text.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = C.muted, fontSize = fontSize.sp)
        BasicTextField(
            text, { text = it }, singleLine = !multiline,
            textStyle = TextStyle(
                color = if (done) C.muted else C.text, fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textDecoration = if (done) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(C.accent),
            keyboardOptions = KeyboardOptions(imeAction = if (multiline) ImeAction.Default else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text != value) onSave(text.trim()) }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NewStepField(accent: Color, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Glyph.Plus, null, tint = accent, modifier = Modifier.size(16.dp)) }
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("新增步驟", color = accent, fontSize = 14.sp)
            BasicTextField(
                text, { text = it.take(300) }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 14.sp), cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }) { Text("確定", color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = C.muted) } },
        colors = DatePickerDefaults.colors(containerColor = C.panel),
    ) { DatePicker(st, colors = DatePickerDefaults.colors(containerColor = C.panel)) }
}

/** 提醒：先選日期（預設今天或已設定的日期），再選時間 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPicker(current: Long?, today: LocalDate, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val init = current?.let { Instant.ofEpochMilli(it).atZone(zone) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    if (date == null) {
        DueDatePicker(init?.toLocalDate() ?: today, onDismiss) { date = it }
        return
    }
    val defaultTime = init?.toLocalTime() ?: LocalTime.now().plusHours(1).withMinute(0)
    val ts = rememberTimePickerState(defaultTime.hour, defaultTime.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.panel,
        title = { Text("${date!!.monthValue}/${date!!.dayOfMonth} 幾點提醒？", color = C.text) },
        text = { TimePicker(ts, colors = TimePickerDefaults.colors(containerColor = C.panel)) },
        confirmButton = {
            TextButton(onClick = { onPick(date!!.atTime(ts.hour, ts.minute).atZone(zone).toInstant().toEpochMilli()) }) { Text("確定", color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = C.muted) } },
    )
}

@Composable
private fun RepeatDialog(current: Repeat?, base: LocalDate, onDismiss: () -> Unit, onSave: (Repeat) -> Unit) {
    var freq by remember { mutableStateOf(current?.freq ?: Repeat.Freq.WEEKLY) }
    var interval by remember { mutableIntStateOf(current?.interval ?: 1) }
    val days = remember { mutableStateListOf<Int>().apply { addAll(current?.weekdays?.takeIf { it.isNotEmpty() } ?: setOf(base.dayOfWeek.value)) } }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.panel,
        title = { Text("重複", color = C.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Repeat.Freq.entries.forEach { f ->
                        Chip(f.label, f == freq) { freq = f }
                    }
                }
                if (freq != Repeat.Freq.WEEKDAYS) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("每", color = C.text)
                    Chip("－", false) { if (interval > 1) interval-- }
                    Text("$interval", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Chip("＋", false) { if (interval < 99) interval++ }
                    Text(mapOf(Repeat.Freq.DAILY to "天", Repeat.Freq.WEEKLY to "週", Repeat.Freq.MONTHLY to "個月", Repeat.Freq.YEARLY to "年")[freq].orEmpty(), color = C.text)
                }
                if (freq == Repeat.Freq.WEEKLY) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Repeat.WEEKDAY_NAMES.forEachIndexed { i, n ->
                        val d = i + 1
                        Chip(n, d in days) { if (d in days) { if (days.size > 1) days.remove(d) } else days.add(d) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Repeat(freq, interval, if (freq == Repeat.Freq.WEEKLY) days.toSet() else emptySet())) }) { Text("儲存", color = C.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = C.muted) } },
    )
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) C.selectBg else Color.Transparent)
            .border(1.dp, if (on) C.selectLine else C.lineHi, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) C.accent else C.text, fontSize = 13.sp) }
}
