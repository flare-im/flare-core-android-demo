package com.flare.im.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flare.im.app.features.shell.FlareApp

/**
 * 单 Activity 宿主：装配 [FlareRootViewModel]（持组合根 FlareAppStore）并渲染 [FlareApp]。
 * 旧极简实现已被 features/shell 的 Compose workbench + 新 Core 架构取代。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏：内容铺到状态栏/导航栏之下（渐变品牌头满铺，对齐 iOS）。各屏自行用 inset padding。
        enableEdgeToEdge()
        val dataDir = filesDir.absolutePath
        // The native core resolves its default data root from XDG/HOME conventions, which on
        // Android point under the read-only "/" mount. Anchor them to app-private storage so
        // any default-root fallback (and SQLite/media-cache roots) lands in a writable place.
        runCatching {
            android.system.Os.setenv("HOME", dataDir, true)
            android.system.Os.setenv("XDG_DATA_HOME", dataDir, true)
            android.system.Os.setenv("TMPDIR", cacheDir.absolutePath, true)
        }
        setContent {
            val root: FlareRootViewModel = viewModel(factory = FlareRootViewModel.factory(dataDir))
            FlareApp(root.store)
        }
    }
}
