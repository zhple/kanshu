# 看书 · 桌面写作端

与 Android 手机端**共用同一套写作格式**（`.draft.txt` + `[[IMG:write_assets/...]]`），方便在电脑上写长文，保存后手机同步即可继续看/编。

## 功能

- 打开 kanshu 仓库根目录（或 `default-books/` 文件夹）
- 编辑 `.draft.txt` 文稿（段落块 + 插图，分页浏览）
- 自动导出同名 `.txt`（UTF-8 BOM，图片替换为【图片：xxx】）
- 插图保存到 `write_assets/`
- 可选：上传到 GitHub `default-books/`（含 catalog、draft、txt、插图）

## 环境

- Node.js 18+
- Windows / macOS / Linux

## 启动

```bash
cd desktop-writer
npm install
npm start
```

## 使用步骤

1. 首次启动点 **打开目录**，选择本仓库根目录 `kanshu`（或其中的 `default-books`）
2. 左侧列表选已有 `.draft.txt`，或 **新建文稿**
3. 写作、插图、**下一章**（与手机端规则一致）
4. **Ctrl+S** 或点 **保存**
5. 设置里填 GitHub Token（`repo` 权限）后，可 **上传 GitHub**，手机端同步仓库书即可看到

## 与手机端格式对齐

| 项目 | 格式 |
| --- | --- |
| 草稿 | `{id}.draft.txt` |
| 导出 | `{id}.txt` |
| 图片 | `write_assets/img_{uuid}.jpg` |
| 图片标记 | `[[IMG:write_assets/xxx.jpg\|w=0.75]]` |

## 打包 Windows 便携版

```bash
npm run pack
```

输出在 `desktop-writer/dist/`。

## 注意

- Token 只存在本机 `%APPDATA%/kanshu-writer/config.json`，勿提交到 git
- 上传时会同步 `default-books/write_assets/` 下的插图，手机端需更新到支持拉取插图的版本
