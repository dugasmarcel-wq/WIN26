# Separate Daybook update channel

Package com.marcel.dayboox, separate from original Daybook. Updates are checked on launch approximately every 24 hours or manually under Settings. Android requests approval to install. Existing original package and daybook-update.json are unchanged.

Version 1.11 (code 12): default Quick Notes and separate combined dated Journal, inline notes in the four modules and reading history, month-first calendar and separate Statistics. Journal views reuse the previous native note records and their stable IDs, including book-specific records. No database migration or native changes. Browser interaction regression checks passed. On-device Android testing is not yet performed.

The signed APK is reconstructed exactly from the previous signed release and public delta, then checked for SHA-256, certificate, package and version before publication. Source web assets are contained in the verified APK; the prior native classes.dex is unchanged.

For later releases, build the separate package, bump versionCode, retain the same certificate, provider authority and feed, sign offline and commit the full signed APK and matching JSON. Remove bootstrap.delta.b64 when switching to a full signed APK. Do not commit signing keys.
