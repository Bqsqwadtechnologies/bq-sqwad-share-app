# Your part only (BQ Sqwad Share)

The code is on GitHub. You build and ship with **Android Studio** on your machine. This page is everything you need from AdMob and Play — no guessing.

## App identity (use exactly these)

| Field | Value |
|--------|--------|
| App name | BQ Sqwad Share |
| Package name / applicationId | `com.bqsqwad.share` |
| Company | BQ Sqwad Technologies |
| Contact | bqsqwadtechnologies@bqsqwadai.com |
| Default version | 1.1.0 (versionCode 2) |

## 1. Create the app in AdMob

1. Open [AdMob](https://admob.google.com/) → Apps → **Add app**.
2. Platform: **Android**.
3. App name: **BQ Sqwad Share**.
4. If the app is not on Play yet, choose “No” for listed on a store.
5. Copy the **App ID** (looks like `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`).

You will paste that App ID in two places later (manifest + ads_config).

## 2. Create these ad units (names to use)

Create **one unit per row**. Suggested display names in AdMob so you can find them later:

| AdMob unit name (suggested) | Format | Where it shows in the app |
|-----------------------------|--------|---------------------------|
| BQ Share – Banner Home | Banner (adaptive if offered) | Home + history bottom |
| BQ Share – Native Lists | Native advanced | Every 8 items in pickers / history / received / cleaner |
| BQ Share – Interstitial After Transfer | Interstitial | Only after a transfer, when returning home |
| BQ Share – Rewarded Unlocks | Rewarded | Ad-free 24h, Phone Clone, computer share, internet transfer, multi-send |
| BQ Share – App Open | App open | Return after 4+ hours away |

Copy each **Ad unit ID** (`ca-app-pub-XXXX/YYYY`).

## 3. Paste IDs into the project

Open these files in Android Studio and replace the **test** IDs:

1. `app/src/main/res/values/ads_config.xml` — all string resources
2. `core/ads/src/main/kotlin/com/bqsqwad/share/ads/Slots.kt` — object `AdUnits` (same values)
3. `app/src/main/AndroidManifest.xml` — `com.google.android.gms.ads.APPLICATION_ID` meta-data = **App ID** (the one with `~`)

Until you paste real IDs, Google **test** IDs stay in the repo so builds still load sample ads safely.

## 4. Signing (you already have the keystore)

Keep your keystore offline. For local release builds, create `keystore.properties` (gitignored) at the repo root:

```properties
storeFile=/absolute/path/to/your.jks
storePassword=...
keyAlias=bqsqwad-share
keyPassword=...
```

Or set GitHub Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## 5. Build on your PC (Android Studio)

```text
File → Open → folder of the cloned repo
Wait for Gradle sync
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

Or terminal in the project folder:

```bash
./gradlew :core:common:test :app:assembleDebug
```

Install the debug APK on a phone from Android Studio’s Run button.

## 6. GitHub Pages (legal links in Settings)

Repo → Settings → Pages → Deploy from branch **main** → folder **/docs**.

## 7. Play Console later

- Package: `com.bqsqwad.share`
- Privacy URL: `https://bqsqwadtechnologies.github.io/bq-sqwad-share-app/privacy.html`
- Terms URL: `https://bqsqwadtechnologies.github.io/bq-sqwad-share-app/terms.html`
- Listing draft: `docs/store/PLAY_LISTING.md` and `DATA_SAFETY.md`

## What is not your job

Writing Kotlin, CI YAML, protocol tests, logo pipeline, and legal page HTML — that is already in the repo. Your job is IDs, keystore, Studio build, two-phone testing, and Play listing.
