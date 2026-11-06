# BQ Sqwad Share — Offline Mesh Relay Policy

Status: required architecture and acceptance contract. This document does not claim that multi-hop relay is already implemented.

## Product behaviour

1. During onboarding, clearly explain that the user may contribute temporary encrypted storage and local radio connectivity to the offline relay network. Require affirmative acceptance. Do not silently enrol users.
2. After acceptance, relay handoffs are automatic; do not show a per-package approval prompt or repetitive user-facing notifications.
3. Provide a persistent settings control to withdraw from relay participation. Withdrawal stops accepting new packages and safely drains or expires packages already held according to the retention policy.
4. Never use cellular/mobile data for offline relay. Never silently fall back to the internet. If no permitted local transport exists, retain the sender's package locally and wait.
5. Do not claim zero battery consumption. Use bounded discovery windows, adaptive backoff, batching, OS-aware scheduling and pause conditions for low battery, thermal pressure, low storage, or Android background restrictions.
6. Respect Android foreground-service and notification rules whenever a foreground service is required.

## Confidentiality contract

- Encrypt file contents on the sender device using authenticated encryption with unique nonces.
- Wrap the content key to the verified recipient public key. Only the recipient private key can unwrap it.
- Relay devices receive ciphertext chunks and the minimum routing/expiry metadata required for forwarding; never send them the content key.
- Never create previews, thumbnails, MIME-based renderings, or content indexes on relay devices.
- Verify chunk hashes and authenticated-encryption tags; reject corrupted, replayed, expired, or incorrectly addressed packages.
- Explain that relays may still observe limited metadata such as package size, timing and pseudonymous routing identifiers. Minimise and rotate identifiers where practical.

## Store-and-forward contract

- Use a durable queue with opaque transfer IDs, per-chunk hashes, byte offsets, expiry, hop count, and deduplication keys.
- Forward only over local peer-to-peer transports explicitly approved for offline mode (for example Nearby Connections or Wi-Fi Direct). Do not bind relay sockets to cellular interfaces.
- Enforce configurable system-wide storage quotas and per-package caps. Reserve free space for the device and never evict user files.
- Keep a relay copy until the next hop or recipient acknowledges verified receipt; use idempotent acknowledgements to avoid duplicate delivery.
- Sender may extend expiry through an authenticated, signed metadata update. Relays must validate it and apply a maximum retention policy.
- Expired packages are securely deleted on a best-effort basis; document that flash storage wear-levelling means software cannot guarantee forensic erasure.
- Delivery is opportunistic, not guaranteed or instant: it requires participating devices to encounter one another physically. If no route appears before expiry, report undelivered/expired.

## Acceptance tests

- With mobile data enabled at OS level, offline relay transfers still use only the local peer transport; verify no cellular bytes are attributed to the app during relay.
- With Wi-Fi/Bluetooth peer links unavailable, the package remains queued and no internet fallback is attempted.
- Relay device cannot decrypt, preview, or open any relayed file, including images, video, audio, documents and archives.
- Repeated handoffs do not duplicate delivered files; corrupted chunks are rejected and resumable from the last verified checkpoint.
- Withdrawal prevents new relay acceptance; expiry and quota cleanup do not delete sender originals or recipient files.
- Battery, thermal and storage constraints pause relay safely and resume when eligible.
- Verify required Android runtime permissions, background limits, and foreground-service notifications on supported API levels.

## Online transfer is separate

Online long-distance transfer must be a distinct mode. Its data necessarily traverses the internet. Prefer a direct WebRTC data channel when peer connectivity succeeds; use a TURN relay only when direct connectivity fails and only with an explicitly disclosed network/data path. Signaling exchanges connection metadata only and must not carry file bytes. Never describe online file delivery as zero-data or as avoiding all upload/download: the bytes must be transmitted by the sender and received by the recipient.
