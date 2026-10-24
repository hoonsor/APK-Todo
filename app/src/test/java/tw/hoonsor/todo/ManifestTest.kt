package tw.hoonsor.todo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.todo.agent.TodoAgentCatalog
import java.io.File

/** docs/agent-manifest.json 必須與 describe 完全一致（版本號除外）；語音總控的 AGENT_PROTOCOL.md 第 6 節 */
class ManifestTest {
    @Test fun manifestMatchesDescribe() {
        val file = listOf(File("../docs/agent-manifest.json"), File("docs/agent-manifest.json")).first { it.parentFile?.exists() == true }
        val actual = TodoAgentCatalog.describe("x").apply { remove("version") }
        if (System.getenv("UPDATE_MANIFEST") == "1") file.writeText(actual.toString(2) + "\n", Charsets.UTF_8)
        val expected = JSONObject(file.readText(Charsets.UTF_8)).apply { remove("version") }
        assertTrue("docs/agent-manifest.json 與 TodoAgentCatalog 不一致；執行 UPDATE_MANIFEST=1 gradlew testProdDebugUnitTest 重新產生", expected.similar(actual))
    }

    @Test fun actionNamesFollowProtocol() {
        val names = TodoAgentCatalog.actions.map { it.getString("name") }
        assertEquals(listOf("list_list", "todo_list", "todo_add", "todo_complete", "todo_delete"), names)
        // destructive 動作一定要有，而且不提供語音例句（只給 undo 用）
        assertEquals("destructive", TodoAgentCatalog.actions.first { it.getString("name") == "todo_delete" }.getString("risk"))
    }
}
