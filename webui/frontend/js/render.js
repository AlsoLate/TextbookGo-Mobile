/* 下书 render：业务组件。全部返回 DOM 节点，事件内聚，无字符串拼接。 */
(function () {
  var el = function () { return window.ui.el.apply(null, arguments); };

  function badge(book) {
    if (window.S.selected[book.id]) return '已选';
    return book.downloaded ? '已下载' : '待选';
  }

  // 图书卡片：勾选/取消。封面三策：平台图 > 已下载用本地渲染 > 拼贴占位。
  // 不再盲打 /api/cover —— 未下载且无平台图的书请求它必 404。
  function bookCard(book, onToggle) {
    var cov = window.ui.cover(book.id, book.title,
      book.cover_url || (book.downloaded ? window.api.coverUrl(book.id) : null));
    var mt = el('span', 'mt', [badge(book)]);
    var tt = el('span', 'bm-title', [book.title]); // 书名常显：有封面图时 cov-t 被图盖住，元数据行必须补上
    window.ui.hoverTip(tt); // 截断时悬停看全名
    var meta = el('div', 'book-meta', [mt, tt]);
    var card = el('div', 'book-card' + (window.S.selected[book.id] ? ' sel' : ''), [cov, meta]);
    card.dataset.id = book.id;
    card._book = book; // 供 toggleBook 原地更新角标，免全量重建
    card.addEventListener('click', function () { onToggle(book.id); });
    return card;
  }

  // 下载进度行（书架悬浮面板）：标题 + 进度条 + 状态字。
  function dlRow(book) {
    var name = el('div', 'dlr-name', [book.title]);
    var fill = el('i', 'dlr-fill');
    var bar = el('div', 'dlr-bar', [fill]);
    var num = el('span', 'dlr-num', ['等待中']);
    var row = el('div', 'dlr-row', [name, bar, num]);
    row._fill = fill;
    row._num = num;
    return row;
  }

  // 书架卡片：封面 + 书名 + 文件名（普通网格排列，每行多本自动换行）
  // 书架书必已下载，封面显式走 /api/cover（pdfium 渲染 PDF 首页）。
  // draggable + dragstart 携带 bookId，供 trash-zone 接收删除。
  function shelfCard(book) {
    var cov = window.ui.cover(book.id, book.title, window.api.coverUrl(book.id));
    var t = el('div', 'sc-title', [book.title]);
    window.ui.hoverTip(t); // 截断时悬停看全名
    var f = el('div', 'sc-file', [book.filename || book.version || '']);
    window.ui.hoverTip(f); // 文件名常超长，悬停看全名
    var card = el('div', 'shelf-card', [cov, t, f]);
    card.dataset.id = book.id;
    card.draggable = true;
    card.addEventListener('dragstart', function (e) {
      e.dataTransfer.setData('text/bookId', book.id);
      e.dataTransfer.effectAllowed = 'move';
      card.classList.add('is-dragging');
    });
    card.addEventListener('dragend', function () {
      card.classList.remove('is-dragging');
    });
    return card;
  }

  window.render = {
    bookCard: bookCard, dlRow: dlRow,
    shelfCard: shelfCard, badge: badge
  };
})();
