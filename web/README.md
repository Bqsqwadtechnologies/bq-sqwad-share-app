# Browser web client

Static page for receiving an **internet transfer by code** on iPhone, iPad, Mac, Windows, Linux or Chromebook.

## Deploy

1. Deploy `signaling/` with Wrangler and note the `wss://` URL.
2. Set `window.BQ_SIGNALING_URL` in `app.js` (or inject at deploy).
3. Publish `web/` on Cloudflare Pages or GitHub Pages.

Nearby and same-Wi-Fi transfers do not need this site — use the in-app local web server.
