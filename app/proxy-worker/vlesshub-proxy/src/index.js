// VlessHub PWA proxy — serves the PWA through a clean Workers domain

const ORIGIN = 'https://vlesshub-2i2.pages.dev';
const BASE_PATH = '';

export default {
  async fetch(request) {
    const url = new URL(request.url);

    // Rewrite the path: serve the PWA root directly (no base path — the
    // dedicated vlesshub Pages project hosts the PWA at its domain root).
    let path = url.pathname;

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

      // Rewrite any absolute Pages-origin references in HTML
      const contentType = headers.get('Content-Type') || '';
      if (contentType.includes('text/html')) {
        let body = await resp.text();
        body = body.replace(/https:\/\/vlesshub-2i2\.pages\.dev/g, url.origin);
        return new Response(body, { status: resp.status, headers });
      }

      return new Response(resp.body, { status: resp.status, headers });
    } catch (e) {
      return new Response('Proxy error: ' + e.message, { status: 502 });
    }
  },
};
