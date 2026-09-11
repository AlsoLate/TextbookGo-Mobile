/* 下书 API 封装：纯 fetch，只走 /api/*，无外部请求。 */
(function () {
  window.api = {};

  async function j(method, path, body) {
    var opt = { method: method, headers: {} };
    if (body) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    var res = await fetch(path, opt);
    if (!res.ok) {
      var msg = '请求失败';
      try { msg = (await res.json()).error || msg; } catch (e) {}
      throw new Error(msg);
    }
    return res.json();
  }

  window.api.home = function () { return j('GET', '/api/home'); };
  window.api.login = function () { return j('POST', '/api/login'); };
  window.api.catalog = function () { return j('GET', '/api/catalog'); };
  window.api.shelf = function () { return j('GET', '/api/shelf'); };
  window.api.deleteBook = function (id) { return j('POST', '/api/shelf/delete?id=' + encodeURIComponent(id)); };
  window.api.download = function (ids) { return j('POST', '/api/download', { ids: ids }); };
  window.api.openDir = function () { return j('POST', '/api/open-dir'); };
  window.api.forgetToken = function () { return j('POST', '/api/token/forget'); };
  window.api.stream = function (streamID) {
    return new EventSource('/api/download/stream?id=' + encodeURIComponent(streamID));
  };
  window.api.coverUrl = function (id) { return '/api/cover?id=' + encodeURIComponent(id); };
})();
