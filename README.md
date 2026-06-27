# flare-core-android-app

`flare-core-android-sdk` 的生产级 Android IM 应用模板，目录分层与 `flare-core-flutter-app` 对齐。

## 本地运行

示例 app 通过 Gradle composite module 直接依赖 `../../packages/flare-core-android-sdk`，应用层只消费 SDK generated 强类型模型。

```bash
export ANDROID_HOME=/path/to/android/sdk
../flare-core-flutter-app/android/gradlew :app:testDebugUnitTest
```

Debug 打包需要先同步 Rust Android FFI `.so` 到 Android SDK package：

```bash
cd ../../../flare-im-core-sdk
export ANDROID_NDK_ROOT=/path/to/android/sdk/ndk/28.2.13676358
export NDK_TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/bin"
export CC_aarch64_linux_android="$NDK_TOOLCHAIN/aarch64-linux-android35-clang"
export CXX_aarch64_linux_android="$NDK_TOOLCHAIN/aarch64-linux-android35-clang++"
export AR_aarch64_linux_android="$NDK_TOOLCHAIN/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CC_aarch64_linux_android"
cargo xtask build android
cd ../flare-im-core-client-sdk/examples/flare-core-android-app
scripts/sync_ffi.sh
../flare-core-flutter-app/android/gradlew :app:assembleDebug
```

当前样板按 Rust FFI artifact 对齐为 `arm64-v8a` 单 ABI。核心传输层使用 rustls/WebPKI，Android 构建不需要额外提供 OpenSSL。

## 目录结构

```text
app/src/main/kotlin/com/flare/im/
├── app/              # Application / Activity 入口
├── application/      # 状态编排、SDK 事件桥接
├── domain/
├── infrastructure/   # SDK 适配器、仓储、mapper、媒体
├── interface/        # Compose 页面、主题、组件
└── shared/
assets/
scripts/
```

规范见 [`examples/STRUCTURE.md`](../STRUCTURE.md)。参考实现：`flare-core-flutter-app`。
