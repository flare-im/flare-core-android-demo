package com.flare.im.app.features.messaging.composer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Title
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.flare.im.app.R
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.MessageBuildOp
import com.flare.im.app.core.platform.AudioRecorder
import com.flare.im.app.core.platform.FlareAssetImage
import com.flare.im.app.features.messaging.MessagingViewModel
import com.flare.im.app.features.messaging.media.EmojiPresentation

/** 输入区：文本 + 表情/贴纸面板 + 扩展构建菜单 +
 *  录音 + 图片选择 + 发送。自持状态，仅依赖 [MessagingViewModel]。 */
@Composable
fun ComposerBar(vm: MessagingViewModel, conversationTitle: String) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    var composer by remember { mutableStateOf(TextFieldValue("")) }
    var richMode by remember { mutableStateOf(false) }
    var builderMenu by remember { mutableStateOf(false) }
    var emojiPanel by remember { mutableStateOf(false) }
    var formOp by remember { mutableStateOf<MessageBuildOp?>(null) }

    // richMode 下发送走 create_rich_doc（core 归一化 Markdown→docJson）；否则纯文本。
    fun submitComposer() {
        val text = composer.text
        if (text.isBlank()) return
        if (richMode) {
            vm.buildAndSend(MessageBuildOp.CreateRichDoc, mapOf("markdown" to text))
        } else {
            vm.sendText(text)
        }
        composer = TextFieldValue("")
    }

    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    fun stopAndSend() { recorder.stop()?.let { (path, dur) -> vm.sendAudio(path, dur) }; recording = false }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.sendPickedImage(it.toString()) }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recording = recorder.start()
    }

    // 版式对齐 Flutter message_composer（见 examples/COMPOSER-DESIGN-SPEC.md）：
    // 栏底 background + 顶圆角 16；两行——上行白底圆角输入框（IME 发送，无独立发送键），
    // 下行 6 槽均分图标工具栏（emoji/@/语音/图片/富文档/更多）。
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = tk.lg, topEnd = tk.lg))
            .background(colors.background),
    ) {
        if (emojiPanel) EmojiStickerPanel(vm) { emojiPanel = false }
        formOp?.let { op -> ComposerFormDialog(op, vm) { formOp = null } }
        HorizontalDivider(thickness = 0.5.dp, color = colors.hairline)
        Column(Modifier.fillMaxWidth().padding(horizontal = tk.sm, vertical = tk.sm)) {
            // 第 1 行：白底圆角输入框（无描边；发送走 IME 发送键 / 回车，硬件键盘可点发送图标）。
            TextField(
                value = composer,
                onValueChange = { composer = it },
                placeholder = {
                    Text(stringResource(R.string.composer_hint, conversationTitle), color = colors.textSecondary)
                },
                textStyle = FlareTheme.type.body.copy(color = colors.textPrimary),
                modifier = Modifier.fillMaxWidth().clip(tk.radiusSmall),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitComposer() }),
                trailingIcon = {
                    if (composer.text.isNotBlank()) {
                        IconButton(onClick = { submitComposer() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "发送",
                                tint = colors.brand,
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    disabledContainerColor = colors.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            // 富文本格式工具条（对齐 Flutter）：选区感知插入 Markdown 标记，由 core 归一化为 RichDoc v2。
            if (richMode) {
                ComposerRichFormatStrip(
                    onApply = { composer = it(composer) },
                    tint = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(tk.sm))
            HorizontalDivider(thickness = 0.5.dp, color = colors.hairline)
            // 第 2 行：6 槽均分图标工具栏。
            Row(Modifier.fillMaxWidth().padding(top = tk.xs), verticalAlignment = Alignment.CenterVertically) {
                ComposerToolbarIcon(Icons.Outlined.EmojiEmotions, "表情/贴纸", Modifier.weight(1f), colors.textSecondary) {
                    emojiPanel = !emojiPanel
                }
                ComposerToolbarIcon(Icons.Outlined.AlternateEmail, "@提及", Modifier.weight(1f), colors.textSecondary) {
                    composer = composerInsert(composer, "@")
                }
                ComposerToolbarIcon(
                    if (recording) Icons.Filled.Stop else Icons.Outlined.MicNone,
                    "语音",
                    Modifier.weight(1f),
                    if (recording) colors.danger else colors.textSecondary,
                ) {
                    if (recording) {
                        stopAndSend()
                    } else if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        recording = recorder.start()
                    } else {
                        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
                ComposerToolbarIcon(Icons.Outlined.Image, "图片", Modifier.weight(1f), colors.textSecondary) {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ComposerToolbarIcon(
                    Icons.AutoMirrored.Outlined.Article,
                    "富文本",
                    Modifier.weight(1f),
                    if (richMode) colors.brand else colors.textSecondary,
                ) {
                    richMode = !richMode
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ComposerToolbarIcon(
                        if (builderMenu) Icons.Outlined.Close else Icons.Outlined.Add,
                        "更多功能",
                        Modifier,
                        colors.textSecondary,
                    ) { builderMenu = true }
                    DropdownMenu(expanded = builderMenu, onDismissRequest = { builderMenu = false }) {
                        DropdownMenuItem(text = { Text("Pick image") }, onClick = {
                            builderMenu = false
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        listOf(
                            "Image (demo)" to MessageBuildOp.CreateImage,
                            "Video" to MessageBuildOp.CreateVideo,
                            "Location" to MessageBuildOp.CreateLocation,
                            "File" to MessageBuildOp.CreateFile,
                            "Card" to MessageBuildOp.CreateCard,
                            "Task" to MessageBuildOp.CreateTask,
                            "Vote" to MessageBuildOp.CreateVote,
                            "Schedule" to MessageBuildOp.CreateSchedule,
                            "Rich doc" to MessageBuildOp.CreateRichDoc,
                            "Link card" to MessageBuildOp.CreateLinkCard,
                            "Sticker" to MessageBuildOp.CreateSticker,
                        ).forEach { (label, op) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                builderMenu = false
                                if (op in formOps) formOp = op else vm.buildAndSend(op)
                            })
                        }
                    }
                }
            }
        }
    }
}

// ───── 富文本编辑：选区感知的 Markdown 标记插入（对齐 Flutter rich_text_composer_formatter）─────

/** 在光标/选区处插入文本（有选区则替换）。 */
private fun composerInsert(value: TextFieldValue, text: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val merged = value.text.substring(0, start) + text + value.text.substring(end)
    return TextFieldValue(merged, TextRange(start + text.length))
}

/** 用 before/after 包裹选区（无选区则插入标记并把光标置于中间）。 */
private fun composerWrap(value: TextFieldValue, before: String, after: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    val merged = value.text.substring(0, start) + before + selected + after + value.text.substring(end)
    val cursor = if (selected.isEmpty()) start + before.length else start + before.length + selected.length + after.length
    return TextFieldValue(merged, TextRange(cursor))
}

/** 在当前行行首加 prefix（标题/引用/列表）。 */
private fun composerPrefixLine(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val merged = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return TextFieldValue(merged, TextRange(cursor + prefix.length))
}

/** 富文本格式工具条：行内(粗/斜/删/码) + 块级(标题/引用/无序/有序)，应用为 Markdown 标记。 */
@Composable
private fun ComposerRichFormatStrip(
    onApply: ((TextFieldValue) -> TextFieldValue) -> Unit,
    tint: Color,
) {
    val tk = FlareTheme.tokens
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = tk.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposerFormatButton(Icons.Outlined.FormatBold, "粗体", tint) { onApply { composerWrap(it, "**", "**") } }
        ComposerFormatButton(Icons.Outlined.FormatItalic, "斜体", tint) { onApply { composerWrap(it, "*", "*") } }
        ComposerFormatButton(Icons.Outlined.FormatStrikethrough, "删除线", tint) { onApply { composerWrap(it, "~~", "~~") } }
        ComposerFormatButton(Icons.Outlined.Code, "行内代码", tint) { onApply { composerWrap(it, "`", "`") } }
        ComposerFormatButton(Icons.Outlined.Title, "标题", tint) { onApply { composerPrefixLine(it, "## ") } }
        ComposerFormatButton(Icons.Outlined.FormatQuote, "引用", tint) { onApply { composerPrefixLine(it, "> ") } }
        ComposerFormatButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "无序列表", tint) { onApply { composerPrefixLine(it, "- ") } }
        ComposerFormatButton(Icons.Outlined.FormatListNumbered, "有序列表", tint) { onApply { composerPrefixLine(it, "1. ") } }
    }
}

@Composable
private fun ComposerFormatButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 工具栏单色描边图标按钮（命中区 ~48dp，图标 24，色 textSecondary/选中态自定）。 */
@Composable
private fun ComposerToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** 表情 + 贴纸选择面板：委托 kit 的选择器（157 表情 + 全部贴纸包，中心源资源），点选即发送。 */
@Composable
private fun EmojiStickerPanel(vm: MessagingViewModel, onClose: () -> Unit) {
    com.flare.im.ui.FlareEmojiStickerPicker(
        emojiLabel = "Emoji",
        onInsertEmoji = { key ->
            vm.buildAndSend(MessageBuildOp.CreateEmoji, mapOf("emoji" to key)); onClose()
        },
        onSendSticker = { packageId, stickerId ->
            vm.buildAndSend(MessageBuildOp.CreateSticker, mapOf("stickerId" to stickerId, "packageId" to packageId)); onClose()
        },
    )
}

// 这些 build op 弹表单收集输入（其余直接用默认内容发送）。
private val formOps = setOf(
    MessageBuildOp.CreateVote, MessageBuildOp.CreateLocation, MessageBuildOp.CreateCard,
    MessageBuildOp.CreateLinkCard, MessageBuildOp.CreateFile, MessageBuildOp.CreateTask,
    MessageBuildOp.CreateRichDoc,
)

private data class FormSpec(val title: String, val fields: List<Triple<String, String, String>>)

private fun formSpec(op: MessageBuildOp): FormSpec = when (op) {
    MessageBuildOp.CreateVote -> FormSpec("New vote", listOf(
        Triple("title", "Question", "Lunch?"),
        Triple("options", "Options (comma-separated)", "Pizza,Sushi,Salad"),
    ))
    MessageBuildOp.CreateLocation -> FormSpec("Share location", listOf(
        Triple("title", "Title", "Office"),
        Triple("address", "Address", "Shanghai"),
        Triple("latitude", "Latitude", "31.2304"),
        Triple("longitude", "Longitude", "121.4737"),
    ))
    MessageBuildOp.CreateCard -> FormSpec("Card", listOf(
        Triple("title", "Title", "Card"),
        Triple("subtitle", "Subtitle", ""),
    ))
    MessageBuildOp.CreateLinkCard -> FormSpec("Link card", listOf(
        Triple("url", "URL", "https://flare.local"),
        Triple("title", "Title", "Flare"),
        Triple("description", "Description", ""),
    ))
    MessageBuildOp.CreateFile -> FormSpec("File", listOf(
        Triple("fileName", "File name", "report.pdf"),
        Triple("url", "URL", ""),
    ))
    MessageBuildOp.CreateTask -> FormSpec("Task", listOf(
        Triple("title", "Task title", "Task"),
        Triple("participantUserIds", "Participants (comma)", ""),
    ))
    MessageBuildOp.CreateRichDoc -> FormSpec("Rich doc", listOf(
        Triple("title", "Title", "Rich Doc"),
        Triple("markdown", "Markdown", "## Heading\n\n- point one\n- **bold** point"),
    ))
    else -> FormSpec(op.name, emptyList())
}

private fun convertFormField(key: String, value: String): Any? = when (key) {
    "options", "participantUserIds" -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    "latitude", "longitude" -> value.toDoubleOrNull() ?: 0.0
    else -> value
}

/** 富消息构建表单：按 build op 收集字段 → buildAndSend(payload)。 */
@Composable
private fun ComposerFormDialog(op: MessageBuildOp, vm: MessagingViewModel, onDismiss: () -> Unit) {
    val tk = FlareTheme.tokens
    val spec = remember(op) { formSpec(op) }
    val values = remember(op) { mutableStateMapOf<String, String>().apply { spec.fields.forEach { put(it.first, it.third) } } }
    val multiline = setOf("options", "participantUserIds", "description", "markdown")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                vm.buildAndSend(op, values.mapValues { (k, v) -> convertFormField(k, v) })
                onDismiss()
            }) { Text(stringResource(R.string.composer_form_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(spec.title, style = FlareTheme.type.headline) },
        text = {
            Column {
                spec.fields.forEach { (key, label, _) ->
                    OutlinedTextField(
                        value = values[key] ?: "",
                        onValueChange = { values[key] = it },
                        label = { Text(label) },
                        singleLine = key !in multiline,
                        modifier = Modifier.fillMaxWidth().padding(vertical = tk.xs),
                    )
                }
            }
        },
    )
}
