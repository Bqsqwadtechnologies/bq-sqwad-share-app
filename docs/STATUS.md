# BQ Sqwad Share — implementation status

Updated: 7 November 2026  
Package: `com.bqsqwad.share`  
Current release target: **1.1.0** · minSdk 26 · targetSdk 36  
Current source scope: **offline/local sharing only**

## Product scope for this release

BQ Sqwad Share supports direct transfers between nearby devices and a PIN-protected browser session on the same local Wi-Fi network or hotspot. File transfer does not use a BQ cloud, remote signaling service, WebRTC, TURN, or internet fallback. Internet access is used for third-party advertising/consent services, not for file delivery.

The six-digit receive code is a nearby session identifier, not proof of device identity. It is visible to nearby devices. Both devices now show Nearby's connection verification digits and require explicit approval; users are instructed to compare the digits before accepting.

## Implemented in source — runtime proof still pending

- **Nearby pairing:** discovery and advertising, permission-aware start/retry states, connection error reporting, explicit approval on both devices, and verification-digit guidance.
- **Resumable file transfer:** stable transfer offers, 16 KB checksummed chunks, receiver-side durable checkpoints, 1 MiB checkpoint batching, final SHA-256 verification, cancellation, and retry from the receiver's committed offset. If Nearby assigns a different endpoint ID, the user must explicitly select the receiving device before resuming.
- **Multi-file and Team Sharing:** selected files can be queued and sent to currently connected, user-selected recipients. Saved teams and background delivery are not included.
- **Folder Transfer:** user-selected SAF folders are packaged into bounded ZIP archives while preserving relative folder paths, then sent through the Nearby flow.
- **Private Vault:** imported files are encrypted with an Android Keystore AES-GCM key; strong biometric authentication is required for import, restore, and permanent deletion. Vault files are outside FileProvider's shared paths.
- **Device Hub:** stable local installation identity and user-managed trusted-device records are stored locally. This is app-level identity, not hardware-backed device attestation.
- **Share to computer:** a phone-hosted, PIN-gated HTTP server serves selected files to a browser on the same local network. The UI warns that the connection is HTTP and must only be used on a trusted private network.
- **Media and storage:** Photos, Videos, Music and Files browsing; media preview; storage overview limited to BQ Sqwad Share-owned files; cache cleaning does not claim to clean the whole phone.
- **Advertising:** UMP consent flow and privacy-options entry point; active banner/native placements; separate rewarded flows for ad-free and temporary premium access. Reward callbacks are only granted after the SDK reward callback. Transfer interstitial and App Open units are reserved and are not requested or displayed.
- **Permissions and platform:** optional camera/Bluetooth hardware declarations, API-specific Nearby/media permissions, optional completion-notification permission, non-exported transfer service and FileProvider, backup disabled, and edge-to-edge activity setup.
- **Release signing:** private keystore configuration is read from Actions secrets or local properties. Release packaging now fails closed if signing credentials are incomplete.

## Explicitly not shipped in this release

- Multi-hop offline relay forwarding, its Nearby scheduler, authenticated route/acknowledgement protocol, and Windows relay client. OfflineRelayStore and relay crypto code are foundations only and are not connected to the live Nearby transfer path.
- Internet-based long-distance transfer, WebRTC, STUN/TURN, remote signaling, cloud file storage, or internet fallback.
- Automatic App Open or transfer-completion interstitial ads.
- Persistent saved teams or background team delivery.

The `signaling/` directory is retained as a historical/future experiment only. It is not part of the Android app and must not be deployed for this offline-only release.

## Known verification gaps

- The latest source commit has not yet completed a fresh Debug build, Release build, and full test run.
- Two-device transfer, interruption/reconnect, cancellation, simultaneous multi-file send, low-storage behaviour, and API-level permission handling still require real-device tests.
- Private Vault requires strong-biometric device testing, including cancellation, lockout, and biometric-enrolment changes.
- The local browser bridge needs same-Wi-Fi/hotspot testing from Android, iOS Safari, and desktop browsers.
- AdMob/UMP consent, privacy options, reward callbacks, and each active placement need test-device verification.
- TalkBack, large font scaling, contrast, touch-target, and accessibility checks remain outstanding.
- Public Privacy Policy and Terms URLs have not yet been verified. Legal pages and the Data Safety draft must be reconciled with the verified implementation before publication/submission.
- Google Play Console declarations and final release signing/artifact verification remain outstanding.

See `docs/VERIFICATION_REPORT.md` for the current CI evidence and manual test plan. Source implementation is not proof of runtime correctness or policy approval.
