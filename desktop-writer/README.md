# 看书 · 桌面写作端

与 Android 手机端**共用同一套写作格式**（`.draft.txt` + `[[IMG:write_assets/...]]`）。

## 下载安装

到 [Releases](https://github.com/zhple/kanshu/releases) 找 tag 以 `writer-v` 开头的版本：

| 文件 | 说明 |
| --- | --- |
| `kanshu-writer-setup-x.y.z.exe` | **推荐**：NSIS 安装包，可选目录，开始菜单/桌面快捷方式 |
| `kanshu-writer-x.y.z-portable.exe` | 便携版，免安装直接运行 |

应用启动约 4 秒后会**自动检查更新**（可在设置关闭）。也可点侧栏「检查更新」，或菜单 **文件 → 检查更新**。

桌面更新通道与手机 APK 分开：只认 `writer-v*` Release，避免被手机版抢 `latest`。

## 功能

- 编辑 `.draft.txt`（段落块 + 插图，分页）
- 章节大纲、专注模式、字数 / 每日目标、自动保存
- AI 续写 / 润色 / 扩写（DeepSeek）
- 上传 GitHub `default-books/`（含插图）
- 自动更新（下载安装包并打开安装程序）

## 开发启动

```bash
cd desktop-writer
npm install
npm start
```

## 打包

```bash
npm run dist
```

产物在 `desktop-writer/dist/`：

- `kanshu-writer-setup-1.2.0.exe`
- `kanshu-writer-1.2.0-portable.exe`

发布时请打 tag：`writer-v1.2.0`，并把上述两个 exe 挂到该 Release。

## 注意

- Token / DeepSeek Key 存在 `%APPDATA%/kanshu-writer/config.json`
- 勿提交 `node_modules/`、`dist/`
