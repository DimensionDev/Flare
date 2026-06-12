package dev.dimension.flare.feature.agent.common

import kotlin.test.Test
import kotlin.test.assertEquals

internal class AgentVisibleTextTest {
    @Test
    fun removesToolCallJsonFromVisibleText() {
        val text =
            """
            好的，我先搜一下 "suji" 最近的帖子。

            {"tool_call_id":"call_01_4hRV0822a7mHtY9REUw73196","tool_name":"search_posts","tool_args":{"query":"suji","platforms":[],"pageSize":200}}
            """.trimIndent()

        assertEquals(
            "好的，我先搜一下 \"suji\" 最近的帖子。",
            text.cleanAgentVisibleText(),
        )
    }

    @Test
    fun keepsNormalJsonVisible() {
        val text = """{"query":"suji","platforms":[]}"""

        assertEquals(text, text.cleanAgentVisibleText())
    }

    @Test
    fun keepsTextAroundRemovedToolCallJson() {
        val text =
            """
            我先查一下。

            {"tool_call_id":"call_1","tool_name":"search_users","tool_args":{"query":"suji"}}

            找到了多个匹配的用户，请从下方选择你要定位的用户。
            """.trimIndent()

        assertEquals(
            """
            我先查一下。

            找到了多个匹配的用户，请从下方选择你要定位的用户。
            """.trimIndent(),
            text.cleanAgentVisibleText(),
        )
    }

    @Test
    fun removesFencedToolCallJson() {
        val text =
            """
            ```json
            {"tool_call_id":"call_1","tool_name":"search_posts","tool_args":{"query":"suji"}}
            ```
            """.trimIndent()

        assertEquals("", text.cleanAgentVisibleText())
    }
}
