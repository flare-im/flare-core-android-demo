package com.flare.im.app.features.messaging.messagerow

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.ui.FlareStickerPackMessage

/** 贴纸消息：委托 kit 的贴纸组件（资源来自 flare-im-design 中心源，随 im-ui-compose 打包）。 */
@Composable
internal fun StickerMessageView(message: AppMessage, textColor: Color) {
    val content = message.core.content
    FlareStickerPackMessage(
        stickerId = content?.str("stickerId", "id") ?: "",
        packageId = content?.str("packageId", "package_id"),
    )
}
