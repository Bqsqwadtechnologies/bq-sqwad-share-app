# BQ Sqwad Share — status (6 November 2026)

Package: `com.bqsqwad.share` · Target **1.1.0**

## Done

- Multi-module scaffold: app, core:common, core:domain, core:data, core:design, core:ads
- Kotlin, Compose, Material 3, Hilt, Room, DataStore, Navigation
- compileSdk / targetSdk 36, minSdk 26
- Age gate (13+), home Send/Receive, history, settings themes and accents
- Nearby discovery/advertising shell (`NearbyTransport`)
- QR payload + 6-digit session code + AES-GCM session crypto
- Accept / decline / block connection prompt
- File picker entry points, installed apps list (launcher queries only)
- Local web share server with PIN (computer path)
- Phone Clone and premium gates via rewarded ads (test IDs)
- Transfer history Room model, favorites, cleaner list view
- Clipboard / group send / nearby chat UI hooks
- Logo pipeline under branding/logo/ and scripts/generate_branding.py
- AdMob test IDs in AdUnits, UMP start, banner/native/interstitial/rewarded/app-open
- Debug ad slot screen (debug builds)
- Legal pages under docs/, Play and data-safety drafts
- CI: debug.yml, release.yml, branding.yml
- Signing config from env / keystore.properties (keystore not in git)

## Half-done (needs device proof)

- Parallel multi-file Nearby queue; interrupted transfers can be retried from the beginning, but byte-offset resume across process death is not implemented
- CameraX + ML Kit QR scanner on send path
- Full Photo/Video/Music/Document tab pickers via system pickers
- In-app media players and organized receive folders end-to-end
- Storage cleaner (SAF folders, on-disk duplicate hashing)
- WorkManager tip notifications
- TransferProtocol framing, metadata bounds checks, chunk checksums, and resume-offset helpers are unit-tested; these helpers are not yet wired into the Nearby file-payload path, so end-to-end receiver checksum verification and resumable Nearby transfers are not shipped
- Baseline Profiles / Macrobenchmark
- ktlint/detekt as failing CI gates
- GitHub Pages enablement and Actions secrets (manual on the org)

## Missing vs 1.1 brief (scaffolded or planned; not fully shipped)

- Cross-platform browser transfer polish, ZIP folders, HTTP Range resume
- HEIC/HEVC conversion helpers
- Share ID, trusted devices, multi-receiver send
- Static web client under web/
- Share-sheet intents, compress-before-send, widgets
- Compose UI tests and multi-API emulator matrix

## Agent environment limits

No Android SDK or emulator in the coding agent. Full assembleDebug and two-phone tests must run in GitHub Actions or Android Studio on your machine. See docs/VERIFICATION_REPORT.md.


## 7 November 2026 implementation update

- Private Vault screen is now connected to the navigation drawer. It encrypts imported files with the Android Keystore AES-GCM key and requires strong biometric authentication for import, restore, and permanent deletion. Vault metadata (including the original filename) is inside the encrypted payload.
- Folder Transfer now accepts a user-selected Storage Access Framework folder, packages its nested files into a ZIP while preserving relative folder paths, enforces entry/depth/size limits, and queues the archive for the existing Nearby Send flow.
- Device Hub now lists currently discovered and connected Nearby peers and allows disconnecting active peers. It is a live-session manager, not yet a persistent trusted-device registry.
- A durable receiver-side chunk checkpoint store and tests have been added. It fsyncs accepted chunks, atomically records committed offsets, truncates uncommitted tails after restart, and verifies the complete SHA-256 before finalizing. **It is not yet wired into the Nearby sender/receiver callbacks**, so current Nearby file transfers still restart from the beginning after interruption.
- Android build, automated test execution, biometric-device verification, folder transfer verification, and two-device testing remain outstanding. Do not treat source commits as proof of runtime verification.


## 7 November 2026 roadmap items 5–8

- **Storage Intelligence:** added a storage overview for Android's app-data volume, BQ Sqwad Share-owned folders, and the largest app-owned files. It does not scan or delete the user's personal files. The separate Deep Clean screen still requires explicit confirmation and only clears app cache.
- **Media Studio:** added a Photos mode to the existing media library with MediaStore browsing, search, thumbnails, and an in-app preview. Music/video playback remains separate. Photo access and preview still need runtime testing on Android 13–15, including Android 14 selected-photo permission behavior.
- **BQ Relay / Windows Bridge:** added OfflineRelayStore, a local disk queue for opaque recipient-encrypted chunks with bounded chunk size, storage quota, expiry, duplicate suppression, digest verification, and verified-ack deletion. This is storage groundwork only: it is not connected to Nearby, has no multi-hop forwarding scheduler, opt-in participation settings, authenticated route/ack protocol, or Windows client. No cloud or internet transfer was added.
- **Team Sharing:** added a session-scoped recipient picker and multi-file send to only the selected, currently connected peers. It reuses the existing Nearby file transfer path. Saved teams, persistent permissions/trusted memberships, and background delivery are not implemented.

The new Android UI and relay queue have not been built or executed in this environment. Unit tests were added for the relay queue but have not been run. Android Studio/GitHub Actions and real multi-device testing are still required before release.


## 7 November 2026 follow-up — resumable Nearby and Device Hub

- **Live resumable transfers:** the sender now sends a stable transfer offer, waits for the receiver's durable checkpoint offset, sends checksummed 16 KB chunks one at a time, and persists acknowledged progress. The receiver fsyncs each accepted chunk before acknowledging it, verifies the final SHA-256, and only then saves the file. A retry/reconnect reuses the sender's stable device ID plus transfer-row ID so the receiver can return its saved offset. This replaces the normal outgoing Nearby file-payload path for new transfers; legacy incoming FILE payloads remain supported for compatibility.
- **Cancellation:** an active sender sends a transfer-cancel control frame when the peer is still connected, allowing the receiver to discard that partial checkpoint.
- **Persistent Device Hub history:** each installation creates a stable local UUID and exchanges it with the peer over the Nearby connection. Users explicitly approve or remove trust. Approved identities and display names are persisted locally using DataStore and remain visible after disconnect/restart. This is an app-level stable identity record, not hardware-backed device attestation; users should only trust devices they recognize.
- **Still outstanding:** OfflineRelayStore remains a local opaque-chunk queue only. Multi-hop forwarding, relay participation/consent UI, route authentication and delivery scheduling are not implemented. A Windows companion client is also not included. These require a separately designed peer-to-peer protocol and Windows project; no cloud service has been introduced.
- **Verification:** protocol codec tests were added. No Android Gradle build, unit-test execution, or two-device disconnect/reconnect test has been run in this environment. Do not treat these changes as release-verified until those checks pass.
