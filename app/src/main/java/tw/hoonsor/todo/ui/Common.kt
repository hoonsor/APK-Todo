package tw.hoonsor.todo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.hoonsor.todo.data.StepEntity
import tw.hoonsor.todo.data.TaskEntity
import tw.hoonsor.todo.domain.DueSection
import tw.hoonsor.todo.domain.Repeat
import tw.hoonsor.todo.ui.theme.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun RoundIconButton(icon: ImageVector, desc: String, tint: Color = C.text, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).border(1.dp, C.lineHi, CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, color: Color = C.text, border: Color = C.lineHi, bg: Color = Color.Transparent, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(bg).border(1.dp, border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, fontSize = 14.sp)
    }
}

/** 打勾用的圓圈（完成時填滿＋勾） */
@Composable
fun CheckCircle(done: Boolean, color: Color, size: Dp = 20.dp, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape).background(if (done) color else Color.Transparent)
                .border(2.dp, if (done) color else C.ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Glyph.Check, null, tint = C.onAccent, modifier = Modifier.size(size - 6.dp))
        }
    }
}

fun localDateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "今天"
    today.plusDays(1) -> "明天"
    today.minusDays(1) -> "昨天"
    else -> "${d.monthValue}/${d.dayOfMonth}（${Repeat.WEEKDAY_NAMES[d.dayOfWeek.value - 1]}）"
}

fun timeLabel(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    val h = t.hour
    val ap = if (h < 12) "上午" else "下午"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$ap $h12:%02d".format(t.minute)
}

/** 待辦卡片的第二行：到期、提醒、我的一天、步驟、重複、備註、所屬清單 */
fun taskMeta(t: TaskEntity, today: LocalDate, steps: List<StepEntity>, listName: String?, showDue: Boolean, showMyDay: Boolean = true): Pair<String, Color> {
    val parts = mutableListOf<String>()
    var color = C.muted
    if (listName != null) parts += listName
    if (showMyDay && t.inMyDay(today)) parts += "我的一天"
    t.due?.let { d ->
        if (showDue) parts += when {
            d < today && !t.done -> dayLabel(d, today)
            d > today.plusDays(1) -> dayLabel(d, today)
            else -> "${dayLabel(d, today)}到期"
        }
        if (!t.done) when (DueSection.of(d, today)) {
            DueSection.OVERDUE -> color = C.overdue
            DueSection.TODAY -> color = C.accent
            else -> Unit
        }
    }
    t.remindAt?.let { r -> if (!t.done) parts += "${if (localDateOf(r) == today) "" else dayLabel(localDateOf(r), today) + " "}${timeLabel(r)} 提醒" }
    if (steps.isNotEmpty()) parts += "步驟 ${steps.count { it.done }}/${steps.size}"
    Repeat.parse(t.repeat)?.let { parts += it.label() }
    if (t.note.isNotBlank()) parts += "有備註"
    return parts.joinToString(" · ") to color
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    t: TaskEntity,
    meta: Pair<String, Color>,
    accent: Color,
    selecting: Boolean,
    selected: Boolean,
    onToggleDone: () -> Unit,
    onToggleStar: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
            .background(if (selected) C.selectBg else C.surface)
            .border(1.dp, if (selected) C.selectLine else C.line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (selected) C.accent else Color.Transparent)
                        .border(2.dp, if (selected) C.accent else C.ring, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Glyph.Check, null, tint = C.onAccent, modifier = Modifier.size(16.dp)) }
            }
        } else {
            CheckCircle(t.done, accent, desc = if (t.done) "取消完成「${t.title}」" else "完成「${t.title}」", onClick = onToggleDone)
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                t.title, color = if (t.done) C.muted else C.text, fontSize = 15.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
            )
            if (meta.first.isNotEmpty()) Text(meta.first, color = meta.second, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) trailing()
        else if (!selecting) Box(
            Modifier.size(44.dp).clickable(onClick = onToggleStar).semantics { contentDescription = if (t.important) "取消重要" else "標為重要" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (t.important) Glyph.StarFilled else Glyph.Star, null, tint = if (t.important) C.star else C.ring, modifier = Modifier.size(20.dp))
        }
    }
}

/** 底部「新增工作」輸入列；Enter 連續新增 */
@Composable
fun AddBar(accent: Color, placeholder: String, autoFocus: Boolean, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    val submit = {
        if (text.isNotBlank()) onAdd(text.trim())
        text = ""
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp).clip(RoundedCornerShape(10.dp))
            .background(C.surfaceHi).border(1.dp, C.lineHi, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Glyph.Plus, null, tint = accent, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(placeholder, color = C.muted, fontSize = 15.sp)
            BasicTextField(
                value = text, onValueChange = { text = it.take(500) },
                textStyle = TextStyle(color = C.text, fontSize = 15.sp), cursorBrush = SolidColor(accent), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = placeholder },
            )
        }
        if (text.isNotBlank()) Text("新增", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { submit() }.padding(8.dp))
    }
}

@Composable
fun UndoBar(state: AppState, modifier: Modifier = Modifier) {
    val u = state.undo
    AnimatedVisibility(u != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.padding(16.dp).padding(bottom = 64.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.surfaceHi)
                .border(1.dp, C.lineHi, RoundedCornerShape(12.dp)).padding(start = 16.dp, end = 6.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(u?.text.orEmpty(), color = C.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("復原", color = C.accent, fontSize = 14.sp, modifier = Modifier.clickable { state.doUndo() }.padding(14.dp))
        }
    }
}

@Composable
fun Header(title: String, color: Color, subtitle: String?, actions: @Composable () -> Unit, onBack: (() -> Unit)?) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) RoundIconButton(Glyph.Back, "返回", onClick = onBack)
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(title, color = color, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        if (subtitle != null) Text(subtitle, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
    }
}
