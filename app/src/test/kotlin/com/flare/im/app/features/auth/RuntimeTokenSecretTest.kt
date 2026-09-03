package com.flare.im.app.features.auth

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 签名密钥必须是**运行时输入**并持久化，而不是只能靠构建期属性。
 *
 * 此前 Android 的密钥只来自 BuildConfig.DEFAULT_TOKEN_SECRET：换一个服务器
 * 就得重新 assembleDebug；而把密钥打进安装包等于让任何拿到它的人伪造任意用户身份。
 * 做成登录页输入 + 本机持久化后，只输入 user id 就能登录。
 */
class RuntimeTokenSecretTest {

    @Test
    fun `登录页有签名密钥输入并写回草稿`() {
        val src = File("src/main/kotlin/com/flare/im/app/features/auth/LoginScreen.kt").readText()
        assertTrue("登录页缺少密钥输入", src.contains("value = draft.tokenSecret"))
        assertTrue("密钥输入没有写回草稿", src.contains("d.copy(tokenSecret = it)"))
        assertTrue("密钥标签必须走资源", src.contains("R.string.auth_token_secret"))
    }

    @Test
    fun `会话档案持久化并恢复签名密钥`() {
        val src = File("src/main/kotlin/com/flare/im/app/core/session/SavedSessionStore.kt").readText()
        assertTrue("没有保存密钥——每次重启都得重填", src.contains("putString(KEY_TOKEN_SECRET, draft.tokenSecret)"))
        assertTrue("没有恢复密钥", src.contains("tokenSecret = prefs.getString(KEY_TOKEN_SECRET"))
    }
}
