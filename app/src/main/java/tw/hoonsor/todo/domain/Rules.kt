package tw.hoonsor.todo.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 手動排序鍵：0-9a-z 的 36 進位小數字串，用字典序比較。
 * 規則見 docs/DATA_MODEL.md「排序鍵」；網頁版必須用同一套演算法。純邏輯，有 JVM 測試。
 */
object SortKey {
    private const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"
    private const val BASE = 36
    const val MIDDLE = "m"

    private fun idx(c: Char): Int = DIGITS.indexOf(c).also { require(it >= 0) { "排序鍵含不合法字元：$c" } }

    /** 產生字典序介於 a 與 b 之間的最短字串；a 為 null＝最前面，b 為 null＝最後面 */
    fun between(a: String?, b: String?): String {
        val lo = a.orEmpty()
        require(b == null || lo < b) { "排序鍵順序錯誤：$a ≥ $b" }
        val out = StringBuilder()
        var bounded = b != null
        var i = 0
        while (true) {
            val dl = if (i < lo.length) idx(lo[i]) else 0
            val dh = if (bounded) {
                check(i < b!!.length) { "排序鍵無法插入：$a 與 $b 之間" }
                idx(b[i])
            } else BASE
            if (dh - dl > 1) {
                out.append(DIGITS[(dl + dh) / 2])
                return out.toString()
            }
            out.append(DIGITS[dl])
            if (dh - dl == 1) bounded = false // 前綴已經小於 b，之後的位數不受上限限制
            i++
        }
    }

    /** 放在最前面（新增待辦預設放最上面） */
    fun first(currentFirst: String?): String = between(null, currentFirst)

    fun last(currentLast: String?): String = between(currentLast, null)
}

/** 重複規則，見 docs/DATA_MODEL.md「重複待辦」 */
data class Repeat(
    val freq: Freq,
    val interval: Int = 1,
    /** 1＝週一…7＝週日，只用於 WEEKLY */
    val weekdays: Set<Int> = emptySet(),
) {
    enum class Freq(val wire: String, val label: String) {
        DAILY("daily", "每天"),
        WEEKDAYS("weekdays", "平日"),
        WEEKLY("weekly", "每週"),
        MONTHLY("monthly", "每月"),
        YEARLY("yearly", "每年");

        companion object {
            fun of(s: String?): Freq? = entries.firstOrNull { it.wire == s }
        }
    }

    fun next(date: LocalDate): LocalDate {
        val n = interval.coerceAtLeast(1).toLong()
        return when (freq) {
            Freq.DAILY -> date.plusDays(n)
            Freq.WEEKDAYS -> {
                var d = date.plusDays(1)
                while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
                d
            }
            Freq.WEEKLY -> {
                val days = weekdays.filter { it in 1..7 }.toSortedSet().ifEmpty { sortedSetOf(date.dayOfWeek.value) }
                val dow = date.dayOfWeek.value
                days.firstOrNull { it > dow }?.let { return date.plusDays((it - dow).toLong()) }
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(n)
                monday.plusDays((days.first() - 1).toLong())
            }
            Freq.MONTHLY -> date.plusMonths(n)   // 超過月底會停在月底（1/31 → 2/28）
            Freq.YEARLY -> date.plusYears(n)     // 2/29 → 2/28
        }
    }

    fun label(): String = when {
        freq == Freq.WEEKLY && weekdays.isNotEmpty() ->
            (if (interval > 1) "每 $interval 週" else "每週") + weekdays.sorted().joinToString("、", " ") { WEEKDAY_NAMES[it - 1] }
        interval > 1 -> when (freq) {
            Freq.DAILY -> "每 $interval 天"
            Freq.WEEKLY -> "每 $interval 週"
            Freq.MONTHLY -> "每 $interval 個月"
            Freq.YEARLY -> "每 $interval 年"
            Freq.WEEKDAYS -> freq.label
        }
        else -> freq.label
    }

    fun toJson(): String = JSONObject().put("freq", freq.wire).put("interval", interval)
        .apply { if (weekdays.isNotEmpty()) put("weekdays", JSONArray(weekdays.sorted())) }
        .toString()

    companion object {
        val WEEKDAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

        fun parse(json: String?): Repeat? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                val f = Freq.of(o.optString("freq")) ?: return null
                val w = o.optJSONArray("weekdays")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
                Repeat(f, o.optInt("interval", 1).coerceAtLeast(1), w)
            }.getOrNull()
        }

        /** 語音動作用的簡單寫法：daily／weekdays／weekly／monthly／yearly */
        fun simple(word: String?, base: LocalDate?): Repeat? {
            val f = Freq.of(word?.trim()?.lowercase()) ?: return null
            val w = if (f == Freq.WEEKLY && base != null) setOf(base.dayOfWeek.value) else emptySet()
            return Repeat(f, 1, w)
        }
    }
}

/** 清單主畫面的到期分段，見 docs/DATA_MODEL.md「到期分段」 */
enum class DueSection(val label: String) {
    OVERDUE("已逾期"),
    TODAY("今天"),
    TOMORROW("明天"),
    LATER("之後"),
    NONE("沒有到期日");

    companion object {
        fun of(due: LocalDate?, today: LocalDate): DueSection = when {
            due == null -> NONE
            due < today -> OVERDUE
            due == today -> TODAY
            due == today.plusDays(1) -> TOMORROW
            else -> LATER
        }
    }
}

/** 用名稱找待辦或清單（語音「交月報那件做完了」）：完全相同 > 開頭 > 包含 > 字元重疊 */
object NameMatcher {
    fun normalize(s: String): String = s.lowercase().replace(Regex("[\\s，。、,.!！?？「」『』（）()]+"), "")

    fun score(query: String, name: String): Int {
        val q = normalize(query)
        val n = normalize(name)
        if (q.isEmpty() || n.isEmpty()) return 0
        return when {
            q == n -> 100
            n.startsWith(q) -> 85
            n.contains(q) -> 75
            q.contains(n) && n.length >= 2 -> 60
            else -> {
                val overlap = q.toSet().intersect(n.toSet()).size
                val ratio = overlap.toDouble() / q.toSet().size
                if (ratio >= 0.6 && overlap >= 2) (ratio * 55).toInt() else 0
            }
        }
    }

    /** 回傳最高分的那些（分數 > 0）；同分多筆＝不確定，由呼叫端請倬選 */
    fun <T> best(query: String, items: List<T>, name: (T) -> String): List<T> {
        val scored = items.map { it to score(query, name(it)) }.filter { it.second > 0 }
        val top = scored.maxOfOrNull { it.second } ?: return emptyList()
        return scored.filter { it.second == top }.map { it.first }
    }
}
