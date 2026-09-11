/* 下书 下载流：点下载 → 跳书架 → 悬浮进度面板（SSE 实时驱动）→ 完成落架。
   步骤只由流程推进，用户无自由跳转；完成页已删，一切收束在书架。 */
(function () {
  var rows = {};    // book_id -> 进度行
  var errCount = 0;
  var panelEl = null;

  function startDownload() {
    var a = window.app;
    var books = a.selectedBooks();
    if (!books.length) { a.setStatus('先选至少一本教材'); return; }
    errCount = 0;
    rows = {};
    panelEl = a.$('dlStrip');
    panelEl.textContent = '';
    panelEl.classList.remove('hidden', 'fade-out');
    panelEl.appendChild(window.ui.el('div', 'dl-strip-title', ['下载进度']));
    books.forEach(function (b) {
      var row = window.render.dlRow(b);
      panelEl.appendChild(row);
      rows[b.id] = row;
    });
    a.setView('books'); // 下载在书架完成，进度面板悬浮可见
    window.api.download(books.map(function (b) { return b.id; })).then(function (res) {
      window.S.stream = window.api.stream(res.stream_id);
      window.S.stream.onmessage = onStream;
      window.S.stream.onerror = onStreamError;
    }).catch(function (err) {
      a.setStatus(err.message || '下载启动失败');
      hidePanel();
    });
  }

  function hidePanel() {
    if (!panelEl) return;
    panelEl.classList.add('fade-out');
    setTimeout(function () {
      panelEl.classList.add('hidden');
      panelEl.classList.remove('fade-out');
    }, 500);
  }

  function bindTrash() {
    var zone = window.app.$('trashZone');
    if (!zone) return;
    zone.addEventListener('dragover', function (e) {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      zone.classList.add('is-over');
    });
    zone.addEventListener('dragleave', function () { zone.classList.remove('is-over'); });
    zone.addEventListener('drop', function (e) {
      e.preventDefault();
      zone.classList.remove('is-over');
      var id = e.dataTransfer.getData('text/bookId');
      if (!id) return;
      window.api.deleteBook(id).then(loadShelf).catch(function (err) {
        window.app.setStatus(err.message || '删除失败');
      });
    });
  }

  // 六种 SSE 事件全处理。书签事件静默（进度面板不需要展示）。
  function onStream(e) {
    var ev = JSON.parse(e.data);
    if (ev.type === 'start') markStart(ev.book_id);
    else if (ev.type === 'progress') setProgress(ev);
    else if (ev.type === 'done') { markDone(ev.book_id); loadShelf(); }
    else if (ev.type === 'bookmark') return;
    else if (ev.type === 'finished') finish();
    else if (ev.type === 'error') {
      errCount++;
      markError(ev);
    }
  }

  function onStreamError() {
    if (window.S.stream) { window.S.stream.close(); window.S.stream = null; }
    window.app.setStatus('下载连接中断，请刷新页面重试');
    hidePanel();
  }

  function setProgress(ev) {
    var row = rows[ev.book_id];
    if (!row) return;
    var pct = ev.total > 0 ? Math.round(ev.read / ev.total * 100) : 0;
    row._fill.style.width = pct + '%';
    row._num.textContent = pct + '%';
  }

  function markStart(id) {
    var row = rows[id];
    if (!row) return;
    row._num.textContent = '下载中';
    row.classList.add('is-active');
  }

  function markDone(id) {
    var row = rows[id];
    if (!row) return;
    row._fill.style.width = '100%';
    row._num.textContent = '已完成';
    row.classList.remove('is-active');
    row.classList.add('is-done');
  }

  function markError(ev) {
    var row = rows[ev.book_id];
    var msg = ev.book_title ? (ev.book_title + '：' + (ev.message || '出错')) : (ev.message || '下载出错');
    if (row) {
      row._num.textContent = '失败';
      row.classList.remove('is-active');
      row.classList.add('is-err');
    }
    window.app.setStatus(msg);
  }

  function finish() {
    if (window.S.stream) window.S.stream.close();
    window.S.stream = null;
    window.S.selected = {};
    window.app.loadHome();
    loadShelf();
    var title = panelEl.querySelector('.dl-strip-title');
    if (title) title.textContent = errCount
      ? ('完成，' + errCount + ' 本失败')
      : '全部完成';
    if (localStorage.getItem('bd.autoOpen') === '1') openDir();
    setTimeout(hidePanel, 1600);
  }

  function shelfSignature(books) {
    return books.map(function (b) {
      return b.id + '|' + b.title + '|' + b.filename + '|' + b.size;
    }).join(';;');
  }

  function popBadge() {
    var badge = window.app.$('bookCount');
    if (!badge) return;
    badge.classList.remove('pop');
    void badge.offsetWidth; // 重置动画
    badge.classList.add('pop');
  }

  function loadShelf() {
    return window.api.shelf().then(function (books) {
      var sig = shelfSignature(books);
      if (sig === window.S.shelfSig) return; // 无变化，跳过渲染（避免闪烁/重跑动画）
      var prev = (window.S.shelfBooks || []).length;
      window.S.shelfSig = sig;
      window.S.shelfBooks = books;
      window.S.shelfSel = books.length ? books[0].id : null;
      var badge = window.app.$('bookCount');
      if (badge) badge.textContent = String(books.length);
      if (books.length > prev) popBadge(); // 新书落架：徽章脉冲
      renderShelf();
    }).catch(function () {});
  }

  function renderShelf() {
    var root = window.app.$('shelfRoot');
    root.textContent = '';
    if (!window.S.shelfBooks.length) {
      var e = window.ui.el('div', 'bento-empty');
      e.appendChild(window.ui.art('landed'));
      e.appendChild(window.ui.el('div', 'be-txt', ['书架还是空的，去下载几本吧']));
      root.appendChild(e);
    } else {
      var grid = window.ui.el('div', 'shelf-grid');
      window.S.shelfBooks.forEach(function (b) {
        grid.appendChild(window.render.shelfCard(b));
      });
      root.appendChild(grid);
    }
    window.ui.fade(root);
    window.ui.stagger(grid);
  }

  function openDir() {
    window.api.openDir().catch(function () {
      if (window.app.setStatus) window.app.setStatus('打开目录失败');
    });
  }

  var pollTimer = null;
  function startPoll() {
    if (pollTimer) return;
    pollTimer = setInterval(function () {
      if (document.hidden || window.S.view !== 'books') return;
      loadShelf();
    }, 3000);
  }

  function initShelf() {
    bindTrash();
    startPoll();
    return loadShelf();
  }

  window.flow = {
    startDownload: startDownload, loadShelf: loadShelf, renderShelf: renderShelf, openDir: openDir,
    initShelf: initShelf
  };
})();