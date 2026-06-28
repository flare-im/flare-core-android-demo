package com.flare.im.app.features.messaging.messagerow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.core.platform.FlareLocalImage
import com.flare.im.app.features.messaging.MessagingViewModel

/** 图片消息（IMAGE / IMAGE_GROUP）：先用本地直链即时显示，再经
 *  [MessagingViewModel.resolveMediaUrl] 换远端签名 URL；点击全屏预览。 */
@Composable
internal fun ImageMessageView(message: AppMessage, vm: MessagingViewModel, outgoing: Boolean, onPreview: (String) -> Unit) {
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
