package tw.hoonsor.todo.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.hoonsor.todo.domain.Repeat
import tw.hoonsor.todo.domain.SortKey
import tw.hoonsor.todo.sync.Mapping
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** 新增待辦的參數（APP、小工具、語音都用這個） */
data class NewTask(
    val listId: String,
    val title: String,
    val due: LocalDate? = null,
    val remindAt: Long? = null,
    val important: Boolean = false,
    val myDay: Boolean = false,
    val repeat: Repeat? = null,
    val note: String = "",
    val source: String = "app",
)

/** 完成一筆待辦的結果 */
data class CompleteResult(val task: TaskEntity, val next: TaskEntity?)

/**
 * 唯一的寫入入口：先寫 Room，再把修改放進 outbox，最後呼叫 [onChanged]（排同步、更新小工具、重排提醒）。
 * 規則見 docs/DATA_MODEL.md。
 */
class Repository(
    private val db: TodoDb,
    private val onChanged: suspend (affectedTaskIds: Collection<String>) -> Unit,
) {
    val dao = db.dao()

    private fun now() = System.currentTimeMillis()
    private fun today() = LocalDate.now()
    private fun uuid() = UUID.randomUUID().toString()

    private suspend fun <T> write(affected: () -> Collection<String> = { emptyList() }, block: suspend () -> T): T {
        val r = withContext(Dispatchers.IO) { db.withTransaction { block() } }
        onChanged(affected())
        return r
    }

    private suspend fun queueInsert(table: String, id: String, row: org.json.JSONObject) =
        dao.enqueue(OutboxEntity(tableName = table, rowId = id, op = "insert", payload = row.toString()))

    private suspend fun queueUpdate(table: String, id: String, patch: org.json.JSONObject) {
        if (patch.length() > 0) dao.enqueue(OutboxEntity(tableName = table, rowId = id, op = "update", payload = patch.toString()))
    }

    // ================= 清單 =================

    /** 沒有預設清單就建立「工作」（同步完才呼叫，避免兩端各建一個） */
    suspend fun ensureDefaultList(): ListEntity = dao.lists().firstOrNull { it.isDefault } ?: write {
        val e = ListEntity(id = uuid(), name = "工作", color = 0, isDefault = true, sortKey = SortKey.MIDDLE, createdAt = now())
        dao.upsertList(e)
        queueInsert(Mapping.LISTS, e.id, Mapping.listRow(e))
        e
    }

    suspend fun addList(name: String, color: Int): ListEntity = write {
        val last = dao.lists().filterNot { it.isDefault }.maxOfOrNull { it.sortKey }
        val e = ListEntity(id = uuid(), name = name.trim().take(100), color = color, sortKey = SortKey.last(last), createdAt = now())
        dao.upsertList(e)
        queueInsert(Mapping.LISTS, e.id, Mapping.listRow(e))
        e
    }

    suspend fun updateList(id: String, change: (ListEntity) -> ListEntity) = write {
        val old = dao.list(id) ?: return@write
        val new = change(old)
        dao.upsertList(new)
        queueUpdate(Mapping.LISTS, id, Mapping.listDiff(old, new))
    }

    /** 刪除清單（預設清單不能刪）；清單裡的待辦一併軟刪除，回傳被刪的待辦 id（給「復原」用） */
    suspend fun deleteList(id: String): List<String> {
        val ids = dao.tasks().filter { it.listId == id }.map { it.id }
        write({ ids }) {
            val old = dao.list(id) ?: return@write
            if (old.isDefault) return@write
            val t = now()
            dao.upsertList(old.copy(deletedAt = t))
            queueUpdate(Mapping.LISTS, id, Mapping.listDiff(old, old.copy(deletedAt = t)))
            ids.forEach { softDeleteTask(it, t) }
        }
        return ids
    }

    suspend fun restoreList(id: String, taskIds: List<String>) = write({ taskIds }) {
        val old = dao.list(id) ?: return@write
        dao.upsertList(old.copy(deletedAt = null))
        queueUpdate(Mapping.LISTS, id, Mapping.listDiff(old, old.copy(deletedAt = null)))
        taskIds.forEach { restoreTaskRow(it) }
    }

    // ================= 待辦 =================

    suspend fun addTask(n: NewTask): TaskEntity {
        var created: TaskEntity? = null
        write({ listOfNotNull(created?.id) }) {
            val first = dao.tasks().filter { it.listId == n.listId }.minOfOrNull { it.sortKey }
            val e = TaskEntity(
                id = uuid(), listId = n.listId, title = n.title.trim().take(500), note = n.note,
                important = n.important, dueDate = n.due?.toString(), remindAt = n.remindAt,
                repeat = n.repeat?.toJson(), myDayDate = if (n.myDay) today().toString() else null,
                sortKey = SortKey.first(first), source = n.source, createdAt = now(),
            )
            dao.upsertTask(e)
            queueInsert(Mapping.TASKS, e.id, Mapping.taskRow(e))
            created = e
        }
        return created!!
    }

    /** 改一筆待辦的任何欄位（只送有改的欄位） */
    suspend fun updateTask(id: String, change: (TaskEntity) -> TaskEntity) = write({ listOf(id) }) {
        val old = dao.task(id) ?: return@write
        val new = change(old)
        if (new == old) return@write
        dao.upsertTask(new)
        queueUpdate(Mapping.TASKS, id, Mapping.taskDiff(old, new))
    }

    suspend fun setImportant(id: String, on: Boolean) = updateTask(id) { it.copy(important = on) }
    suspend fun setMyDay(ids: Collection<String>, on: Boolean) = ids.forEach { id ->
        updateTask(id) { it.copy(myDayDate = if (on) today().toString() else null) }
    }
    suspend fun moveTasks(ids: Collection<String>, listId: String) = ids.forEach { id -> updateTask(id) { it.copy(listId = listId) } }

    /**
     * 打勾／取消完成。完成重複待辦時建立下一筆（DATA_MODEL「重複待辦」）。
     * 取消完成不會刪除已產生的下一筆（同 To Do）。
     */
    suspend fun setCompleted(id: String, done: Boolean): CompleteResult? {
        var result: CompleteResult? = null
        write({ listOfNotNull(id, result?.next?.id) }) {
            val old = dao.task(id) ?: return@write
            if (old.done == done) {
                result = CompleteResult(old, null)
                return@write
            }
            val new = old.copy(completedAt = if (done) now() else null)
            dao.upsertTask(new)
            queueUpdate(Mapping.TASKS, id, Mapping.taskDiff(old, new))
            val next = if (done) createNextRepeat(new) else null
            result = CompleteResult(new, next)
        }
        return result
    }

    private suspend fun createNextRepeat(t: TaskEntity): TaskEntity? {
        val rule = Repeat.parse(t.repeat) ?: return null
        val root = t.repeatRoot ?: t.id
        val seq = (t.repeatSeq ?: 0) + 1
        dao.repeatInstance(root, seq)?.let { return null } // 已經產生過（例如取消完成後又完成）
        val zone = ZoneId.systemDefault()
        val remindDate = t.remindAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val base = t.due ?: remindDate ?: today()
        val nextDue = rule.next(base)
        val nextRemind = t.remindAt?.let {
            val time = Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
            nextDue.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        val e = TaskEntity(
            id = uuid(), listId = t.listId, title = t.title, note = t.note, important = t.important,
            dueDate = if (t.dueDate != null || t.remindAt == null) nextDue.toString() else null,
            remindAt = nextRemind, repeat = t.repeat, repeatRoot = root, repeatSeq = seq,
            sortKey = t.sortKey, source = t.source, createdAt = now(),
        )
        dao.upsertTask(e)
        queueInsert(Mapping.TASKS, e.id, Mapping.taskRow(e))
        dao.steps(t.id).forEach { s ->
            val ns = StepEntity(id = uuid(), taskId = e.id, title = s.title, done = false, sortKey = s.sortKey, createdAt = now())
            dao.upsertStep(ns)
            queueInsert(Mapping.STEPS, ns.id, Mapping.stepRow(ns))
        }
        return e
    }

    private suspend fun softDeleteTask(id: String, t: Long) {
        val old = dao.task(id) ?: return
        if (old.deletedAt != null) return
        dao.upsertTask(old.copy(deletedAt = t))
        queueUpdate(Mapping.TASKS, id, Mapping.taskDiff(old, old.copy(deletedAt = t)))
    }

    private suspend fun restoreTaskRow(id: String) {
        val old = dao.task(id) ?: return
        dao.upsertTask(old.copy(deletedAt = null))
        queueUpdate(Mapping.TASKS, id, Mapping.taskDiff(old, old.copy(deletedAt = null)))
    }

    /** 刪除（軟刪除，畫面顯示 10 秒「復原」） */
    suspend fun deleteTasks(ids: Collection<String>) = write({ ids }) { val t = now(); ids.forEach { softDeleteTask(it, t) } }

    suspend fun restoreTasks(ids: Collection<String>) = write({ ids }) { ids.forEach { restoreTaskRow(it) } }

    /** 拖曳排序：放在 before 與 after 之間 */
    suspend fun reorderTask(id: String, before: String?, after: String?) =
        updateTask(id) { it.copy(sortKey = SortKey.between(before, after)) }

    // ================= 步驟 =================

    suspend fun addStep(taskId: String, title: String) = write({ listOf(taskId) }) {
        val last = dao.steps(taskId).maxOfOrNull { it.sortKey }
        val e = StepEntity(id = uuid(), taskId = taskId, title = title.trim().take(300), sortKey = SortKey.last(last), createdAt = now())
        dao.upsertStep(e)
        queueInsert(Mapping.STEPS, e.id, Mapping.stepRow(e))
    }

    suspend fun updateStep(id: String, change: (StepEntity) -> StepEntity) = write {
        val old = dao.step(id) ?: return@write
        val new = change(old)
        dao.upsertStep(new)
        queueUpdate(Mapping.STEPS, id, Mapping.stepDiff(old, new))
    }

    suspend fun deleteStep(id: String) = updateStep(id) { it.copy(deletedAt = now()) }

    // ================= 登出 =================

    suspend fun wipeLocal() = withContext(Dispatchers.IO) {
        db.withTransaction { dao.clearOutbox(); dao.clearSteps(); dao.clearTasks(); dao.clearLists() }
    }
}
