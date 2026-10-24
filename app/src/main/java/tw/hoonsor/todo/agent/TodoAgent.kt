package tw.hoonsor.todo.agent

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.agent.AgentRequest
import tw.hoonsor.agent.HoonsorAgent
import tw.hoonsor.agent.HoonsorAgentProvider
import tw.hoonsor.todo.BuildConfig
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.data.ListEntity
import tw.hoonsor.todo.data.NewTask
import tw.hoonsor.todo.data.TaskEntity
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.domain.NameMatcher
import tw.hoonsor.todo.domain.Repeat
import tw.hoonsor.todo.domain.SortMode
import tw.hoonsor.todo.domain.Views
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 動作清單（describe）。純資料，JVM 測試會跟 docs/agent-manifest.json 比對。
 * 協定與撰寫規則見語音總控的 docs/AGENT_PROTOCOL.md。
 */
object TodoAgentCatalog {
    const val APP = "待辦事項"
    const val DESCRIPTION = "倬的待辦清單（Microsoft To Do 的替代品）：新增待辦、查詢清單、打勾完成。資料會同步到電腦網頁版。"

    private fun s(desc: String, enum: List<String>? = null) = JSONObject().put("type", "string").put("description", desc)
        .apply { if (enum != null) put("enum", JSONArray(enum)) }
    private fun b(desc: String) = JSONObject().put("type", "boolean").put("description", desc)
    private fun obj(vararg p: Pair<String, JSONObject>, required: List<String> = emptyList()) = JSONObject().put("type", "object")
        .put("properties", JSONObject().apply { p.forEach { (k, v) -> put(k, v) } }).put("required", JSONArray(required))

    val actions: List<JSONObject> = listOf(
        HoonsorAgent.action(
            "list_list", "列出清單", "列出所有待辦清單與各自未完成的件數。", "read", obj(),
            listOf("我有哪些待辦清單", "待辦有幾個清單"),
        ),
        HoonsorAgent.action(
            "todo_list", "查詢待辦", "唸出某個範圍的未完成待辦：我的一天、重要、已排定、全部，或指定清單名稱。", "read",
            obj(
                "scope" to s("範圍；指定清單時用 list", listOf("my_day", "important", "planned", "all", "list")),
                "list" to s("清單名稱（scope＝list 時必填，名稱可不完整）"),
                "includeDone" to b("也列出已完成的，預設 false"),
            ),
            listOf("我今天有哪些待辦", "工作清單還有什麼沒做", "有哪些重要的待辦", "最近有哪些要到期"),
        ),
        HoonsorAgent.action(
            "todo_add", "新增待辦", "新增一筆待辦。沒說清單就放預設清單；提醒時間與到期日依倬說的換算。", "reversible",
            obj(
                "title" to s("待辦內容（1–500 字，保留倬的原話重點）"),
                "list" to s("清單名稱，可省略"),
                "due" to s("到期日 yyyy-MM-dd，可省略"),
                "remindAt" to s("提醒時間 yyyy-MM-ddTHH:mm（當地時間），可省略"),
                "important" to b("是否標為重要"),
                "myDay" to b("是否加入我的一天"),
                "repeat" to s("重複", listOf("daily", "weekdays", "weekly", "monthly", "yearly")),
                "note" to s("備註，可省略"),
                required = listOf("title"),
            ),
            listOf("待辦，明天下午三點前交人事月報", "提醒我週五下午四點買牛奶，放日常清單", "每週一早上九點開晨會，標重要", "加到我的一天：回覆教育部函釋"),
        ),
        HoonsorAgent.action(
            "todo_complete", "完成待辦", "依名稱（可不完整）或 id 把待辦打勾；undone＝true 是取消完成。符合多筆時會請倬選。", "reversible",
            obj(
                "title" to s("待辦名稱，可不完整"),
                "id" to s("待辦 id（查詢結果裡有）"),
                "undone" to b("取消完成，預設 false"),
            ),
            listOf("交人事月報那件做完了", "把買牛奶打勾", "取消剛剛那件的完成"),
        ),
        HoonsorAgent.action(
            "todo_delete", "刪除待辦", "刪除一筆待辦。一律先確認。", "destructive",
            obj("id" to s("待辦 id"), required = listOf("id")),
            emptyList(),
        ),
    )

    fun describe(version: String): JSONObject =
        HoonsorAgent.describe(APP, "tw.hoonsor.todo", version, DESCRIPTION, actions)
}

/** 本 APP 的 Agent API；動作一律走 Repository（先寫本機、背景同步），所以斷網也能用 */
class AgentProvider : HoonsorAgentProvider() {
    override fun describe(): JSONObject = TodoAgentCatalog.describe(BuildConfig.VERSION_NAME)
        .put("package", context?.packageName ?: "tw.hoonsor.todo")

    override fun health(): JSONObject {
        val issues = JSONArray()
        if (!Graph.api.signedIn) issues.put("待辦 APP 還沒登入")
        Graph.settings.lastSyncError?.let { issues.put("上次同步失敗：$it") }
        return JSONObject().put("ok", issues.length() == 0).put("issues", issues)
    }

    override fun execute(request: AgentRequest): JSONObject = runBlocking {
        if (!Graph.api.signedIn) return@runBlocking HoonsorAgent.fail(HoonsorAgent.PERMISSION_DENIED, "待辦 APP 還沒登入，請先打開 APP 登入")
        val p = request.params
        when (request.action) {
            "list_list" -> listLists()
            "todo_list" -> listTasks(p)
            "todo_add" -> add(p, request.dryRun)
            "todo_complete" -> complete(p, request.dryRun)
            "todo_delete" -> delete(p, request)
            else -> HoonsorAgent.fail(HoonsorAgent.NOT_FOUND, "沒有這個動作：${request.action}")
        }
    }

    private val today get() = LocalDate.now()

    private suspend fun lists() = Graph.repo.dao.lists()
    private suspend fun tasks() = Graph.repo.dao.tasks()

    private fun resolveList(name: String, lists: List<ListEntity>): Pair<ListEntity?, JSONObject?> {
        val hit = NameMatcher.best(name, lists) { it.name }
        return when {
            hit.size == 1 -> hit[0] to null
            hit.isEmpty() -> null to HoonsorAgent.fail(HoonsorAgent.NOT_FOUND, "找不到叫「$name」的清單，現在有：${lists.joinToString("、") { it.name }}")
            else -> null to HoonsorAgent.ambiguous("「$name」符合好幾個清單", hit.map { it.name })
        }
    }

    private suspend fun listLists(): JSONObject {
        val ls = lists()
        val all = tasks()
        val arr = JSONArray()
        ls.forEach { l -> arr.put(JSONObject().put("id", l.id).put("name", l.name).put("count", Views.count(ListKey.Custom(l.id), all, today))) }
        return HoonsorAgent.ok("你有 ${ls.size} 個清單：" + ls.joinToString("、") { "${it.name} ${Views.count(ListKey.Custom(it.id), all, today)} 件" }, JSONObject().put("lists", arr))
    }

    private suspend fun listTasks(p: JSONObject): JSONObject {
        val ls = lists()
        val key: ListKey = when (p.optString("scope", "my_day")) {
            "important" -> ListKey.Important
            "planned" -> ListKey.Planned
            "all" -> ListKey.All
            "list" -> {
                val (l, err) = resolveList(p.optString("list"), ls)
                if (err != null) return err
                ListKey.Custom(l!!.id)
            }
            else -> if (p.optString("list").isNotBlank()) {
                val (l, err) = resolveList(p.optString("list"), ls)
                if (err != null) return err
                ListKey.Custom(l!!.id)
            } else ListKey.MyDay
        }
        val includeDone = p.optBoolean("includeDone", false)
        val items = Views.sort(Views.filter(key, tasks(), today).filter { includeDone || !it.done }, SortMode.DUE)
        val name = Views.title(key, ls)
        val arr = JSONArray()
        items.take(50).forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("title", t.title).put("list", ls.firstOrNull { it.id == t.listId }?.name)
                .put("due", t.dueDate ?: JSONObject.NULL).put("important", t.important).put("done", t.done))
        }
        val summary = when {
            items.isEmpty() -> "$name 沒有未完成的待辦"
            items.size <= 5 -> "$name 有 ${items.size} 件：" + items.joinToString("、") { it.title }
            else -> "$name 有 ${items.size} 件，前 5 件是：" + items.take(5).joinToString("、") { it.title }
        }
        return HoonsorAgent.ok(summary, JSONObject().put("tasks", arr).put("total", items.size))
    }

    private suspend fun add(p: JSONObject, dryRun: Boolean): JSONObject {
        val title = p.optString("title").trim()
        if (title.isEmpty() || title.length > 500) return HoonsorAgent.fail(HoonsorAgent.INVALID_PARAMS, "待辦內容要 1 到 500 字")
        val ls = lists()
        val list = if (p.optString("list").isNotBlank()) {
            val (l, err) = resolveList(p.optString("list"), ls)
            if (err != null) return err
            l!!
        } else ls.firstOrNull { it.isDefault } ?: Graph.repo.ensureDefaultList()
        val due = p.optString("due").takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it.take(10)) }.getOrElse { return HoonsorAgent.fail(HoonsorAgent.INVALID_PARAMS, "到期日格式錯誤：$it") }
        }
        val remind = p.optString("remindAt").takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDateTime.parse(it.take(16)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                .getOrElse { _ -> return HoonsorAgent.fail(HoonsorAgent.INVALID_PARAMS, "提醒時間格式錯誤：$it") }
        }
        val repeat = Repeat.simple(p.optString("repeat").takeIf { it.isNotBlank() }, due ?: remind?.let { LocalDateTime.parse(p.optString("remindAt").take(16)).toLocalDate() })
        val parts = listOfNotNull(
            due?.let { "${dayWord(it)}到期" },
            remind?.let { "${LocalDateTime.parse(p.optString("remindAt").take(16)).let { t -> "${dayWord(t.toLocalDate())} ${"%d:%02d".format(t.hour, t.minute)}" }} 提醒" },
            repeat?.label(),
            "重要".takeIf { p.optBoolean("important") },
            "已加入我的一天".takeIf { p.optBoolean("myDay") },
        )
        val summary = "已在${list.name}清單新增「$title」" + if (parts.isEmpty()) "" else "，" + parts.joinToString("，")
        if (dryRun) return HoonsorAgent.ok("（試跑）$summary")
        val t = Graph.repo.addTask(
            NewTask(
                listId = list.id, title = title, due = due, remindAt = remind, important = p.optBoolean("important"),
                myDay = p.optBoolean("myDay"), repeat = repeat, note = p.optString("note"), source = "voice",
            ),
        )
        return HoonsorAgent.ok(summary, JSONObject().put("id", t.id).put("list", list.name), "todo_delete", JSONObject().put("id", t.id))
    }

    private fun dayWord(d: LocalDate): String = when (d) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "後天"
        else -> "${d.monthValue}/${d.dayOfMonth}"
    }

    private suspend fun complete(p: JSONObject, dryRun: Boolean): JSONObject {
        val undone = p.optBoolean("undone", false)
        val all = tasks()
        val target: TaskEntity = p.optString("id").takeIf { it.isNotBlank() }?.let { id -> all.firstOrNull { it.id == id } }
            ?: run {
                val title = p.optString("title").trim()
                if (title.isEmpty()) return HoonsorAgent.fail(HoonsorAgent.INVALID_PARAMS, "要完成哪一件？")
                val pool = all.filter { it.done == undone }
                val hit = NameMatcher.best(title, pool) { it.title }
                when {
                    hit.isEmpty() -> return HoonsorAgent.fail(HoonsorAgent.NOT_FOUND, "找不到「$title」這件${if (undone) "已完成的" else "未完成的"}待辦")
                    hit.size > 1 -> return HoonsorAgent.ambiguous("「$title」符合 ${hit.size} 件待辦", hit.take(5).map { it.title })
                    else -> hit[0]
                }
            }
        if (dryRun) return HoonsorAgent.ok("（試跑）會把「${target.title}」${if (undone) "取消完成" else "打勾"}")
        val r = Graph.repo.setCompleted(target.id, !undone)
        if (undone) return HoonsorAgent.ok("「${target.title}」改回未完成了", JSONObject().put("id", target.id), "todo_complete", JSONObject().put("id", target.id))
        val left = Views.filter(ListKey.Custom(target.listId), tasks(), today).count { !it.done }
        val nextText = r?.next?.dueDate?.let { "，下一次是 ${dayWord(LocalDate.parse(it))}" } ?: "，這個清單還剩 $left 件"
        return HoonsorAgent.ok("「${target.title}」完成了$nextText", JSONObject().put("id", target.id), "todo_complete", JSONObject().put("id", target.id).put("undone", true))
    }

    private suspend fun delete(p: JSONObject, request: AgentRequest): JSONObject {
        val id = p.optString("id")
        val t = tasks().firstOrNull { it.id == id } ?: return HoonsorAgent.fail(HoonsorAgent.NOT_FOUND, "找不到這件待辦")
        val token = "delete-$id"
        if (request.confirmToken != token) return HoonsorAgent.needsConfirmation("要刪除「${t.title}」", token)
        if (request.dryRun) return HoonsorAgent.ok("（試跑）會刪除「${t.title}」")
        Graph.repo.deleteTasks(listOf(id))
        return HoonsorAgent.ok("已刪除「${t.title}」")
    }
}
