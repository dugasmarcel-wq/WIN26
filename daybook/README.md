# Daybook

Daybook is a standalone Android journal under the package `com.marcel.daybook`. The Daybook APK and optional WIN26 widget-host installer are assets of the existing [WIN26 release](https://github.com/dugasmarcel-wq/WIN26/releases/tag/win26-update-100141).

## Install and connect

Install Daybook and open it once. The four permanent Method of 4 bubbles record actual time against one hour per category per day. Tap a bubble to add minutes, correct today's total, or start/stop a persistent stopwatch. Extra time is retained separately from balanced category progress. The stopwatch records elapsed time when stopped, splitting at midnight in the start timezone; stop it before exporting a backup. Green Daily repeats each day. Blue Weekly and purple Objectives are persistent one-time checklists with optional deadlines; checked and unfinished tasks remain visible until removed. Prior check-ins and actual task completion dates are kept. Past task completions cannot be undone; a same-day correction is possible. Hold a goal or tap its ⋯ button to edit, see history, or remove it. Removed goals stop appearing on widgets. The daily writing pad is directly beneath Daily goals. The Notebook starts with Daily and Long Term folders. Notes save as you type, and each can carry a notification that opens the note or snoozes for ten minutes.

Note Calendar includes a day-dot calendar, monthly and yearly completion rates, and period navigation. The widget shows the last seven days and this month’s rate. Its tracker row opens the full calendar. The calendar includes recorded category minutes, additional time, daily adherence and dated task achievements. Old check-ins retain their historical rules and are never converted into guessed hours. The database migration retains old notes, marks, edits, visibility, reflections and colors; active old weekly/long-term goals become persistent tasks. Existing weekly checks with a known current-week tap date are carried forward, but old repeated long-term check-ins are not treated as completed objectives. Open daily check-ins are excluded from the percentage until completed or missed.

On WIN26 release 100141, install the optional WIN26-Daybook-Widget.apk over the existing launcher. Open WINSUNG Widgets, choose Daybook, choose Manage, and add it to Desktop. Tap Connect Daybook widget and approve Android's binding prompt. The host also supports the Quick Glance and Page 2 placements. On launchers with a standard widget picker, add Daybook directly.

## Build and verify

Use JDK 17, Android platform 35, and Android SDK Build Tools. Run `bash daybook/build.sh` from the repository root. The unsigned APK is written to `daybook/build/Daybook-unsigned.apk`. With an Android emulator running, `bash daybook/test/run.sh` installs a temporary test-signed build and exercises the actual app and widget. Use a test emulator with a clean install for these checks.

## Publish an update

1. Increase the version code and version name in the standalone AndroidManifest.xml. Build and pass the device checks on the daybook-journal branch.
2. Sign the aligned APK offline with the private Daybook release keystore. The owner holds the signing backup. Keep signing material outside the repository.
3. Store the signed APK under `daybook/publishing/Daybook-<code>.apk`. Update `daybook/publishing/daybook-update.json` with its version, release URL, and SHA-256 checksum. Commit both together.
4. The Daybook publication workflow verifies the package, certificate, version and checksum before attaching the installer and updating the feed on the original release. Keep that release tag to preserve links used by installed copies.

Daybook checks for an update on opening after about 24 hours. Settings also has Check now. Android prompts for installation. App data stays in its private database. JSON backups are manual through the Android system document picker. Treat exported backups as private.
