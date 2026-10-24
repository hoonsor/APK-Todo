package tw.hoonsor.todo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.todo.data.TaskEntity
import tw.hoonsor.todo.domain.DueSection
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.domain.SortMode
import tw.hoonsor.todo.domain.Views
import tw.hoonsor.todo.sync.Mapping
import java.time.LocalDate

class ViewsTest {
    private val today = LocalDate.parse("2026-10-24")
    private var n = 0L
    private fun t(title: String, due: String? = null, important: Boolean = false, myDay: String? = null, list: String = "L1", done: Boolean = false, sortKey: String = "m") =
        TaskEntity(id = title, listId = list, title = title, dueDate = due, important = important, myDayDate = myDay,
            completedAt = if (done) 1L else null, sortKey = sortKey, createdAt = ++n)

    private val tasks = listOf(
        t("逾期", "2026-10-22"),
        t("今天重要", "2026-10-24", important = true),
        t("今天", "2026-10-24", myDay = "2026-10-24"),
        t("明天", "2026-10-25", list = "L2"),
        t("之後", "2026-11-01"),
        t("沒日期"),
        t("昨天我的一天", myDay = "2026-10-23"),
        t("已完成", "2026-10-20", done = true),
    )

    @Test fun smartLists() {
        assertEquals(listOf("今天"), Views.filter(ListKey.MyDay, tasks, today).map { it.title })
        assertEquals(listOf("今天重要"), Views.filter(ListKey.Important, tasks, today).map { it.title })
        assertEquals(6, Views.filter(ListKey.Planned, tasks, today).size)
        assertEquals(listOf("明天"), Views.filter(ListKey.Custom("L2"), tasks, today).map { it.title })
        assertEquals(6, Views.count(ListKey.Custom("L1"), tasks, today))
    }

    @Test fun dueSections() {
        val open = tasks.filter { !it.done }
        val s = Views.sections(open, SortMode.DUE, today)
        assertEquals(listOf(DueSection.OVERDUE, DueSection.TODAY, DueSection.TOMORROW, DueSection.LATER, DueSection.NONE), s.map { it.section })
        assertEquals(listOf("今天重要", "今天"), s[1].tasks.map { it.title }) // 同一天重要的排前面
        assertEquals(1, Views.sections(open, SortMode.MANUAL, today).size)
    }

    @Test fun suggestions() {
        val titles = Views.suggestions(tasks, today).map { it.title }
        assertEquals(listOf("逾期", "今天重要", "昨天我的一天"), titles)
    }

    @Test fun search() {
        val r = Views.search("今天", tasks, mapOf("沒日期" to listOf("問今天的會議室")))
        assertEquals(setOf("今天重要", "今天", "沒日期"), r.map { it.title }.toSet())
        assertTrue(Views.search("  ", tasks, emptyMap()).isEmpty())
    }

    @Test fun listKeyParse() {
        assertEquals(ListKey.MyDay, ListKey.parse(null))
        assertEquals(ListKey.Custom("abc"), ListKey.parse("abc"))
        assertEquals("planned", ListKey.parse("planned").wire)
    }
}

class MappingTest {
    private val base = TaskEntity(id = "a", listId = "L", title = "交月報", sortKey = "m", createdAt = 1_700_000_000_000)

    @Test fun diffOnlyChangedFields() {
        val patch = Mapping.taskDiff(base, base.copy(important = true, dueDate = "2026-10-25"))
        assertEquals(setOf("important", "due_date"), patch.keys().asSequence().toSet())
        assertEquals("2026-10-25", patch.getString("due_date"))
    }

    @Test fun clearingFieldSendsNull() {
        val patch = Mapping.taskDiff(base.copy(dueDate = "2026-10-25"), base)
        assertTrue(patch.isNull("due_date"))
    }

    @Test fun roundTripFromServer() {
        val row = Mapping.taskRow(base.copy(repeat = "{\"freq\":\"monthly\",\"interval\":1}", remindAt = 1_700_000_100_000))
            .put("updated_at", "2026-10-24T03:51:37.598123+00:00")
        val back = Mapping.taskFrom(JSONObject(row.toString()))
        assertEquals(base.title, back.title)
        assertEquals(1_700_000_100_000, back.remindAt)
        assertEquals("monthly", JSONObject(back.repeat!!).getString("freq"))
        assertEquals(base.createdAt, back.createdAt)
    }

    @Test fun parsesServerTimestamps() {
        assertEquals(java.time.Instant.parse("2025-10-24T03:51:37.598Z").toEpochMilli(), Mapping.ms("2025-10-24T03:51:37.598123+00:00"))
        assertEquals(java.time.Instant.parse("2025-10-23T19:51:37Z").toEpochMilli(), Mapping.ms("2025-10-24T03:51:37+08:00"))
        assertEquals("2026-10-24T03:51:38+00:00", Mapping.maxTime("2026-10-24T03:51:37.9+00:00", "2026-10-24T03:51:38+00:00"))
        assertFalse(Mapping.ms(null) != null)
    }
}
