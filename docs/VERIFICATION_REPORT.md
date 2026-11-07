# BQ Sqwad Share — technical verification report

Updated: 7 November 2026  
Current branch: `main`  
Current source commit at report time: `033d04d36877c03f9a5e5407ee0548a79ca3e656`  
Build environment: GitHub Actions, JDK 17, Android SDK 36

## Automated evidence

| Check | Commit / run | Result | Interpretation |
|---|---|---|---|
| Debug APK | `4f0f1bb77dbbf32d44a53a6d4eea7d8336492393` · run [42345077903](https://github.com/Bqsqwadtechnologies/bq-sqwad-share-app/actions/runs/42345077903) | Success | This is an older commit, not the current source. It does not verify later changes. |
| Release unit tests | `ee5fa024c10cd872a693089c54988cc304de22f0` · run [42371254826](https://github.com/Bqsqwadtechnologies/bq-sqwad-share-app/actions/runs/42371254826) | Success | The test step passed on that earlier commit. Newer tests and code still need a fresh run. |
| Release APK/AAB compile | Same Release run | Failed | Kotlin compilation stopped before APK/AAB creation. |
| Keystore decode | Same Release run | Success | The Actions secret was present and decoded to a non-empty temporary keystore file. |
| Release signing configuration validation | Same Release run | Success | Gradle reached `validateSigningRelease`; this does not prove a final artifact was signed. |
| Current main build/test | `033d04d36877c03f9a5e5407ee0548a79ca3e656` | Not yet run | The fixes made after the failed Release run still need a fresh workflow execution. |

### Compile errors found in the failed Release run

The failed run reported unresolved references in:

- `ShareViewModel.kt`: settings Flow was accessed as though it were a StateFlow.
- `MediaLibraryScreen.kt`: Dialog APIs were imported from the wrong package.
- `ShareAppRoot.kt`: AdMob consent state was referenced outside the relevant composable scope, the clipboard action used an outdated ViewModel method, and a stale rewarded-ad method name remained.

Those source issues have since been addressed on `main`. They have **not** yet been confirmed by a fresh build. Do not mark the build as passing until the new workflow run succeeds.

## Current Actions configuration

- Debug and Release workflows are manually dispatched; pushes to `main` do not automatically run them.
- The Release workflow now runs the full Gradle test task and uses versionCode `3` / versionName `1.1.0` for this release.
- Release signing requires `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Never put these values in source control or chat.
- Release packaging is configured to fail if the private signing credentials are missing.
- The failed run produced no APK/AAB artifact, so no artifact-signature verification has yet been performed.

## Required real-device verification

1. Install the latest Debug APK on at least two supported Android devices.
2. Test Nearby permission grant, denial, retry, revocation, and re-grant on supported API levels.
3. Start Receive on one device and Send on the other. Confirm both devices display the same Nearby verification digits and require explicit approval.
4. Test wrong receive code, declined connection, blocked peer, lost connection, reconnect, resume offset, cancellation, and retry.
5. Send multiple files and a large file; verify names, sizes, final SHA-256 integrity, history records, and that sender originals remain untouched.
6. Test Folder Transfer with nested folders, duplicate names, empty folders, large archives, and low-storage conditions.
7. Test Private Vault import, restore, deletion, biometric cancellation, lockout, and biometric-enrolment changes on a device with strong biometrics.
8. Test the local browser bridge from a second device on the same Wi-Fi/hotspot. Confirm the HTTP/private-network warning, PIN rejection, upload/download, and stop-on-exit behaviour.
9. Test media permissions on Android 13–15, including Android 14 selected-photo access; test camera denial and QR scanning.
10. Test UMP consent, privacy-options entry, ad-free and premium reward callbacks, and all active banner/native placements with test devices. Confirm no ads interrupt pairing or active transfers.
11. Run TalkBack, large-font, contrast, orientation, and touch-target checks.
12. Run the current Release workflow, verify APK/AAB signatures, and compare the upload certificate with the expected Play Console upload certificate before distributing artifacts.

## Release blockers

- A fresh full test + Debug build on current `main` is required.
- A fresh signed Release APK/AAB build and signature verification are required.
- Real-device transfer, Vault, browser, permissions, ads, and accessibility tests are required.
- Privacy Policy and Terms public URLs must be verified, and legal/Data Safety declarations must be reconciled with actual SDK and app behaviour.
- Google Play Console declarations, store listing, target audience, and final upload acceptance must be completed by the account owner.

This report is an evidence record, not a security certification, legal opinion, or Google Play approval.
