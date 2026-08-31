import {
  parse, serialize, buildPages, newId,
  charCount, extractTitleFromContent,
  splitTextOverflow, PAGE_CHAR_BUDGET,
  blocksToChapters, chaptersToBlocks, chapterOutline, chapterCharCount,
  nextChapterTitle, expandBlocksByChapterHeadings
} from './write-blocks.js';

const state = {
  remoteId: null,
  title: '',
  chapters: [{ title: '第1章', pages: [''], images: [] }],
  chapterIndex: 0,
  pageIndex: 0,
  booksDir: '',
  bookFolder: '仓库书',
  libraryFolders: ['仓库书'],
  screen: 'home',
  intent: null,
  libraryLevel: 'folders',
  libraryFolder: null,
  libraryCache: { books: [], folders: [], version: 0 },
  viewMode: 'idle',
  dirty: false,
  config: null,
  focusMode: false,
  autoFocusEditor: false,
  editorWantsFocus: false,
  baselineChars: 0,
  sessionGain: 0,
  autoSaveTimer: null,
  activeEditor: null
};

const el = {
  hubPanel: document.getElementById('hub-panel'),
  hubContent: document.getElementById('hub-content'),
  hubStatus: document.getElementById('hub-status'),
  editorPanel: document.getElementById('editor-panel'),
  btnBack: document.getElementById('btn-back'),
  sidebarEditorTools: document.getElementById('sidebar-editor-tools'),
  outlineList: document.getElementById('outline-list'),
  titleInput: document.getElementById('title-input'),
  blocks: document.getElementById('blocks'),
  pageLabel: document.getElementById('page-label'),
  pageTitle: document.getElementById('page-title'),
  pageSubtitle: document.getElementById('page-subtitle'),
  status: document.getElementById('status'),
  statsText: document.getElementById('stats-text'),
  dirtyBadge: document.getElementById('dirty-badge'),
  sidebar: document.getElementById('sidebar'),
  settingsDialog: document.getElementById('settings-dialog'),
  newDraftDialog: document.getElementById('new-draft-dialog'),
  newDraftTitle: document.getElementById('new-draft-title'),
  newDraftFolder: document.getElementById('new-draft-folder'),
  newDraftForm: document.getElementById('new-draft-form'),
  chapterDialog: document.getElementById('chapter-dialog'),
  chapterSubtitle: document.getElementById('chapter-subtitle'),
  chapterForm: document.getElementById('chapter-form'),
  editorPane: document.getElementById('editor-panel'),
  cfgToken: document.getElementById('cfg-token'),
  cfgOwner: document.getElementById('cfg-owner'),
  cfgRepo: document.getElementById('cfg-repo'),
  cfgBranch: document.getElementById('cfg-branch'),
  cfgAutoUpdate: document.getElementById('cfg-auto-update'),
  appVersion: document.getElementById('app-version'),
  updateProgress: document.getElementById('update-progress'),
  updateProgressTitle: document.getElementById('update-progress-title'),
  updateProgressFill: document.getElementById('update-progress-fill'),
  updateProgressText: document.getElementById('update-progress-text')
};

function setHubStatus(msg, isError = false) {
  if (!el.hubStatus) return;
  el.hubStatus.textContent = msg || '';
  el.hubStatus.style.color = isError ? 'var(--danger)' : 'var(--muted)';
}

function setStatus(msg, isError = false) {
  if (state.screen !== 'editor') {
    setHubStatus(msg, isError);
    if (el.status) el.status.textContent = '';
    return;
  }
  if (el.status) {
    el.status.textContent = msg || '';
    el.status.style.color = isError ? 'var(--danger)' : 'var(--muted)';
  }
  if (el.hubStatus) el.hubStatus.textContent = '';
}

function showUpdateProgress({ phase, progress = 0, message = '', version = '' } = {}) {
  if (!el.updateProgress) return;
  if (phase === 'hide') {
    el.updateProgress.classList.add('hidden');
    return;
  }
  el.updateProgress.classList.remove('hidden');
  const pct = Math.round(Math.min(100, Math.max(0, progress * 100)));
  if (el.updateProgressFill) el.updateProgressFill.style.width = `${pct}%`;
  if (el.updateProgressText) {
    el.updateProgressText.textContent = message || `${pct}%`;
  }
  if (el.updateProgressTitle) {
    if (phase === 'complete') {
      el.updateProgressTitle.textContent = '下载完成';
    } else if (version) {
      el.updateProgressTitle.textContent = `正在下载 v${version}`;
    } else {
      el.updateProgressTitle.textContent = '正在下载更新';
    }
  }
}

function bindUpdateProgressListener() {
  window.kanshu.onUpdateDownloadProgress?.((payload) => showUpdateProgress(payload));
}

function allBlocks() {
  return chaptersToBlocks(state.chapters);
}

function totalCharCount() {
  return state.chapters.reduce((sum, ch) => sum + chapterCharCount(ch), 0);
}

function currentChapter() {
  return state.chapters[state.chapterIndex] || state.chapters[0];
}

function syncEditorToChapter() {
  if (state.activeEditor && state.viewMode === 'write') {
    const ch = currentChapter();
    if (!ch.pages) ch.pages = [''];
    ch.pages[state.pageIndex] = state.activeEditor.value;
  }
}

function commitEditor() {
  syncEditorToChapter();
  state.activeEditor = null;
}

function clampNavIndices() {
  state.chapterIndex = Math.min(Math.max(0, state.chapterIndex), state.chapters.length - 1);
  const ch = currentChapter();
  if (!ch.pages || !ch.pages.length) ch.pages = [''];
  state.pageIndex = Math.min(Math.max(0, state.pageIndex), ch.pages.length - 1);
  state.sessionGain = Math.max(0, totalCharCount() - state.baselineChars);
}

function updateEditorChrome() {
  el.editorPanel?.classList.toggle('read-mode', state.viewMode === 'read');
  if (el.titleInput) el.titleInput.readOnly = state.viewMode === 'read';
}

function updateLayout() {
  const inEditor = state.screen === 'editor';
  el.hubPanel?.classList.toggle('hidden', inEditor);
  el.editorPanel?.classList.toggle('hidden', !inEditor);
  el.btnBack?.classList.toggle('hidden', state.screen === 'home');
  el.sidebarEditorTools?.classList.toggle('hidden', !inEditor);
  document.body.classList.toggle('focus-mode', state.focusMode && inEditor);
  if (!inEditor) renderHub();
  else renderEditor();
}

function updatePageChrome() {
  const ch = currentChapter();
  const pages = ch.pages || [''];
  if (el.pageTitle) el.pageTitle.textContent = ch.title || '正文';
  if (el.pageSubtitle) {
    el.pageSubtitle.textContent = `第 ${state.pageIndex + 1} / ${pages.length} 页 · 本章 ${chapterCharCount(ch)} 字`;
  }
  if (el.pageLabel) {
    el.pageLabel.textContent = `第 ${state.pageIndex + 1} / ${pages.length} 页`;
  }
}

async function init() {
  state.config = await window.kanshu.getConfig();
  try {
    const info = await window.kanshu.getAppInfo();
    if (el.appVersion) el.appVersion.textContent = `v${info.version}`;
  } catch {
    if (el.appVersion) el.appVersion.textContent = '';
  }
  updateWorkspaceLabel();
  bindUpdateProgressListener();
  showUpdateProgress({ phase: 'hide' });
  bindEvents();
  try {
    await loadLibraryCache();
  } catch (e) {
    setHubStatus(e.message || '书库列表加载失败', true);
  }
  startAutoSave();
  updateLayout();
}

function updateWorkspaceLabel() {
  /* workspace shown in hub home */
}

async function loadLibraryCache() {
  const data = await window.kanshu.listLibrary();
  state.libraryCache = {
    books: data.books || [],
    folders: data.folders?.length ? data.folders : ['仓库书'],
    version: data.version || 0
  };
  state.libraryFolders = state.libraryCache.folders;
}

function booksForIntent(folder = null) {
  const books = state.libraryCache.books || [];
  return books.filter((book) => {
    if (folder && (book.folder || '仓库书') !== folder) return false;
    if (state.intent === 'write') return book.hasDraft || book.editable;
    if (state.intent === 'read') {
      return book.hasReadable && (
        (book.format || '').toUpperCase() === 'TXT' || (book.file || '').endsWith('.txt')
      );
    }
    return true;
  });
}

function folderNamesForIntent() {
  const names = [...state.libraryFolders];
  for (const book of state.libraryCache.books || []) {
    const f = book.folder || '仓库书';
    if (!names.includes(f)) names.push(f);
  }
  return names.filter((folder) => booksForIntent(folder).length > 0);
}

function renderHub() {
  if (!el.hubContent) return;
  el.hubContent.innerHTML = '';

  if (state.screen === 'home') {
    const wrap = document.createElement('div');
    wrap.className = 'hub-home';
    const ws = state.config?.workspace?.trim();
    wrap.innerHTML = `
      <h2>你想做什么？</h2>
      <p class="hint">${ws ? '从共享书库选一本书，或开始写新稿' : '请先选择工作目录（含 default-books）'}</p>
      <div class="hub-mode-grid">
        <div class="hub-mode-card" data-intent="read">
          <div class="icon">📖</div>
          <div class="title">看书</div>
          <div class="desc">阅读共享书库里的 TXT</div>
        </div>
        <div class="hub-mode-card" data-intent="write">
          <div class="icon">✍️</div>
          <div class="title">写作</div>
          <div class="desc">选一篇文稿或新建</div>
        </div>
      </div>
    `;
    if (!ws) {
      const pick = document.createElement('p');
      pick.style.marginTop = '20px';
      const btn = document.createElement('button');
      btn.className = 'btn primary';
      btn.textContent = '打开工作目录';
      btn.onclick = async () => {
        const ws = await window.kanshu.pickWorkspace();
        if (ws) {
          state.config = await window.kanshu.getConfig();
          await loadLibraryCache();
          setHubStatus('工作目录已更新');
          renderHub();
        }
      };
      pick.appendChild(btn);
      wrap.appendChild(pick);
    }
    wrap.querySelectorAll('[data-intent]').forEach((card) => {
      card.onclick = () => {
        if (!state.config?.workspace?.trim()) {
          setHubStatus('请先打开工作目录', true);
          return;
        }
        enterLibrary(card.dataset.intent);
      };
    });
    el.hubContent.appendChild(wrap);
    return;
  }

  if (state.screen === 'library') {
    const header = document.createElement('div');
    header.className = 'hub-header';
    const title = state.libraryLevel === 'folders'
      ? (state.intent === 'write' ? '选择要写的文稿' : '选择要读的书')
      : state.libraryFolder;
    header.innerHTML = `<h2>${escapeHtml(title)}</h2>`;
    const actions = document.createElement('div');
    actions.className = 'hub-actions';
    const syncBtn = document.createElement('button');
    syncBtn.className = 'btn secondary';
    syncBtn.textContent = '同步书库';
    syncBtn.onclick = () => syncLibraryFromRemote();
    actions.appendChild(syncBtn);
    if (state.intent === 'write' && state.libraryLevel === 'books') {
      const newBtn = document.createElement('button');
      newBtn.className = 'btn primary';
      newBtn.textContent = '新建文稿';
      newBtn.onclick = () => openNewDraftDialog(state.libraryFolder);
      actions.appendChild(newBtn);
    }
    header.appendChild(actions);
    el.hubContent.appendChild(header);

    const ver = state.libraryCache.version;
    if (ver) {
      const meta = document.createElement('p');
      meta.className = 'hint';
      meta.style.margin = '0 0 10px';
      meta.textContent = `共享书库 v${ver}`;
      el.hubContent.appendChild(meta);
    }

    const list = document.createElement('ul');
    list.className = 'hub-list';

    if (state.libraryLevel === 'folders') {
      const folders = folderNamesForIntent();
      if (!folders.length) {
        list.innerHTML = '<li class="hub-empty meta">书库是空的，点「同步书库」拉取</li>';
      } else {
        for (const folder of folders) {
          const count = booksForIntent(folder).length;
          const li = document.createElement('li');
          li.innerHTML = `<div class="name">${escapeHtml(folder)}</div><div class="meta">${count} 本</div>`;
          li.onclick = () => openLibraryFolder(folder);
          list.appendChild(li);
        }
      }
    } else {
      const books = booksForIntent(state.libraryFolder);
      if (!books.length) {
        list.innerHTML = '<li class="hub-empty meta">这个分类下没有符合条件的书</li>';
      } else {
        for (const book of books) {
          list.appendChild(hubBookItem(book));
        }
      }
    }
    el.hubContent.appendChild(list);
  }
}

function hubBookItem(book) {
  const li = document.createElement('li');
  if (book.id === state.remoteId) li.classList.add('active');
  const label = book.title || book.id;
  const tags = [];
  if (book.hasDraft) tags.push('可编辑');
  else if (book.hasReadable) tags.push('阅读');
  if (book.localOnly) tags.push('本地');
  const meta = tags.length ? tags.join(' · ') : (book.author || book.format || '');
  li.innerHTML = `<div class="name">${escapeHtml(label)}</div><div class="meta">${escapeHtml(meta)}</div>`;
  li.onclick = () => openLibraryBook(book);
  return li;
}

function enterLibrary(intent) {
  state.intent = intent;
  state.screen = 'library';
  state.libraryLevel = 'folders';
  state.libraryFolder = null;
  setHubStatus('');
  updateLayout();
}

function openLibraryFolder(folder) {
  state.libraryLevel = 'books';
  state.libraryFolder = folder;
  updateLayout();
}

function navigateBack() {
  if (state.screen === 'editor') {
    if (state.dirty && !confirm('当前文稿未保存，确定返回？')) return;
    commitEditor();
    state.screen = 'library';
    state.viewMode = 'idle';
    state.remoteId = null;
    state.focusMode = false;
    updateLayout();
    setHubStatus('');
    return;
  }
  if (state.screen === 'library' && state.libraryLevel === 'books') {
    state.libraryLevel = 'folders';
    state.libraryFolder = null;
    updateLayout();
    return;
  }
  if (state.screen === 'library') {
    state.screen = 'home';
    state.intent = null;
    state.libraryLevel = 'folders';
    state.libraryFolder = null;
    updateLayout();
  }
}

async function refreshLibraryList() {
  await loadLibraryCache();
  if (state.screen !== 'editor') renderHub();
}

function updateStats() {
  const chars = totalCharCount();
  const gain = state.sessionGain > 0 ? ` · 本会话 +${state.sessionGain}` : '';
  el.statsText.textContent = `${chars} 字${gain}`;
  el.dirtyBadge.classList.toggle('hidden', !state.dirty);
}

function renderOutline() {
  const items = chapterOutline(state.chapters);
  el.outlineList.innerHTML = '';
  if (!items.length) {
    el.outlineList.innerHTML = '<li class="meta">点「下一章」开始分章写作</li>';
    return;
  }
  for (const item of items) {
    const li = document.createElement('li');
    if (item.chapterIndex === state.chapterIndex) li.classList.add('active');
    li.innerHTML = `<div class="name">${escapeHtml(item.title)}</div><div class="meta">${item.charCount} 字 · ${item.pageCount} 页</div>`;
    li.onclick = () => {
      commitEditor();
      state.chapterIndex = item.chapterIndex;
      state.pageIndex = 0;
      state.autoFocusEditor = state.viewMode === 'write';
      renderEditor();
    };
    el.outlineList.appendChild(li);
  }
}

async function openLibraryBook(book) {
  if (state.intent === 'write') {
    if (book.hasDraft || book.editable) {
      await loadDraft(book.id);
      return;
    }
    setStatus(`「${book.title || book.id}」暂无可编辑文稿`, true);
    return;
  }
  if (book.hasReadable && ((book.format || '').toUpperCase() === 'TXT' || book.file?.endsWith('.txt'))) {
    await openReader(book.id);
    return;
  }
  setStatus(`「${book.title || book.id}」暂无可读 TXT，电脑端暂不支持 ${book.format || '该格式'}`, true);
}

async function openReader(remoteId) {
  if (state.dirty && !confirm('当前文稿未保存，确定切换？')) return;
  commitEditor();
  try {
    const { content, title, booksDir, folder } = await window.kanshu.readBook(remoteId);
    state.screen = 'editor';
    state.viewMode = 'read';
    state.remoteId = remoteId;
    state.booksDir = booksDir;
    state.title = title;
    state.bookFolder = folder || '仓库书';
    state.chapters = blocksToChapters(expandBlocksByChapterHeadings(parse(content)));
    state.chapterIndex = 0;
    state.pageIndex = 0;
    state.dirty = false;
    el.titleInput.value = title;
    clampNavIndices();
    updateLayout();
    await refreshLibraryList();
    setStatus('阅读模式');
  } catch (e) {
    setStatus(e.message || '打开失败', true);
  }
}

async function createNewDraft(title, folder = '仓库书') {
  const trimmed = (title || '').trim() || '未命名';
  const catFolder = (folder || '仓库书').trim() || '仓库书';
  if (!state.config?.workspace?.trim()) {
    setStatus('请先点击「打开目录」选择 kanshu 仓库目录', true);
    return;
  }
  try {
    const { remoteId, booksDir, folder: savedFolder } = await window.kanshu.createDraft({
      title: trimmed,
      folder: catFolder
    });
    state.viewMode = 'write';
    state.screen = 'editor';
    state.remoteId = remoteId;
    state.title = trimmed;
    state.bookFolder = savedFolder || catFolder;
    state.booksDir = booksDir;
    state.chapters = [{ title: '第1章', pages: [''], images: [] }];
    state.chapterIndex = 0;
    state.pageIndex = 0;
    state.dirty = false;
    state.baselineChars = 0;
    state.sessionGain = 0;
    state.autoFocusEditor = true;
    el.titleInput.value = trimmed;
    updateLayout();
    await refreshLibraryList();
    setStatus('已创建新文稿');
  } catch (e) {
    setStatus(e.message || '创建失败', true);
  }
}

function populateFolderSelect() {
  if (!el.newDraftFolder) return;
  el.newDraftFolder.innerHTML = '';
  for (const name of state.libraryFolders) {
    const opt = document.createElement('option');
    opt.value = name;
    opt.textContent = name;
    el.newDraftFolder.appendChild(opt);
  }
}

function openNewDraftDialog(defaultFolder = null) {
  if (!state.config?.workspace?.trim()) {
    setHubStatus('请先打开工作目录', true);
    return;
  }
  populateFolderSelect();
  if (el.newDraftFolder && defaultFolder) {
    el.newDraftFolder.value = defaultFolder;
  }
  if (el.newDraftTitle) el.newDraftTitle.value = '未命名';
  el.newDraftDialog?.showModal();
  setTimeout(() => el.newDraftTitle?.select(), 0);
}

function openChapterDialog() {
  if (state.viewMode !== 'write' || !state.remoteId) {
    setStatus('请先打开或新建一篇文稿', true);
    return;
  }
  commitEditor();
  if (el.chapterSubtitle) el.chapterSubtitle.value = '';
  el.chapterDialog?.showModal();
  setTimeout(() => el.chapterSubtitle?.focus(), 0);
}

function insertNextChapter(subtitle) {
  commitEditor();
  const title = nextChapterTitle(state.chapters, subtitle);
  state.chapters.push({ title, pages: [''], images: [] });
  state.chapterIndex = state.chapters.length - 1;
  state.pageIndex = 0;
  state.dirty = true;
  state.autoFocusEditor = true;
  renderEditor();
  setStatus(`新章节：${title}（空白页）`);
}

function goPrevPage() {
  commitEditor();
  if (state.pageIndex > 0) {
    state.pageIndex--;
    state.autoFocusEditor = state.viewMode === 'write';
    renderEditor();
    return;
  }
  if (state.chapterIndex > 0) {
    state.chapterIndex--;
    const ch = currentChapter();
    state.pageIndex = Math.max(0, (ch.pages?.length || 1) - 1);
    state.autoFocusEditor = state.viewMode === 'write';
    renderEditor();
    setStatus(`上一章：${ch.title}`);
  }
}

function goNextPage() {
  commitEditor();
  const ch = currentChapter();
  if (!ch.pages) ch.pages = [''];

  if (state.pageIndex < ch.pages.length - 1) {
    state.pageIndex++;
    state.autoFocusEditor = state.viewMode === 'write';
    renderEditor();
    return;
  }

  if (state.viewMode === 'write') {
    ch.pages.push('');
    state.pageIndex = ch.pages.length - 1;
    state.dirty = true;
    state.autoFocusEditor = true;
    renderEditor();
    setStatus('已翻到新的空白页');
    return;
  }

  if (state.chapterIndex < state.chapters.length - 1) {
    state.chapterIndex++;
    state.pageIndex = 0;
    renderEditor();
  }
}

async function syncLibraryFromRemote() {
  const btn = document.getElementById('btn-sync-library');
  if (btn) btn.disabled = true;
  setStatus('正在同步书库…');
  let unsub = null;
  try {
    unsub = window.kanshu.onLibrarySyncProgress?.((msg) => setStatus(msg));
    const result = await window.kanshu.syncLibrary();
    await refreshLibraryList();
    setStatus(result?.message || '书库同步完成');
  } catch (e) {
    setStatus(e.message || '同步失败', true);
  } finally {
    if (typeof unsub === 'function') unsub();
    if (btn) btn.disabled = false;
  }
}

async function loadDraft(remoteId) {
  if (state.dirty && !confirm('当前文稿未保存，确定切换？')) return;
  commitEditor();
  try {
    const { content, booksDir, title, folder } = await window.kanshu.readDraft(remoteId);
    state.viewMode = 'write';
    state.screen = 'editor';
    state.remoteId = remoteId;
    state.booksDir = booksDir;
    state.bookFolder = folder || '仓库书';
    const blocks = parse(content);
    const repairedBlocks = expandBlocksByChapterHeadings(blocks);
    state.chapters = blocksToChapters(repairedBlocks);
    state.title = title || extractTitleFromContent(content, remoteId);
    const chapterRepaired = repairedBlocks.length > blocks.length;
    state.chapterIndex = 0;
    state.pageIndex = 0;
    state.dirty = false;
    state.baselineChars = totalCharCount();
    state.sessionGain = 0;
    state.autoFocusEditor = true;
    clampNavIndices();
    el.titleInput.value = state.title;
    updateLayout();
    await refreshLibraryList();
    if (chapterRepaired) {
      state.dirty = true;
      await saveDraft(true);
      setStatus('已打开文稿，并自动修复章节结构');
    } else {
      setStatus('已打开文稿');
    }
  } catch (e) {
    setStatus(e.message || '打开失败', true);
  }
}

function getViewportCharBudget(ta) {
  if (!ta || ta.clientHeight < 40) return PAGE_CHAR_BUDGET;
  const style = getComputedStyle(ta);
  const lineHeight = parseFloat(style.lineHeight) || 28;
  const fontSize = parseFloat(style.fontSize) || 16;
  const lines = Math.floor((ta.clientHeight - 24) / lineHeight);
  const charsPerLine = Math.max(12, Math.floor(ta.clientWidth / (fontSize * 0.95)));
  return Math.max(100, Math.floor(lines * charsPerLine * 0.92));
}

function splitOverflowForEditor(ta, text) {
  const budget = getViewportCharBudget(ta);
  let result = splitTextOverflow(text, budget);
  if (!result.overflow && ta.scrollHeight > ta.clientHeight + 4) {
    const saved = ta.value;
    const selStart = ta.selectionStart;
    const selEnd = ta.selectionEnd;
    try {
      let lo = 0;
      let hi = text.length;
      while (lo < hi) {
        const mid = Math.ceil((lo + hi) / 2);
        ta.value = text.slice(0, mid);
        if (ta.scrollHeight > ta.clientHeight + 4) hi = mid - 1;
        else lo = mid;
      }
      const keep = text.slice(0, lo).trimEnd();
      const overflow = text.slice(lo).trimStart();
      if (overflow) result = { keep, overflow };
    } finally {
      ta.value = saved;
      try { ta.setSelectionRange(selStart, selEnd); } catch { /* ignore */ }
    }
  }
  return result;
}

function applyPageOverflow(keep, overflow) {
  const ch = currentChapter();
  if (!ch.pages) ch.pages = [''];
  ch.pages[state.pageIndex] = keep;
  if (state.pageIndex + 1 < ch.pages.length) {
    const tail = ch.pages[state.pageIndex + 1] || '';
    ch.pages[state.pageIndex + 1] = overflow + (tail ? `\n\n${tail}` : '');
  } else {
    ch.pages.push(overflow);
  }
  state.pageIndex++;
  state.dirty = true;
  state.autoFocusEditor = true;
  state.activeEditor = null;
  renderEditor();
  setStatus('本页已满，已自动翻到下一页');
}

function focusPageEditor(ta, { atEnd = false } = {}) {
  if (!ta) return;
  state.editorWantsFocus = true;
  requestAnimationFrame(() => {
    if (!ta.isConnected) return;
    ta.focus({ preventScroll: true });
    if (atEnd) {
      const len = ta.value.length;
      try { ta.setSelectionRange(len, len); } catch { /* ignore */ }
    }
  });
}

function handlePageInput(ta) {
  state.activeEditor = ta;
  const text = ta.value;
  const ch = currentChapter();
  if (!ch.pages) ch.pages = [''];

  const { keep, overflow } = splitOverflowForEditor(ta, text);
  if (overflow) {
    applyPageOverflow(keep, overflow);
    return;
  }

  ch.pages[state.pageIndex] = text;
  state.dirty = true;
  clampNavIndices();
  updateStats();
  updatePageChrome();
  renderOutline();
}

async function renderImageInline(wrap, block) {
  const uri = await window.kanshu.resolveAsset(state.booksDir, block.path);
  wrap.innerHTML = '';
  if (uri) {
    const img = document.createElement('img');
    img.src = uri;
    img.style.width = `${Math.round(block.widthPercent * 100)}%`;
    img.alt = block.path;
    wrap.appendChild(img);
  } else {
    const miss = document.createElement('div');
    miss.className = 'missing';
    miss.textContent = `图片缺失：${block.path}`;
    wrap.appendChild(miss);
  }
}

function renderEditor() {
  clampNavIndices();
  updateEditorChrome();
  updatePageChrome();
  state.activeEditor = null;
  el.blocks.innerHTML = '';

  const ch = currentChapter();
  const pageText = (ch.pages || [''])[state.pageIndex] ?? '';

  if (state.viewMode === 'read') {
    const reader = document.createElement('div');
    reader.className = 'page-reader';
    reader.textContent = pageText;
    el.blocks.appendChild(reader);
    renderOutline();
    updateStats();
    return;
  }

  for (const block of ch.images || []) {
    const wrap = document.createElement('div');
    wrap.className = 'image-inline';
    renderImageInline(wrap, block);
    el.blocks.appendChild(wrap);
  }

  const wrap = document.createElement('div');
  wrap.className = 'page-editor-wrap';
  const ta = document.createElement('textarea');
  ta.className = 'page-editor';
  ta.setAttribute('spellcheck', 'true');
  ta.value = pageText;
  ta.placeholder = '在这里写本章内容…写满会自动翻页；「下一章」换一张新纸';
  ta.oninput = () => handlePageInput(ta);
  ta.onfocus = () => {
    state.activeEditor = ta;
    state.editorWantsFocus = true;
  };
  ta.onblur = () => {
    window.setTimeout(() => {
      if (document.activeElement !== ta && !wrap.contains(document.activeElement)) {
        state.editorWantsFocus = false;
      }
    }, 0);
  };
  wrap.addEventListener('mousedown', (e) => {
    state.editorWantsFocus = true;
    if (e.target === wrap) {
      e.preventDefault();
      focusPageEditor(ta, { atEnd: true });
    }
  });
  wrap.appendChild(ta);
  el.blocks.appendChild(wrap);

  renderOutline();
  updateStats();
  if (state.autoFocusEditor || state.editorWantsFocus) {
    state.autoFocusEditor = false;
    focusPageEditor(ta, { atEnd: !state.editorWantsFocus });
  }
}

async function saveDraft(fromAuto = false) {
  if (state.viewMode !== 'write' || !state.remoteId) {
    if (!fromAuto) setStatus('请先新建或打开一篇文稿', true);
    return;
  }
  commitEditor();
  state.title = el.titleInput.value.trim() || state.remoteId;
  const content = serialize(allBlocks());
  try {
    await window.kanshu.saveDraft({
      remoteId: state.remoteId,
      title: state.title,
      content,
      exportTxt: true,
      folder: state.bookFolder
    });
    state.baselineChars = totalCharCount();
    state.sessionGain = 0;
    state.dirty = false;
    setStatus(fromAuto ? '已自动保存' : '已保存 draft.txt 与 txt 导出');
    await refreshLibraryList();
    updateStats();
  } catch (e) {
    setStatus(e.message || '保存失败', true);
  }
}

function startAutoSave() {
  if (state.autoSaveTimer) clearInterval(state.autoSaveTimer);
  state.autoSaveTimer = setInterval(() => {
    if (state.dirty && state.remoteId) saveDraft(true);
  }, 18000);
}

async function uploadGithub() {
  if (!state.remoteId) return;
  if (state.dirty) await saveDraft();
  state.title = el.titleInput.value.trim() || state.remoteId;
  commitEditor();
  try {
    setStatus('正在上传…');
    const msg = await window.kanshu.githubUpload({
      remoteId: state.remoteId,
      title: state.title,
      content: serialize(allBlocks()),
      booksDir: state.booksDir
    });
    setStatus(msg);
  } catch (e) {
    setStatus(e.message || '上传失败', true);
  }
}

function bindEvents() {
  el.btnBack?.addEventListener('click', () => navigateBack());

  el.newDraftForm?.addEventListener('submit', async (e) => {
    const submitter = e.submitter;
    if (submitter && submitter.value === 'cancel') {
      el.newDraftDialog.close();
      return;
    }
    e.preventDefault();
    const title = el.newDraftTitle?.value || '未命名';
    const folder = el.newDraftFolder?.value || '仓库书';
    el.newDraftDialog.close();
    await createNewDraft(title, folder);
  });

  el.chapterForm?.addEventListener('submit', (e) => {
    const submitter = e.submitter;
    if (submitter && submitter.value === 'cancel') {
      el.chapterDialog.close();
      return;
    }
    e.preventDefault();
    const sub = el.chapterSubtitle?.value || '';
    el.chapterDialog.close();
    insertNextChapter(sub);
  });

  document.getElementById('btn-save').onclick = () => saveDraft(false);
  document.getElementById('btn-upload').onclick = uploadGithub;
  document.getElementById('btn-prev-page').onclick = () => goPrevPage();
  document.getElementById('btn-next-page').onclick = () => goNextPage();
  document.getElementById('btn-focus').onclick = () => {
    commitEditor();
    state.focusMode = !state.focusMode;
    state.editorWantsFocus = true;
    document.getElementById('btn-focus').textContent = state.focusMode ? '退出专注' : '专注';
    updateLayout();
  };

  document.getElementById('btn-chapter').onclick = () => openChapterDialog();

  document.getElementById('btn-image').onclick = async () => {
    if (state.viewMode !== 'write' || !state.remoteId) {
      setStatus('请先打开或新建文稿', true);
      return;
    }
    const picked = await window.kanshu.pickImage(state.remoteId);
    if (!picked) return;
    commitEditor();
    const ch = currentChapter();
    if (!ch.images) ch.images = [];
    ch.images.push({ type: 'image', path: picked.relativePath, widthPercent: 1, id: newId() });
    state.dirty = true;
    renderEditor();
  };

  document.getElementById('btn-check-update').onclick = async () => {
    setStatus('正在检查更新…');
    try {
      const result = await window.kanshu.checkUpdate({ prompt: true });
      showUpdateProgress({ phase: 'hide' });
      if (result?.updateAvailable === false) {
        setStatus(`已是最新版 v${result.info?.currentVersion || ''}`);
      } else if (result?.installing) {
        setStatus('正在打开安装程序…');
      } else if (result?.deferred) {
        setStatus('已跳过本次更新');
      } else if (result?.error) {
        setStatus(result.error, true);
      } else {
        setStatus('更新检查完成');
      }
    } catch (e) {
      showUpdateProgress({ phase: 'hide' });
      setStatus(e.message || '检查更新失败', true);
    }
  };

  document.getElementById('btn-settings').onclick = () => {
    el.cfgToken.value = state.config?.githubToken || '';
    el.cfgOwner.value = state.config?.githubOwner || 'zhple';
    el.cfgRepo.value = state.config?.githubRepo || 'kanshu';
    el.cfgBranch.value = state.config?.githubBranch || 'main';
    if (el.cfgAutoUpdate) {
      el.cfgAutoUpdate.checked = state.config?.autoCheckUpdate !== false;
    }
    el.settingsDialog.showModal();
  };

  document.getElementById('settings-form').onsubmit = async (e) => {
    e.preventDefault();
    const submitter = e.submitter;
    if (submitter && submitter.value === 'cancel') {
      el.settingsDialog.close();
      return;
    }
    state.config = {
      ...state.config,
      githubToken: el.cfgToken.value.trim(),
      githubOwner: el.cfgOwner.value.trim() || 'zhple',
      githubRepo: el.cfgRepo.value.trim() || 'kanshu',
      githubBranch: el.cfgBranch.value.trim() || 'main',
      autoCheckUpdate: el.cfgAutoUpdate ? el.cfgAutoUpdate.checked : true
    };
    await window.kanshu.saveConfig(state.config);
    el.settingsDialog.close();
    updateStats();
    setStatus('设置已保存');
  };

  el.titleInput.oninput = () => {
    state.title = el.titleInput.value;
    state.dirty = true;
    updateStats();
  };

  el.titleInput.onchange = () => {
    state.title = el.titleInput.value.trim() || state.remoteId || '未命名';
    state.dirty = true;
  };

  window.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
      e.preventDefault();
      saveDraft(false);
    }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === '\\') {
      e.preventDefault();
      commitEditor();
      state.focusMode = !state.focusMode;
      state.editorWantsFocus = true;
      document.getElementById('btn-focus').textContent = state.focusMode ? '退出专注' : '专注';
      updateLayout();
    }
    if (state.viewMode === 'write' && (e.ctrlKey || e.metaKey) && e.key === 'ArrowRight') {
      e.preventDefault();
      goNextPage();
    }
    if (state.viewMode === 'write' && (e.ctrlKey || e.metaKey) && e.key === 'ArrowLeft') {
      e.preventDefault();
      goPrevPage();
    }
  });
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

init().catch(err => setStatus(err.message, true));
