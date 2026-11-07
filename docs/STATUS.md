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
