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
