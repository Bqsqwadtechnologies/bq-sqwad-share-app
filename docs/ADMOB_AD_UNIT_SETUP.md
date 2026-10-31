# BQ Sqwad Share — AdMob ad-unit inventory

Create these **nine separate ad units** in the AdMob app for BQ Sqwad Share. Keep the names exactly as written so each returned unit ID can be mapped to its own placement in the Android app.

| # | Ad unit name (exact) | Ad format | App placement |
|---:|---|---|---|
| 1 | BQ Sqwad Share - Home Banner | Banner | Home screen, bottom banner area |
| 2 | BQ Sqwad Share - History Banner | Banner | Transfer History screen, bottom banner area |
| 3 | BQ Sqwad Share - Home Native | Native Advanced | Home screen, between recent-transfer content and lower actions |
| 4 | BQ Sqwad Share - Apps Native | Native Advanced | Installed-app selection list, between app rows |
| 5 | BQ Sqwad Share - History Native | Native Advanced | Transfer History list, between history rows |
| 6 | BQ Sqwad Share - Transfer Interstitial | Interstitial | Only after successful transfer sessions; never during connection, sending, receiving, or confirmation |
| 7 | BQ Sqwad Share - Ad Free Rewarded | Rewarded | Settings: optional 24-hour ad-free reward |
| 8 | BQ Sqwad Share - Premium Rewarded | Rewarded | Phone Clone and Computer Sharing unlock screen; grant only after SDK reward callback |
| 9 | BQ Sqwad Share - App Open | App Open | Returning after a long absence; never during an active transfer |

## IDs to return for integration

Send the **AdMob ad unit ID** for each of the nine names above. Also send the AdMob **App ID** (the value containing a tilde, such as the Google sample App ID); this is different from an ad unit ID (which contains a slash).

The app currently contains Google's sample test IDs. Keep test ads enabled during development; do not treat sample IDs as production IDs.

## Placement safeguards

- Keep ads clearly distinguishable from transfer controls and file rows.
- Never place an ad over a QR code, connection approval, file picker, transfer progress, or completion/error message.
- Never interrupt an active transfer with an interstitial or App Open ad.
- Rewarded benefits must be granted only inside the SDK's earned-reward callback. A dismissed or failed ad earns no reward.
- Native placements must use a real loaded NativeAd and a properly populated NativeAdView; a text placeholder is not a native ad.
- Follow AdMob and Google Play policies, including consent and privacy disclosures.

## Current integration status

The source currently has five shared test ad-unit constants (one per format family), not nine independently configurable production placement IDs. Home and History share the same banner unit; native placements share one placeholder slot; interstitial, rewarded, and App Open each use a Google test unit. The nine placement names above are the final requested inventory. The nine production IDs and App ID must be supplied before production ad integration can be completed and verified.