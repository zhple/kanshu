import {
  parse, serialize, buildPages, nextChapterTitle, newId,
  charCount, outline, extractTitleFromContent
} from './write-blocks.js';

const state = {
  remoteId: null,
  title: '',
  blocks: [{ type: 'paragraph', text: '', id: newId() }],
  pages: [],
  pageIndex: 0,
  booksDir: '',
  dirty: false,
  config: null,
  focusMode: false,
  focusedLocal: 0,
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
  newDraftForm: document.getElementById('new-draft-form'),
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

function globalIndexFromPageLocal(localIndex) {
  const page = state.pages[state.pageIndex];
  return page.startIndex + localIndex;
}

function focusedGlobalIndex() {
  const page = state.pages[state.pageIndex];
  if (!page) return 0;
  const local = Math.min(Math.max(0, state.focusedLocal), Math.max(0, page.endExclusive - page.startIndex - 1));
  return page.startIndex + local;
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
      state.focusedLocal = 0;
      render();
    };
    el.outlineList.appendChild(li);
  }
}

async function refreshLibraryList() {
  if (!el.libraryList) return;
  const { books, version } = await window.kanshu.listLibrary();
  if (el.libraryVersion) {
    el.libraryVersion.textContent = version ? `v${version}` : '';
  }
  el.libraryList.innerHTML = '';
  if (!books.length) {
    el.libraryList.innerHTML = '<li class="meta" style="padding:12px">点「同步书库」拉取朋友上传的书</li>';
    return;
  }
  for (const book of books) {
    const li = document.createElement('li');
    li.dataset.remoteId = book.id;
    if (book.id === state.remoteId) li.classList.add('active');
    const label = book.title || book.id;
    const tags = [];
    if (book.hasDraft) tags.push('可编辑');
    else if (book.hasReadable) tags.push('只读');
    if (book.localOnly) tags.push('本地');
    const meta = tags.length ? tags.join(' · ') : (book.author || book.format || '');
    li.innerHTML = `<div class="name">${escapeHtml(label)}</div><div class="meta">${escapeHtml(meta)}</div>`;
    li.onclick = () => openLibraryBook(book);
    el.libraryList.appendChild(li);
  }
}

async function openLibraryBook(book) {
  if (!book.hasDraft && !book.editable) {
    setStatus(`「${book.title || book.id}」暂无 draft 文稿，可在手机端阅读`, true);
    return;
  }
  await loadDraft(book.id);
}

async function createNewDraft(title) {
  const trimmed = (title || '').trim() || '未命名';
  if (!state.config?.workspace?.trim()) {
    setStatus('请先点击「打开目录」选择 kanshu 仓库目录', true);
    return;
  }
  try {
    const { remoteId } = await window.kanshu.createDraft(trimmed);
    state.remoteId = remoteId;
    state.title = trimmed;
    state.blocks = [{ type: 'paragraph', text: '', id: newId() }];
    state.pageIndex = 0;
    state.dirty = false;
    state.baselineChars = 0;
    state.sessionGain = 0;
    el.titleInput.value = trimmed;
    rebuildPages();
    render();
    await refreshLibraryList();
    setStatus('已创建新文稿');
  } catch (e) {
    setStatus(e.message || '创建失败', true);
  }
}

function openNewDraftDialog() {
  if (!state.config?.workspace?.trim()) {
    setStatus('请先点击「打开目录」选择 kanshu 仓库目录', true);
    return;
  }
  if (el.newDraftTitle) el.newDraftTitle.value = '未命名';
  el.newDraftDialog?.showModal();
  setTimeout(() => el.newDraftTitle?.select(), 0);
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
    const { content, booksDir, title } = await window.kanshu.readDraft(remoteId);
    state.remoteId = remoteId;
    state.booksDir = booksDir;
    state.blocks = parse(content);
    state.title = title || extractTitleFromContent(content, remoteId);
    state.pageIndex = 0;
    state.dirty = false;
    state.baselineChars = charCount(state.blocks);
    state.sessionGain = 0;
    rebuildPages();
    el.titleInput.value = state.title;
    render();
    await refreshLibraryList();
    setStatus('已打开文稿');
  } catch (e) {
    setStatus(e.message || '打开失败', true);
  }
}

function render() {
  rebuildPages();
  const page = state.pages[state.pageIndex];
  el.pageLabel.textContent = `第 ${state.pageIndex + 1} / ${state.pages.length} 页`;
  el.pageTitle.textContent = page?.title || '正文';
  el.blocks.innerHTML = '';
  const pageBlocks = currentPageBlocks();
  pageBlocks.forEach((block, localIdx) => {
    el.blocks.appendChild(renderBlock(block, localIdx));
  });
  renderOutline();
  updateStats();
  document.body.classList.toggle('focus-mode', state.focusMode);
}

function renderBlock(block, localIdx) {
  const card = document.createElement('div');
  card.className = 'block-card';
  card.dataset.localIdx = String(localIdx);
  card.draggable = true;

  const toolbar = document.createElement('div');
  toolbar.className = 'block-toolbar';
  toolbar.innerHTML = `<span class="drag-hint">拖拽排序</span>`;
  const up = btn('↑', () => moveBlock(localIdx, -1));
  const down = btn('↓', () => moveBlock(localIdx, 1));
  const del = btn('删除', () => removeBlock(localIdx));
  del.classList.add('danger');
  toolbar.append(up, down, del);
  card.appendChild(toolbar);

  if (block.type === 'paragraph') {
    const ta = document.createElement('textarea');
    ta.className = 'paragraph';
    ta.value = block.text;
    ta.placeholder = '在这里写段落…';
    ta.onfocus = () => { state.focusedLocal = localIdx; };
    ta.oninput = () => {
      const gi = globalIndexFromPageLocal(localIdx);
      state.blocks[gi].text = ta.value;
      state.dirty = true;
      state.focusedLocal = localIdx;
      rebuildPages();
      updateStats();
      renderOutline();
    };
    card.appendChild(ta);
  } else {
    const wrap = document.createElement('div');
    wrap.className = 'image-block';
    renderImagePreview(wrap, block);
    const controls = document.createElement('div');
    controls.className = 'image-controls';
    controls.innerHTML = `<span>宽度</span>`;
    const slider = document.createElement('input');
    slider.type = 'range';
    slider.min = '30';
    slider.max = '100';
    slider.value = String(Math.round(block.widthPercent * 100));
    const label = document.createElement('span');
    label.textContent = `${slider.value}%`;
    slider.oninput = () => {
      const gi = globalIndexFromPageLocal(localIdx);
      state.blocks[gi].widthPercent = parseInt(slider.value, 10) / 100;
      label.textContent = `${slider.value}%`;
      renderImagePreview(wrap, state.blocks[gi]);
      state.dirty = true;
      updateStats();
    };
    controls.append(slider, label);
    wrap.appendChild(controls);
    card.appendChild(wrap);
  }

  card.addEventListener('dragstart', (e) => {
    e.dataTransfer.setData('text/plain', String(localIdx));
    card.classList.add('dragging');
  });
  card.addEventListener('dragend', () => card.classList.remove('dragging'));
  card.addEventListener('dragover', (e) => { e.preventDefault(); });
  card.addEventListener('drop', (e) => {
    e.preventDefault();
    const from = parseInt(e.dataTransfer.getData('text/plain'), 10);
    const to = localIdx;
    if (!Number.isNaN(from) && from !== to) reorderWithinPage(from, to);
  });

  return card;
}

async function renderImagePreview(wrap, block) {
  wrap.querySelectorAll('img, .missing').forEach(n => n.remove());
  const uri = await window.kanshu.resolveAsset(state.booksDir, block.path);
  if (uri) {
    const img = document.createElement('img');
    img.src = uri;
    img.style.width = `${Math.round(block.widthPercent * 100)}%`;
    img.alt = block.path;
    wrap.prepend(img);
  } else {
    const miss = document.createElement('div');
    miss.className = 'missing';
    miss.textContent = `图片缺失：${block.path}`;
    wrap.prepend(miss);
  }
}

function btn(text, onClick) {
  const b = document.createElement('button');
  b.type = 'button';
  b.className = 'btn ghost';
  b.textContent = text;
  b.onclick = onClick;
  return b;
}

function moveBlock(localIdx, delta) {
  const page = state.pages[state.pageIndex];
  const from = page.startIndex + localIdx;
  const to = from + delta;
  if (to < page.startIndex || to >= page.endExclusive) return;
  const tmp = state.blocks[from];
  state.blocks[from] = state.blocks[to];
  state.blocks[to] = tmp;
  state.dirty = true;
  render();
}

function reorderWithinPage(fromLocal, toLocal) {
  const page = state.pages[state.pageIndex];
  const from = page.startIndex + fromLocal;
  const to = page.startIndex + toLocal;
  const [item] = state.blocks.splice(from, 1);
  state.blocks.splice(to, 0, item);
  state.dirty = true;
  render();
}

function removeBlock(localIdx) {
  const gi = globalIndexFromPageLocal(localIdx);
  state.blocks.splice(gi, 1);
  if (state.blocks.length === 0) state.blocks.push({ type: 'paragraph', text: '', id: newId() });
  if (state.blocks[state.blocks.length - 1].type !== 'paragraph') {
    state.blocks.push({ type: 'paragraph', text: '', id: newId() });
  }
  state.dirty = true;
  render();
}

async function saveDraft(fromAuto = false) {
  if (!state.remoteId) {
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
      exportTxt: true
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
    el.newDraftDialog.close();
    await createNewDraft(title);
  });

  document.getElementById('btn-save').onclick = () => saveDraft(false);
  document.getElementById('btn-upload').onclick = uploadGithub;
  document.getElementById('btn-prev-page').onclick = () => {
    if (state.pageIndex > 0) { state.pageIndex--; render(); }
  };
  document.getElementById('btn-next-page').onclick = () => {
    if (state.pageIndex < state.pages.length - 1) { state.pageIndex++; render(); }
  };
  document.getElementById('btn-focus').onclick = () => {
    state.focusMode = !state.focusMode;
    document.getElementById('btn-focus').textContent = state.focusMode ? '退出专注' : '专注';
    render();
  };

  document.getElementById('btn-chapter').onclick = () => {
    const sub = prompt('章节名（可选）', '') || '';
    const title = nextChapterTitle(state.blocks, sub);
    const last = state.blocks[state.blocks.length - 1];
    if (last.type === 'paragraph') {
      last.text = last.text.trimEnd() + (last.text.length ? '\n\n' : '') + title;
    } else {
      state.blocks.push({ type: 'paragraph', text: title, id: newId() });
    }
    state.blocks.push({ type: 'paragraph', text: '', id: newId() });
    state.dirty = true;
    state.pageIndex = state.pages.length;
    render();
  };

  document.getElementById('btn-image').onclick = async () => {
    if (!state.remoteId) {
      setStatus('请先打开或新建文稿', true);
      return;
    }
    const picked = await window.kanshu.pickImage(state.remoteId);
    if (!picked) return;
    state.blocks.push({ type: 'image', path: picked.relativePath, widthPercent: 1, id: newId() });
    state.blocks.push({ type: 'paragraph', text: '', id: newId() });
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
