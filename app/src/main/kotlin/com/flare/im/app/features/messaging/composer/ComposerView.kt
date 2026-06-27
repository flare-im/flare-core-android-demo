package com.flare.im.app.features.messaging.composer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
    var composer by remember { mutableStateOf("") }
    var builderMenu by remember { mutableStateOf(false) }
    var emojiPanel by remember { mutableStateOf(false) }
    var formOp by remember { mutableStateOf<MessageBuildOp?>(null) }

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

    Column {
        if (emojiPanel) EmojiStickerPanel(vm) { emojiPanel = false }
        formOp?.let { op -> ComposerFormDialog(op, vm) { formOp = null } }
        Row(Modifier.fillMaxWidth().background(colors.surface).padding(tk.md), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { emojiPanel = !emojiPanel }) { Text("☺", style = FlareTheme.type.title, color = colors.brand) }
            IconButton(onClick = {
                if (recording) {
                    stopAndSend()
                } else if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    recording = recorder.start()
                } else {
                    micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }) { Text(if (recording) "■" else "🎙", style = FlareTheme.type.title, color = if (recording) colors.danger else colors.brand) }
            Box {
                IconButton(onClick = { builderMenu = true }) { Text("+", style = FlareTheme.type.title, color = colors.brand) }
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
            OutlinedTextField(
                value = composer, onValueChange = { composer = it },
                placeholder = { Text(stringResource(R.string.composer_hint, conversationTitle)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            )
            Spacer(Modifier.width(tk.sm))
            IconButton(onClick = { if (composer.isNotBlank()) { vm.sendText(composer); composer = "" } }) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(colors.brand), Alignment.Center) {
                    Text("→", color = colors.outgoingText, style = FlareTheme.type.headline)
                }
            }
        }
    }
}

/** 表情 + 贴纸选择面板：点选即发送（CreateEmoji / CreateSticker）。 */
@Composable
private fun EmojiStickerPanel(vm: MessagingViewModel, onClose: () -> Unit) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    Column(
        Modifier.fillMaxWidth().background(colors.surfaceAlt).heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()).padding(tk.md),
    ) {
        Text("Emoji", style = FlareTheme.type.captionStrong, color = colors.textTertiary)
        FlowGrid(EmojiPresentation.composerEmojiKeys) { key ->
            Box(Modifier.padding(tk.xs).clickable {
                vm.buildAndSend(MessageBuildOp.CreateEmoji, mapOf("emoji" to key)); onClose()
            }) { FlareAssetImage(EmojiPresentation.emojiAssetPath(key), key, 44.dp) { Text("🙂") } }
        }
        Spacer(Modifier.height(tk.sm))
        Text("Stickers", style = FlareTheme.type.captionStrong, color = colors.textTertiary)
        FlowGrid(EmojiPresentation.composerStickers) { s ->
            Box(Modifier.padding(tk.xs).clickable {
                vm.buildAndSend(MessageBuildOp.CreateSticker, mapOf("stickerId" to s.stickerId, "packageId" to s.packageId)); onClose()
            }) { FlareAssetImage(EmojiPresentation.stickerAssetPath(s.packageId, s.stickerId), s.id, 60.dp) { Text("🎴") } }
        }
    }
}

@Composable
private fun <T> FlowGrid(items: List<T>, item: @Composable (T) -> Unit) {
    Column {
        items.chunked(6).forEach { row -> Row { row.forEach { item(it) } } }
    }
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
