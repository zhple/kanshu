# 共享歌单

本目录的歌曲会在 App「共享歌单」页点同步时拉取。曲目以 `playlist.json` 为准。

## 从 App 分享

1. 在书架设置里填写 GitHub Token（勾选 `repo`）
2. 导入本地音频后，点云朵上传
3. 成功后会把文件写到本目录，并更新 `playlist.json`

单文件建议小于 40MB（GitHub Contents API 限制）。支持 mp3 / m4a / aac / ogg / wav / flac。
