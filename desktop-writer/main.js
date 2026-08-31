const { app, BrowserWindow, ipcMain, dialog, Menu, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const fsp = fs.promises;
const { randomUUID } = require('crypto');
const { promptAndUpdate, checkForUpdate } = require('./updater');
const { syncLibrary, listLibrary } = require('./library-sync');

let mainWindow;
const configPath = () => path.join(app.getPath('userData'), 'config.json');

async function readConfig() {
  try {
    const raw = await fsp.readFile(configPath(), 'utf8');
    return JSON.parse(raw);
  } catch {
    return {
      workspace: '',
      githubToken: '',
      githubOwner: 'zhple',
      githubRepo: 'kanshu',
      githubBranch: 'main',
      deepseekApiKey: '',
      dailyGoal: 1000,
      dailyDone: 0,
      dailyDate: '',
      autoCheckUpdate: true
    };
  }
}

async function writeConfig(cfg) {
  await fsp.mkdir(path.dirname(configPath()), { recursive: true });
  await fsp.writeFile(configPath(), JSON.stringify(cfg, null, 2), 'utf8');
}

function resolveBooksDir(workspace) {
  const draftInDefault = path.join(workspace, 'default-books');
  if (fs.existsSync(draftInDefault)) return draftInDefault;
  return workspace;
}

function assetsDir(booksDir) {
  return path.join(booksDir, 'write_assets');
}

async function readLocalCatalog(booksDir) {
  const catalogPath = path.join(booksDir, 'catalog.json');
  try {
    return JSON.parse(await fsp.readFile(catalogPath, 'utf8'));
  } catch {
    return { version: 1, folders: ['仓库书'], books: [] };
  }
}

async function writeLocalCatalog(booksDir, root) {
  root.version = (root.version || 0) + 1;
  await fsp.writeFile(
    path.join(booksDir, 'catalog.json'),
    JSON.stringify(root, null, 2),
    'utf8'
  );
}

async function upsertLocalCatalog(booksDir, spec) {
  const root = await readLocalCatalog(booksDir);
  if (!Array.isArray(root.folders)) root.folders = ['仓库书'];
  const folder = (spec.folder || '仓库书').trim() || '仓库书';
  if (!root.folders.includes(folder)) root.folders.push(folder);
  if (!Array.isArray(root.books)) root.books = [];
  const idx = root.books.findIndex((b) => b.id === spec.id);
  const prev = idx >= 0 ? root.books[idx] : {};
  const entry = {
    ...prev,
    id: spec.id,
    title: spec.title || prev.title || spec.id,
    author: spec.author || prev.author || '我写的',
    file: spec.file || prev.file || `${spec.id}.txt`,
    format: spec.format || prev.format || 'TXT',
    folder,
    chapterIndex: prev.chapterIndex ?? 0,
    scrollOffset: prev.scrollOffset ?? 0,
    lastReadAt: spec.lastReadAt ?? prev.lastReadAt ?? Date.now()
  };
  if (idx >= 0) root.books[idx] = entry;
  else root.books.push(entry);
  await writeLocalCatalog(booksDir, root);
  return entry;
}

function buildAppMenu() {
  const template = [
    {
      label: '文件',
      submenu: [
        {
          label: '检查更新',
          click: () => {
            if (mainWindow) promptAndUpdate(mainWindow, { silentIfCurrent: false });
          }
        },
        { type: 'separator' },
        { role: 'quit', label: '退出' }
      ]
    },
    {
      label: '编辑',
      submenu: [
        { role: 'undo', label: '撤销' },
        { role: 'redo', label: '重做' },
        { type: 'separator' },
        { role: 'cut', label: '剪切' },
        { role: 'copy', label: '复制' },
        { role: 'paste', label: '粘贴' },
        { role: 'selectAll', label: '全选' }
      ]
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '关于看书写作',
          click: () => {
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: '关于',
              message: `看书写作 ${app.getVersion()}`,
              detail: '与手机端共享 .draft.txt 格式的桌面写作工具。\n更新通道：GitHub Release tag writer-v*'
            });
          }
        },
        {
          label: '打开发布页',
          click: () => shell.openExternal('https://github.com/zhple/kanshu/releases')
        }
      ]
    }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 820,
    minWidth: 900,
    minHeight: 640,
    title: `看书 · 写作 ${app.getVersion()}`,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));
}

app.whenReady().then(async () => {
  buildAppMenu();
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });

  // 启动后静默检查更新（安装版 / 便携版均可用）
  setTimeout(async () => {
    try {
      const cfg = await readConfig();
      if (cfg.autoCheckUpdate === false) return;
      if (!mainWindow || mainWindow.isDestroyed()) return;
      const token = cfg.githubToken?.trim() || '';
      await promptAndUpdate(mainWindow, { silentIfCurrent: true, token });
    } catch {
      /* ignore startup update errors */
    }
  }, 4000);
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

ipcMain.handle('get-app-info', () => ({
  version: app.getVersion(),
  name: app.getName(),
  isPackaged: app.isPackaged,
  userData: app.getPath('userData')
}));

ipcMain.handle('check-update', async (_e, opts = {}) => {
  const cfg = await readConfig();
  const token = cfg.githubToken?.trim() || '';
  if (opts?.prompt) {
    return promptAndUpdate(mainWindow, { silentIfCurrent: false, token });
  }
  return checkForUpdate(app.getVersion(), token);
});

ipcMain.handle('sync-library', async (_e, opts = {}) => {
  const cfg = await readConfig();
  const booksDir = opts.booksDir || resolveBooksDir(cfg.workspace || '');
  if (!booksDir || !cfg.workspace) {
    throw new Error('请先选择 kanshu 仓库目录（含 default-books）');
  }
  return syncLibrary(booksDir, cfg, (msg) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('library-sync-progress', msg);
    }
  });
});

ipcMain.handle('list-library', async () => {
  const cfg = await readConfig();
  if (!cfg.workspace) return { books: [], folders: ['仓库书'], version: 0 };
  const booksDir = resolveBooksDir(cfg.workspace);
  return listLibrary(booksDir);
});

ipcMain.handle('get-config', readConfig);

ipcMain.handle('save-config', async (_e, cfg) => {
  await writeConfig(cfg);
  return true;
});

ipcMain.handle('pick-workspace', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    title: '选择工作目录（kanshu 仓库根目录或 default-books 文件夹）',
    properties: ['openDirectory']
  });
  if (res.canceled || !res.filePaths[0]) return null;
  const workspace = res.filePaths[0];
  const cfg = await readConfig();
  cfg.workspace = workspace;
  await writeConfig(cfg);
  return workspace;
});

ipcMain.handle('list-drafts', async () => {
  const cfg = await readConfig();
  if (!cfg.workspace) return { drafts: [], booksDir: '' };
  const booksDir = resolveBooksDir(cfg.workspace);
  const entries = await fsp.readdir(booksDir, { withFileTypes: true });
  let catalogTitles = {};
  try {
    const catalogRaw = await fsp.readFile(path.join(booksDir, 'catalog.json'), 'utf8');
    const catalog = JSON.parse(catalogRaw);
    for (const book of catalog.books || []) {
      if (book?.id && book?.title) catalogTitles[book.id] = book.title;
    }
  } catch { /* optional */ }
  const drafts = [];
  for (const ent of entries) {
    if (!ent.isFile() || !ent.name.endsWith('.draft.txt')) continue;
    const full = path.join(booksDir, ent.name);
    const stat = await fsp.stat(full);
    const remoteId = ent.name.replace(/\.draft\.txt$/, '');
    let title = catalogTitles[remoteId] || '';
    if (!title) {
      try {
        const head = (await fsp.readFile(full, 'utf8')).split(/\r?\n/).map(l => l.trim()).find(l => l && !l.startsWith('[[IMG:'));
        title = head ? head.replace(/^#+\s*/, '').slice(0, 40) : remoteId;
      } catch {
        title = remoteId;
      }
    }
    drafts.push({
      remoteId,
      fileName: ent.name,
      title,
      mtime: stat.mtimeMs
    });
  }
  drafts.sort((a, b) => b.mtime - a.mtime);
  return { drafts, booksDir };
});

ipcMain.handle('read-draft', async (_e, remoteId) => {
  const cfg = await readConfig();
  const booksDir = resolveBooksDir(cfg.workspace);
  const draftPath = path.join(booksDir, `${remoteId}.draft.txt`);
  const content = await fsp.readFile(draftPath, 'utf8');
  let title = remoteId;
  let folder = '仓库书';
  try {
    const catalogRaw = await fsp.readFile(path.join(booksDir, 'catalog.json'), 'utf8');
    const catalog = JSON.parse(catalogRaw);
    const hit = (catalog.books || []).find(b => b.id === remoteId);
    if (hit?.title) title = hit.title;
    if (hit?.folder) folder = hit.folder;
  } catch { /* optional */ }
  if (title === remoteId) {
    const head = content.split(/\r?\n/).map(l => l.trim()).find(l => l && !l.startsWith('[[IMG:'));
    if (head) title = head.replace(/^#+\s*/, '').slice(0, 40);
  }
  return { content, draftPath, booksDir, title, folder };
});

ipcMain.handle('save-draft', async (_e, payload) => {
  const { remoteId, title, content, exportTxt } = payload;
  const cfg = await readConfig();
  const booksDir = resolveBooksDir(cfg.workspace);
  await fsp.mkdir(booksDir, { recursive: true });
  await fsp.mkdir(assetsDir(booksDir), { recursive: true });

  const draftPath = path.join(booksDir, `${remoteId}.draft.txt`);
  await fsp.writeFile(draftPath, content, 'utf8');

  if (exportTxt !== false) {
    const plain = stripImagesForPlainText(content);
    const txtPath = path.join(booksDir, `${remoteId}.txt`);
    await fsp.writeFile(txtPath, '\uFEFF' + plain, 'utf8');
  }

  const catalog = await readLocalCatalog(booksDir);
  const existing = catalog.books?.find((b) => b.id === remoteId);
  await upsertLocalCatalog(booksDir, {
    id: remoteId,
    title: title || remoteId,
    folder: existing?.folder || payload.folder || '仓库书',
    file: existing?.file || `${remoteId}.txt`,
    format: existing?.format || 'TXT'
  });

  return { draftPath, title, remoteId, booksDir };
});

ipcMain.handle('create-draft', async (_e, payload) => {
  const title = typeof payload === 'string' ? payload : payload?.title;
  const folder = typeof payload === 'object' ? payload?.folder : '';
  const cfg = await readConfig();
  if (!cfg.workspace?.trim()) {
    throw new Error('请先点击「打开目录」选择 kanshu 仓库目录');
  }
  const booksDir = resolveBooksDir(cfg.workspace);
  if (!booksDir?.trim()) {
    throw new Error('工作目录无效，请重新选择');
  }
  await fsp.mkdir(booksDir, { recursive: true });
  const remoteId = 'user-' + randomUUID().replace(/-/g, '').slice(0, 10);
  const draftPath = path.join(booksDir, `${remoteId}.draft.txt`);
  await fsp.writeFile(draftPath, '', 'utf8');
  const safeTitle = (title || '未命名').trim() || '未命名';
  const safeFolder = (folder || '仓库书').trim() || '仓库书';
  await upsertLocalCatalog(booksDir, {
    id: remoteId,
    title: safeTitle,
    folder: safeFolder,
    file: `${remoteId}.txt`,
    format: 'TXT'
  });
  return { remoteId, title: safeTitle, folder: safeFolder, draftPath, booksDir };
});

ipcMain.handle('read-book', async (_e, remoteId) => {
  const cfg = await readConfig();
  if (!cfg.workspace?.trim()) throw new Error('请先选择工作目录');
  const booksDir = resolveBooksDir(cfg.workspace);
  const catalog = await readLocalCatalog(booksDir);
  const book = (catalog.books || []).find((b) => b.id === remoteId);
  if (!book?.file) throw new Error('catalog 中找不到这本书');
  const filePath = path.join(booksDir, book.file);
  if (!fs.existsSync(filePath)) throw new Error(`文件缺失：${book.file}`);
  const raw = await fsp.readFile(filePath, 'utf8');
  return {
    content: raw.replace(/^\uFEFF/, ''),
    title: book.title || remoteId,
    author: book.author || '',
    folder: book.folder || '仓库书',
    format: (book.format || 'TXT').toUpperCase(),
    booksDir
  };
});

ipcMain.handle('pick-image', async (_e, remoteId) => {
  const cfg = await readConfig();
  const booksDir = resolveBooksDir(cfg.workspace);
  const dir = assetsDir(booksDir);
  await fsp.mkdir(dir, { recursive: true });

  const res = await dialog.showOpenDialog(mainWindow, {
    title: '选择插图',
    filters: [{ name: 'Images', extensions: ['jpg', 'jpeg', 'png', 'webp', 'gif'] }],
    properties: ['openFile']
  });
  if (res.canceled || !res.filePaths[0]) return null;

  const src = res.filePaths[0];
  const ext = path.extname(src).toLowerCase() || '.jpg';
  const name = `img_${randomUUID()}${ext}`;
  const dest = path.join(dir, name);
  await fsp.copyFile(src, dest);
  return { relativePath: `write_assets/${name}`, absolutePath: dest };
});

ipcMain.handle('resolve-asset', async (_e, booksDir, relativePath) => {
  const fileName = relativePath.replace(/^write_assets[\\/]/, '');
  const candidates = [
    path.join(booksDir, relativePath),
    path.join(booksDir, 'write_assets', fileName),
    path.join(assetsDir(booksDir), fileName)
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) return 'file:///' + p.replace(/\\/g, '/');
  }
  return null;
});

ipcMain.handle('github-upload', async (_e, payload) => {
  const cfg = await readConfig();
  if (!cfg.githubToken?.trim()) throw new Error('请先在设置里填写 GitHub Token');
  const { remoteId, title, content, booksDir } = payload;
  const gh = createGithubClient(cfg);

  const plain = stripImagesForPlainText(content);
  await gh.putFile(`default-books/${remoteId}.txt`, Buffer.from('\uFEFF' + plain, 'utf8'), `Update book: ${title}`);
  await gh.putFile(`default-books/${remoteId}.draft.txt`, Buffer.from(content, 'utf8'), `Update draft: ${title}`);

  const imagePaths = [...content.matchAll(/\[\[IMG:([^|\]]+?)(?:\|w=[0-9.]+)?\]\]/g)].map(m => m[1].trim());
  for (const rel of imagePaths) {
    const fileName = rel.replace(/^write_assets[\\/]/, '');
    const local = path.join(booksDir, rel);
    const alt = path.join(assetsDir(booksDir), fileName);
    const filePath = fs.existsSync(local) ? local : alt;
    if (fs.existsSync(filePath)) {
      await gh.putFile(`default-books/write_assets/${fileName}`, await fsp.readFile(filePath), `Upload asset ${fileName}`);
    }
  }

  const catalog = await readLocalCatalog(booksDir);
  const existing = catalog.books?.find((b) => b.id === remoteId);
  const folder = existing?.folder || '仓库书';

  await gh.upsertCatalog(remoteId, {
    id: remoteId,
    title,
    author: existing?.author || '我写的',
    file: existing?.file || `${remoteId}.txt`,
    format: existing?.format || 'TXT',
    folder
  });

  await upsertLocalCatalog(booksDir, {
    id: remoteId,
    title,
    folder,
    file: existing?.file || `${remoteId}.txt`,
    format: existing?.format || 'TXT',
    author: existing?.author || '我写的'
  });

  return '已上传到 GitHub default-books/';
});

ipcMain.handle('write-assist', async (_e, payload) => {
  const cfg = await readConfig();
  const key = (cfg.deepseekApiKey || '').trim();
  if (!key) throw new Error('请先在设置里填写 DeepSeek API Key');
  const mode = payload.mode || 'CONTINUE';
  const system = {
    CONTINUE: '你是中文小说写作助手。根据前文自然续写 200–500 字。保持人称语气一致；不要复述前文；不要输出标题或解释；只输出续写正文。',
    POLISH: '你是中文小说润色助手。在保持原意与情节的前提下润色段落：修正语病、提升文采与节奏，不要大幅扩写或删减情节。只输出润色后的段落正文，不要解释。',
    EXPAND: '你是中文小说扩写助手。把给定段落扩写到约 1.5–2.5 倍长度，补充感官细节、对话与心理活动，不改变既有情节走向。只输出扩写后的段落正文，不要解释。'
  }[mode] || '你是中文写作助手。只输出正文，不要解释。';

  let user = '';
  if (payload.title) user += `【书名】${payload.title}\n\n`;
  if (payload.precedingText) user += `【前文摘录】\n${String(payload.precedingText).slice(-1800)}\n\n`;
  if (mode === 'CONTINUE') {
    user += `请从这里继续写：\n${String(payload.focusText || '（请开篇）').slice(-600)}`;
  } else {
    if (!String(payload.focusText || '').trim()) throw new Error('请先选中或聚焦一段文字');
    user += `【待处理段落】\n${payload.focusText}`;
  }
  if (payload.userHint?.trim()) user += `\n\n【作者补充】${payload.userHint.trim()}`;

  const res = await fetch('https://api.deepseek.com/chat/completions', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      model: 'deepseek-v4-pro',
      temperature: mode === 'POLISH' ? 0.55 : 0.85,
      stream: false,
      thinking: { type: 'disabled' },
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user }
      ]
    })
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`DeepSeek 失败 HTTP ${res.status}: ${text.slice(0, 180)}`);
  const json = JSON.parse(text);
  const content = json?.choices?.[0]?.message?.content?.trim();
  if (!content) throw new Error('模型返回为空');
  return content;
});

function stripImagesForPlainText(content) {
  return content.replace(/\[\[IMG:([^|\]]+?)(?:\|w=[0-9]*\.?[0-9]+)?\]\]/g, (_m, p1) => {
    const name = p1.split(/[\\/]/).pop();
    return `【图片：${name}】`;
  });
}

function createGithubClient(cfg) {
  const base = `https://api.github.com/repos/${cfg.githubOwner}/${cfg.githubRepo}/contents`;
  const branch = cfg.githubBranch || 'main';
  const token = cfg.githubToken.trim();

  async function getFile(repoPath) {
    const res = await fetch(`${base}/${repoPath}?ref=${branch}`, {
      headers: githubHeaders(token)
    });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`GitHub 读取失败 HTTP ${res.status}`);
    const json = await res.json();
    return { content: Buffer.from(json.content.replace(/\n/g, ''), 'base64'), sha: json.sha };
  }

  async function putFile(repoPath, bytes, message) {
    const existing = await getFile(repoPath);
    const body = {
      message,
      branch,
      content: Buffer.from(bytes).toString('base64')
    };
    if (existing?.sha) body.sha = existing.sha;
    const res = await fetch(`${base}/${repoPath}`, {
      method: 'PUT',
      headers: { ...githubHeaders(token), 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error(`GitHub 上传失败 HTTP ${res.status}`);
  }

  async function upsertCatalog(remoteId, spec) {
    const path = 'default-books/catalog.json';
    const existing = await getFile(path);
    let root;
    if (existing) {
      root = JSON.parse(existing.content.toString('utf8'));
    } else {
      root = { version: 1, folders: ['仓库书'], books: [] };
    }
    if (!Array.isArray(root.folders)) root.folders = ['仓库书'];
    if (!Array.isArray(root.books)) root.books = [];
    const folder = (spec.folder || '仓库书').trim() || '仓库书';
    if (!root.folders.includes(folder)) root.folders.push(folder);
    const idx = root.books.findIndex(b => b.id === remoteId);
    const entry = {
      id: remoteId,
      title: spec.title,
      author: spec.author,
      file: spec.file,
      format: spec.format,
      folder: spec.folder,
      chapterIndex: 0,
      scrollOffset: 0,
      lastReadAt: Date.now()
    };
    if (idx >= 0) root.books[idx] = { ...root.books[idx], ...entry };
    else root.books.push(entry);
    root.version = (root.version || 1) + 1;
    await putFile(path, Buffer.from(JSON.stringify(root, null, 2), 'utf8'), `Update catalog for ${remoteId}`);
  }

  return { putFile, upsertCatalog };
}

function githubHeaders(token) {
  return {
    Accept: 'application/vnd.github+json',
    Authorization: `Bearer ${token}`,
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'Kanshu-Desktop-Writer'
  };
}
