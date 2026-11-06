# Your part only (BQ Sqwad Share)

## App identity
- App: BQ Sqwad Share
- Package: `com.bqsqwad.share`
- Company: BQ Sqwad Technologies
- Contact: bqsqwadtechnologies@bqsqwadai.com
- Default version: 1.2.0 (CI supplies monotonically increasing version codes)

## AdMob
Create the Android app and ad units in AdMob. Add the real IDs to the root `config/ads.properties` file. Debug builds always use Google's test ad units.

## IDs
Paste the IDs into `config/ads.properties`. Nothing else.

## Signing
Keep the keystore offline. Add GitHub Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

## Build
Use Android Studio with SDK 36 and JDK 17: `./gradlew test :app:lintDebug :app:assembleDebug`.

## Release
Run the Release workflow with a version name after adding real AdMob IDs and all signing secrets. The workflow builds a signed APK/AAB and verifies the APK certificate.

## Manual device checks
Two-phone transfers, QR scanning, and browser transfers require physical-device testing. Record actual results in `docs/VERIFICATION_REPORT.md`.
