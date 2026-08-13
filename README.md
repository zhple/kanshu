# 看书

本地 Android 电子书阅读器。导入 TXT / EPUB / PDF，按屏分页滑动翻页，支持文件夹分类、日夜模式，以及从本仓库同步书目、检查 GitHub Release 更新。

适合自己和朋友私下用：装好就能看仓库里的默认书，也能把本地书传到仓库给对方同步。

## 下载安装

- 最新安装包：[Releases](https://github.com/zhple/kanshu/releases/latest)
- 当前推荐：`kanshu-v1.4.2.apk`（[v9](https://github.com/zhple/kanshu/releases/tag/v9)）
- 仓库内也有副本：`release/kanshu-v1.4.2.apk`

Android 8.0+（API 24+）。首次安装若提示「未知来源」，按系统提示允许即可。

## 功能

| 能力 | 说明 |
| --- | --- |
| 格式 | TXT、EPUB、PDF |
| 书架 | 导入、改书名、删除、进度记忆 |
| 分类 | 新建文件夹，书籍可移动 |
| 阅读 | 左右滑动翻页；点中间显隐工具栏；目录跳转 |
| 主题 | 白天 / 黑夜 |
| 仓库书 | 启动或检查更新时，同步 `default-books/` |
| 上传 | 本地书可上传到仓库，供其他人同步 |
| 更新 | 检查 GitHub Release，有新版本会提示 |

## 使用

1. 右下角 **+** 导入 `.txt` / `.epub` / `.pdf`
2. 顶栏可切换「全部 / 仓库书 / 我的上传」，可新建文件夹
3. 点书右侧菜单：**改书名 / 上传 / 移动 / 删除**
4. 阅读页左右滑翻页；点中间出工具栏；可打开目录
5. 右上角可同步仓库书、检查应用更新

### 仓库书怎么来的

仓库里的 [`default-books/`](default-books/) 是共享书库。App 会按 `catalog.json` 自动下载到本地书架。

上传流程（给朋友用的安装包一般已内置权限，通常不用再配 Token）：

1. 书架里对本地书打开菜单 → **上传到远程仓库**
2. 成功后会写入 `default-books/` 并更新 `catalog.json`
3. 对方打开 App 或点同步后即可看到

若要用自己的 GitHub 账号上传：在 App 设置里粘贴 Personal Access Token（需要仓库 `contents` 写权限）。更省事的做法是用已内置上传权限的 Release 安装包。

## 本地构建

需要 JDK 17+、Android SDK。

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

可选：在 `local.properties`（已 gitignore）写入上传 Token，打包时会打进 APK，方便给朋友直接用：

```properties
sdk.dir=...你的 SDK 路径...
github.upload.token=ghp_xxxxxxxx
```

## 发布新版本

1. 提高 `app/build.gradle.kts` 里的 `versionCode` / `versionName`
2. 打 Tag（如 `v10`），打包 APK
3. 上传到 [GitHub Releases](https://github.com/zhple/kanshu/releases)
4. 用户端「检查更新」会读 latest Release

## 技术栈

Kotlin · Jetpack Compose · Room · DataStore · OkHttp · GitHub Contents / Releases API

## 说明

- 默认书与用户上传书仅供个人/朋友间分享，请自行确认版权与使用范围。
- Token 若打进 APK，拿到安装包的人都能用该权限上传；只适合小范围使用。
