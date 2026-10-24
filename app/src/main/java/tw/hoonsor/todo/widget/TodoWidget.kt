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
import tw.hoonsor.todo.ui.theme.C
import tw.hoonsor.todo.ui.theme.TodoTheme
import java.time.LocalDate
import androidx.glance.text.Text as GText

/**
 * 桌面小工具：顯示一個清單的未完成待辦，標題「清單名稱▾」切換清單（每個小工具各自記住），
 * 圓圈直接打勾完成，「＋」快速新增。資料來自 Room，Repository 修改後會呼叫 [refresh]。
 */
class TodoWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    private data class Data(val key: ListKey, val title: String, val open: List<TaskEntity>, val showLogin: Boolean, val today: LocalDate)

    private suspend fun load(widgetId: Int): Data {
        val key = ListKey.parse(Graph.settings.widgetList(widgetId))
        val today = LocalDate.now()
        val lists = Graph.repo.dao.lists()
        val all = Graph.repo.dao.tasks()
        val open = Views.sort(Views.filter(key, all, today).filter { !it.done }, SortMode.DUE).take(40)
        return Data(key, Views.title(key, lists), open, !Graph.api.signedIn && all.isEmpty(), today)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val first = load(widgetId)
        provideContent {
            // 小工具還在執行時，更新只會重畫；用狀態裡的版本號觸發重新讀資料（見 refresh）
            val version = currentState<Preferences>()[VERSION] ?: 0L
            var data by remember { mutableStateOf(first) }
            LaunchedEffect(version) { data = load(widgetId) }
            val (key, title, open, showLogin, today) = data
            GColumn(
                GlanceModifier.gFillMaxSize().gBackground(ColorProvider(C.panel)).cornerRadius(22.dp).gPadding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                GRow(GlanceModifier.gFillMaxWidth(), verticalAlignment = GAlignment.CenterVertically) {
                    GText(
                        "$title ▾",
                        style = TextStyle(color = ColorProvider(C.accent), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.gPadding(vertical = 10.dp, horizontal = 4.dp)
                            .clickable(actionStartActivity(pickerIntent(context, widgetId))),
                    )
                    GText(open.size.toString(), style = TextStyle(color = ColorProvider(C.muted), fontSize = 12.sp))
                    GSpacer(GlanceModifier.defaultWeight())
                    GBox(
                        GlanceModifier.gSize(44.dp).cornerRadius(22.dp).gBackground(ColorProvider(C.surfaceHi))
                            .clickable(actionStartActivity(addIntent(context, key))),
                        contentAlignment = GAlignment.Center,
                    ) {
                        Image(ImageProvider(R.drawable.ic_plus), "新增工作", GlanceModifier.gSize(20.dp))
                    }
                }
                when {
                    showLogin -> Hint("打開待辦事項 APP 登入")
                    open.isEmpty() -> Hint("這裡沒有未完成的待辦")
                    else -> LazyColumn(GlanceModifier.gFillMaxSize()) {
                        items(open, itemId = { it.id.hashCode().toLong() }) { t -> WidgetRow(context, t, today) }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Hint(text: String) {
        GText(text, style = TextStyle(color = ColorProvider(C.muted), fontSize = 14.sp), modifier = GlanceModifier.gPadding(12.dp))
    }

    @androidx.compose.runtime.Composable
    private fun WidgetRow(context: Context, t: TaskEntity, today: LocalDate) {
        GColumn {
            GRow(
                GlanceModifier.gFillMaxWidth().gBackground(ColorProvider(C.surface)).cornerRadius(8.dp).gPadding(horizontal = 8.dp),
                verticalAlignment = GAlignment.CenterVertically,
            ) {
                GBox(
                    GlanceModifier.gSize(44.dp).clickable(actionRunCallback<CompleteAction>(actionParametersOf(CompleteAction.TASK to t.id))),
                    contentAlignment = GAlignment.Center,
                ) {
                    Image(ImageProvider(R.drawable.ic_circle), "完成「${t.title}」", GlanceModifier.gSize(20.dp))
                }
                GText(
                    t.title, maxLines = 1,
                    style = TextStyle(color = ColorProvider(C.text), fontSize = 14.sp),
                    modifier = GlanceModifier.defaultWeight().gPadding(vertical = 12.dp).clickable(actionStartActivity(openIntent(context, t.id))),
                )
                val (label, color) = dueLabel(t, today)
                if (label.isNotEmpty()) GText(label, style = TextStyle(color = ColorProvider(color), fontSize = 11.sp), modifier = GlanceModifier.gPadding(start = 6.dp))
            }
            GSpacer(GlanceModifier.gHeight(4.dp))
        }
    }

    private fun dueLabel(t: TaskEntity, today: LocalDate): Pair<String, androidx.compose.ui.graphics.Color> {
        val d = t.due ?: return "" to C.muted
        return when (DueSection.of(d, today)) {
            DueSection.OVERDUE -> "${d.monthValue}/${d.dayOfMonth}" to C.overdue
            DueSection.TODAY -> "今天" to C.accent
            DueSection.TOMORROW -> "明天" to C.muted
            else -> "${d.monthValue}/${d.dayOfMonth}" to C.muted
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

        private fun openIntent(context: Context, taskId: String) =
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TASK, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(android.net.Uri.parse("todo://task/$taskId"))

        const val EXTRA_WIDGET = "widgetId"
        private val VERSION = longPreferencesKey("version")
    }
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
                var options by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                LaunchedEffect(Unit) {
                    val lists = Graph.repo.dao.lists()
                    options = ListKey.SMART.map { it.wire to Views.title(it, lists) } + lists.map { it.id to it.name }
                }
                val current = Graph.settings.widgetList(widgetId)
                Box(Modifier.fillMaxSize().background(C.scrim).clickable { finish() }, contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.panel)
                            .border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(vertical = 10.dp)
                            .heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    ) {
                        Text("小工具要顯示哪個清單？", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        options.forEach { (key, name) ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                                    Graph.settings.setWidgetList(widgetId, key)
                                    TodoWidget.refresh(applicationContext)
                                    finish()
                                }.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (key == current) C.accent else C.line))
                                Text(name, color = C.text, fontSize = 16.sp)
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
