import {
  parse, serialize, buildPages, nextChapterTitle, newId,
  charCount, outline, extractTitleFromContent,
  pagePlainText, setPagePlainText, splitTextOverflow, PAGE_CHAR_BUDGET
} from './write-blocks.js';

const state = {
  remoteId: null,
  title: '',
  blocks: [{ type: 'paragraph', text: '', id: newId() }],
  pages: [],
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
  aiMode: null,
  aiPreview: '',
  autoSaveTimer: null
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
  status: document.getElementById('status'),
  statsText: document.getElementById('stats-text'),
  goalFill: document.getElementById('goal-fill'),
  dirtyBadge: document.getElementById('dirty-badge'),
  sidebar: document.getElementById('sidebar'),
  settingsDialog: document.getElementById('settings-dialog'),
  aiDialog: document.getElementById('ai-dialog'),
  aiPreviewDialog: document.getElementById('ai-preview-dialog'),
  aiPreviewBody: document.getElementById('ai-preview-body'),
  aiHint: document.getElementById('ai-hint'),
  aiDialogTitle: document.getElementById('ai-dialog-title'),
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
  cfgDeepseek: document.getElementById('cfg-deepseek'),
  cfgGoal: document.getElementById('cfg-goal'),
  cfgAutoUpdate: document.getElementById('cfg-auto-update'),
  appVersion: document.getElementById('app-version')
};

function todayKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function setStatus(msg, isError = false) {
  el.status.textContent = msg || '';
  el.status.style.color = isError ? 'var(--danger)' : 'var(--muted)';
}

function rebuildPages() {
  state.pages = buildPages(state.blocks);
  state.pageIndex = Math.min(state.pageIndex, Math.max(0, state.pages.length - 1));
  const chars = charCount(state.blocks);
  state.sessionGain = Math.max(0, chars - state.baselineChars);
}

function currentPageBlocks() {
  const page = state.pages[state.pageIndex];
  if (!page) return [];
  return state.blocks.slice(page.startIndex, page.endExclusive);
}

function focusedGlobalIndex() {
  const page = state.pages[state.pageIndex];
  if (!page) return 0;
  for (let i = page.startIndex; i < page.endExclusive; i++) {
    if (state.blocks[i]?.type === 'paragraph') return i;
  }
  return page.startIndex;
}

function updateEditorChrome() {
  el.editorPane?.classList.toggle('read-mode', state.viewMode === 'read');
  if (el.titleInput) el.titleInput.readOnly = state.viewMode === 'read';
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
  state.baselineChars = charCount(state.blocks);
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
  const chars = charCount(state.blocks);
  const goal = Number(state.config?.dailyGoal || 1000);
  const today = state.dailyDone + state.sessionGain;
  el.statsText.textContent = `${chars} 字 · 本会话 +${state.sessionGain} · 今日 ${today} / ${goal}`;
  el.goalFill.style.width = `${Math.min(100, (today / Math.max(goal, 1)) * 100)}%`;
  el.dirtyBadge.classList.toggle('hidden', !state.dirty);
}

function renderOutline() {
  const items = outline(state.blocks);
  el.outlineList.innerHTML = '';
  if (!items.length) {
    el.outlineList.innerHTML = '<li class="meta">插入「下一章」后显示</li>';
    return;
  }
  for (const item of items) {
    const li = document.createElement('li');
    li.innerHTML = `<div class="name">${escapeHtml(item.title)}</div><div class="meta">第 ${item.pageIndex + 1} 页 · ${item.charCount} 字</div>`;
    li.onclick = () => {
      state.pageIndex = item.pageIndex;
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
  try {
    const { content, title, booksDir, folder } = await window.kanshu.readBook(remoteId);
    state.viewMode = 'read';
    state.remoteId = remoteId;
    state.booksDir = booksDir;
    state.title = title;
    state.bookFolder = folder || '仓库书';
    state.blocks = parse(content);
    state.pageIndex = 0;
    state.dirty = false;
    el.titleInput.value = title;
    rebuildPages();
    render();
    await refreshLibraryList();
    setStatus('阅读模式（保存请先在手机端或上传后编辑 draft）');
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
    state.blocks = [{ type: 'paragraph', text: '', id: newId() }];
    state.pageIndex = 0;
    state.dirty = false;
    state.baselineChars = 0;
    state.sessionGain = 0;
    state.autoFocusEditor = true;
    el.titleInput.value = trimmed;
    rebuildPages();
    render();
    await refreshLibraryList();
    setStatus('已创建新文稿，标题可在顶部修改');
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
  if (el.chapterSubtitle) el.chapterSubtitle.value = '';
  el.chapterDialog?.showModal();
  setTimeout(() => el.chapterSubtitle?.focus(), 0);
}

function insertNextChapter(subtitle) {
  const title = nextChapterTitle(state.blocks, subtitle);
  const last = state.blocks[state.blocks.length - 1];
  if (last.type === 'paragraph' && !last.text.trim()) {
    state.blocks.pop();
  }
  state.blocks.push({ type: 'paragraph', text: title, id: newId() });
  state.blocks.push({ type: 'paragraph', text: '', id: newId() });
  state.dirty = true;
  rebuildPages();
  const emptyIdx = state.blocks.length - 1;
  state.pageIndex = Math.max(0, state.pages.findIndex(
    (p) => emptyIdx >= p.startIndex && emptyIdx < p.endExclusive
  ));
  state.autoFocusEditor = true;
  render();
  setStatus(`已插入 ${title}`);
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
  try {
    const { content, booksDir, title, folder } = await window.kanshu.readDraft(remoteId);
    state.viewMode = 'write';
    state.remoteId = remoteId;
    state.booksDir = booksDir;
    state.bookFolder = folder || '仓库书';
    state.blocks = parse(content);
    state.title = title || extractTitleFromContent(content, remoteId);
    state.pageIndex = 0;
    state.dirty = false;
    state.baselineChars = charCount(state.blocks);
    state.sessionGain = 0;
    state.autoFocusEditor = true;
    rebuildPages();
    el.titleInput.value = state.title;
    render();
    await refreshLibraryList();
    setStatus('已打开文稿');
  } catch (e) {
    setStatus(e.message || '打开失败', true);
  }
}

function handlePageInput(ta) {
  const text = ta.value;
  const page = state.pages[state.pageIndex];
  if (!page) return;

  const { keep, overflow } = splitTextOverflow(text, PAGE_CHAR_BUDGET);
  if (overflow) {
    setPagePlainText(state.blocks, page, keep);
    state.dirty = true;
    rebuildPages();
    const nextPage = state.pages[state.pageIndex + 1];
    if (nextPage) {
      const nextText = pagePlainText(state.blocks, nextPage);
      setPagePlainText(state.blocks, nextPage, overflow + (nextText ? `\n\n${nextText}` : ''));
      state.pageIndex++;
    } else {
      state.blocks.push({ type: 'paragraph', text: overflow, id: newId() });
      rebuildPages();
      state.pageIndex = state.pages.length - 1;
    }
    state.autoFocusEditor = true;
    render();
    setStatus('本页已满，已自动翻到下一页');
    return;
  }

  setPagePlainText(state.blocks, page, text);
  state.dirty = true;
  const prevLen = state.pages.length;
  rebuildPages();
  updateStats();
  renderOutline();
  if (state.pages.length !== prevLen) {
    el.pageLabel.textContent = `第 ${state.pageIndex + 1} / ${state.pages.length} 页`;
  }
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
  rebuildPages();
  updateEditorChrome();
  const page = state.pages[state.pageIndex];
  el.pageLabel.textContent = `第 ${state.pageIndex + 1} / ${state.pages.length} 页`;
  el.pageTitle.textContent = page?.title || '正文';
  el.blocks.innerHTML = '';

  if (state.viewMode === 'idle') {
    el.blocks.innerHTML = '<p class="page-hint meta">从左侧书库选择文稿，或新建</p>';
    renderOutline();
    updateStats();
    document.body.classList.toggle('focus-mode', state.focusMode);
    return;
  }

  if (state.viewMode === 'read') {
    const reader = document.createElement('div');
    reader.className = 'page-reader';
    reader.textContent = pagePlainText(state.blocks, page);
    el.blocks.appendChild(reader);
    renderOutline();
    updateStats();
    document.body.classList.toggle('focus-mode', state.focusMode);
    return;
  }

  const pageBlocks = currentPageBlocks();
  for (const block of pageBlocks) {
    if (block.type === 'image') {
      const wrap = document.createElement('div');
      wrap.className = 'image-inline';
      renderImageInline(wrap, block);
      el.blocks.appendChild(wrap);
    }
  }

  const wrap = document.createElement('div');
  wrap.className = 'page-editor-wrap';
  const ta = document.createElement('textarea');
  ta.className = 'page-editor';
  ta.value = pagePlainText(state.blocks, page);
  ta.placeholder = '在这里写作…写满本页会自动翻到下一页';
  ta.oninput = () => handlePageInput(ta);
  wrap.appendChild(ta);
  el.blocks.appendChild(wrap);

  renderOutline();
  updateStats();
  document.body.classList.toggle('focus-mode', state.focusMode);
  if (state.autoFocusEditor) {
    state.autoFocusEditor = false;
    ta.focus();
  }
}

async function saveDraft(fromAuto = false) {
  if (state.viewMode !== 'write' || !state.remoteId) {
    if (!fromAuto) setStatus('请先新建或打开一篇文稿', true);
    return;
  }
  state.title = el.titleInput.value.trim() || state.remoteId;
  const content = serialize(state.blocks);
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
  try {
    setStatus('正在上传…');
    const msg = await window.kanshu.githubUpload({
      remoteId: state.remoteId,
      title: state.title,
      content: serialize(state.blocks),
      booksDir: state.booksDir
    });
    setStatus(msg);
  } catch (e) {
    setStatus(e.message || '上传失败', true);
  }
}

function openAiDialog(mode) {
  if (!state.config?.deepseekApiKey) {
    setStatus('请先在设置里填写 DeepSeek API Key', true);
    return;
  }
  state.aiMode = mode;
  el.aiDialogTitle.textContent = ({
    CONTINUE: 'AI 续写',
    POLISH: 'AI 润色',
    EXPAND: 'AI 扩写'
  })[mode] || 'AI 助手';
  el.aiHint.value = '';
  el.aiDialog.showModal();
}

async function runAiAssist(hint) {
  const mode = state.aiMode;
  if (!mode) return;
  const gi = focusedGlobalIndex();
  const focus = state.blocks[gi];
  if (!focus || focus.type !== 'paragraph') {
    setStatus('请先聚焦一段文字', true);
    return;
  }
  const focusText = focus.text.trim();
  if (mode !== 'CONTINUE' && !focusText) {
    setStatus('润色/扩写需要当前段落有内容', true);
    return;
  }
  const preceding = serialize(state.blocks.slice(0, gi)).slice(-1800);
  setStatus('AI 生成中…');
  try {
    const text = await window.kanshu.writeAssist({
      mode,
      title: el.titleInput.value.trim(),
      precedingText: preceding,
      focusText,
      userHint: hint || ''
    });
    state.aiPreview = text;
    el.aiPreviewBody.textContent = text;
    el.aiPreviewDialog.showModal();
    setStatus('已生成，请确认后写入');
  } catch (e) {
    setStatus(e.message || 'AI 失败', true);
  }
}

function applyAiPreview() {
  const mode = state.aiMode;
  const preview = (state.aiPreview || '').trim();
  if (!mode || !preview) return;
  const gi = focusedGlobalIndex();
  const cur = state.blocks[gi];
  if (!cur || cur.type !== 'paragraph') return;
  if (mode === 'CONTINUE') {
    const base = cur.text.trimEnd();
    cur.text = base ? `${base}\n\n${preview}` : preview;
  } else {
    cur.text = preview;
  }
  state.dirty = true;
  state.aiPreview = '';
  state.aiMode = null;
  el.aiPreviewDialog.close();
  render();
  setStatus('已写入 AI 结果');
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
  document.getElementById('btn-prev-page').onclick = () => {
    if (state.pageIndex > 0) {
      state.pageIndex--;
      state.autoFocusEditor = state.viewMode === 'write';
      render();
    }
  };
  document.getElementById('btn-next-page').onclick = () => {
    if (state.pageIndex < state.pages.length - 1) {
      state.pageIndex++;
      state.autoFocusEditor = state.viewMode === 'write';
      render();
    }
  };
  document.getElementById('btn-focus').onclick = () => {
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
    const page = state.pages[state.pageIndex];
    const insertAt = page ? page.endExclusive - 1 : state.blocks.length - 1;
    const idx = Math.max(0, insertAt);
    state.blocks.splice(idx, 0, { type: 'image', path: picked.relativePath, widthPercent: 1, id: newId() });
    state.dirty = true;
    render();
  };

  document.getElementById('btn-ai-continue').onclick = () => openAiDialog('CONTINUE');
  document.getElementById('btn-ai-polish').onclick = () => openAiDialog('POLISH');
  document.getElementById('btn-ai-expand').onclick = () => openAiDialog('EXPAND');
  document.getElementById('ai-preview-apply').onclick = applyAiPreview;
  document.getElementById('ai-preview-discard').onclick = () => {
    state.aiPreview = '';
    state.aiMode = null;
    el.aiPreviewDialog.close();
  };

  document.getElementById('ai-form').onsubmit = async (e) => {
    e.preventDefault();
    const submitter = e.submitter;
    if (submitter && submitter.value === 'cancel') {
      el.aiDialog.close();
      return;
    }
    const hint = el.aiHint.value;
    el.aiDialog.close();
    await runAiAssist(hint);
  };

  document.getElementById('btn-check-update').onclick = async () => {
    setStatus('正在检查更新…');
    try {
      const result = await window.kanshu.checkUpdate({ prompt: true });
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
      setStatus(e.message || '检查更新失败', true);
    }
  };

  document.getElementById('btn-settings').onclick = () => {
    el.cfgToken.value = state.config?.githubToken || '';
    el.cfgOwner.value = state.config?.githubOwner || 'zhple';
    el.cfgRepo.value = state.config?.githubRepo || 'kanshu';
    el.cfgBranch.value = state.config?.githubBranch || 'main';
    el.cfgDeepseek.value = state.config?.deepseekApiKey || '';
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
      deepseekApiKey: el.cfgDeepseek.value.trim(),
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
      state.focusMode = !state.focusMode;
      document.getElementById('btn-focus').textContent = state.focusMode ? '退出专注' : '专注';
      render();
    }
  });
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

init().catch(err => setStatus(err.message, true));
