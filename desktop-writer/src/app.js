import {
  parse, serialize, buildPages, nextChapterTitle, newId
} from './write-blocks.js';

const state = {
  remoteId: null,
  title: '',
  blocks: [{ type: 'paragraph', text: '', id: newId() }],
  pages: [],
  pageIndex: 0,
  booksDir: '',
  dirty: false,
  config: null
};

const el = {
  draftList: document.getElementById('draft-list'),
  workspaceLabel: document.getElementById('workspace-label'),
  titleInput: document.getElementById('title-input'),
  blocks: document.getElementById('blocks'),
  pageLabel: document.getElementById('page-label'),
  pageTitle: document.getElementById('page-title'),
  status: document.getElementById('status'),
  settingsDialog: document.getElementById('settings-dialog'),
  cfgToken: document.getElementById('cfg-token'),
  cfgOwner: document.getElementById('cfg-owner'),
  cfgRepo: document.getElementById('cfg-repo'),
  cfgBranch: document.getElementById('cfg-branch')
};

function setStatus(msg, isError = false) {
  el.status.textContent = msg || '';
  el.status.style.color = isError ? 'var(--danger)' : 'var(--muted)';
}

function rebuildPages() {
  state.pages = buildPages(state.blocks);
  state.pageIndex = Math.min(state.pageIndex, Math.max(0, state.pages.length - 1));
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

async function init() {
  state.config = await window.kanshu.getConfig();
  updateWorkspaceLabel();
  await refreshDraftList();
  bindEvents();
}

function updateWorkspaceLabel() {
  const ws = state.config?.workspace;
  el.workspaceLabel.textContent = ws ? `工作目录：${ws}` : '请先选择 kanshu 仓库根目录（或 default-books 文件夹）';
}

async function refreshDraftList() {
  const { drafts, booksDir } = await window.kanshu.listDrafts();
  state.booksDir = booksDir;
  el.draftList.innerHTML = '';
  if (!drafts.length) {
    el.draftList.innerHTML = '<li class="meta" style="padding:12px">暂无 .draft.txt 文稿</li>';
    return;
  }
  for (const d of drafts) {
    const li = document.createElement('li');
    li.dataset.remoteId = d.remoteId;
    if (d.remoteId === state.remoteId) li.classList.add('active');
    li.innerHTML = `<div class="name">${escapeHtml(d.remoteId)}</div><div class="meta">${new Date(d.mtime).toLocaleString()}</div>`;
    li.onclick = () => loadDraft(d.remoteId);
    el.draftList.appendChild(li);
  }
}

async function loadDraft(remoteId) {
  if (state.dirty && !confirm('当前文稿未保存，确定切换？')) return;
  try {
    const { content, booksDir } = await window.kanshu.readDraft(remoteId);
    state.remoteId = remoteId;
    state.booksDir = booksDir;
    state.title = remoteId;
    state.blocks = parse(content);
    state.pageIndex = 0;
    state.dirty = false;
    rebuildPages();
    el.titleInput.value = state.title;
    render();
    await refreshDraftList();
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
    ta.oninput = () => {
      const gi = globalIndexFromPageLocal(localIdx);
      state.blocks[gi].text = ta.value;
      state.dirty = true;
      rebuildPages();
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

async function saveDraft() {
  if (!state.remoteId) {
    setStatus('请先新建或打开一篇文稿', true);
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
    state.dirty = false;
    setStatus('已保存 draft.txt 与 txt 导出');
    await refreshDraftList();
  } catch (e) {
    setStatus(e.message || '保存失败', true);
  }
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

function bindEvents() {
  document.getElementById('btn-workspace').onclick = async () => {
    const ws = await window.kanshu.pickWorkspace();
    if (ws) {
      state.config = await window.kanshu.getConfig();
      updateWorkspaceLabel();
      await refreshDraftList();
      setStatus('工作目录已更新');
    }
  };

  document.getElementById('btn-new').onclick = async () => {
    const title = prompt('新文稿标题', '未命名') || '未命名';
    try {
      const { remoteId } = await window.kanshu.createDraft(title);
      state.remoteId = remoteId;
      state.title = title;
      state.blocks = [{ type: 'paragraph', text: '', id: newId() }];
      state.pageIndex = 0;
      state.dirty = false;
      el.titleInput.value = title;
      rebuildPages();
      render();
      await refreshDraftList();
      setStatus('已创建新文稿');
    } catch (e) {
      setStatus(e.message || '创建失败', true);
    }
  };

  document.getElementById('btn-save').onclick = saveDraft;
  document.getElementById('btn-upload').onclick = uploadGithub;
  document.getElementById('btn-prev-page').onclick = () => {
    if (state.pageIndex > 0) { state.pageIndex--; render(); }
  };
  document.getElementById('btn-next-page').onclick = () => {
    if (state.pageIndex < state.pages.length - 1) { state.pageIndex++; render(); }
  };

  document.getElementById('btn-chapter').onclick = () => {
    const title = nextChapterTitle(state.blocks);
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

  document.getElementById('btn-settings').onclick = () => {
    el.cfgToken.value = state.config?.githubToken || '';
    el.cfgOwner.value = state.config?.githubOwner || 'zhple';
    el.cfgRepo.value = state.config?.githubRepo || 'kanshu';
    el.cfgBranch.value = state.config?.githubBranch || 'main';
    el.settingsDialog.showModal();
  };

  document.getElementById('settings-form').onsubmit = async (e) => {
    e.preventDefault();
    state.config = {
      ...state.config,
      githubToken: el.cfgToken.value.trim(),
      githubOwner: el.cfgOwner.value.trim() || 'zhple',
      githubRepo: el.cfgRepo.value.trim() || 'kanshu',
      githubBranch: el.cfgBranch.value.trim() || 'main'
    };
    await window.kanshu.saveConfig(state.config);
    el.settingsDialog.close();
    setStatus('设置已保存');
  };

  window.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
      e.preventDefault();
      saveDraft();
    }
  });
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

init().catch(err => setStatus(err.message, true));
