# 仓库默认书 / 用户上传书

本目录的书会在 App 启动、检查更新，或点「同步仓库书」时同步到书架。书目以 `catalog.json` 为准。

## 从 App 上传

1. 对本地书打开菜单 → **上传到远程仓库**
2. 成功后会把文件写到本目录，并更新 `catalog.json`

给朋友用的 Release 安装包一般已内置上传权限，通常不用再配 Token。若要自己配：在 App 设置里填 GitHub Token（需 `contents: write`）。

## 当前书目

见 [`catalog.json`](catalog.json)。
