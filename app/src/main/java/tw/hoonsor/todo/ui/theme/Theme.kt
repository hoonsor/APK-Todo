package tw.hoonsor.todo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 唯一色票（docs/DESIGN.md「色票」，方案 A）。全部 HSL，不用原色 */
object C {
    val bg = Color.hsl(222f, 0.22f, 0.09f)
    val surface = Color.hsl(222f, 0.18f, 0.14f)
    val surfaceHi = Color.hsl(222f, 0.18f, 0.16f)
    val panel = Color.hsl(222f, 0.20f, 0.11f)
    val line = Color.hsl(222f, 0.16f, 0.19f)
    val lineHi = Color.hsl(222f, 0.16f, 0.25f)
    val text = Color.hsl(220f, 0.25f, 0.94f)
    val textSoft = Color.hsl(220f, 0.18f, 0.82f)
    val muted = Color.hsl(220f, 0.12f, 0.66f)
    val ring = Color.hsl(220f, 0.12f, 0.58f)
    val accent = Color.hsl(214f, 0.80f, 0.70f)
    val onAccent = Color.hsl(222f, 0.40f, 0.10f)
    val star = Color.hsl(40f, 0.90f, 0.62f)
    val overdue = Color.hsl(8f, 0.78f, 0.70f)
    val ok = Color.hsl(150f, 0.48f, 0.62f)
    val selectBg = Color.hsl(214f, 0.30f, 0.17f)
    val selectLine = Color.hsl(214f, 0.60f, 0.55f)
    val scrim = Color.hsl(222f, 0.30f, 0.04f).copy(alpha = 0.72f)

    // 從鬧鐘 APP 複製來的 App 內更新畫面用到的名稱
    val accentSoft = Color.hsl(214f, 0.80f, 0.78f)
    val border = line
    val dim = Color.hsl(220f, 0.10f, 0.50f)
    val warnText = Color.hsl(28f, 0.85f, 0.70f)

    /** 清單顏色：順序固定、只能往後加（資料庫存索引） */
    private val listHues = listOf(214f, 150f, 40f, 330f, 190f, 268f, 8f, 100f)
    val listColorCount get() = listHues.size
    fun listDot(i: Int) = Color.hsl(listHues[i.mod(listHues.size)], 0.60f, 0.62f)
    fun listTint(i: Int) = Color.hsl(listHues[i.mod(listHues.size)], 0.40f, 0.22f)
    /** 清單標題（比點再亮一點，深色底上要清楚） */
    fun listTitle(i: Int) = Color.hsl(listHues[i.mod(listHues.size)], 0.75f, 0.70f)

    val myDay = Color.hsl(40f, 0.85f, 0.64f)
    val important = Color.hsl(330f, 0.65f, 0.72f)
    val planned = Color.hsl(172f, 0.50f, 0.58f)
}

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp),
)

@Composable
fun TodoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent, onPrimary = C.onAccent, secondary = C.star,
            background = C.bg, onBackground = C.text, surface = C.surface, onSurface = C.text,
            surfaceVariant = C.surfaceHi, onSurfaceVariant = C.muted, surfaceContainer = C.panel,
            surfaceContainerHigh = C.surfaceHi, surfaceContainerHighest = C.surfaceHi,
            outline = C.lineHi, outlineVariant = C.line, error = C.overdue,
        ),
        typography = typography,
        content = content,
    )
}
