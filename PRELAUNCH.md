# Pre-launch checklist

- Replace AdMob test IDs in `AdUnits` and the manifest app id.
- Confirm the logo regeneration and the 512 px Play icon in branding/logo/play_store_512.png.
- Back up the release keystore. Turn on Play App Signing.
- Store KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD as Actions secrets.
- Enable GitHub Pages from /docs.
- Fill the governing-law placeholder in docs/terms.html.
- Complete the Data safety form from DATA_SAFETY.md and the content-rating questionnaire.
- Run the app on a phone: age gate, nearby permission, QR, accept prompt, picker, history, settings links.
- Privacy policy URL must match the listing.
- Declare Nearby devices, camera, and notifications in the Play console.
- Do not ship QUERY_ALL_PACKAGES. The manifest uses a launcher `<queries>` block only.
