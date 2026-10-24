package tw.hoonsor.todo.sync

import org.json.JSONObject
import tw.hoonsor.todo.data.ListEntity
import tw.hoonsor.todo.data.StepEntity
import tw.hoonsor.todo.data.TaskEntity
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Room 實體 ↔ Supabase 列（snake_case）的轉換。純邏輯，有 JVM 測試（MappingTest）。
 * 欄位名稱必須與 supabase/migrations 一致。
 */
object Mapping {
    const val LISTS = "todo_lists"
    const val TASKS = "todo_tasks"
    const val STEPS = "todo_steps"
    val TABLES = listOf(LISTS, TASKS, STEPS)

    fun iso(ms: Long?): Any = ms?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL
    fun ms(s: String?): Long? = s?.takeIf { it.isNotBlank() && it != "null" }?.let { v ->
        runCatching { OffsetDateTime.parse(v).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(v).toEpochMilli() }.getOrNull()
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)
    private fun nul(v: Any?): Any = v ?: JSONObject.NULL

    // ---------- 整筆（insert） ----------
    fun listRow(e: ListEntity) = JSONObject()
        .put("id", e.id).put("name", e.name).put("color", e.color).put("icon", nul(e.icon))
        .put("is_default", e.isDefault).put("sort_key", e.sortKey)
        .put("created_at", iso(e.createdAt)).put("deleted_at", iso(e.deletedAt))

    fun taskRow(e: TaskEntity) = JSONObject()
        .put("id", e.id).put("list_id", e.listId).put("title", e.title).put("note", e.note)
        .put("important", e.important).put("completed_at", iso(e.completedAt))
        .put("due_date", nul(e.dueDate)).put("remind_at", iso(e.remindAt))
        .put("repeat", e.repeat?.let { JSONObject(it) } ?: JSONObject.NULL)
        .put("repeat_root", nul(e.repeatRoot)).put("repeat_seq", nul(e.repeatSeq))
        .put("my_day_date", nul(e.myDayDate)).put("sort_key", e.sortKey).put("source", e.source)
        .put("created_at", iso(e.createdAt)).put("deleted_at", iso(e.deletedAt))

    fun stepRow(e: StepEntity) = JSONObject()
        .put("id", e.id).put("task_id", e.taskId).put("title", e.title).put("done", e.done)
        .put("sort_key", e.sortKey).put("created_at", iso(e.createdAt)).put("deleted_at", iso(e.deletedAt))

    // ---------- 只送有改的欄位（update） ----------
    fun taskDiff(old: TaskEntity, new: TaskEntity): JSONObject {
        val all = taskRow(new)
        val before = taskRow(old)
        return diff(before, all, setOf("id", "created_at"))
    }

    fun listDiff(old: ListEntity, new: ListEntity) = diff(listRow(old), listRow(new), setOf("id", "created_at"))

    fun stepDiff(old: StepEntity, new: StepEntity) = diff(stepRow(old), stepRow(new), setOf("id", "created_at"))

    private fun diff(a: JSONObject, b: JSONObject, skip: Set<String>): JSONObject {
        val out = JSONObject()
        b.keys().forEach { k ->
            if (k in skip) return@forEach
            val va = a.opt(k)
            val vb = b.opt(k)
            if (va.toString() != vb.toString()) out.put(k, vb)
        }
        return out
    }

    // ---------- 伺服器 → 本機 ----------
    fun listFrom(j: JSONObject) = ListEntity(
        id = j.getString("id"), name = j.optString("name"), color = j.optInt("color"),
        icon = j.str("icon"), isDefault = j.optBoolean("is_default"), sortKey = j.optString("sort_key", "m"),
        createdAt = ms(j.str("created_at")) ?: 0L, deletedAt = ms(j.str("deleted_at")),
        serverUpdatedAt = j.str("updated_at"),
    )

    fun taskFrom(j: JSONObject) = TaskEntity(
        id = j.getString("id"), listId = j.getString("list_id"), title = j.optString("title"),
        note = j.optString("note", ""), important = j.optBoolean("important"),
        completedAt = ms(j.str("completed_at")), dueDate = j.str("due_date"), remindAt = ms(j.str("remind_at")),
        repeat = if (j.isNull("repeat")) null else j.opt("repeat")?.toString(),
        repeatRoot = j.str("repeat_root"), repeatSeq = if (j.isNull("repeat_seq")) null else j.optInt("repeat_seq"),
        myDayDate = j.str("my_day_date"), sortKey = j.optString("sort_key", "m"), source = j.optString("source", "app"),
        createdAt = ms(j.str("created_at")) ?: 0L, deletedAt = ms(j.str("deleted_at")),
        serverUpdatedAt = j.str("updated_at"),
    )

    fun stepFrom(j: JSONObject) = StepEntity(
        id = j.getString("id"), taskId = j.getString("task_id"), title = j.optString("title"),
        done = j.optBoolean("done"), sortKey = j.optString("sort_key", "m"),
        createdAt = ms(j.str("created_at")) ?: 0L, deletedAt = ms(j.str("deleted_at")),
        serverUpdatedAt = j.str("updated_at"),
    )

    /** 比較兩個伺服器時間字串，回傳較新的那個 */
    fun maxTime(a: String?, b: String?): String? = when {
        a == null -> b
        b == null -> a
        (ms(b) ?: 0) > (ms(a) ?: 0) -> b
        else -> a
    }
}
