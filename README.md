# WIN26

WIN26 is a Windows-inspired launcher for Android 11+. This fork is configured as a local-first build: personal data stays on-device, and network access is reserved for Internet Explorer/browser actions.

The current privacy baseline removes cloud sync, automatic update checks, location/weather fetching, contacts/calendar reads, external TTS, Google sign-in/Drive integration, and other non-browser network paths.

## Features
- Themes to faithfully recreate:
    - Windows 95/98
    - Windows XP
    - Windows Vista
- Working home screen, with desktop icons that can be renamed and changed (included are a bunch of icons from the era
-   Working start menu as an app launcher
-   Gestures:
    -   Swipe down on the desktop to open the notification bar
    -   Swipe up on the desktop to open the start menu in search mode
    -   Swipe right on the desktop to open a favorite app (set via the start menu)
-   Included wallpapers from the era, 3D pipes screen saver and customization options
-   Included retro apps, all coded from scratch to be as close as possible to the original but work on modern phones:
    -   IE 6/7 - works as a browser (hold the homepage button to set current page as homepage)
    -   Winamp - plays local MP3 files
    -   Notepad - for storing notes but included tabs as well
    -   Phone Dialer - working phone with basic features and speed dial
    -   Registry Editor - to check locally stored settings and import/export local settings files
    -   3 games: Minesweeper, Solitare, 3D Pinball Space Cadet
-   Calendar and clock shortcuts that open installed Android apps without reading calendar data
-   Windows Update menu item is disabled; it does not check the network

## Privacy Notes
- `INTERNET` remains because Internet Explorer is built into the same APK.
- Android cannot restrict that permission to only one Kotlin class inside the same APK/UID.
- Known non-browser networking paths have been removed or disabled.
- Explorer, media apps, notification dots, app enumeration, uninstall flow, wallpapers, and local import/export still require their related Android permissions.

## Special Permissions
In order for the launcher to do some of its things like access notifications for notification dots, it needs special permissions which Android restricts by default, especially for side-loaded apps. To enable them, follow these steps:

### Option 1 - Install via ADB
Apps installed with adb install aren’t treated as the “untrusted sideload” case, so Restricted Settings doesn’t trigger.
Just: `adb install -r app-release.apk`
Then go to Settings → Notification access and you should be able to toggle the notification service without the “Restricted setting” dialog.

### Option 2 - Allow restricted settings
1. Settings → Apps → See all apps → WIN26
2. Tap the ⋮ three-dot menu (top right)
3. Tap “Allow restricted settings”, Unlock with PIN if asked
4. Now go to Notification access and enable the service.


## Notes
1) The codebase is inherited and still needs cleanup. Keep changes controlled and build after each privacy or theme stage.
2) WIN26 claims no copyright over the era-specific assets used for compatibility and nostalgia.
