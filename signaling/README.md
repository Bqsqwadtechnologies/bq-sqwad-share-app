# Future internet-transfer experiment — not shipped

**Status: NOT PART OF THE CURRENT BQ SQWAD SHARE RELEASE.**

BQ Sqwad Share's current product scope is offline, local file sharing between nearby devices and a PIN-protected browser session on the same local network or hotspot. The released app does not use this directory to provide internet-based long-distance transfer.

This directory is retained only as an isolated historical/experimental design note. It is not a deployment guide and must not be deployed for the current release.

## Explicit exclusions

- No WebRTC data-channel transfer.
- No STUN/TURN service.
- No Cloudflare Worker or remote signaling service.
- No cloud file storage or internet fallback.
- No remote transfer codes or internet rendezvous.

Do not connect this experiment to the Android app or publish a deployment endpoint unless a future product decision explicitly changes the offline-only scope and a separate security, privacy, cost and compliance review has been completed.

The current offline transfer architecture is documented in `docs/OFFLINE_MESH_POLICY.md`. That document distinguishes direct two-device transfers from the future multi-hop relay roadmap.
