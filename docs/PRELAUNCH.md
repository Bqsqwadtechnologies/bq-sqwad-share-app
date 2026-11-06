# Pre-launch checklist

## Google Play storage and media permission declaration

BQ Sqwad Share uses media access to provide its Photos, Videos, and Music browsing categories. Google Play restricts access to `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and especially `MANAGE_EXTERNAL_STORAGE`. Play Console may require a permissions declaration and a review of the app's core functionality. Approval is not automatic, even for file-transfer and file-manager apps.

Before publishing, decide whether to apply for the required Play Console declarations and submit the requested demonstration and policy information.

If the all-files access declaration is not approved or the owner decides not to apply, remove the `MANAGE_EXTERNAL_STORAGE` permission from `app/src/main/AndroidManifest.xml`. The app must continue to work without it:
- Photos, Videos, Music, Apps, Received, and Sent remain available with their applicable media permissions.
- Documents, Files, and deep-clean scans must show a friendly explanation that broader file access is unavailable, with an optional link to the system settings where appropriate.
- Do not block app startup or the other categories when full storage access is denied.

Re-check the current Google Play policy and the exact permission declarations in Play Console immediately before release. Do not assume that adding a permission to the manifest grants approval.
