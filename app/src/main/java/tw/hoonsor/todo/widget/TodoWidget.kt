package tw.hoonsor.todo.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.background as gBackground
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment as GAlignment
import androidx.glance.layout.Box as GBox
import androidx.glance.layout.Column as GColumn
import androidx.glance.layout.Row as GRow
import androidx.glance.layout.Spacer as GSpacer
import androidx.glance.layout.fillMaxSize as gFillMaxSize
import androidx.glance.layout.fillMaxWidth as gFillMaxWidth
import androidx.glance.layout.height as gHeight
import androidx.glance.layout.padding as gPadding
import androidx.glance.layout.size as gSize
import androidx.glance.layout.width as gWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.launch
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.R
import tw.hoonsor.todo.data.TaskEntity
import tw.hoonsor.todo.domain.DueSection
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.domain.SortMode
import tw.hoonsor.todo.domain.Views
import tw.hoonsor.todo.ui.MainActivity
import tw.hoonsor.todo.ui.dayLabel
import tw.hoonsor.todo.ui.listColor
import tw.hoonsor.todo.ui.listDotColor
import tw.hoonsor.todo.ui.theme.C
import tw.hoonsor.todo.ui.theme.TodoTheme
import java.time.LocalDate
import androidx.compose.ui.graphics.Color
import androidx.glance.text.Text as GText

/**
 * 桌面小工具（版面仿 Microsoft To Do）：顯示一個清單的未完成待辦，標題「清單名稱⌄」切換清單（每個小工具各自記住），
 * 圓圈直接打勾完成、星號切換重要；標題列有重新整理、「＋」快速新增、設定。
 * 資料來自 Room，Repository 修改後會呼叫 [refresh]。
 */
class TodoWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    /** [accent]：這個清單在 APP 裡的顏色（標題、＋、今天到期），切換清單後跟著換 */
    private data class Data(val key: ListKey, val title: String, val accent: Color, val open: List<TaskEntity>, val showLogin: Boolean, val today: LocalDate)

    private suspend fun load(widgetId: Int): Data {
        val key = ListKey.parse(Graph.settings.widgetList(widgetId))
        val today = LocalDate.now()
        val lists = Graph.repo.dao.lists()
        val all = Graph.repo.dao.tasks()
        val open = Views.sort(Views.filter(key, all, today).filter { !it.done }, SortMode.DUE).take(40)
        return Data(key, Views.title(key, lists), listColor(key, lists), open, !Graph.api.signedIn && all.isEmpty(), today)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val first = load(widgetId)
        provideContent {
            // 小工具還在執行時，更新只會重畫；用狀態裡的版本號觸發重新讀資料（見 refresh）
            val version = currentState<Preferences>()[VERSION] ?: 0L
            var data by remember { mutableStateOf(first) }
            LaunchedEffect(version) { data = load(widgetId) }
            val (key, title, accent, open, showLogin, today) = data
            GColumn(
                GlanceModifier.gFillMaxSize().gBackground(ColorProvider(C.panel)).cornerRadius(22.dp).gPadding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ) {
                // 標題列（仿 Microsoft To Do）：標誌、清單名稱⌄、件數 … 重新整理、新增、設定
                GRow(GlanceModifier.gFillMaxWidth(), verticalAlignment = GAlignment.CenterVertically) {
                    IconButton(R.drawable.ic_w_logo, "打開待辦事項", accent, actionStartActivity(homeIntent(context)), iconSize = 24.dp)
                    GRow(
                        GlanceModifier.defaultWeight().gPadding(start = 2.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                            .clickable(actionStartActivity(pickerIntent(context, widgetId))),
                        verticalAlignment = GAlignment.CenterVertically,
                    ) {
                        GText(title, maxLines = 1, style = TextStyle(color = ColorProvider(accent), fontSize = 17.sp, fontWeight = FontWeight.Bold))
                        Image(ImageProvider(R.drawable.ic_w_chevron), "切換清單", GlanceModifier.gSize(20.dp).gPadding(start = 4.dp), colorFilter = ColorFilter.tint(ColorProvider(C.text)))
                        if (open.isNotEmpty()) GText(open.size.toString(), style = TextStyle(color = ColorProvider(C.muted), fontSize = 12.sp), modifier = GlanceModifier.gPadding(start = 4.dp))
                    }
                    IconButton(R.drawable.ic_w_refresh, "重新整理", C.text, actionRunCallback<RefreshAction>())
                    IconButton(R.drawable.ic_plus, "新增工作", C.text, actionStartActivity(addIntent(context, key)))
                    IconButton(R.drawable.ic_w_settings, "設定", C.text, actionStartActivity(settingsIntent(context)))
                }
                when {
                    showLogin -> Hint("打開待辦事項 APP 登入")
                    open.isEmpty() -> Hint("這裡沒有未完成的待辦")
                    else -> LazyColumn(GlanceModifier.gFillMaxSize()) {
                        items(open, itemId = { it.id.hashCode().toLong() }) { t -> WidgetRow(context, t, today, accent, first = t.id == open.first().id) }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun IconButton(icon: Int, desc: String, tint: Color, action: androidx.glance.action.Action, iconSize: androidx.compose.ui.unit.Dp = 22.dp) {
        GBox(GlanceModifier.gSize(40.dp).cornerRadius(20.dp).clickable(action), contentAlignment = GAlignment.Center) {
            Image(ImageProvider(icon), desc, GlanceModifier.gSize(iconSize), colorFilter = ColorFilter.tint(ColorProvider(tint)))
        }
    }

    @androidx.compose.runtime.Composable
    private fun Hint(text: String) {
        GText(text, style = TextStyle(color = ColorProvider(C.muted), fontSize = 14.sp), modifier = GlanceModifier.gPadding(12.dp))
    }

    /** 一列待辦：清單色圓圈（打勾）、標題（開詳細）、到期日、星號（切換重要）；列與列之間一條細線 */
    @androidx.compose.runtime.Composable
    private fun WidgetRow(context: Context, t: TaskEntity, today: LocalDate, accent: Color, first: Boolean) {
        GColumn(GlanceModifier.gFillMaxWidth()) {
            if (!first) GBox(GlanceModifier.gFillMaxWidth().gPadding(start = 44.dp, end = 8.dp)) {
                GSpacer(GlanceModifier.gFillMaxWidth().gHeight(1.dp).gBackground(ColorProvider(C.line)))
            }
            GRow(GlanceModifier.gFillMaxWidth().gHeight(52.dp), verticalAlignment = GAlignment.CenterVertically) {
                GBox(
                    GlanceModifier.gSize(44.dp).clickable(actionRunCallback<CompleteAction>(actionParametersOf(CompleteAction.TASK to t.id))),
                    contentAlignment = GAlignment.Center,
                ) {
                    Image(ImageProvider(R.drawable.ic_circle), "完成「${t.title}」", GlanceModifier.gSize(22.dp), colorFilter = ColorFilter.tint(ColorProvider(accent)))
                }
                GText(
                    t.title, maxLines = 1,
                    style = TextStyle(color = ColorProvider(C.text), fontSize = 15.sp),
                    modifier = GlanceModifier.defaultWeight().gPadding(start = 4.dp, top = 14.dp, bottom = 14.dp).clickable(actionStartActivity(openIntent(context, t.id))),
                )
                widgetDueLabel(t, today, accent)?.let { (label, color) ->
                    GText(label, maxLines = 1, style = TextStyle(color = ColorProvider(color), fontSize = 12.sp), modifier = GlanceModifier.gPadding(start = 6.dp))
                }
                GBox(
                    GlanceModifier.gSize(44.dp).clickable(
                        actionRunCallback<StarAction>(actionParametersOf(CompleteAction.TASK to t.id, StarAction.ON to !t.important)),
                    ),
                    contentAlignment = GAlignment.Center,
                ) {
                    Image(
                        ImageProvider(if (t.important) R.drawable.ic_w_star_on else R.drawable.ic_w_star),
                        if (t.important) "取消重要「${t.title}」" else "標為重要「${t.title}」",
                        GlanceModifier.gSize(22.dp), colorFilter = ColorFilter.tint(ColorProvider(accent)),
                    )
                }
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            Graph.scope.launch {
                runCatching {
                    val manager = GlanceAppWidgetManager(context)
                    manager.getGlanceIds(TodoWidget::class.java).forEach { id ->
                        updateAppWidgetState(context, id) { it[VERSION] = System.nanoTime() }
                        TodoWidget().update(context, id)
                    }
                }
            }
        }

        private fun pickerIntent(context: Context, widgetId: Int) =
            Intent(context, WidgetListPickerActivity::class.java).putExtra(EXTRA_WIDGET, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .setData(android.net.Uri.parse("todo://widget/$widgetId")) // 讓每個小工具的 Intent 不同

        private fun addIntent(context: Context, key: ListKey) =
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_QUICK_ADD, key.wire)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(android.net.Uri.parse("todo://add/${key.wire}"))

        private fun homeIntent(context: Context) =
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun settingsIntent(context: Context) =
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(android.net.Uri.parse("todo://settings"))

        private fun openIntent(context: Context, taskId: String) =
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TASK, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(android.net.Uri.parse("todo://task/$taskId"))

        const val EXTRA_WIDGET = "widgetId"
        private val VERSION = longPreferencesKey("version")
    }
}

/**
 * 小工具待辦列右側的到期日：文字與 APP 的 [dayLabel] 一致（省略星期），
 * 逾期紅、今天用清單色、其他灰；沒有到期日回傳 null。
 */
internal fun widgetDueLabel(t: TaskEntity, today: LocalDate, accent: Color): Pair<String, Color>? {
    val d = t.due ?: return null
    val color = when (DueSection.of(d, today)) {
        DueSection.OVERDUE -> C.overdue
        DueSection.TODAY -> accent
        else -> C.muted
    }
    return dayLabel(d, today, weekday = false) to color
}

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK] ?: return
        Graph.repo.setCompleted(id, true)
    }

    companion object {
        val TASK = ActionParameters.Key<String>("task")
    }
}

/** 星號：切換重要（離線也能用，之後背景同步） */
class StarAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[CompleteAction.TASK] ?: return
        Graph.repo.setImportant(id, parameters[ON] ?: true)
    }

    companion object {
        val ON = ActionParameters.Key<Boolean>("on")
    }
}

/** 重新整理：立即和 Supabase 同步一次（沒登入或斷網時只重讀本機） */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (Graph.api.signedIn) runCatching { Graph.sync.sync() }
        TodoWidget.refresh(context)
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}

/** 小工具標題點下去：選這個小工具要顯示的清單 */
class WidgetListPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(TodoWidget.EXTRA_WIDGET, -1)
        setContent {
            TodoTheme {
                var options by remember { mutableStateOf<List<Triple<String, String, Color>>>(emptyList()) }
                LaunchedEffect(Unit) {
                    val lists = Graph.repo.dao.lists()
                    options = (ListKey.SMART + lists.map { ListKey.Custom(it.id) }).map { Triple(it.wire, Views.title(it, lists), listDotColor(it, lists)) }
                }
                val current = Graph.settings.widgetList(widgetId)
                Box(Modifier.fillMaxSize().background(C.scrim).clickable { finish() }, contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.panel)
                            .border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(vertical = 10.dp)
                            .heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    ) {
                        Text("小工具要顯示哪個清單？", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        options.forEach { (key, name, dot) ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                                    Graph.settings.setWidgetList(widgetId, key)
                                    TodoWidget.refresh(applicationContext)
                                    finish()
                                }.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(dot))
                                Text(name, color = C.text, fontSize = 16.sp, fontWeight = if (key == current) androidx.compose.ui.text.font.FontWeight.Bold else null)
                                Spacer(Modifier.weight(1f))
                                if (key == current) Text("目前", color = C.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
