---
name: kanshu-writing
description: >-
  看书双端写作（Android 写点东西 + desktop-writer）的格式、功能与改动约定。
  在修改写作编辑器、草稿同步、插图、AI 续写/润色、字数目标或 .draft.txt 时使用。
---

# 看书双端写作

## 两端入口

| 端 | 路径 |
| --- | --- |
| Android | `app/.../ui/write/WriteScreen.kt` + `WriteViewModel.kt` |
| 桌面 | `desktop-writer/`（Electron，与手机共用草稿格式） |
| 块模型 | Android `WriteMarkers.kt` / Desktop `src/write-blocks.js` |

## 书库互通（catalog.json）

- **索引**：`default-books/catalog.json` 是唯一书库清单（title/author/folder/进度）
- **Android**：书库页点云下载图标 → `DefaultBooksSync` 拉 catalog + 缺失正文/draft
- **桌面**：侧栏「同步书库」→ `library-sync.js` 同样逻辑写入本地 `default-books/`
- **上传**：任一端写作后点「上传 GitHub」→ 更新 catalog + `{id}.txt` + `{id}.draft.txt` + 插图
- **朋友可见**：对方在手机/桌面各同步一次即可看到新书

## 写小说用什么格式

| 用途 | 文件 | 说明 |
| --- | --- | --- |
| **写作源稿（双端编辑）** | `{id}.draft.txt` | 块格式 + `[[IMG:...]]`，**日常写小说用这个** |
| **阅读/分享正文** | `{id}.txt` | 保存时自动导出，UTF-8，插图变 `【图片：xxx】` |
| **定稿带图** | `{id}.pdf` | 仅 Android 可导出 |
| **索引** | `catalog.json` | 不要手改；由上传流程维护 |
| **导入他人书** | EPUB/TXT | 仅阅读，不是写作主格式 |

## 共享格式（必须对齐）

- 草稿：`{id}.draft.txt`
- 导出：`{id}.txt`（图片 → `【图片：文件名】`）
- 插图：`write_assets/img_{uuid}.{ext}`
- 标记：`[[IMG:write_assets/xxx.jpg]]` 或 `|w=0.75`
- 章节行：`第N章` / 序章/终章/楔子/尾声/番外 / `Chapter N`
- 分页：章节硬切 + 约 1600 字软切

改块模型时**两端同步改**。

## 已有能力（勿重复造轮子）

- 字数统计、每日目标、会话增量
- 专注模式、章节大纲跳转
- ~18s 自动保存
- AI：续写 / 润色 / 扩写（DeepSeek，先预览再写入）
- GitHub 上传含 `write_assets`

## 改动原则

1. 优先增强现有块编辑，不要换成另一套文档格式
2. AI 结果必须可预览/可丢弃，禁止静默覆盖
3. 桌面与手机功能尽量对称；桌面暂无 PDF 导出可保留差异
4. Token / DeepSeek Key 只存本机配置，勿写入仓库
