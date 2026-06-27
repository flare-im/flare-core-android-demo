package com.flare.im.app.features.messaging.messagerow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flare.im.app.core.designsystem.FlareTheme
import org.json.JSONArray
import org.json.JSONObject

/**
 * 渲染 RichDoc v2 `docJson` 为带格式文本：heading(level) / 段落 / 有序无序列表 / 引用 / 代码块，
 * 行内 marks = bold·strong / italic·em / underline / strike / code。对应主流 IM 富文本消息渲染。
 * 解析失败或空 → 回退 plainText。SDK `normalizeRichDocFromMarkdown` 产出的就是这个 schema。
 */
@Composable
fun RichDocText(docJson: String, fallback: String, color: Color) {
    val blocks = remember(docJson) { runCatching { parseRichDoc(docJson) }.getOrNull().orEmpty() }
    if (blocks.isEmpty()) {
        Text(fallback, style = FlareTheme.type.body, color = color)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        blocks.forEach { RichBlockView(it, color) }
    }
}

@Composable
private fun RichBlockView(block: RichBlock, color: Color) {
    when (block) {
        is RichBlock.Heading -> Text(
            block.text, color = color,
            style = FlareTheme.type.body.copy(fontWeight = FontWeight.Bold, fontSize = headingSize(block.level)),
        )
        is RichBlock.Paragraph -> Text(block.text, style = FlareTheme.type.body, color = color)
        is RichBlock.ListItem -> Text(
            buildAnnotatedString { append(block.marker); append(block.text) },
            style = FlareTheme.type.body, color = color,
        )
        is RichBlock.Quote -> Text(
            buildAnnotatedString { append("│  "); append(block.text) },
            style = FlareTheme.type.body.copy(fontStyle = FontStyle.Italic),
            color = color.copy(alpha = 0.85f),
        )
        is RichBlock.Code -> Text(
            block.text,
            style = FlareTheme.type.callout.copy(fontFamily = FontFamily.Monospace),
            color = color,
            modifier = Modifier.background(FlareTheme.colors.surfaceAlt).padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

private fun headingSize(level: Int) = when (level) {
    1 -> 22.sp
    2 -> 19.sp
    3 -> 17.sp
    else -> 16.sp
}

// ---- parse RichDoc v2 ----
private sealed interface RichBlock {
    data class Heading(val level: Int, val text: AnnotatedString) : RichBlock
    data class Paragraph(val text: AnnotatedString) : RichBlock
    data class ListItem(val marker: String, val text: AnnotatedString) : RichBlock
    data class Quote(val text: AnnotatedString) : RichBlock
    data class Code(val text: String) : RichBlock
}

private fun parseRichDoc(json: String): List<RichBlock> {
    val out = mutableListOf<RichBlock>()
    val children = JSONObject(json).optJSONArray("children") ?: return out
    for (i in 0 until children.length()) {
        children.optJSONObject(i)?.let { parseBlock(it, out, null) }
    }
    return out
}

private fun parseBlock(node: JSONObject, out: MutableList<RichBlock>, listMarker: String?) {
    when (node.optString("type")) {
        "heading" -> out += RichBlock.Heading(node.optInt("level", 1).coerceIn(1, 6), inlineOf(node))
        "paragraph" -> {
            val a = inlineOf(node)
            out += if (listMarker != null) RichBlock.ListItem(listMarker, a) else RichBlock.Paragraph(a)
        }
        "blockquote" -> out += RichBlock.Quote(inlineOf(node))
        "code_block" -> out += RichBlock.Code(inlineOf(node).text)
        "bullet_list" -> forEachChild(node) { parseBlock(it, out, "•  ") }
        "ordered_list" -> {
            var n = 1
            forEachChild(node) { parseBlock(it, out, "${n++}.  ") }
        }
        "list_item" -> forEachChild(node) { parseBlock(it, out, listMarker) }
        else -> {
            val a = inlineOf(node)
            if (a.isNotEmpty()) out += RichBlock.Paragraph(a)
        }
    }
}

private inline fun forEachChild(node: JSONObject, action: (JSONObject) -> Unit) {
    val ch = node.optJSONArray("children") ?: return
    for (i in 0 until ch.length()) ch.optJSONObject(i)?.let(action)
}

private fun inlineOf(node: JSONObject): AnnotatedString = buildAnnotatedString { appendInline(node, this) }

private fun appendInline(node: JSONObject, b: AnnotatedString.Builder) {
    val children = node.optJSONArray("children")
    if (children == null) {
        if (node.optString("type") == "text") appendText(node, b)
        return
    }
    for (i in 0 until children.length()) {
        val c = children.optJSONObject(i) ?: continue
        when (c.optString("type")) {
            "text" -> appendText(c, b)
            "hard_break" -> b.append("\n")
            else -> appendInline(c, b)
        }
    }
}

private fun appendText(node: JSONObject, b: AnnotatedString.Builder) {
    val text = node.optString("text")
    if (text.isEmpty()) return
    val span = spanFromMarks(node.optJSONArray("marks"))
    if (span != null) {
        b.pushStyle(span); b.append(text); b.pop()
    } else {
        b.append(text)
    }
}

private fun spanFromMarks(marks: JSONArray?): SpanStyle? {
    if (marks == null || marks.length() == 0) return null
    var weight: FontWeight? = null
    var style: FontStyle? = null
    var deco: TextDecoration? = null
    var mono = false
    for (i in 0 until marks.length()) {
        when (marks.optJSONObject(i)?.optString("type")) {
            "bold", "strong" -> weight = FontWeight.Bold
            "italic", "em" -> style = FontStyle.Italic
            "underline" -> deco = TextDecoration.Underline
            "strike" -> deco = TextDecoration.LineThrough
            "code" -> mono = true
        }
    }
    return SpanStyle(
        fontWeight = weight,
        fontStyle = style,
        textDecoration = deco,
        fontFamily = if (mono) FontFamily.Monospace else null,
    )
}
