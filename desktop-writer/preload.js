const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('kanshu', {
  getConfig: () => ipcRenderer.invoke('get-config'),
  saveConfig: (cfg) => ipcRenderer.invoke('save-config', cfg),
  pickWorkspace: () => ipcRenderer.invoke('pick-workspace'),
  listDrafts: () => ipcRenderer.invoke('list-drafts'),
  readDraft: (remoteId) => ipcRenderer.invoke('read-draft', remoteId),
  saveDraft: (payload) => ipcRenderer.invoke('save-draft', payload),
  createDraft: (title) => ipcRenderer.invoke('create-draft', title),
  pickImage: (remoteId) => ipcRenderer.invoke('pick-image', remoteId),
  resolveAsset: (booksDir, relativePath) => ipcRenderer.invoke('resolve-asset', booksDir, relativePath),
  githubUpload: (payload) => ipcRenderer.invoke('github-upload', payload),
  writeAssist: (payload) => ipcRenderer.invoke('write-assist', payload)
});
