const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('kanshu', {
  getAppInfo: () => ipcRenderer.invoke('get-app-info'),
  checkUpdate: (opts) => ipcRenderer.invoke('check-update', opts || {}),
  syncLibrary: () => ipcRenderer.invoke('sync-library'),
  listLibrary: () => ipcRenderer.invoke('list-library'),
  onLibrarySyncProgress: (cb) => {
    const listener = (_e, msg) => cb(msg);
    ipcRenderer.on('library-sync-progress', listener);
    return () => ipcRenderer.removeListener('library-sync-progress', listener);
  },
  getConfig: () => ipcRenderer.invoke('get-config'),
  saveConfig: (cfg) => ipcRenderer.invoke('save-config', cfg),
  pickWorkspace: () => ipcRenderer.invoke('pick-workspace'),
  listDrafts: () => ipcRenderer.invoke('list-drafts'),
  readDraft: (remoteId) => ipcRenderer.invoke('read-draft', remoteId),
  saveDraft: (payload) => ipcRenderer.invoke('save-draft', payload),
  createDraft: (payload) => ipcRenderer.invoke('create-draft', payload),
  readBook: (remoteId) => ipcRenderer.invoke('read-book', remoteId),
  pickImage: (remoteId) => ipcRenderer.invoke('pick-image', remoteId),
  resolveAsset: (booksDir, relativePath) => ipcRenderer.invoke('resolve-asset', booksDir, relativePath),
  githubUpload: (payload) => ipcRenderer.invoke('github-upload', payload),
  writeAssist: (payload) => ipcRenderer.invoke('write-assist', payload)
});
