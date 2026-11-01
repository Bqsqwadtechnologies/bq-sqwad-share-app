# Verification report — BQ Sqwad Share 1.1

Date: 1 November 2026 · Agent environment: **no Android SDK / no emulator**

| Feature | How tested | Device / API | Result |
|---------|------------|--------------|--------|
| AES-GCM session crypto | CryptoTest | JVM | Pass (when CI runs) |
| Module/Gradle structure | static review | n/a | Pass (static) |
| assembleDebug / release | GitHub Actions | ubuntu-latest | Manual / CI |
| Emulator API 26/30/33/36 | not available here | — | Manual |
| Two-phone Nearby send/receive | not available here | — | Manual |
| QR CameraX scan | not available here | — | Manual |
| Local web server PIN | code review + your browser | same Wi-Fi | Manual |
| Internet WebRTC | needs Worker deploy | — | Manual |
| AdMob test slots | code review | — | Manual |
| UMP consent | code review | — | Manual |
| R8 release smoke | Studio / CI | — | Manual |
| 4 GB file / pause / airplane | — | — | Manual |

## Manual steps for you

1. Android Studio, SDK 36, JDK 17: `./gradlew :core:common:test :app:assembleDebug`
2. Two phones API 26+: grant Nearby/Bluetooth as prompted
3. A Receive (QR/code), B Send → accept → transfer photo → check history
4. Share to computer → open URL+PIN on iPhone Safari or laptop
5. After deploying signaling Worker: far-transfer code join from browser
6. Confirm no ads on QR, accept, or progress screens
7. Settings: theme, Terms/Privacy links, feedback email

## Protocol tests in CI

debug.yml runs `./gradlew :core:common:test` on every main push. Keep that required.
