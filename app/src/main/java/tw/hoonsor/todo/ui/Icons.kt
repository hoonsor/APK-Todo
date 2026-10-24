package tw.hoonsor.todo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 手繪線條圖示（24×24、1.8 線寬、圓角端點），與設計稿相同的路徑 */
object Glyph {
    private fun line(name: String, vararg paths: String, fill: String? = null): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            fill?.let { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.White)) }
            paths.forEach { d ->
                addPath(
                    PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val STAR = "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.8z"
    val Back = line("back", "M15 5l-7 7 7 7")
    val Sort = line("sort", "M7 4v16M4 17l3 3 3-3M17 20V4M14 7l3-3 3 3")
    val More = line("more", fill = "M5 10.2a1.8 1.8 0 1 1 0 3.6 1.8 1.8 0 0 1 0 -3.6zM12 10.2a1.8 1.8 0 1 1 0 3.6 1.8 1.8 0 0 1 0 -3.6zM19 10.2a1.8 1.8 0 1 1 0 3.6 1.8 1.8 0 0 1 0 -3.6z")
    val Plus = line("plus", "M12 5v14M5 12h14")
    val Star = line("star", STAR)
    val StarFilled = line("starFilled", STAR, fill = STAR)
    val Chevron = line("chevron", "M8 10l4 4 4-4")
    val ChevronRight = line("chevronRight", "M10 8l4 4-4 4")
    val Search = line("search", "M11 4.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0 -13zM16 16l4 4")
    val Sun = line("sun", "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0 -8z")
    val Bell = line("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0")
    val Calendar = line("calendar", "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4")
    val Repeat = line("repeat", "M4 12a8 8 0 0 1 14 -5l2 2M20 12a8 8 0 0 1 -14 5l-2-2M18 4v5h-5M6 20v-5h5")
    val Move = line("move", "M4 7h11M4 12h8M4 17h11M17 10l3 3-3 3")
    val Trash = line("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    val Check = line("check", "M5 12.5l4.5 4.5 9.5-10")
    val Close = line("close", "M6.5 6.5l11 11M17.5 6.5l-11 11")
    val Lines = line("lines", "M5 7h14M5 12h14M5 17h14")
    val Gear = line("gear", "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0 -6zM12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7")
    val Note = line("note", "M6 4h9l3 3v13H6zM9 11h6M9 15h6")
    val Refresh = line("refresh", "M19.5 8.5a8 8 0 1 0 0.5 6", "M20 3.5v5h-5")
}
