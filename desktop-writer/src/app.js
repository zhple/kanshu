import {
  parse, serialize, buildPages, newId,
  charCount, extractTitleFromContent,
  splitTextOverflow, PAGE_CHAR_BUDGET,
  blocksToChapters, chaptersToBlocks, chapterOutline, chapterCharCount,
  nextChapterTitle
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
  viewMode: 'idle',
  dirty: false,
  config: null,
  focusMode: false,
  autoFocusEditor: false,
  baselineChars: 0,
  sessionGain: 0,
  dailyDone: 0,
  dailyDate: '',
  autoSaveTimer: null,
  activeEditor: null,
  resizeObserver: null
};

const el = {
  libraryList: document.getElementById('library-list'),
  libraryVersion: document.getElementById('library-version'),
  outlineList: document.getElementById('outline-list'),
  workspaceLabel: document.getElementById('workspace-label'),
  titleInput: document.getElementById('title-input'),
  blocks: document.getElementById('blocks'),
  pageLabel: document.getElementById('page-label'),
  pageTitle: document.getElementById('page-title'),
  pageSubtitle: document.getElementById('page-subtitle'),
  status: document.getElementById('status'),
  statsText: document.getElementById('stats-text'),
  goalFill: document.getElementById('goal-fill'),
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
  editorPane: document.querySelector('.editor-pane'),
  cfgToken: document.getElementById('cfg-token'),
  cfgOwner: document.getElementById('cfg-owner'),
  cfgRepo: document.getElementById('cfg-repo'),
  cfgBranch: document.getElementById('cfg-branch'),
  cfgGoal: document.getElementById('cfg-goal'),
  cfgAutoUpdate: document.getElementById('cfg-auto-update'),
  appVersion: document.getElementById('app-version'),
  updateProgress: document.getElementById('update-progress'),
  updateProgressTitle: document.getElementById('update-progress-title'),
  updateProgressFill: document.getElementById('update-progress-fill'),
  updateProgressText: document.getElementById('update-progress-text')
};

function todayKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function setStatus(msg, isError = false) {
  el.status.textContent = msg || '';
  el.status.style.color = isError ? 'var(--danger)' : 'var(--muted)';
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
  el.editorPane?.classList.toggle('read-mode', state.viewMode === 'read');
  if (el.titleInput) el.titleInput.readOnly = state.viewMode === 'read';
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
  loadDailyProgress();
  updateWorkspaceLabel();
  bindUpdateProgressListener();
  bindEvents();
  try {
    await refreshLibraryList();
  } catch (e) {
    setStatus(e.message || '书库列表加载失败', true);
  }
  startAutoSave();
  render();
}

function loadDailyProgress() {
  const today = todayKey();
  if (state.config?.dailyDate === today) {
    state.dailyDone = Number(state.config.dailyDone || 0);
    state.dailyDate = today;
  } else {
    state.dailyDone = 0;
    state.dailyDate = today;
  }
}

async function persistDailyProgress() {
  const today = todayKey();
  if (state.dailyDate !== today) {
    state.dailyDate = today;
    state.dailyDone = 0;
  }
  state.dailyDone += Math.max(0, state.sessionGain);
  state.baselineChars = totalCharCount();
  state.sessionGain = 0;
  state.config = {
    ...state.config,
    dailyDate: state.dailyDate,
    dailyDone: state.dailyDone
  };
  await window.kanshu.saveConfig(state.config);
}

function updateWorkspaceLabel() {
  const ws = state.config?.workspace;
  el.workspaceLabel.textContent = ws ? `工作目录：${ws}` : '请先选择 kanshu 仓库根目录（或 default-books 文件夹）';
}

function updateStats() {
  const chars = totalCharCount();
  const goal = Number(state.config?.dailyGoal || 1000);
  const today = state.dailyDone + state.sessionGain;
  el.statsText.textContent = `${chars} 字 · 本会话 +${state.sessionGain} · 今日 ${today} / ${goal}`;
  el.goalFill.style.width = `${Math.min(100, (today / Math.max(goal, 1)) * 100)}%`;
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
      render();
    };
    el.outlineList.appendChild(li);
  }
}

async function refreshLibraryList() {
  if (!el.libraryList) return;
  const { books, folders, version } = await window.kanshu.listLibrary();
  state.libraryFolders = folders?.length ? folders : ['仓库书'];
  if (el.libraryVersion) {
    el.libraryVersion.textContent = version ? `v${version}` : '';
  }
  el.libraryList.innerHTML = '';
  if (!books.length) {
    el.libraryList.innerHTML = '<li class="meta" style="padding:12px">点「同步书库」拉取朋友上传的书</li>';
    return;
  }

  const folderNames = [...state.libraryFolders];
  for (const book of books) {
    const f = book.folder || '仓库书';
    if (!folderNames.includes(f)) folderNames.push(f);
  }

  for (const folder of folderNames) {
    const inFolder = books.filter((b) => (b.folder || '仓库书') === folder);
    if (!inFolder.length) continue;
    const header = document.createElement('li');
    header.className = 'folder-header';
    header.textContent = folder;
    el.libraryList.appendChild(header);
    for (const book of inFolder) {
      el.libraryList.appendChild(libraryBookItem(book));
    }
  }
}

function libraryBookItem(book) {
  const li = document.createElement('li');
  li.dataset.remoteId = book.id;
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

async function openLibraryBook(book) {
  if (book.hasDraft || book.editable) {
    await loadDraft(book.id);
    return;
  }
  if (book.hasReadable && ((book.format || '').toUpperCase() === 'TXT' || book.file?.endsWith('.txt'))) {
    await openReader(book.id);
    return;
  }
  setStatus(`「${book.title || book.id}」暂无 draft，电脑端暂不支持阅读 ${book.format || '该格式'}`, true);
}

async function openReader(remoteId) {
  if (state.dirty && !confirm('当前文稿未保存，确定切换？')) return;
  commitEditor();
  try {
    const { content, title, booksDir, folder } = await window.kanshu.readBook(remoteId);
    state.viewMode = 'read';
    state.remoteId = remoteId;
    state.booksDir = booksDir;
    state.title = title;
    state.bookFolder = folder || '仓库书';
    state.chapters = blocksToChapters(parse(content));
    state.chapterIndex = 0;
    state.pageIndex = 0;
    state.dirty = false;
    el.titleInput.value = title;
    clampNavIndices();
    render();
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
    render();
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

function openNewDraftDialog() {
  if (!state.config?.workspace?.trim()) {
    setStatus('请先点击「打开目录」选择 kanshu 仓库目录', true);
    return;
  }
  populateFolderSelect();
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
  render();
  setStatus(`新章节：${title}（空白页）`);
}

function goPrevPage() {
  commitEditor();
  if (state.pageIndex > 0) {
    state.pageIndex--;
    state.autoFocusEditor = state.viewMode === 'write';
    render();
    return;
  }
  if (state.chapterIndex > 0) {
    state.chapterIndex--;
    const ch = currentChapter();
    state.pageIndex = Math.max(0, (ch.pages?.length || 1) - 1);
    state.autoFocusEditor = state.viewMode === 'write';
    render();
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
    render();
    return;
  }

  if (state.viewMode === 'write') {
    ch.pages.push('');
    state.pageIndex = ch.pages.length - 1;
    state.dirty = true;
    state.autoFocusEditor = true;
    render();
    setStatus('已翻到新的空白页');
    return;
  }

  if (state.chapterIndex < state.chapters.length - 1) {
    state.chapterIndex++;
    state.pageIndex = 0;
    render();
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
    state.remoteId = remoteId;
    state.booksDir = booksDir;
    state.bookFolder = folder || '仓库书';
    state.chapters = blocksToChapters(parse(content));
    state.title = title || extractTitleFromContent(content, remoteId);
    state.chapterIndex = 0;
    state.pageIndex = 0;
    state.dirty = false;
    state.baselineChars = totalCharCount();
    state.sessionGain = 0;
    state.autoFocusEditor = true;
    clampNavIndices();
    el.titleInput.value = state.title;
    render();
    await refreshLibraryList();
    setStatus('已打开文稿');
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
  render();
  setStatus('本页已满，已自动翻到下一页');
}

function setupEditorResize(ta) {
  if (state.resizeObserver) state.resizeObserver.disconnect();
  state.resizeObserver = new ResizeObserver(() => {
    if (state.activeEditor !== ta || !ta.value) return;
    const { overflow } = splitOverflowForEditor(ta, ta.value);
    if (overflow) {
      const { keep } = splitOverflowForEditor(ta, ta.value);
      applyPageOverflow(keep, overflow);
    }
  });
  state.resizeObserver.observe(ta);
  if (el.editorPane) state.resizeObserver.observe(el.editorPane);
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

function render() {
  clampNavIndices();
  updateEditorChrome();
  updatePageChrome();
  state.activeEditor = null;
  el.blocks.innerHTML = '';

  if (state.viewMode === 'idle') {
    el.blocks.innerHTML = '<p class="page-hint meta">从左侧书库选择文稿，或新建</p>';
    renderOutline();
    updateStats();
    document.body.classList.toggle('focus-mode', state.focusMode);
    return;
  }

  const ch = currentChapter();
  const pageText = (ch.pages || [''])[state.pageIndex] ?? '';

  if (state.viewMode === 'read') {
    const reader = document.createElement('div');
    reader.className = 'page-reader';
    reader.textContent = pageText;
    el.blocks.appendChild(reader);
    renderOutline();
    updateStats();
    document.body.classList.toggle('focus-mode', state.focusMode);
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
  ta.value = pageText;
  ta.placeholder = '在这里写本章内容…写满会自动翻页；「下一章」换一张新纸';
  ta.oninput = () => handlePageInput(ta);
  ta.onfocus = () => { state.activeEditor = ta; };
  wrap.appendChild(ta);
  el.blocks.appendChild(wrap);

  renderOutline();
  updateStats();
  document.body.classList.toggle('focus-mode', state.focusMode);
  if (state.autoFocusEditor) {
    state.autoFocusEditor = false;
    ta.focus();
  }
  setupEditorResize(ta);
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
    await persistDailyProgress();
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
  document.getElementById('btn-workspace').onclick = async () => {
    const ws = await window.kanshu.pickWorkspace();
    if (ws) {
      state.config = await window.kanshu.getConfig();
      updateWorkspaceLabel();
      await refreshLibraryList();
      setStatus('工作目录已更新');
    }
  };

  document.getElementById('btn-sync-library').onclick = () => syncLibraryFromRemote();
  document.getElementById('btn-new').onclick = () => openNewDraftDialog();

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
    document.getElementById('btn-focus').textContent = state.focusMode ? '退出专注' : '专注';
    render();
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
    render();
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
    el.cfgGoal.value = String(state.config?.dailyGoal || 1000);
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
      dailyGoal: Math.min(50000, Math.max(100, parseInt(el.cfgGoal.value, 10) || 1000)),
      dailyDate: state.dailyDate || todayKey(),
      dailyDone: state.dailyDone,
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
      document.getElementById('btn-focus').textContent = state.focusMode ? '退出专注' : '专注';
      render();
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
