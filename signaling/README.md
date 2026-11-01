# Signaling service (internet transfer)

Introduces sender and receiver for WebRTC data channels. **Does not see file contents.**

## Deploy

```bash
cd signaling
npx wrangler deploy
```

Cloudflare free Worker tier usually covers connection setup only.

## TURN (optional, can cost money)

STUN (`stun:stun.l.google.com:19302`) is free. TURN is only needed for strict NATs and is often billed per GB. **Do not enable paid TURN until you accept that cost.** Prefer a clear “try same Wi-Fi or hotspot” message first.

## Security

Codes expire after 15 minutes. Sender approval lives in the app UI. Add rate limits before a public launch.
