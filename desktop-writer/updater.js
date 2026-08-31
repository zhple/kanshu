const { app, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const fsp = fs.promises;
const https = require('https');
const http = require('http');
const { spawn } = require('child_process');

const RELEASES_API = 'https://api.github.com/repos/zhple/kanshu/releases?per_page=30';
const LATEST_JSON_URL = 'https://raw.githubusercontent.com/zhple/kanshu/main/desktop-writer/latest.json';
const TAG_PREFIX = 'writer-v';

function compareSemver(a, b) {
  const pa = String(a).replace(/^v/i, '').split('.').map((n) => parseInt(n, 10) || 0);
  const pb = String(b).replace(/^v/i, '').split('.').map((n) => parseInt(n, 10) || 0);
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const x = pa[i] || 0;
    const y = pb[i] || 0;
    if (x > y) return 1;
    if (x < y) return -1;
  }
  return 0;
}

function requestText(url, headers = {}, timeoutMs = 20000) {
  return new Promise((resolve, reject) => {
    const mod = url.startsWith('http://') ? http : https;
    const req = mod.get(
      url,
      { headers, timeout: timeoutMs },
      (res) => {
        if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          requestText(res.headers.location, headers, timeoutMs).then(resolve, reject);
          return;
        }
        let body = '';
        res.on('data', (c) => { body += c; });
        res.on('end', () => {
          if (res.statusCode !== 200) {
            reject(new Error(`HTTP ${res.statusCode}`));
            return;
          }
          resolve(body);
        });
      }
    );
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('请求超时'));
    });
  });
}

function githubApiHeaders(token) {
  const headers = {
    Accept: 'application/vnd.github+json',
    'User-Agent': `Kanshu-Writer-Updater/${app.getVersion()}`,
    'X-GitHub-Api-Version': '2022-11-28'
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

async function fetchJson(url, token = '') {
  const body = await requestText(url, githubApiHeaders(token));
  return JSON.parse(body);
}

async function fetchLatestManifest() {
  const body = await requestText(LATEST_JSON_URL, {
    Accept: 'application/json',
    'User-Agent': `Kanshu-Writer-Updater/${app.getVersion()}`
  });
  return JSON.parse(body);
}

function pickDesktopAsset(assets = []) {
  const names = assets.map((a) => ({
    name: a.name || '',
    url: a.browser_download_url || '',
    size: a.size || 0
  }));
  const setup = names.find((a) => /setup.*\.exe$/i.test(a.name) || /kanshu-writer-setup/i.test(a.name));
  if (setup) return { ...setup, kind: 'setup' };
  const nsis = names.find((a) => /\.exe$/i.test(a.name) && !/portable/i.test(a.name));
  if (nsis) return { ...nsis, kind: 'setup' };
  const portable = names.find((a) => /portable.*\.exe$/i.test(a.name) || /kanshu-writer-.*\.exe$/i.test(a.name));
  if (portable) return { ...portable, kind: 'portable' };
  return null;
}

function parseWriterVersion(tagName = '') {
  const t = String(tagName).trim();
  if (t.toLowerCase().startsWith(TAG_PREFIX)) {
    return t.slice(TAG_PREFIX.length);
  }
  return null;
}

function buildUpdateInfo(currentVersion, latestVersion, asset, extra = {}) {
  return {
    updateAvailable: true,
    currentVersion,
    latestVersion,
    releaseNotes: extra.releaseNotes || '',
    htmlUrl: extra.htmlUrl || '',
    downloadUrl: asset.url,
    fileName: asset.name,
    kind: asset.kind,
    size: asset.size || 0,
    source: extra.source || 'api'
  };
}

async function checkViaGithubApi(currentVersion, token) {
  const releases = await fetchJson(RELEASES_API, token);
  if (!Array.isArray(releases)) throw new Error('更新接口返回异常');

  for (const rel of releases) {
    if (rel.draft || rel.prerelease) continue;
    const version = parseWriterVersion(rel.tag_name || '');
    if (!version) continue;
    if (compareSemver(version, currentVersion) <= 0) {
      return { updateAvailable: false, currentVersion, latestVersion: version, source: 'api' };
    }
    const asset = pickDesktopAsset(rel.assets || []);
    if (!asset?.url) continue;
    return buildUpdateInfo(currentVersion, version, asset, {
      releaseNotes: rel.body || '',
      htmlUrl: rel.html_url || '',
      source: 'api'
    });
  }
  return { updateAvailable: false, currentVersion, latestVersion: currentVersion, source: 'api' };
}

async function checkViaLatestJson(currentVersion) {
  const manifest = await fetchLatestManifest();
  const version = String(manifest.version || '').trim();
  if (!version) throw new Error('latest.json 缺少 version');

  if (compareSemver(version, currentVersion) <= 0) {
    return { updateAvailable: false, currentVersion, latestVersion: version, source: 'manifest' };
  }

  const setup = manifest.setup || {};
  const portable = manifest.portable || {};
  const asset = setup.url
    ? { name: setup.name || `kanshu-writer-setup-${version}.exe`, url: setup.url, kind: 'setup', size: 0 }
    : portable.url
      ? { name: portable.name || `kanshu-writer-${version}-portable.exe`, url: portable.url, kind: 'portable', size: 0 }
      : null;
  if (!asset?.url) throw new Error('latest.json 缺少下载地址');

  return buildUpdateInfo(currentVersion, version, asset, {
    releaseNotes: manifest.releaseNotes || '',
    htmlUrl: manifest.htmlUrl || '',
    source: 'manifest'
  });
}

/**
 * 在 releases / latest.json 中找最新的桌面端 writer 版本。
 * API 403 或限流时自动回退 raw latest.json。
 */
async function checkForUpdate(currentVersion = app.getVersion(), token = '') {
  try {
    return await checkViaGithubApi(currentVersion, token);
  } catch (apiErr) {
    try {
      return await checkViaLatestJson(currentVersion);
    } catch (manifestErr) {
      const detail = apiErr.message || '未知错误';
      if (/403/.test(detail)) {
        throw new Error('检查更新失败：无法访问 GitHub（403）。请检查网络或到发布页手动下载。');
      }
      throw new Error(`检查更新失败：${detail}`);
    }
  }
}

function downloadFile(url, destPath, onProgress) {
  return new Promise((resolve, reject) => {
    const doGet = (target, redirects = 0) => {
      if (redirects > 8) {
        reject(new Error('下载重定向过多'));
        return;
      }
      const mod = target.startsWith('http://') ? http : https;
      const req = mod.get(
        target,
        {
          headers: { 'User-Agent': `Kanshu-Writer-Updater/${app.getVersion()}` },
          timeout: 120000
        },
        (res) => {
          if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
            res.resume();
            doGet(res.headers.location, redirects + 1);
            return;
          }
          if (res.statusCode !== 200) {
            reject(new Error(`下载失败 HTTP ${res.statusCode}`));
            res.resume();
            return;
          }
          const total = parseInt(res.headers['content-length'] || '0', 10) || 0;
          let received = 0;
          const out = fs.createWriteStream(destPath);
          res.on('data', (chunk) => {
            received += chunk.length;
            if (onProgress && total > 0) onProgress(received / total);
          });
          res.pipe(out);
          out.on('finish', () => out.close(() => resolve(destPath)));
          out.on('error', reject);
        }
      );
      req.on('error', reject);
      req.on('timeout', () => {
        req.destroy();
        reject(new Error('下载超时'));
      });
    };
    doGet(url);
  });
}

async function downloadUpdate(info, onProgress) {
  if (!info?.downloadUrl) throw new Error('没有可下载的安装包');
  const dir = path.join(app.getPath('temp'), 'kanshu-writer-updates');
  await fsp.mkdir(dir, { recursive: true });
  const fileName = info.fileName || `kanshu-writer-${info.latestVersion}.exe`;
  const dest = path.join(dir, fileName);
  if (fs.existsSync(dest)) {
    try { await fsp.unlink(dest); } catch { /* ignore */ }
  }
  await downloadFile(info.downloadUrl, dest, onProgress);
  return dest;
}

function launchInstallerAndQuit(filePath) {
  const child = spawn(filePath, [], {
    detached: true,
    stdio: 'ignore',
    windowsHide: false
  });
  child.unref();
  setTimeout(() => app.quit(), 400);
}

async function promptAndUpdate(parentWindow, { silentIfCurrent = true, token = '' } = {}) {
  let info;
  try {
    info = await checkForUpdate(app.getVersion(), token);
  } catch (e) {
    if (!silentIfCurrent) {
      const { response } = await dialog.showMessageBox(parentWindow, {
        type: 'error',
        title: '检查更新',
        message: e.message || '检查更新失败',
        buttons: ['确定', '打开发布页'],
        defaultId: 0,
        cancelId: 0
      });
      if (response === 1) {
        await shell.openExternal('https://github.com/zhple/kanshu/releases?q=writer-v');
      }
    }
    return { ok: false, error: e.message };
  }

  if (!info.updateAvailable) {
    if (!silentIfCurrent) {
      await dialog.showMessageBox(parentWindow, {
        type: 'info',
        title: '检查更新',
        message: `已是最新版本 ${info.currentVersion}`
      });
    }
    return { ok: true, updateAvailable: false, info };
  }

  const notes = (info.releaseNotes || '').trim().slice(0, 800);
  const { response } = await dialog.showMessageBox(parentWindow, {
    type: 'info',
    title: '发现新版本',
    message: `看书写作 ${info.latestVersion} 可用（当前 ${info.currentVersion}）`,
    detail: notes || '建议更新以获得最新写作功能与修复。',
    buttons: ['立即更新', '稍后再说', '打开发布页'],
    defaultId: 0,
    cancelId: 1
  });

  if (response === 2 && info.htmlUrl) {
    await shell.openExternal(info.htmlUrl);
    return { ok: true, updateAvailable: true, openedPage: true, info };
  }
  if (response !== 0) {
    return { ok: true, updateAvailable: true, deferred: true, info };
  }

  try {
    if (parentWindow && !parentWindow.isDestroyed()) {
      parentWindow.setProgressBar(0.05);
    }
    const filePath = await downloadUpdate(info, (p) => {
      if (parentWindow && !parentWindow.isDestroyed()) {
        parentWindow.setProgressBar(Math.min(0.99, Math.max(0.05, p)));
      }
    });
    if (parentWindow && !parentWindow.isDestroyed()) {
      parentWindow.setProgressBar(-1);
    }

    if (info.kind === 'setup') {
      const confirm = await dialog.showMessageBox(parentWindow, {
        type: 'question',
        title: '安装更新',
        message: '安装包已下载，将退出应用并打开安装程序。',
        buttons: ['开始安装', '取消'],
        defaultId: 0,
        cancelId: 1
      });
      if (confirm.response === 0) {
        launchInstallerAndQuit(filePath);
        return { ok: true, updateAvailable: true, installing: true, info };
      }
      await shell.showItemInFolder(filePath);
      return { ok: true, updateAvailable: true, savedPath: filePath, info };
    }

    await dialog.showMessageBox(parentWindow, {
      type: 'info',
      title: '便携版已下载',
      message: '已下载新的便携版 exe，请关闭本程序后替换运行。',
      detail: filePath
    });
    await shell.showItemInFolder(filePath);
    return { ok: true, updateAvailable: true, savedPath: filePath, info };
  } catch (e) {
    if (parentWindow && !parentWindow.isDestroyed()) {
      parentWindow.setProgressBar(-1);
    }
    await dialog.showMessageBox(parentWindow, {
      type: 'error',
      title: '更新失败',
      message: e.message || '下载或安装失败'
    });
    return { ok: false, error: e.message, info };
  }
}

module.exports = {
  checkForUpdate,
  downloadUpdate,
  launchInstallerAndQuit,
  promptAndUpdate,
  compareSemver
};
