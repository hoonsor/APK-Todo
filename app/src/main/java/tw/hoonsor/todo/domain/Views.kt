package tw.hoonsor.todo.domain

import tw.hoonsor.todo.data.ListEntity
import tw.hoonsor.todo.data.TaskEntity
import java.time.LocalDate

/** 清單畫面要顯示的範圍：智慧清單或自訂清單 */
sealed interface ListKey {
    val wire: String

    data object MyDay : ListKey { override val wire = "myday" }
    data object Important : ListKey { override val wire = "important" }
    data object Planned : ListKey { override val wire = "planned" }
    data object All : ListKey { override val wire = "all" }
    data class Custom(val id: String) : ListKey { override val wire = id }

    companion object {
        val SMART = listOf(MyDay, Important, Planned, All)
        fun parse(s: String?): ListKey = when (s) {
            null, "", "myday" -> MyDay
            "important" -> Important
            "planned" -> Planned
            "all" -> All
            else -> Custom(s)
        }
    }
}

/** 排序方式（每個清單各自記住） */
enum class SortMode(val wire: String, val label: String) {
    DUE("due", "依到期（分段）"),
    MANUAL("manual", "手動排序"),
    IMPORTANT("important", "依重要"),
    CREATED("created", "依建立時間"),
    ALPHA("alpha", "依字母");

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.wire == s } ?: DUE
    }
}

data class Section(val section: DueSection?, val title: String, val tasks: List<TaskEntity>)

/**
 * 畫面上的待辦清單怎麼篩、怎麼排、怎麼分段。純邏輯，有 JVM 測試（ViewsTest）。
 */
object Views {
    fun title(key: ListKey, lists: List<ListEntity>): String = when (key) {
        ListKey.MyDay -> "我的一天"
        ListKey.Important -> "重要"
        ListKey.Planned -> "已排定"
        ListKey.All -> "全部"
        is ListKey.Custom -> lists.firstOrNull { it.id == key.id }?.name ?: "清單"
    }

    fun filter(key: ListKey, tasks: List<TaskEntity>, today: LocalDate): List<TaskEntity> = tasks.filter { t ->
        t.deletedAt == null && when (key) {
            ListKey.MyDay -> t.inMyDay(today)
            ListKey.Important -> t.important
            ListKey.Planned -> t.dueDate != null || t.remindAt != null
            ListKey.All -> true
            is ListKey.Custom -> t.listId == key.id
        }
    }

    private val byDue = compareBy<TaskEntity, String?>(nullsLast()) { it.dueDate }

    fun sort(tasks: List<TaskEntity>, mode: SortMode): List<TaskEntity> = when (mode) {
        SortMode.DUE -> tasks.sortedWith(byDue.thenByDescending { it.important }.thenBy { it.sortKey })
        SortMode.MANUAL -> tasks.sortedWith(compareBy<TaskEntity> { it.sortKey }.thenByDescending { it.createdAt })
        SortMode.IMPORTANT -> tasks.sortedWith(compareByDescending<TaskEntity> { it.important }.then(byDue).thenBy { it.sortKey })
        SortMode.CREATED -> tasks.sortedByDescending { it.createdAt }
        SortMode.ALPHA -> tasks.sortedBy { it.title }
    }

    /** 未完成的待辦：依到期分段（排序方式＝DUE），否則一個不分段的區塊 */
    fun sections(open: List<TaskEntity>, mode: SortMode, today: LocalDate): List<Section> {
        val sorted = sort(open, mode)
        if (mode != SortMode.DUE) return listOf(Section(null, "", sorted))
        return DueSection.entries.mapNotNull { s ->
            sorted.filter { DueSection.of(it.due, today) == s }.takeIf { it.isNotEmpty() }?.let { Section(s, s.label, it) }
        }
    }

    /** 我的一天的「建議」：未完成，且已逾期、今天到期，或昨天在我的一天但沒做完 */
    fun suggestions(tasks: List<TaskEntity>, today: LocalDate): List<TaskEntity> = tasks.filter { t ->
        t.deletedAt == null && !t.done && !t.inMyDay(today) &&
            (t.due?.let { it <= today } == true || t.myDayDate == today.minusDays(1).toString())
    }.sortedWith(compareBy<TaskEntity, String?>(nullsLast()) { it.dueDate })

    /** 搜尋標題、備註；步驟由呼叫端另外比對 */
    fun search(query: String, tasks: List<TaskEntity>, stepTitlesByTask: Map<String, List<String>>): List<TaskEntity> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return tasks.filter { t ->
            t.deletedAt == null && (t.title.contains(q, true) || t.note.contains(q, true) ||
                stepTitlesByTask[t.id].orEmpty().any { it.contains(q, true) })
        }.sortedWith(compareBy<TaskEntity> { it.done }.thenByDescending { it.createdAt })
    }

    /** 清單總覽的件數（未完成） */
    fun count(key: ListKey, tasks: List<TaskEntity>, today: LocalDate): Int = filter(key, tasks, today).count { !it.done }
}
