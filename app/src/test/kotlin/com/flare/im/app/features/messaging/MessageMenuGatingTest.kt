package com.flare.im.app.features.messaging

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息菜单的三条约束，钉的是**源码形态**：
 * 真正的失败要在真机上长按一条别人的消息、看到 Recall、点下去才暴露，
 * 而形态判据是确定的。
 */
class MessageMenuGatingTest {

    private val menuSource: String by lazy {
        File("src/main/kotlin/com/flare/im/app/features/messaging/messagerow/MessageRowView.kt")
            .readText()
    }

    @Test
    fun `菜单按核心判定渲染，不是无条件全显的静态列表`() {
        assertTrue(
            "菜单必须消费核心的 action_availability",
            menuSource.contains("vm.actionAvailability(message)"),
        )
        for (key in listOf("canRecall", "canEdit", "canPin", "canUnpin", "canCopy", "canSave")) {
            assertTrue("菜单项必须按 $key 门控", menuSource.contains("can(\"$key\")"))
        }
        assertFalse(
            "Pin 与 Unpin 不能再出现在同一个静态列表里",
            menuSource.contains("\"Pin\" to \"pin\""),
        )
        // 只断言"某个 key 出现过"太弱：把某一处 can(...) 换成 if (true)，
        // 另一处仍在，门禁照样报绿（实测过）。改成计数——少一处门控就红。
        val gated = menuSource.split("can(\"").size - 1
        assertTrue("菜单门控处数降到 $gated（基线 12），说明有菜单项被放开了", gated >= 12)
    }

    @Test
    fun `菜单文案走字符串资源，不硬编码`() {
        for (hardcoded in listOf("\"Recall\"", "\"Delete for me\"", "\"Edit text\"", "\"Forward\"")) {
            assertFalse("菜单文案 $hardcoded 必须迁进 strings.xml", menuSource.contains(hardcoded))
        }
        assertTrue(menuSource.contains("R.string.msg_action_recall"))
    }

    @Test
    fun `编辑不得把原文替换成写死的占位串`() {
        val vmSource = File(
            "src/main/kotlin/com/flare/im/app/features/messaging/MessagingViewModel.kt",
        ).readText()
        // 判据要看**赋值形态**而不是字符串出现：注释里引用这个旧串是合理的
        // （说明为什么不能这么写），把注释也算成违规会让门禁误报。
        assertFalse(
            "编辑必须用调用方传入的正文——写死占位串等于把用户的内容删了",
            Regex("""\"text\"\s+to\s+\"[^\"]*Edited from Android""").containsMatchIn(vmSource),
        )
        assertTrue(vmSource.contains("\"text\" to text"))
    }
}
