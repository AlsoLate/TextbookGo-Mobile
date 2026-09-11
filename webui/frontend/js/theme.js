/* 下书 theme · 双态切换：晨光 Day / 夜读 Night
   token 全部在 tokens.css，JS 只切 data-theme。零计算、零依赖。 */
(function () {
  var themes = [
    { id: 'day', label: '晨光' },
    { id: 'night', label: '夜读' }
  ];
  var current = 0;
  var KEY = 'xshu.theme';

  function apply(i) {
    current = ((i % themes.length) + themes.length) % themes.length;
    var t = themes[current];
    var root = document.documentElement;
    root.dataset.theme = t.id;
    root.style.colorScheme = t.id === 'night' ? 'dark' : 'light';
  }

  function load() {
    var id = localStorage.getItem(KEY);
    var idx = themes.findIndex(function (t) { return t.id === id; });
    apply(idx >= 0 ? idx : 0);
  }

  window.theme = {
    themes: themes,
    current: function () { return themes[current].id; },
    cycle: function () { apply(current + 1); }
  };

  document.addEventListener('DOMContentLoaded', load);
})();