/* 下书 按键声音：Web Audio 合成短 click，无外部音频资源。 */
(function () {
  var ctx = null;

  function ensure() {
    if (ctx) return ctx;
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    try { ctx = new AC(); } catch (e) {}
    return ctx;
  }

  window.sound = function () {
    var c = ensure();
    if (!c) return;
    if (c.state === 'suspended') c.resume();
    var o = c.createOscillator();
    var g = c.createGain();
    o.type = 'sine';
    o.frequency.value = 660;
    g.gain.setValueAtTime(0.1, c.currentTime);
    g.gain.exponentialRampToValueAtTime(0.0001, c.currentTime + 0.08);
    o.connect(g);
    g.connect(c.destination);
    o.start();
    o.stop(c.currentTime + 0.09);
  };
})();
