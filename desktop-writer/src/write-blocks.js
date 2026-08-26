/** 与 Android WriteMarkers.kt / WriteBlocks.kt 对齐 */

export const imageRegex = /\[\[IMG:([^|\]]+?)(?:\|w=([0-9]*\.?[0-9]+))?\]\]/g;

export function imageMarker(path, widthPercent = 1) {
  const w = Math.min(1, Math.max(0.3, widthPercent));
  if (w >= 0.995) return `[[IMG:${path}]]`;
  return `[[IMG:${path}|w=${w.toFixed(2)}]]`;
}

export function stripImagesForPlainText(content) {
  return content.replace(imageRegex, (_m, p1) => {
    const name = p1.split(/[\\/]/).pop();
    return `【图片：${name}】`;
  });
}

export function newId() {
  return crypto.randomUUID();
}

export function parse(content) {
  const normalized = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  if (!normalized.trim()) return [{ type: 'paragraph', text: '', id: newId() }];

  const result = [];
  let last = 0;
  const re = new RegExp(imageRegex.source, 'g');
  let match;
  while ((match = re.exec(normalized)) !== null) {
    if (match.index > last) {
      const text = normalized.slice(last, match.index).replace(/^\n+|\n+$/g, '');
      if (text.length > 0 || result.length === 0) {
        result.push({ type: 'paragraph', text, id: newId() });
      }
    }
    const path = match[1].trim();
    const width = match[2] ? Math.min(1, Math.max(0.3, parseFloat(match[2]))) : 1;
    if (path) result.push({ type: 'image', path, widthPercent: width, id: newId() });
    last = match.index + match[0].length;
  }
  if (last < normalized.length) {
    result.push({ type: 'paragraph', text: normalized.slice(last).replace(/^\n+/, ''), id: newId() });
  }
  if (result.length === 0) result.push({ type: 'paragraph', text: '', id: newId() });
  if (result[result.length - 1].type !== 'paragraph') {
    result.push({ type: 'paragraph', text: '', id: newId() });
  }
  return result;
}

export function serialize(blocks) {
  const parts = [];
  for (const block of blocks) {
    if (block.type === 'paragraph') {
      if (block.text.length > 0 || parts.length === 0) parts.push(block.text);
    } else if (block.type === 'image') {
      parts.push(imageMarker(block.path, block.widthPercent));
    }
  }
  return parts.join('\n\n').replace(/\n{3,}/g, '\n\n').trim();
}

export function contentLooksEmpty(blocks) {
  return !blocks.some(b => (b.type === 'paragraph' && b.text.trim()) || b.type === 'image');
}

const chapterLineRegex = /^第[\d零一二三四五六七八九十百千两]+章|^序章|^终章|^楔子|^尾声|^番外|^Chapter\s+\d+/;

function blockWeight(block) {
  if (block.type === 'paragraph') return Math.max(block.text.length, 40);
  return 500;
}

export function isChapterStart(block) {
  if (block.type !== 'paragraph') return false;
  const first = block.text.split('\n').map(l => l.trim()).find(l => l.length > 0);
  return first ? chapterLineRegex.test(first) : false;
}

export function chapterTitleOf(block) {
  if (block.type !== 'paragraph') return '正文';
  const first = block.text.split('\n').map(l => l.trim()).find(l => l.length > 0);
  return first ? first.slice(0, 24) : '正文';
}

export function buildPages(blocks) {
  if (!blocks.length) return [{ title: '正文', startIndex: 0, endExclusive: 0 }];
  const pages = [];
  let start = 0;
  let title = '开头';
  let weight = 0;
  let pageOrdinal = 1;
  const PAGE_CHAR_BUDGET = 1600;

  function flush(end) {
    if (end <= start) return;
    pages.push({ title, startIndex: start, endExclusive: end });
    start = end;
    weight = 0;
    pageOrdinal++;
  }

  blocks.forEach((block, index) => {
    const chapter = isChapterStart(block);
    if (chapter && index > start) {
      flush(index);
      title = chapterTitleOf(block);
    } else if (chapter && index === start) {
      title = chapterTitleOf(block);
    }
    const w = blockWeight(block);
    if (weight > 0 && weight + w > PAGE_CHAR_BUDGET) {
      flush(index);
      title = chapter ? chapterTitleOf(block) : `续·${pageOrdinal}`;
    }
    weight += w;
  });
  flush(blocks.length);
  if (!pages.length) pages.push({ title: '正文', startIndex: 0, endExclusive: blocks.length });
  return pages;
}

export function countChapters(content) {
  return content.split('\n').filter(line => chapterLineRegex.test(line.trim())).length;
}

export function nextChapterTitle(blocks) {
  const n = countChapters(serialize(blocks)) + 1;
  return `第${n}章 `;
}
