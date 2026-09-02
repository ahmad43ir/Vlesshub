// VlessHub PWA proxy — serves the PWA through a clean Workers domain

const ORIGIN = 'https://chobgroup.pages.dev';
const BASE_PATH = '/vlesshub';

export default {
  async fetch(request) {
    const url = new URL(request.url);

    // Rewrite the path: / → /vlesshub/
    let path = url.pathname;
    if (path === '/' || path === '') {
      path = BASE_PATH + '/';
    } else if (!path.startsWith(BASE_PATH)) {
      path = BASE_PATH + path;
    }

    const target = ORIGIN + path + (url.search || '');

    try {
      const resp = await fetch(target, {
        method: request.method,
        headers: {
          'User-Agent': request.headers.get('User-Agent') || '',
          'Accept': request.headers.get('Accept') || '*/*',
          'Accept-Language': request.headers.get('Accept-Language') || '',
        },
      });

      const headers = new Headers(resp.headers);
      headers.set('Access-Control-Allow-Origin', '*');

      // Rewrite any absolute chobgroup.pages.dev references in HTML
      const contentType = headers.get('Content-Type') || '';
      if (contentType.includes('text/html')) {
        let body = await resp.text();
        body = body.replace(/https:\/\/chobgroup\.pages\.dev/g, url.origin);
        return new Response(body, { status: resp.status, headers });
      }

      return new Response(resp.body, { status: resp.status, headers });
    } catch (e) {
      return new Response('Proxy error: ' + e.message, { status: 502 });
    }
  },
};
