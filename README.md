# SuperScreenshot（超级截屏）

Android 优先：**一键把“屏幕内容 + 屏幕反射”记录到同一张图**，并支持把反射层背景替换为纯色。

## 运行/构建

1. 安装 **Android Studio**（推荐）或至少安装 Android SDK（platform 34 + build-tools 34）。
2. 配置 `local.properties`（构建时会用到）：

```text
sdk.dir=C:\\Android\\Sdk
```

3. 构建 Debug APK：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

## 使用方式（最像“随时截屏”）

1. 安装 App 后，打开一次 `SuperScreenshot`（用于设置背景纯色）。
2. 下拉系统快捷设置 → “编辑” → 把 **超级截屏** 磁贴拖进面板。
3. 以后任意时刻：下拉点一下磁贴 → 按系统提示授权屏幕捕获 → 自动拍摄并保存到相册。

## 说明

- 若遇到受保护内容（DRM）导致截图置黑：会提示失败并不保存。
- 背景纯色替换只做一个简单入口：在 App 首页点颜色即可设置默认背景色。


