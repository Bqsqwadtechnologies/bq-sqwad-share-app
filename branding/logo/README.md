
# Logo

Put the master mark here as `logo.png` (1024×1024 or larger, square). An optional `logo_mono.png` can be a single-color silhouette.

The file already in this folder is the BQ Sqwad Share mark supplied for this app. Every launcher icon, the round icon, the monochrome adaptive layer, the notification small icon, the in-app header (`brand_logo`) and `play_store_512.png` are generated from it.

Regenerate after you replace the file:

```bash
python3 scripts/generate_branding.py
```

Or run the Gradle task `generateBranding`. Do not draw the logo by hand inside feature code. Screens read `@drawable/brand_logo` and `@mipmap/ic_launcher`. Notifications use `@drawable/ic_stat_notify`.
