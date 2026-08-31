/**
 * 与 Android DefaultBooksSync 对齐：以 catalog.json 为索引，拉取 default-books/ 缺失文件。
 */
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');

const IMAGE_RE = /\[\[IMG:([^|\]]+?)(?:\|w=[0-9.]+)?\]\]/g;

function rawBase(cfg) {
  const owner = cfg.githubOwner || 'zhple';
  const repo = cfg.githubRepo || 'kanshu';
  const branch = cfg.githubBranch || 'main';
  return `https://raw.githubusercontent.com/${owner}/${repo}/${branch}/default-books`;
}

function apiBase(cfg) {
  const owner = cfg.githubOwner || 'zhple';
  const repo = cfg.githubRepo || 'kanshu';
  return `https://api.github.com/repos/${owner}/${repo}/contents/default-books`;
}

function githubHeaders(token) {
  const h = {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'Kanshu-Desktop-LibrarySync',
    'X-GitHub-Api-Version': '2022-11-28'
  };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

async function fetchJson(url, token) {
  const res = await fetch(url, { headers: githubHeaders(token) });
  if (!res.ok) throw new Error(`HTTP ${res.status} ${url}`);
  return res.json();
}

async function fetchCatalog(cfg) {
  const token = (cfg.githubToken || '').trim();
  const rawUrl = `${rawBase(cfg)}/catalog.json`;
  try {
    const res = await fetch(rawUrl, { headers: { 'User-Agent': 'Kanshu-Desktop-LibrarySync' } });
    if (res.ok) return res.json();
  } catch { /* fallback */ }

  if (token) {
    const json = await fetchJson(`${apiBase(cfg)}/catalog.json?ref=${cfg.githubBranch || 'main'}`, token);
    return JSON.parse(Buffer.from(json.content.replace(/\n/g, ''), 'base64').toString('utf8'));
  }
  throw new Error('无法读取 catalog.json，请检查网络或 Token');
}

async function downloadTo(url, destPath, token) {
  await fsp.mkdir(path.dirname(destPath), { recursive: true });
  const res = await fetch(url, { headers: githubHeaders(token) });
  if (!res.ok) throw new Error(`下载失败 HTTP ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length === 0) throw new Error('文件为空');
  await fsp.writeFile(destPath, buf);
}

async function downloadRepoFile(cfg, fileName, destPath) {
  const token = (cfg.githubToken || '').trim();
  const rawUrl = `${rawBase(cfg)}/${fileName.split('/').map(encodeURIComponent).join('/')}`;
  try {
    await downloadTo(rawUrl, destPath, null);
    return;
  } catch {
    if (!token) throw new Error(`无法下载 ${fileName}`);
  }
  const apiUrl = `${apiBase(cfg)}/${fileName.split('/').map(encodeURIComponent).join('/')}?ref=${cfg.githubBranch || 'main'}`;
  const fileJson = await fetchJson(apiUrl, token);
  const bytes = Buffer.from(fileJson.content.replace(/\n/g, ''), 'base64');
  await fsp.mkdir(path.dirname(destPath), { recursive: true });
  await fsp.writeFile(destPath, bytes);
}

function collectAssetPathsFromDraft(content) {
  const names = new Set();
  let m;
  const re = new RegExp(IMAGE_RE.source, 'g');
  while ((m = re.exec(content)) !== null) {
    const rel = m[1].trim();
    const fileName = rel.replace(/^write_assets[\\/]/, '');
    if (fileName) names.add(fileName);
  }
  return [...names];
}

async function ensureDraftAssets(booksDir, cfg, draftContent) {
  const assetsRoot = path.join(booksDir, 'write_assets');
  await fsp.mkdir(assetsRoot, { recursive: true });
  let count = 0;
  for (const fileName of collectAssetPathsFromDraft(draftContent)) {
    const dest = path.join(assetsRoot, fileName);
    if (fs.existsSync(dest) && fs.statSync(dest).size > 0) continue;
    try {
      await downloadRepoFile(cfg, `write_assets/${fileName}`, dest);
      count++;
    } catch {
      /* 插图缺失不阻断整本同步 */
    }
  }
  return count;
}

/**
 * @param {string} booksDir default-books 目录
 * @param {object} cfg 含 githubOwner/repo/branch/token
 * @param {(msg:string)=>void} [onProgress]
 */
async function syncLibrary(booksDir, cfg, onProgress) {
  if (!booksDir) throw new Error('请先选择工作目录');
  await fsp.mkdir(booksDir, { recursive: true });

  onProgress?.('正在读取 catalog.json…');
  const catalog = await fetchCatalog(cfg);
  await fsp.writeFile(
    path.join(booksDir, 'catalog.json'),
    JSON.stringify(catalog, null, 2),
    'utf8'
  );

  const books = Array.isArray(catalog.books) ? catalog.books : [];
  let added = 0;
  let skipped = 0;
  let failed = 0;
  let assets = 0;

  for (const book of books) {
    const id = book.id;
    const fileName = book.file;
    if (!id || !fileName) continue;

    onProgress?.(`同步：${book.title || id}`);

    const readablePath = path.join(booksDir, fileName);
    if (!fs.existsSync(readablePath) || fs.statSync(readablePath).size === 0) {
      try {
        await downloadRepoFile(cfg, fileName, readablePath);
        added++;
      } catch {
        failed++;
      }
    } else {
      skipped++;
    }

    const draftName = `${id}.draft.txt`;
    const draftPath = path.join(booksDir, draftName);
    let draftContent = '';
    if (!fs.existsSync(draftPath) || fs.statSync(draftPath).size === 0) {
      try {
        await downloadRepoFile(cfg, draftName, draftPath);
        draftContent = await fsp.readFile(draftPath, 'utf8');
        added++;
      } catch {
        /* 没有 draft 也正常（只读 EPUB 等） */
      }
    } else {
      try {
        draftContent = await fsp.readFile(draftPath, 'utf8');
      } catch { /* ignore */ }
    }

    if (draftContent) {
      assets += await ensureDraftAssets(booksDir, cfg, draftContent);
    }
  }

  return {
    added,
    skipped,
    failed,
    assets,
    total: books.length,
    catalog,
    message: buildSyncMessage(added, skipped, failed, assets, books.length)
  };
}

function buildSyncMessage(added, skipped, failed, assets, total) {
  const parts = [`书库共 ${total} 本`];
  if (added > 0) parts.push(`新下载 ${added} 个文件`);
  if (assets > 0) parts.push(`插图 ${assets} 张`);
  if (failed > 0) parts.push(`失败 ${failed}`);
  if (added === 0 && failed === 0) parts.push('本地已是最新');
  return parts.join('，');
}

async function listLibrary(booksDir) {
  const catalogPath = path.join(booksDir, 'catalog.json');
  let catalog = { books: [], folders: ['仓库书'], version: 0 };
  if (fs.existsSync(catalogPath)) {
    catalog = JSON.parse(await fsp.readFile(catalogPath, 'utf8'));
  }

  const byId = new Map();
  for (const book of catalog.books || []) {
    const id = book.id;
    if (!id) continue;
    const readablePath = book.file ? path.join(booksDir, book.file) : null;
    const draftPath = path.join(booksDir, `${id}.draft.txt`);
    const hasReadable = readablePath && fs.existsSync(readablePath) && fs.statSync(readablePath).size > 0;
    const draftStat = fs.existsSync(draftPath) ? fs.statSync(draftPath) : null;
    byId.set(id, {
      ...book,
      hasReadable,
      hasDraft: !!(draftStat && draftStat.size > 0),
      editable: !!(draftStat && draftStat.size >= 0 && fs.existsSync(draftPath)),
      draftMtime: draftStat ? draftStat.mtimeMs : 0,
      format: (book.format || '').toUpperCase(),
      localOnly: false
    });
  }

  try {
    const entries = await fsp.readdir(booksDir, { withFileTypes: true });
    for (const ent of entries) {
      if (!ent.isFile() || !ent.name.endsWith('.draft.txt')) continue;
      const id = ent.name.replace(/\.draft\.txt$/, '');
      if (byId.has(id)) continue;
      const draftPath = path.join(booksDir, ent.name);
      const draftStat = fs.statSync(draftPath);
      byId.set(id, {
        id,
        title: id,
        author: '',
        file: '',
        format: 'DRAFT',
        folder: '仓库书',
        hasReadable: false,
        hasDraft: draftStat.size > 0,
        editable: true,
        draftMtime: draftStat.mtimeMs,
        localOnly: true
      });
    }
  } catch { /* ignore */ }

  const books = [...byId.values()];
  books.sort((a, b) => (b.lastReadAt || b.draftMtime || 0) - (a.lastReadAt || a.draftMtime || 0));
  return {
    books,
    folders: catalog.folders || ['仓库书'],
    version: catalog.version || 0
  };
}

module.exports = {
  syncLibrary,
  listLibrary,
  fetchCatalog
};
