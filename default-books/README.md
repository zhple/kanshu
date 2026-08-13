# 仓库默认书 / 用户上传书

本目录的书会在 App 启动、检查更新，或点「同步仓库书」时同步到书架。书目以 `catalog.json` 为准。

## 从 App 上传

1. 在 App 设置里填写 GitHub Token（勾选 `repo`，只保存在手机）
2. 对本地书打开菜单 → **上传到远程仓库**
3. 成功后会把文件写到本目录，并更新 `catalog.json`

不要把 Token 放进仓库或安装包；公开仓库尤其要注意。

## 当前书目

见 [`catalog.json`](catalog.json)。支持 `folders` 分类列表，以及每本书的 `folder` 字段；App 里创建/移动分类会写入这里。
