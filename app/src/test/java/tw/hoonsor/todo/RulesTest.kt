package tw.hoonsor.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.todo.domain.DueSection
import tw.hoonsor.todo.domain.NameMatcher
import tw.hoonsor.todo.domain.Repeat
import tw.hoonsor.todo.domain.Repeat.Freq
import tw.hoonsor.todo.domain.SortKey
import java.time.LocalDate

/** 這些案例也要放進網頁版的測試，兩端結果必須相同（docs/DATA_MODEL.md） */
class SortKeyTest {
    @Test fun specCases() {
        assertEquals("i", SortKey.between(null, null))
        assertEquals("b", SortKey.between(null, "m"))
        assertEquals("t", SortKey.between("m", null))
        assertEquals("p", SortKey.between("m", "t"))
        assertEquals("mi", SortKey.between("m", "n"))
        assertEquals("0i", SortKey.between(null, "1"))
    }

    @Test fun alwaysStrictlyBetween() {
        // 反覆往同一個位置插入，順序必須一直正確
        var lo: String? = null
        var hi: String? = "m"
        repeat(200) {
            val k = SortKey.between(lo, hi)
            if (lo != null) assertTrue("$lo < $k", lo!! < k)
            assertTrue("$k < $hi", k < hi!!)
            hi = k
        }
        lo = "m"; hi = "n"
        repeat(200) {
            val k = SortKey.between(lo, hi)
            assertTrue(lo!! < k && k < hi!!)
            lo = k
        }
    }

    @Test fun neverEndsWithZero() {
        var hi: String? = "1"
        repeat(50) { hi = SortKey.between(null, hi).also { assertTrue(!it.endsWith("0")) } }
    }
}

class RepeatTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun daily() {
        assertEquals(d("2026-10-25"), Repeat(Freq.DAILY).next(d("2026-10-24")))
        assertEquals(d("2026-10-27"), Repeat(Freq.DAILY, 3).next(d("2026-10-24")))
    }

    @Test fun weekdays() {
        assertEquals(d("2026-10-26"), Repeat(Freq.WEEKDAYS).next(d("2026-10-23"))) // 週五 → 週一
        assertEquals(d("2026-10-27"), Repeat(Freq.WEEKDAYS).next(d("2026-10-26")))
    }

    @Test fun weekly() {
        // 2026-10-26 是週一
        assertEquals(d("2026-11-02"), Repeat(Freq.WEEKLY).next(d("2026-10-26")))
        assertEquals(d("2026-10-28"), Repeat(Freq.WEEKLY, 1, setOf(1, 3)).next(d("2026-10-26")))
        assertEquals(d("2026-11-02"), Repeat(Freq.WEEKLY, 1, setOf(1, 3)).next(d("2026-10-28")))
        assertEquals(d("2026-11-09"), Repeat(Freq.WEEKLY, 2, setOf(1, 3)).next(d("2026-10-28")))
    }

    @Test fun monthlyClampsToMonthEnd() {
        assertEquals(d("2027-02-28"), Repeat(Freq.MONTHLY).next(d("2027-01-31")))
        assertEquals(d("2027-03-28"), Repeat(Freq.MONTHLY).next(d("2027-02-28")))
        assertEquals(d("2026-11-24"), Repeat(Freq.MONTHLY).next(d("2026-10-24")))
    }

    @Test fun yearlyLeapDay() {
        assertEquals(d("2029-02-28"), Repeat(Freq.YEARLY).next(d("2028-02-29")))
    }

    @Test fun jsonRoundTrip() {
        val r = Repeat(Freq.WEEKLY, 2, setOf(3, 1))
        assertEquals(r, Repeat.parse(r.toJson()))
        assertNull(Repeat.parse("{\"freq\":\"hourly\"}"))
        assertNull(Repeat.parse("壞掉"))
    }

    @Test fun labels() {
        assertEquals("每月", Repeat(Freq.MONTHLY).label())
        assertEquals("每週 一、三", Repeat(Freq.WEEKLY, 1, setOf(3, 1)).label())
        assertEquals("每 2 天", Repeat(Freq.DAILY, 2).label())
    }
}

class SectionTest {
    @Test fun sections() {
        val today = LocalDate.parse("2026-10-24")
        assertEquals(DueSection.OVERDUE, DueSection.of(LocalDate.parse("2026-10-23"), today))
        assertEquals(DueSection.TODAY, DueSection.of(today, today))
        assertEquals(DueSection.TOMORROW, DueSection.of(LocalDate.parse("2026-10-25"), today))
        assertEquals(DueSection.LATER, DueSection.of(LocalDate.parse("2026-10-26"), today))
        assertEquals(DueSection.NONE, DueSection.of(null, today))
    }
}

class NameMatcherTest {
    private val tasks = listOf("交 10 月人事月報", "核對 11 月薪資異動清冊", "買牛奶", "買牛奶和雞蛋", "研習時數登錄")

    @Test fun exactWins() {
        assertEquals(listOf("買牛奶"), NameMatcher.best("買牛奶", tasks) { it })
    }

    @Test fun partial() {
        assertEquals(listOf("交 10 月人事月報"), NameMatcher.best("人事月報", tasks) { it })
        assertEquals(listOf("研習時數登錄"), NameMatcher.best("研習時數", tasks) { it })
    }

    @Test fun ambiguous() {
        assertEquals(2, NameMatcher.best("牛奶", tasks) { it }.size)
    }

    @Test fun none() {
        assertTrue(NameMatcher.best("繳電費", tasks) { it }.isEmpty())
    }
}
