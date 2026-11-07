package tw.hoonsor.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.hoonsor.todo.data.ListEntity
import tw.hoonsor.todo.data.TaskEntity
import tw.hoonsor.todo.domain.ListKey
import tw.hoonsor.todo.ui.dayLabel
import tw.hoonsor.todo.ui.listColor
import tw.hoonsor.todo.ui.theme.C
import tw.hoonsor.todo.widget.widgetDueLabel
import java.time.LocalDate

/** 小工具的到期日文字、顏色，以及標題用的清單色 */
class WidgetTest {
    private val today = LocalDate.parse("2026-10-24") // 週六
    private val listGreen = C.listTitle(1)
    private fun t(due: String?) = TaskEntity(id = "x", listId = "L1", title = "x", dueDate = due, sortKey = "m", createdAt = 1)

    @Test fun 沒有到期日不顯示() = assertNull(widgetDueLabel(t(null), today, listGreen))

    @Test fun 今天用清單色() = assertEquals("今天" to listGreen, widgetDueLabel(t("2026-10-24"), today, listGreen))

    @Test fun 明天灰色() = assertEquals("明天" to C.muted, widgetDueLabel(t("2026-10-25"), today, listGreen))

    @Test fun 昨天逾期紅() = assertEquals("昨天" to C.overdue, widgetDueLabel(t("2026-10-23"), today, listGreen))

    @Test fun 更早逾期紅色日期() = assertEquals("10/20" to C.overdue, widgetDueLabel(t("2026-10-20"), today, listGreen))

    @Test fun 之後灰色日期不含星期() = assertEquals("10/30" to C.muted, widgetDueLabel(t("2026-10-30"), today, listGreen))

    @Test fun APP的日期仍含星期() = assertEquals("10/30（五）", dayLabel(LocalDate.parse("2026-10-30"), today))

    @Test fun 標題用清單在APP設定的顏色() {
        val lists = listOf(ListEntity(id = "L1", name = "工作", color = 3, sortKey = "m", createdAt = 1))
        assertEquals(C.listTitle(3), listColor(ListKey.Custom("L1"), lists))
        assertEquals(C.myDay, listColor(ListKey.MyDay, lists))
        assertEquals(C.important, listColor(ListKey.Important, lists))
        assertEquals(C.planned, listColor(ListKey.Planned, lists))
        assertEquals(C.accent, listColor(ListKey.All, lists))
    }
}
