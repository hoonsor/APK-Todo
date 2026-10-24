package tw.hoonsor.todo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.data.ListEntity
import tw.hoonsor.todo.data.NewTask
import tw.hoonsor.todo.data.TaskEntity
import tw.hoonsor.todo.domain.DueSection
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.domain.SortMode
import tw.hoonsor.todo.domain.Views
import tw.hoonsor.todo.ui.theme.C
import kotlin.math.roundToInt

@Composable
fun TasksScreen(state: AppState, key: ListKey, quickAdd: Boolean) {
    val lists by state.lists.collectAsState()
    val all by state.tasks.collectAsState()
    val steps by state.steps.collectAsState()
    val today = state.today
    var sort by remember(key) { mutableStateOf(SortMode.of(Graph.settings.sortOf(key.wire))) }
    val accent = listColor(key, lists)
    val inList = Views.filter(key, all, today)
    val open = inList.filter { !it.done }
    val done = inList.filter { it.done }.sortedByDescending { it.completedAt }
    val sections = Views.sections(open, sort, today)
    val selected = remember(key) { mutableStateListOf<String>() }
    val selecting = selected.isNotEmpty()
    var showDone by remember { mutableStateOf(false) }
    val collapsed = remember(key) { mutableStateListOf<DueSection>() }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var editList by remember { mutableStateOf(false) }
    var movePick by remember { mutableStateOf(false) }
    var suggest by remember { mutableStateOf(false) }
    val listEntity = (key as? ListKey.Custom)?.let { k -> lists.firstOrNull { it.id == k.id } }
    val showListName = key !is ListKey.Custom

    // 依到期分段時，「今天」「明天」段標題已經說了日期；「已逾期」「之後」仍要顯示是哪一天
    fun meta(t: TaskEntity) = taskMeta(
        t, today, steps[t.id].orEmpty(), if (showListName) lists.firstOrNull { it.id == t.listId }?.name else null,
        showDue = sort != SortMode.DUE || t.done || DueSection.of(t.due, today) in setOf(DueSection.OVERDUE, DueSection.LATER),
        showMyDay = key != ListKey.MyDay,
    )
    fun toggleDone(t: TaskEntity) = state.launch {
        val r = state.repo.setCompleted(t.id, !t.done)
        if (!t.done && r?.next != null) state.showUndo("完成了，下一次是 ${dayLabel(r.next.due ?: today, today)}") { state.repo.setCompleted(t.id, false) }
    }
    fun select(t: TaskEntity) { if (t.id in selected) selected.remove(t.id) else selected.add(t.id) }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().background(C.selectBg).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoundIconButton(Glyph.Close, "取消多選") { selected.clear() }
                Text("已選 ${selected.size}", color = C.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                PillButton("全選", color = C.accent) { selected.clear(); selected.addAll(open.map { it.id }) }
            }
        } else {
            Header(
                title = Views.title(key, lists), color = accent,
                subtitle = buildString {
                    if (key == ListKey.MyDay) append("${today.monthValue} 月 ${today.dayOfMonth} 日 星期${tw.hoonsor.todo.domain.Repeat.WEEKDAY_NAMES[today.dayOfWeek.value - 1]} · ")
                    append("${open.size} 件未完成")
                    val over = open.count { DueSection.of(it.due, today) == DueSection.OVERDUE }
                    if (over > 0) append(" · $over 件已逾期")
                },
                onBack = { state.back() },
                actions = {
                    Box {
                        PillButton(if (sort == SortMode.DUE) "依到期" else sort.label, icon = Glyph.Sort) { sortMenu = true }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }, containerColor = C.panel) {
                            SortMode.entries.forEach { m ->
                                DropdownMenuItem(text = { Text(m.label, color = if (m == sort) C.accent else C.text) }, onClick = {
                                    sort = m; Graph.settings.setSort(key.wire, m.wire); sortMenu = false
                                })
                            }
                        }
                    }
                    if (listEntity != null) Box {
                        RoundIconButton(Glyph.More, "清單選項") { moreMenu = true }
                        DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }, containerColor = C.panel) {
                            DropdownMenuItem(text = { Text("改名或換顏色", color = C.text) }, onClick = { moreMenu = false; editList = true })
                        }
                    }
                },
            )
        }

        Box(Modifier.weight(1f)) {
            if (open.isEmpty() && done.isEmpty()) {
                EmptyState(key, Views.suggestions(all, today).size) { suggest = true }
            } else {
                TaskList(
                    sections = sections, done = done, showDone = showDone, onToggleShowDone = { showDone = !showDone },
                    collapsed = collapsed, sort = sort, accent = accent, selected = selected, selecting = selecting, meta = ::meta,
                    onToggleDone = ::toggleDone,
                    onStar = { t -> state.launch { state.repo.setImportant(t.id, !t.important) } },
                    onClick = { t -> if (selecting) select(t) else state.go(Screen.Detail(t.id)) },
                    onLongClick = { t -> if (!t.done) select(t) },
                    onReorder = { id, before, after -> state.launch { state.repo.reorderTask(id, before, after) } },
                )
            }
        }

        if (selecting) {
            Row(
                Modifier.fillMaxWidth().background(C.panel).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BulkButton("完成", Glyph.Check, C.ok) {
                    val ids = selected.toList(); selected.clear()
                    state.launch { ids.forEach { state.repo.setCompleted(it, true) } }
                    state.showUndo("已完成 ${ids.size} 件") { ids.forEach { state.repo.setCompleted(it, false) } }
                }
                BulkButton("我的一天", Glyph.Sun, C.myDay) {
                    val ids = selected.toList(); selected.clear()
                    state.launch { state.repo.setMyDay(ids, true) }
                }
                BulkButton("移動", Glyph.Move, C.accent) { movePick = true }
                BulkButton("刪除", Glyph.Trash, C.overdue) {
                    val ids = selected.toList(); selected.clear()
                    state.launch { state.repo.deleteTasks(ids) }
                    state.showUndo("已刪除 ${ids.size} 件") { state.repo.restoreTasks(ids) }
                }
            }
        } else {
            AddBar(accent, "新增工作", autoFocus = quickAdd) { title ->
                state.launch {
                    val listId = (key as? ListKey.Custom)?.id ?: (lists.firstOrNull { it.isDefault } ?: state.repo.ensureDefaultList()).id
                    state.repo.addTask(
                        NewTask(
                            listId = listId, title = title, myDay = key == ListKey.MyDay, important = key == ListKey.Important,
                            due = if (key == ListKey.Planned) today else null,
                        ),
                    )
                }
            }
        }
    }

    if (editList && listEntity != null) ListDialog(listEntity, onDismiss = { editList = false }, onDelete = if (listEntity.isDefault) null else {
        {
            editList = false
            state.launch {
                val ids = state.repo.deleteList(listEntity.id)
                state.back()
                state.showUndo("已刪除清單「${listEntity.name}」") { state.repo.restoreList(listEntity.id, ids) }
            }
        }
    }) { name, color ->
        editList = false
        state.launch { state.repo.updateList(listEntity.id) { it.copy(name = name, color = color) } }
    }

    if (movePick) ListPicker(lists, title = "移動到哪個清單？", onDismiss = { movePick = false }) { l ->
        movePick = false
        val ids = selected.toList(); selected.clear()
        state.launch { state.repo.moveTasks(ids, l.id) }
    }

    if (suggest) SuggestionsDialog(state, onDismiss = { suggest = false })
}

@Composable
private fun TaskList(
    sections: List<tw.hoonsor.todo.domain.Section>,
    done: List<TaskEntity>,
    showDone: Boolean,
    onToggleShowDone: () -> Unit,
    collapsed: MutableList<DueSection>,
    sort: SortMode,
    accent: Color,
    selected: List<String>,
    selecting: Boolean,
    meta: (TaskEntity) -> Pair<String, Color>,
    onToggleDone: (TaskEntity) -> Unit,
    onStar: (TaskEntity) -> Unit,
    onClick: (TaskEntity) -> Unit,
    onLongClick: (TaskEntity) -> Unit,
    onReorder: (String, String?, String?) -> Unit,
) {
    val rowPx = with(LocalDensity.current) { 58.dp.toPx() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sections.forEach { sec ->
            if (sec.section != null) item(key = "h-${sec.section}") {
                val isCollapsed = sec.section in collapsed
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { if (isCollapsed) collapsed.remove(sec.section) else collapsed.add(sec.section) }.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val c = when (sec.section) {
                        DueSection.OVERDUE -> C.overdue
                        DueSection.TODAY -> accent
                        else -> C.text
                    }
                    Icon(if (isCollapsed) Glyph.ChevronRight else Glyph.Chevron, null, tint = c, modifier = Modifier.size(14.dp))
                    Text(sec.title, color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(sec.tasks.size.toString(), color = C.muted, fontSize = 13.sp)
                }
            }
            if (sec.section == null || sec.section !in collapsed) {
                items(sec.tasks, key = { it.id }) { t ->
                    val manual = sort == SortMode.MANUAL && !selecting
                    TaskCard(
                        t, meta(t), accent, selecting, t.id in selected,
                        onToggleDone = { onToggleDone(t) }, onToggleStar = { onStar(t) },
                        onClick = { onClick(t) }, onLongClick = { onLongClick(t) },
                        trailing = if (manual) ({
                            Box(
                                Modifier.size(44.dp).semantics { contentDescription = "拖曳排序「${t.title}」" }.pointerInput(t.id) {
                                    detectDragGestures(
                                        onDragStart = { dragId = t.id; dragOffset = 0f },
                                        onDragEnd = {
                                            val list = sec.tasks
                                            val from = list.indexOfFirst { it.id == t.id }
                                            val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, list.lastIndex)
                                            if (from >= 0 && to != from) {
                                                val rest = list.filter { it.id != t.id }
                                                val before = rest.getOrNull(to - 1)?.sortKey
                                                val after = rest.getOrNull(to)?.sortKey
                                                if (before == null || after == null || before < after) onReorder(t.id, before, after)
                                            }
                                            dragId = null; dragOffset = 0f
                                        },
                                        onDragCancel = { dragId = null; dragOffset = 0f },
                                        onDrag = { change, amount -> change.consume(); dragOffset += amount.y },
                                    )
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Glyph.Lines, null, tint = C.muted, modifier = Modifier.size(20.dp)) }
                        }) else null,
                        modifier = Modifier.graphicsLayer {
                            if (dragId == t.id) { translationY = dragOffset; shadowElevation = 12f }
                        },
                    )
                }
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done-h") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onToggleShowDone).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(if (showDone) Glyph.Chevron else Glyph.ChevronRight, null, tint = C.muted, modifier = Modifier.size(16.dp))
                    Text("已完成 ${done.size}", color = C.muted, fontSize = 13.sp)
                }
            }
            if (showDone) items(done, key = { it.id }) { t ->
                TaskCard(t, meta(t), accent, false, false, { onToggleDone(t) }, { onStar(t) }, { onClick(t) }, {})
            }
        }
        item { Spacer(Modifier.heightIn(min = 80.dp)) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BulkButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, C.lineHi, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(key: ListKey, suggestions: Int, onSuggest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (key == ListKey.MyDay) {
            Canvas(Modifier.size(120.dp)) {
                val s = size.width / 120f
                val gold = C.myDay
                drawCircle(gold, radius = 22 * s, style = Stroke(3 * s))
                val rays = listOf(60f to 18f, 60f to 102f, 18f to 60f, 102f to 60f, 30f to 30f, 90f to 90f, 30f to 90f, 90f to 30f)
                rays.forEach { (x, y) ->
                    val dx = (x - 60) / 42f; val dy = (y - 60) / 42f
                    drawLine(gold.copy(alpha = 0.8f), Offset((60 + dx * 30) * s, (60 + dy * 30) * s), Offset(x * s, y * s), 3 * s, StrokeCap.Round)
                }
                drawLine(C.text, Offset(50 * s, 61 * s), Offset(57 * s, 68 * s), 3.2f * s, StrokeCap.Round)
                drawLine(C.text, Offset(57 * s, 68 * s), Offset(70 * s, 54 * s), 3.2f * s, StrokeCap.Round)
            }
            Text("今天想專心做什麼？", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("從建議裡挑幾件加進來，\n或直接說「待辦，……，加入我的一天」。", color = C.muted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
            if (suggestions > 0) PillButton("看建議（$suggestions 件）", color = C.myDay, border = C.myDay.copy(alpha = 0.7f), bg = C.myDay.copy(alpha = 0.12f), onClick = onSuggest)
        } else {
            Text("這裡還沒有待辦", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("在下方輸入，或說「待辦，……」。", color = C.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SuggestionsDialog(state: AppState, onDismiss: () -> Unit) {
    val all by state.tasks.collectAsState()
    val lists by state.lists.collectAsState()
    val items = Views.suggestions(all, state.today)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.panel,
        title = { Text("加入我的一天", color = C.text) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.isEmpty()) Text("沒有建議了", color = C.muted)
                items.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(C.surface)
                            .clickable { state.launch { state.repo.setMyDay(listOf(t.id), true) } }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = C.text, fontSize = 15.sp)
                            val (m, c) = taskMeta(t, state.today, emptyList(), lists.firstOrNull { it.id == t.listId }?.name, true)
                            Text(m, color = c, fontSize = 12.sp)
                        }
                        Icon(Glyph.Plus, "加入我的一天", tint = C.myDay, modifier = Modifier.size(22.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = C.accent) } },
    )
}

@Composable
fun ListPicker(lists: List<ListEntity>, title: String, onDismiss: () -> Unit, onPick: (ListEntity) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.panel,
        title = { Text(title, color = C.text) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                lists.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(l) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(C.listTint(l.color)).border(1.dp, C.listDot(l.color), RoundedCornerShape(6.dp)))
                        Text(l.name, color = C.text, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消", color = C.muted) } },
    )
}
