# 看书（Kanshu）

本地 Android 电子书阅读 App：导入 TXT/EPUB、按屏分页滑动翻页、白天/黑夜模式，支持检查 GitHub Release 自动更新。

## APK

`release/kanshu-debug.apk`

## 使用

1. 打开 App，点右下角 **+** 导入 `.txt` / `.epub`
2. 阅读页：**左右滑动**或点屏幕左右侧翻页；点中间显示/隐藏工具栏
3. 顶栏避开状态栏；工具栏可切换昼夜、打开目录
4. 书架右上角可**检查更新**（读取本仓库 Releases）

## 发布更新（给自动更新用）

1. 提高 `app/build.gradle.kts` 里的 `versionCode` / `versionName`
2. 打包 APK，在 GitHub 创建 Release
3. Tag 建议用 `v2`、`v3`（与 `versionCode` 一致），或 `v1.1.0+2`
4. 把 APK 作为 Release 附件上传

## 重新打包

```bat
gradlew.bat assembleDebug
```
