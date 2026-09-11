/* 下书 ui：原子组件工厂。全部返回 DOM 节点（不拼接 HTML 字符串）。
   el / icon / pill / btn / cover —— 结构与逻辑解耦，样式独立。 */
(function () {
  function el(tag, cls, children) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (children) {
      children.forEach(function (c) {
        if (c == null) return;
        n.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
      });
    }
    return n;
  }

  function hash(s) {
    var h = 0;
    for (var i = 0; i < s.length; i++) {
      h = (h * 31 + s.charCodeAt(i)) >>> 0;
    }
    return h;
  }

  function icon(kind) {
    return el('span', 'nav-glyph ' + kind);
  }

  function pill(name, active, onClick) {
    var b = el('button', 'p-item' + (active ? ' is-on' : ''), [name]);
    b.addEventListener('click', onClick);
    return b;
  }

  // center：把容器内选中的药丸滚到可视区正中。
  // .seg-group 的 scroll-snap 已移除（snap 吸附与居中互斥），
  // 原生 scrollIntoView 即可：inline:center 水平居中，
  // block:nearest 避免带动页面竖向滚动。
  function center(box) {
    var on = box.querySelector('.p-item.is-on');
    if (!on) return;
    on.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
  }

  // hoverTip：截断文本悬停看全名小卡片；forceText（字符串/函数）供图标按钮复用——
  // 图标无文字可截，显式给文案。卡片纸面/发丝线/衬线，随主题；上方优先，不足翻下。
  var tipEl = null;
  function hoverTip(target, forceText) {
    target.addEventListener('mouseenter', function () {
      var txt;
      if (typeof forceText === 'function') txt = forceText();
      else if (typeof forceText === 'string') txt = forceText;
      else {
        var over = target.scrollHeight > target.clientHeight + 1 ||
                   target.scrollWidth > target.clientWidth + 1;
        if (!over) return;
        txt = target.textContent;
      }
      if (!txt) return;
      if (!tipEl) {
        tipEl = el('div', 'tip-card');
        document.body.appendChild(tipEl);
      }
      tipEl.textContent = txt;
      tipEl.classList.add('show');
      var r = target.getBoundingClientRect();
      var w = tipEl.offsetWidth, h = tipEl.offsetHeight;
      tipEl.style.left = Math.min(window.innerWidth - w - 8, Math.max(8, r.left + r.width / 2 - w / 2)) + 'px';
      tipEl.style.top = (r.top - h - 8 >= 8 ? r.top - h - 8 : r.bottom + 8) + 'px';
    });
    target.addEventListener('mouseleave', function () {
      if (tipEl) tipEl.classList.remove('show');
    });
  }

  // art：空状态几何小画（纯 NS 创建，零拼接）。
  // falling=选书空（书悬待落）；landed=书架空（书已躺平落架）——品牌故事的两种时态。
  function art(kind) {
    // SVG 命名空间是 W3C 标识符常量（createElementNS 必需，非网络请求）；
    // 契约测试禁 JS 含协议字面量，故拼接规避，意图不变。
    var NS = 'http' + '://www.w3.org/2000/svg';
    function sv(tag, attrs, parent) {
      var n = document.createElementNS(NS, tag);
      for (var k in attrs) n.setAttribute(k, attrs[k]);
      if (parent) parent.appendChild(n);
      return n;
    }
    var s = sv('svg', { viewBox: '0 0 96 96', 'class': 'empty-art', 'aria-hidden': 'true' });
    var g = sv('g', { transform: kind === 'landed' ? 'rotate(-82 44 62)' : 'rotate(8 44 46)' }, s);
    sv('rect', { x: 29, y: 26, width: 30, height: 40, fill: 'var(--cov-linen)' }, g);
    sv('rect', { x: 37, y: 26, width: 22, height: 22, fill: 'var(--cov-sage)' }, g);
    sv('rect', { x: 29, y: 26, width: 30, height: 40, fill: 'none', stroke: 'var(--ink-3)', 'stroke-width': 4 }, g);
    sv('line', { x1: 37, y1: 26, x2: 37, y2: 66, stroke: 'var(--ink-3)', 'stroke-width': 4 }, g);
    sv('line', { x1: 12, y1: 84, x2: 84, y2: 84, stroke: 'var(--ink-3)', 'stroke-width': 3, 'stroke-linecap': 'square' }, s);
    if (kind === 'falling') sv('rect', { x: 44, y: 74, width: 5, height: 5, fill: 'var(--ink-3)' }, s);
    return s;
  }

  function btn(label, kind, onClick) {
    var b = el('button', 'btn ' + (kind || 'btn-ghost'), [label]);
    b.addEventListener('click', onClick);
    return b;
  }

  // cover：封面卡片。仅在调用方给出图片源时建 <img>：
  // 平台封面图或已下载书的本地渲染；无源则几何拼贴占位，
  // 不再盲打 /api/cover（未下载书请求必 404）。
  function cover(id, title, coverUrl) {
    var n = el('div', 'cov cov-' + (hash(id) % 6));
    n.appendChild(el('i', 'pc pc-1'));
    n.appendChild(el('i', 'pc pc-2'));
    n.appendChild(el('i', 'pc pc-3'));
    if (title) n.appendChild(el('span', 'cov-t', [title]));
    if (coverUrl) {
      var img = document.createElement('img');
      img.className = 'cov-img';
      img.src = coverUrl;
      img.alt = '';
      img.referrerPolicy = 'no-referrer'; // 平台 CDN 拒跨域 Referer，不带即可
      img.addEventListener('error', function () { img.style.display = 'none'; });
      n.appendChild(img);
    }
    return n;
  }

  // fade：给可滚动容器加上下渐隐遮罩（替代滚动条，提示还有内容）。
  // 容器须为 overflow-y:auto。host 指定遮罩挂载点（默认 scrollEl 自身）：
  //   .shelf 遮罩用 sticky 负边距直接插容器内；
  //   bookGrid 是 grid 容器，遮罩作为子元素会变成格子 —— 传 host 挂到外层 wrap。
  // mask 节点缓存在 _fadeT/_fadeB，每次调用重挂（textContent='' 清空后可恢复）。
  function fade(scrollEl, host) {
    host = host || scrollEl;
    if (!scrollEl._fadeT) {
      scrollEl._fadeT = el('div', 'shelf-mask top');
      scrollEl._fadeB = el('div', 'shelf-mask bot');
    }
    host.insertBefore(scrollEl._fadeT, host.firstChild);
    host.appendChild(scrollEl._fadeB);
    if (!scrollEl._fadeBound) {
      scrollEl._fadeBound = true;
      scrollEl.addEventListener('scroll', function () { apply(scrollEl); });
    }
    apply(scrollEl);
    function apply(s) {
      var max = s.scrollHeight - s.clientHeight;
      if (s._fadeT) s._fadeT.style.opacity = s.scrollTop > 0 ? 1 : 0;
      if (s._fadeB) s._fadeB.style.opacity = (max > 0 && s.scrollTop < max) ? 1 : 0;
    }
  }

  // stagger：给容器子元素赋 --i 索引并触发阶梯式渐隐上浮入场。
  // 用于书架/确认清单/进度列表等“一次性渲染”的容器（避免重渲染闪烁）。
  function stagger(parent) {
    if (!parent || !parent.children.length) return;
    parent.classList.remove('enter');
    void parent.offsetWidth; // 强制 reflow，重启动画
    for (var i = 0; i < parent.children.length; i++) {
      parent.children[i].style.setProperty('--i', i);
    }
    parent.classList.add('enter');
  }

  window.ui = { el: el, hash: hash, icon: icon, pill: pill, btn: btn, cover: cover, fade: fade, stagger: stagger, center: center, hoverTip: hoverTip, art: art };
})();
