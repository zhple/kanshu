# 看书

本地 Android 电子书阅读器。导入 TXT / EPUB / PDF，按屏分页滑动翻页，支持文件夹分类、日夜模式，以及从本仓库同步书目、检查 GitHub Release 更新。

适合自己和朋友私下用：装好就能看仓库里的默认书；有上传权限的人可以把本地书传到仓库，对方同步后即可看到。

## 下载安装

- 最新安装包：[Releases](https://github.com/zhple/kanshu/releases/latest)
- 当前推荐：`kanshu-v1.6.0.apk`（[v10](https://github.com/zhple/kanshu/releases/tag/v14)）
- 仓库内也有副本：`release/kanshu-v1.6.0.apk`

Android 8.0+（API 24+）。首次安装若提示「未知来源」，按系统提示允许即可。

## 功能

| 能力 | 说明 |
| --- | --- |
| 格式 | TXT、EPUB、PDF |
| 书架 | 导入、写作、改书名、删除、进度记忆 |
| 分类 | 新建文件夹；仓库书分类可同步到远程 `catalog.json` |
| 阅读 | 左右滑动翻页；点中间显隐工具栏；目录跳转 |
| 主题 | 白天 / 黑夜 |
| 仓库书 | 启动或检查更新时，同步 `default-books/` |
| 上传 | 在 App 里填写 Token 后，可将本地书上传到仓库 |
| 角色聊天 | DeepSeek 角色场景扮演：优化提示词、流式对话、本地保存会话 |
| 更新 | 检查 GitHub Release，有新版本会提示 |

## 使用

1. 右下角 **+** 导入 `.txt` / `.epub` / `.pdf`
2. 顶栏可切换「全部 / 仓库书 / 我的上传」，可新建文件夹
3. 点书右侧菜单：**改书名 / 上传 / 移动 / 删除**
4. 阅读页左右滑翻页；点中间出工具栏；可打开目录
5. 右上角可同步仓库书、检查应用更新

### 上传到远程仓库

仓库是公开的，**不要把 Token 写进代码或打进安装包**。只在手机 App 里填写：

1. 打开 [创建 Token](https://github.com/settings/tokens/new)，勾选 **`repo`**，生成后复制
2. App 右上角设置 → 粘贴 Token → 保存（只存在本机）
3. 对本地书打开菜单 → **上传到远程仓库**
4. 成功后会写入 [`default-books/`](default-books/)，对方同步即可看到

朋友只看书、不同步上传的话，不用填 Token。

### 角色场景聊天

1. 书架右下角对话框图标进入
2. 设置里填写 DeepSeek API Key（只存在本机，勿提交到仓库）
3. 新建：填写场景、对方人设、我的人设 → 生成并确认提示词 → AI 开场后开始聊

## 本地构建

需要 JDK 17+、Android SDK。

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

`local.properties` 只需配置 SDK 路径（已 gitignore），不要放 Token。

## 发布新版本

1. 提高 `app/build.gradle.kts` 里的 `versionCode` / `versionName`
2. 打 Tag（如 `v10`），打包 APK
3. 上传到 [GitHub Releases](https://github.com/zhple/kanshu/releases)
4. 用户端「检查更新」会读 latest Release

## 技术栈

Kotlin · Jetpack Compose · Room · DataStore · GitHub Contents / Releases API

## 说明

- 默认书与用户上传书仅供个人/朋友间分享，请自行确认版权与使用范围。
- Token 仅保存在手机本地；公开仓库请勿把 Token 提交到 git 或内置进 APK。
