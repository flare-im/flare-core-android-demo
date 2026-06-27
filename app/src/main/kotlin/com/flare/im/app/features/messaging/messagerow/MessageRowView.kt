package com.flare.im.app.features.messaging.messagerow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.core.domain.MessageBuildOp
import com.flare.im.app.core.platform.FlareAssetImage
import com.flare.im.app.core.platform.FlareLocalImage
import com.flare.im.app.features.messaging.MessagingViewModel
import com.flare.im.app.features.messaging.media.EmojiPresentation
import com.flare.im.model.common.enums.MessageContentType
import com.flare.im.model.entity.MessageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun MessageContent.str(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { (data[it] as? String)?.takeIf { s -> s.isNotBlank() } }

/** 一行消息：气泡 + 按 contentType 渲染（emoji/sticker 图、媒体/富卡片）+ 长按动作 + 发送状态。
 *  负责气泡、媒体与富内容消息渲染。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(message: AppMessage, outgoing: Boolean, vm: MessagingViewModel) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    var menu by remember { mutableStateOf(false) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    val pending by vm.pendingMessageKeys.collectAsState()
    val failed by vm.failedMessageKeys.collectAsState()
    val clipboard = LocalClipboardManager.current
    val me by vm.currentUserId.collectAsState()
    val content = message.core.content
    val standalone = !message.core.isRecalled && isStandaloneAsset(content, message.previewText)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
        Box {
            Box(
                Modifier.widthIn(max = 280.dp)
                    .then(
                        if (standalone) Modifier
                        else Modifier.clip(tk.radiusLarge).background(
                            if (message.core.isRecalled) colors.surfaceAlt else if (outgoing) colors.outgoing else colors.incomingBubble,
                        ),
                    )
                    .combinedClickable(onClick = {}, onLongClick = { if (!message.core.isRecalled) menu = true })
                    .padding(if (standalone) PaddingValues(2.dp) else PaddingValues(horizontal = tk.md, vertical = tk.sm)),
            ) {
                MessageContentView(message, outgoing, vm) { previewPath = it }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                // 快捷表情回应。
                Row(
                    Modifier.padding(horizontal = tk.md, vertical = tk.xs),
                    horizontalArrangement = Arrangement.spacedBy(tk.sm),
                ) {
                    quickReactions.forEach { emoji ->
                        Text(
                            emoji,
                            style = FlareTheme.type.title,
                            modifier = Modifier.clip(tk.pill).clickable { menu = false; vm.messageAction("react", message, emoji) }.padding(2.dp),
                        )
                    }
                }
                HorizontalDivider(color = colors.hairline)
                listOf(
                    "Pin" to "pin", "Pin for me" to "pinSelf", "Unpin" to "unpin", "Flag" to "mark",
                    "Edit text" to "edit", "Recall" to "recall", "Delete for me" to "deleteSelf",
                ).forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; vm.messageAction(action, message) })
                }
                DropdownMenuItem(text = { Text("Copy") }, onClick = { menu = false; clipboard.setText(AnnotatedString(message.previewText)) })
                DropdownMenuItem(text = { Text("Forward") }, onClick = { menu = false; vm.buildAndSend(MessageBuildOp.CreateForward) })
                if (content?.contentType in mediaContentTypes) {
                    DropdownMenuItem(text = { Text("Save to downloads") }, onClick = { menu = false; vm.saveToDownloads(message) })
                }
            }
        }
        val key = message.appStableId
        when {
            key in failed -> Text("Failed · tap to retry", style = FlareTheme.type.caption, color = colors.danger, modifier = Modifier.clickable { vm.retry(message) })
            key in pending -> Text("Sending…", style = FlareTheme.type.caption, color = colors.textTertiary)
        }
        ReactionStrip(message, me, vm)
    }
    previewPath?.let { p -> MediaPreviewDialog(p) { previewPath = null } }
}

/** 表情回应条：每个 emoji + 计数为可点 chip，点己有=取消、点他人=追加。 */
@Composable
private fun ReactionStrip(message: AppMessage, me: String?, vm: MessagingViewModel) {
    val reactions = message.core.reactions
    if (reactions.isEmpty()) return
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    Row(
        Modifier.padding(top = 2.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tk.xs),
    ) {
        reactions.forEach { r ->
            val mine = me != null && me in r.userIds
            Row(
                Modifier.clip(tk.pill)
                    .background(if (mine) colors.brandSoft else colors.surfaceAlt)
                    .clickable { vm.messageAction(if (mine) "unreact" else "react", message, r.emoji) }
                    .padding(horizontal = tk.sm, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.emoji, style = FlareTheme.type.caption)
                if (r.count > 0) {
                    Spacer(Modifier.width(2.dp))
                    Text("${r.count}", style = FlareTheme.type.caption, color = if (mine) colors.brand else colors.textSecondary)
                }
            }
        }
    }
}

private val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "👏")

private val mediaContentTypes = setOf(
    MessageContentType.IMAGE, MessageContentType.IMAGE_GROUP, MessageContentType.VIDEO,
    MessageContentType.AUDIO, MessageContentType.FILE,
)

@Composable
private fun MessageContentView(message: AppMessage, outgoing: Boolean, vm: MessagingViewModel, onPreview: (String) -> Unit) {
    val colors = FlareTheme.colors
    val content = message.core.content
    val textColor = if (outgoing) colors.outgoingText else colors.textPrimary

    if (message.core.isRecalled) {
        Text("Message recalled", style = FlareTheme.type.body, color = colors.textTertiary); return
    }
    when (content?.contentType ?: MessageContentType.TEXT) {
        MessageContentType.EMOJI -> emojiAsset(content)?.let { FlareAssetImage(it, "emoji", 78.dp) { Fallback(message, textColor) } } ?: Fallback(message, textColor)
        MessageContentType.STICKER -> stickerAsset(content)?.let { FlareAssetImage(it, "sticker", 96.dp) { Fallback(message, textColor) } } ?: Fallback(message, textColor)
        MessageContentType.TEXT, MessageContentType.RICH_TEXT -> {
            val docJson = content?.str("docJson")
            if (docJson != null) {
                // 富文档：按 RichDoc v2 docJson 带格式渲染（heading/列表/bold 等）。
                RichDocText(docJson, message.previewText, textColor)
            } else {
                val raw = content?.str("text", "plainText") ?: message.previewText
                val lone = EmojiPresentation.lonePackKey(raw)
                val single = EmojiPresentation.singleEmoji(raw)
                when {
                    lone != null -> FlareAssetImage(EmojiPresentation.emojiAssetPath(lone), "emoji", 78.dp) { Text(message.previewText, style = FlareTheme.type.body, color = textColor) }
                    single != null -> Text(single, style = FlareTheme.type.largeTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified), color = textColor)
                    else -> Text(message.previewText, style = FlareTheme.type.body, color = textColor)
                }
            }
        }
        MessageContentType.IMAGE, MessageContentType.IMAGE_GROUP -> MessageImage(message, vm, outgoing, onPreview)
        MessageContentType.VIDEO -> MessageVideo(message, vm, outgoing)
        MessageContentType.AUDIO -> AudioBubble(message, vm, outgoing)
        MessageContentType.FILE -> CardRow("📄", content?.str("name", "fileName") ?: "File", outgoing)
        MessageContentType.LOCATION -> CardRow("📍", content?.str("title", "address") ?: "Location", outgoing)
        MessageContentType.CARD -> CardRow("🪪", content?.str("title") ?: "Card", outgoing)
        MessageContentType.LINK_CARD -> CardRow("🔗", content?.str("title", "url") ?: "Link", outgoing)
        MessageContentType.MINI_PROGRAM -> CardRow("▦", content?.str("title") ?: "Mini program", outgoing)
        MessageContentType.VOTE -> CardRow("📊", content?.str("title") ?: "Vote", outgoing)
        MessageContentType.TASK -> CardRow("✓", content?.str("title") ?: "Task", outgoing)
        MessageContentType.SCHEDULE -> CardRow("📅", content?.str("title") ?: "Schedule", outgoing)
        MessageContentType.NOTIFICATION, MessageContentType.ANNOUNCEMENT, MessageContentType.SYSTEM ->
            CardRow("📢", message.previewText, outgoing)
        else -> Fallback(message, textColor)
    }
}

@Composable
private fun Fallback(message: AppMessage, color: androidx.compose.ui.graphics.Color) {
    Text(message.previewText, style = FlareTheme.type.body, color = color)
}

@Composable
private fun CardRow(icon: String, label: String, outgoing: Boolean) {
    val colors = FlareTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = FlareTheme.type.title)
        Spacer(Modifier.width(FlareTheme.tokens.sm))
        Text(label, style = FlareTheme.type.body, color = if (outgoing) colors.outgoingText else colors.textPrimary)
    }
}

/** 图片消息：先用本地直链即时显示，再经 [MessagingViewModel.resolveMediaUrl] 换远端签名 URL；点击全屏预览。 */
@Composable
private fun MessageImage(message: AppMessage, vm: MessagingViewModel, outgoing: Boolean, onPreview: (String) -> Unit) {
    val content = message.core.content
    var path by remember(message.appStableId) { mutableStateOf(imagePath(content)) }
    LaunchedEffect(message.appStableId) { vm.resolveMediaUrl(message)?.let { path = it } }
    val p = path
    if (p != null) {
        FlareLocalImage(
            path = p,
            contentDescription = content?.str("description", "title") ?: "Image",
            modifier = Modifier.sizeIn(maxWidth = 220.dp, maxHeight = 280.dp).clip(FlareTheme.tokens.radiusLarge).clickable { onPreview(p) },
            maxPx = 1024,
        ) { CardRow("🖼", content?.str("description", "title") ?: "Image", outgoing) }
    } else {
        CardRow("🖼", content?.str("description", "title") ?: "Image", outgoing)
    }
}

/** 视频消息：MediaMetadataRetriever 取首帧做缩略图（本地/远端），点击内联播放。 */
@Composable
private fun MessageVideo(message: AppMessage, vm: MessagingViewModel, outgoing: Boolean) {
    val context = LocalContext.current
    val content = message.core.content
    var url by remember(message.appStableId) { mutableStateOf(imagePath(content)) }
    var thumb by remember(message.appStableId) { mutableStateOf<ImageBitmap?>(null) }
    var showPlayer by remember(message.appStableId) { mutableStateOf(false) }
    LaunchedEffect(message.appStableId) {
        vm.resolveMediaUrl(message)?.let { url = it }
        url?.let { p ->
            thumb = withContext(Dispatchers.IO) {
                runCatching {
                    val r = android.media.MediaMetadataRetriever()
                    when {
                        p.startsWith("content://") || p.startsWith("file://") -> r.setDataSource(context, android.net.Uri.parse(p))
                        p.startsWith("http") -> r.setDataSource(p, HashMap<String, String>())
                        else -> r.setDataSource(p)
                    }
                    val f = r.frameAtTime
                    r.release()
                    f?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(
        Modifier.sizeIn(maxWidth = 220.dp, maxHeight = 280.dp).clip(FlareTheme.tokens.radiusLarge)
            .background(FlareTheme.colors.surfaceAlt)
            .clickable { if (url != null) showPlayer = true },
        Alignment.Center,
    ) {
        val t = thumb
        if (t != null) {
            androidx.compose.foundation.Image(bitmap = t, contentDescription = "video", modifier = Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            CardRow("🎬", content?.str("description", "title") ?: "Video", outgoing)
        }
        Text("▶", style = FlareTheme.type.largeTitle, color = androidx.compose.ui.graphics.Color.White)
    }
    if (showPlayer) url?.let { VideoPlayerDialog(it) { showPlayer = false } }
}

/** 内联视频播放器：全屏 VideoView + MediaController（播放/暂停/拖动），点空白关闭。 */
@Composable
private fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable { onDismiss() }, Alignment.Center) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        val mc = android.widget.MediaController(ctx)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        if (url.startsWith("/")) setVideoPath(url) else setVideoURI(android.net.Uri.parse(url))
                        setOnPreparedListener { it.start() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 语音气泡：▶/⏸ 播放（MediaPlayer.prepareAsync 不卡主线程）；远端音频经 resolveMediaUrl。 */
@Composable
private fun AudioBubble(message: AppMessage, vm: MessagingViewModel, outgoing: Boolean) {
    val colors = FlareTheme.colors
    val context = LocalContext.current
    var path by remember(message.appStableId) { mutableStateOf(imagePath(message.core.content)) }
    LaunchedEffect(message.appStableId) { vm.resolveMediaUrl(message)?.let { path = it } }
    var playing by remember { mutableStateOf(false) }
    val player = remember { android.media.MediaPlayer() }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (playing) "⏸" else "▶",
            style = FlareTheme.type.title,
            color = if (outgoing) colors.outgoingText else colors.brand,
            modifier = Modifier.clickable {
                val p = path ?: return@clickable
                runCatching {
                    if (playing) {
                        player.pause(); playing = false
                    } else {
                        player.reset()
                        if (p.startsWith("content://") || p.startsWith("file://")) player.setDataSource(context, android.net.Uri.parse(p)) else player.setDataSource(p)
                        player.setOnPreparedListener { it.start(); playing = true }
                        player.setOnCompletionListener { playing = false }
                        player.prepareAsync()
                    }
                }
            },
        )
        Spacer(Modifier.width(FlareTheme.tokens.sm))
        Text("Voice message", style = FlareTheme.type.body, color = if (outgoing) colors.outgoingText else colors.textPrimary)
    }
}

/** 媒体全屏预览：双指缩放 + 拖动平移 + 双击放大复位；单击关闭。 */
@Composable
private fun MediaPreviewDialog(path: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        Box(
            Modifier.fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        },
                        onTap = { onDismiss() },
                    )
                },
            Alignment.Center,
        ) {
            FlareLocalImage(
                path = path,
                contentDescription = "Preview",
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                },
                maxPx = 2048,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            ) {
                Text("Cannot preview", style = FlareTheme.type.body, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(FlareTheme.tokens.lg))
            }
        }
    }
}

private fun imagePath(content: MessageContent?): String? =
    content?.str("sourceUrl", "url", "path", "localPath")?.takeIf { it.isNotBlank() }

private fun emojiAsset(content: MessageContent?): String? =
    EmojiPresentation.normalizedPackKey(content?.str("emoji", "key"))?.let { EmojiPresentation.emojiAssetPath(it) }

private fun stickerAsset(content: MessageContent?): String? {
    val id = content?.str("stickerId", "id") ?: return null
    val pkg = content.str("packageId", "package_id") ?: "classic"
    return EmojiPresentation.stickerAssetPath(pkg, id)
}

private fun isStandaloneAsset(content: MessageContent?, preview: String): Boolean {
    if (content == null) return false
    return when (content.contentType) {
        MessageContentType.EMOJI -> emojiAsset(content) != null
        MessageContentType.STICKER -> stickerAsset(content) != null
        MessageContentType.TEXT -> EmojiPresentation.lonePackKey(content.str("text", "plainText") ?: preview) != null
        else -> false
    }
}
