# BQ Sqwad Share — AdMob ad-unit inventory

Create the ad units below in the AdMob app for BQ Sqwad Share (com.bqsqwad.share). Keep the names exact so each production unit ID can be mapped to its intended placement.

## Core launch placements

| # | Exact AdMob ad-unit name | Format | Intended placement |
|---:|---|---|---|
| 1 | BQ Sqwad Share - Home Banner | Banner | Home screen bottom |
| 2 | BQ Sqwad Share - History Banner | Banner | Transfer history bottom |
| 3 | BQ Sqwad Share - Home Native | Native Advanced | Home content, between sections |
| 4 | BQ Sqwad Share - Apps Native | Native Advanced | Installed-app browsing list |
| 5 | BQ Sqwad Share - History Native | Native Advanced | History list between completed records |
| 6 | BQ Sqwad Share - Transfer Interstitial | Interstitial | Only after a successful transfer session |
| 7 | BQ Sqwad Share - Ad Free Rewarded | Rewarded | Optional 24-hour ad-free reward |
| 8 | BQ Sqwad Share - Premium Rewarded | Rewarded | Optional unlock flow; reward only after SDK callback |
| 9 | BQ Sqwad Share - App Open | App Open | Returning after a long absence |

## Additional named placements for future rollout

These names reserve a clear inventory for later AdMob setup. They are not live placements until their individual ad-unit IDs and app integration are supplied and verified.

| # | Exact AdMob ad-unit name | Format | Intended placement |
|---:|---|---|---|
| 10 | BQ Sqwad Share - Photos Native | Native Advanced | Photo library browsing |
| 11 | BQ Sqwad Share - Videos Native | Native Advanced | Video library browsing |
| 12 | BQ Sqwad Share - Music Native | Native Advanced | Music library browsing |
| 13 | BQ Sqwad Share - Files Native | Native Advanced | File browser between file groups |
| 14 | BQ Sqwad Share - Favorites Native | Native Advanced | Favorites list |
| 15 | BQ Sqwad Share - More Screen Banner | Banner | About and information screen |
| 16 | BQ Sqwad Share - Storage Tools Banner | Banner | Storage overview, outside active cleaning |
| 17 | BQ Sqwad Share - Team Sharing Native | Native Advanced | Team information/list browsing, not active transfer |
| 18 | BQ Sqwad Share - Folder Transfer Complete | Interstitial | Only after a folder transfer completes successfully; never during selection or transfer |

## Placement safeguards

- Ads must be visually separated from navigation, file rows, buttons, and transfer controls. The app's ad container has no app action of its own; Google's ad creative may contain its own clickable elements.
- Never place ads over a QR code, connection approval, file picker, send/receive action, transfer progress, retry/cancel controls, or completion/error messages.
- Do not show banners during Send, Receive, QR Scan, recipient selection, pairing, or active folder/file transfers.
- Do not interrupt an active transfer with interstitial or App Open ads.
- Keep the global banner limited to non-transactional browsing/information routes. Do not stack duplicate banners on one screen.
- Native placements require a real loaded NativeAd and a properly populated NativeAdView. A text label or empty placeholder is not a functioning native ad.
- Rewarded benefits must be granted only inside the SDK's earned-reward callback. A dismissed or failed ad earns no reward.
- Respect consent, frequency limits, ad-free access, and applicable AdMob/Google Play policies. More ad units do not guarantee more impressions or revenue.

## IDs required for production

Provide the AdMob App ID (contains ~) and each production ad-unit ID (contains /). Do not replace the Google sample IDs with guessed IDs.

The app currently uses Google's sample test IDs. The banner is integrated as a shared test unit; interstitial, rewarded, and App Open use shared test units. The NativeSlot is currently a placeholder and is not a production native-ad implementation. Additional placements above are inventory names only until separately configured and integrated.
