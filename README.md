# BQ Sqwad Share

Offline, no-account file sharing for nearby Android phones, plus browser paths for iPhone and computers. Package `com.bqsqwad.share`. Published by BQ Sqwad Technologies (`bqsqwadtechnologies@bqsqwadai.com`).

Nearby and same-network transfers do not upload your files to BQ Sqwad. Optional internet transfer uses short-lived codes; signaling only introduces peers.

## Open the project

Android Studio, SDK 36, JDK 17:

```bash
./gradlew :core:common:test :app:assembleDebug
```

`minSdk` 26, `targetSdk` 36, default `versionName` **1.1.0**, `versionCode` **2** (CI can override).

## Logo

Replace `branding/logo/logo.png`, then `python3 scripts/generate_branding.py`.

## Ad unit IDs

Test IDs in `core/ads/.../Slots.kt` (`AdUnits`) and the AdMob app id in `AndroidManifest.xml`. Replace with your real IDs in one place only.

## Transfer protocol

`core/common` includes `TransferProtocol` (framing, chunk SHA-256, resume offsets, AES-GCM frame wrap) and unit tests. CI runs `./gradlew :core:common:test`.

## Browser / iPhone on the same Wi-Fi

In-app **Share to a computer** starts a local PIN-gated HTTP server on the phone. Open the QR or URL on Safari/Chrome. No install on the other device.

## Internet transfer (optional)

1. Deploy `signaling/` (`npx wrangler deploy`). Free Worker tier is usually enough for setup messages only.
2. Point the app and `web/app.js` at the Worker `wss://` URL.
3. Publish `web/` for receive-by-code in any browser.

STUN is free. **TURN can cost money per GB — leave it off until you accept that cost.**

## Future iOS companion (not in this repo)

A native iOS app would need Multipeer Connectivity or a Network.framework path, the same QR/code formats, and the same WebRTC code path for far transfers. Browser transfer already covers iPhone without an App Store build.

## Release

Keystore stays out of git. Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Tag or run Release workflow for signed APK/AAB.

## Docs

- `docs/STATUS.md` — done / half-done / missing
- `docs/VERIFICATION_REPORT.md` — what was proven vs manual steps
- `docs/privacy.html`, `docs/terms.html`
- `docs/store/PLAY_LISTING.md`

Enable GitHub Pages from `/docs` on `main`.


## Offline relay policy (mandatory)

- Offline relay is a separate, opt-in capability accepted explicitly during onboarding. After acceptance, eligible relay handoffs may happen automatically without per-package prompts.
- Relay traffic is local-link only. The relay transport MUST NOT use cellular/mobile data, metered internet, cloud storage, or an internet fallback. If no permitted local peer link is available, queue locally and wait.
- Relay devices store and forward recipient-encrypted opaque chunks only. They must never receive the content-decryption key, render previews, index file contents, or expose package contents to the relay user.
- Enforce storage quotas, package expiry, duplicate suppression, authenticated routing metadata, integrity checks, and deletion only after a verified next-hop/recipient acknowledgement or safe expiry policy.
- Do not promise zero battery use: discovery and radio activity consume power. Use bounded, opportunistic discovery and pause under OS restrictions or critical battery/storage conditions.
- Android background execution and foreground-service notification requirements must be respected; do not hide required system notifications.

See [docs/OFFLINE_MESH_POLICY.md](docs/OFFLINE_MESH_POLICY.md) for the implementation contract.
