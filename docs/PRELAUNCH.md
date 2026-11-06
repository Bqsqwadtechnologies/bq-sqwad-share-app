# Pre-launch checklist

## Google Play storage and media permission declaration

BQ Sqwad Share uses scoped media permissions for Photos, Videos, and Music. The current manifest does not request `MANAGE_EXTERNAL_STORAGE`; this avoids relying on a restricted all-files permission without an approved Play Console declaration.

Current behavior:
- Photos, Videos, Music, Apps, Received, and Sent remain available with applicable media access.
- Documents and full Files browsing display a friendly notice because broad storage access is not enabled in this build.
- App startup and the other categories must remain usable when media access is denied.

If the owner later decides to apply for all-files access, first verify the current Google Play policy, demonstrate that the use case qualifies as core functionality, and submit the required Play Console declaration. Approval is not automatic. Only add `MANAGE_EXTERNAL_STORAGE` after that policy decision and update the in-app access explanation accordingly.

Re-check the current Google Play policy and the exact permission declarations in Play Console immediately before release.
