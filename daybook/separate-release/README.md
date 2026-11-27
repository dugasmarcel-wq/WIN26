# Separate Daybook update channel

Package: com.marcel.dayboox. The original com.marcel.daybook application, its signing certificate, and daybook-update.json are unchanged.

The separate app checks dayboox-update.json in release win26-update-100141 on launch (about every 24 hours), and on Settings > App updates > Check now. Android requires confirmation to install updates. Version 1.10 (code 11) enables this feed, uses the certificate of the separate 1.8 and 1.9 installers, and retains existing app data.

Bootstrap publication reconstructs the exact already-signed APK from the verified Android build and a public binary delta, then verifies SHA-256, package, version and signing certificate before publishing. No private key is stored in GitHub.

Future releases: build with the same separate package, provider authority and update endpoint, increment versionCode, sign with the existing separate release key offline, and commit Dayboox-<code>.apk and matching dayboox-update.json together on this branch. Remove bootstrap.delta.b64 when committing a new full signed APK. The publication job checks the package and certificate and refuses downgrades or a changed APK for the same version. Keep the signing key and password backed up privately.
