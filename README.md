# BQ Sqwad Share

Offline, no-account file sharing for nearby Android phones. Package `com.bqsqwad.share`. Published by BQ Sqwad Technologies (`bqsqwadtechnologies@bqsqwadai.com`).

Files move between devices. The app does not upload them to a BQ Sqwad server.

## Open the project

Use Android Studio with SDK 36 and JDK 17. From this folder:

```bash
./gradlew :core:common:test :app:assembleDebug
```

`minSdk` 26, `targetSdk` 36, `versionName` 1.0.0, `versionCode` 1 unless CI sets `VERSION_CODE`.

## Logo

Replace [branding/logo/logo.png](branding/logo/logo.png), then:

```bash
python3 scripts/generate_branding.py
```

That refreshes the launcher, monochrome icon, notification icon, in-app header and the 512 px Play icon. See [branding/logo/README.md](branding/logo/README.md).

## Ad unit IDs

Test IDs live in one place: `core/ads/src/main/kotlin/com/bqsqwad/share/ads/Slots.kt` (`AdUnits`) and the AdMob app id meta-data in `app/src/main/AndroidManifest.xml`. Replace those strings with your real IDs. Do not scatter IDs through screens. `AdManager` owns consent, caps and loading. `BannerSlot` and `NativeSlot` are the only composable placements.

## Release

1. Keep `bq-sqwad-share-release.jks` outside git. Losing it blocks updates. Enroll in Play App Signing.
2. Repository secrets the release workflow expects: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Tag `v1.0.0` or run the Release workflow. It runs JVM unit tests, then uploads a signed APK and AAB when the secrets exist. Without secrets, release builds fall back to the debug signing key so the Gradle task can still finish.
4. Debug workflow builds a debug APK on every push to `main`.

Signing reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`, or a local `keystore.properties` (gitignored).

## Play and legal

Listing copy, Data safety notes and the pre-launch list are in `PLAY_STORE.md`, `DATA_SAFETY.md` and `PRELAUNCH.md`.

Privacy and terms: `docs/privacy.html` and `docs/terms.html`. In the app they open `https://bqsqwadtechnologies.github.io/bq-sqwad-share-app/privacy.html` and `terms.html`. Enable GitHub Pages from the `/docs` folder on `main`.

## Modules

`app` (UI), `core:common` (crypto, speed, file kinds), `core:domain`, `core:data` (Room, DataStore, Nearby, local web share), `core:design`, `core:ads`.

## Extra behavior beyond the brief

Shared clipboard text, group send to every connected phone, nearby chat on the same encrypted session, favorite files, device block from the accept sheet, and a debug ad screen in debug builds.
