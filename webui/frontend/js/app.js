/* 下书 主逻辑：状态 + 视图 + 装配。渲染全用 ui/render 组件（DOM），零拼接。 */
(function () {
  var STEPS = ['', '登录', '选书'];
  var el = function () { return window.ui.el.apply(null, arguments); };
  window.S = {
    view: 'download', step: 1,
    catalog: null, grades: [], grade: '', subject: '',
    selected: {}, stream: null
  };
  var $ = function (id) { return document.getElementById(id); };

  function bind(sel, fn) {
    document.addEventListener('click', function (e) {
      var t = e.target.closest(sel);
      if (t) { window.sound(); fn(t, e); }
    });
  }
  function setView(v) {
    window.S.view = v;
    document.querySelectorAll('.nav-item').forEach(function (b) {
      b.classList.toggle('is-active', b.dataset.view === v);
    });
    ['download', 'books', 'settings'].forEach(function (k) {
      var s = $('view-' + k);
      if (s) s.classList.toggle('hidden', k !== v);
    });
  }
  function setStep(n) {
    window.S.step = Math.max(1, Math.min(2, n));
    document.querySelectorAll('.step-card').forEach(function (c) {
      c.classList.toggle('is-on', +c.dataset.stepcard === window.S.step);
    });
    document.querySelectorAll('.stepper .st').forEach(function (s) {
      var d = +s.dataset.step;
      if (d) s.classList.toggle('st-on', d <= window.S.step);
    });
    $('stepLabel').textContent = STEPS[window.S.step];
  }
  function setStatus(txt) {
    var s = $('loginStatus');
    if (s) s.textContent = txt || '';
  }
  function loadHome() {
    return window.api.home().then(function (h) {
      $('bookCount').textContent = String(h.history_count || 0);
      var chip = document.querySelector('.login-chip');
      chip.classList.toggle('ok', !!h.logged_in);
      $('loginState').textContent = h.logged_in ? '已登录' : '未登录';
    }).catch(function () {});
  }

  function onLogin() {
    var btn = $('btnLogin');
    setStatus('正在校验登录态，浏览器可能弹出授权...');
    if (btn) btn.disabled = true;
    return window.api.login().then(function () {
      setStatus('登录成功，正在预检目录…');
      return loadCatalog();
    }).then(function () {
      if (btn) btn.disabled = false;
      loadHome(); // 刷新侧栏登录态，避免已登录却仍显示未登录
      setStep(2); // 步骤只由实际流程推进，用户不可自由跳转
    }).catch(function (err) {
      if (btn) btn.disabled = false;
      setStatus('登录失败：' + err.message);
    });
  }

  function loadCatalog() {
    return window.api.catalog().then(function (c) {
      window.S.catalog = c;
      window.S.grades = c.grades.map(function (g) { return g.name; });
      if (window.S.grades.length) selectGrade(window.S.grades[0]);
    });
  }

  function gradeOf(name) {
    return window.S.catalog.grades.find(function (g) { return g.name === name; });
  }

  function selectGrade(name) {
    window.S.grade = name;
    var g = gradeOf(name);
    window.S.subjects = (g ? g.subjects : []).map(function (s) { return s.name; });
    renderSeg('gradeSeg', window.S.grades, name, selectGrade);
    if (window.S.subjects.length) selectSubject(window.S.subjects[0]);
    else { renderSeg('subjectSeg', [], '', selectSubject); renderBooks(); }
  }

  function selectSubject(name) {
    window.S.subject = name;
    renderSeg('subjectSeg', window.S.subjects, name, selectSubject);
    renderBooks();
  }

  function renderSeg(id, items, current, onPick) {
    var box = $(id);
    box.textContent = '';
    items.forEach(function (n) {
      box.appendChild(window.ui.pill(n, n === current, function () { onPick(n); }));
    });
    window.ui.center(box);
  }

  function renderBooks() {
    var grid = $('bookGrid');
    grid.textContent = '';
    grid.scrollTop = 0;
    var g = gradeOf(window.S.grade);
    var sub = g ? g.subjects.find(function (s) { return s.name === window.S.subject; }) : null;
    var books = sub ? sub.books.slice() : [];
    var q = (window.S.query || '').trim().toLowerCase();
    if (q) books = books.filter(function (b) { return (b.title || '').toLowerCase().indexOf(q) >= 0; });
    books.forEach(function (b) { grid.appendChild(window.render.bookCard(b, toggleBook)); });
    if (!books.length) {
      var e = el('div', 'bento-empty');
      e.appendChild(window.ui.art('falling'));
      e.appendChild(el('div', 'be-txt', [q ? '没有匹配的教材' : '该学科暂无教材']));
      grid.appendChild(e);
    }
    window.ui.fade(grid, $('gridWrap')); // 滚动边界渐隐遮罩（挂外层 wrap，不进 grid）
    refreshPick();
  }

  function toggleBook(id) {
    if (window.S.selected[id]) delete window.S.selected[id];
    else window.S.selected[id] = true;
    // 原地翻转单张卡片，不重建网格——重建会 scrollTop=0 导致跳顶。
    var card = document.querySelector('.book-card[data-id="' + id + '"]');
    if (card && card._book) {
      card.classList.toggle('sel', !!window.S.selected[id]);
      var mt = card.querySelector('.mt');
      if (mt) mt.textContent = window.render.badge(card._book);
    }
    refreshPick();
  }

  function refreshPick() {
    var n = Object.keys(window.S.selected).length;
    $('pickCount').textContent = '已选 ' + n + ' 本';
    $('btnConfirm').textContent = n ? '开始下载（' + n + '）' : '开始下载';
  }

  function selectedBooks() {
    var g = gradeOf(window.S.grade);
    if (!g) return [];
    var out = [];
    g.subjects.forEach(function (s) {
      s.books.forEach(function (b) {
        if (window.S.selected[b.id]) out.push(b);
      });
    });
    return out;
  }

  function toggleTheme() {
    if (!window.theme) return;
    window.theme.cycle();
    // 图标随主题切换：晨光=烧陶土太阳，夜色=玄武岩月亮；主题名不再暴露
    var use = document.querySelector('#btnTheme use');
    if (use) use.setAttribute('href', window.theme.current() === 'night' ? '#ic-night' : '#ic-day');
  }

  function bindAll() {
    bind('.nav-item', function (t) { setView(t.dataset.view); });
    bind('[data-view]:not(.nav-item)', function (t) { setView(t.dataset.view); });
    bind('#btnLogin', onLogin);
    bind('#btnClear', function () { window.S.selected = {}; renderBooks(); });
    bind('#btnConfirm', window.flow.startDownload);
    bind('#btnOpenDir2', window.flow.openDir);
    bind('#btnOpenDir3', window.flow.openDir);
    bindSwitch('#swRemember', 'bd.remember', function (on) {
      // 关闭“记住登录状态”：立即忘掉已存凭据，下次启动需重新扫码
      if (!on) window.api.forgetToken().catch(function () {});
    });
    bindSwitch('#swAutoOpen', 'bd.autoOpen', null);
    bind('#btnTheme', toggleTheme);
    var inp = $('bookSearch');
    if (inp) inp.addEventListener('input', function () {
      window.S.query = inp.value;
      renderBooks();
    });
  }

  window.app = {
    S: window.S, $: $,
    setView: setView, setStep: setStep, setStatus: setStatus, loadHome: loadHome,
    selectedBooks: selectedBooks
  };

  // bindSwitch：设置页开关。点按/回车/空格切换，localStorage 记忆（'1'/'0'），
  // 可选 onChange(on) 执行副作用。初始状态：localStorage 有值则覆盖，无值保持 HTML 默认。
  function bindSwitch(sel, key, onChange) {
    var el = document.querySelector(sel);
    if (!el) return;
    var st = localStorage.getItem(key);
    if (st === '1') el.classList.add('on');
    if (st === '0') el.classList.remove('on');
    function toggle() {
      var on = el.classList.toggle('on');
      try { localStorage.setItem(key, on ? '1' : '0'); } catch (e) {}
      if (onChange) onChange(on);
    }
    el.addEventListener('click', toggle);
    el.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    bindAll();
    setStep(1);
    loadHome();
    window.flow.initShelf();
  });
})();
