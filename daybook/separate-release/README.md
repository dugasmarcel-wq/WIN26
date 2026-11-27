# Separate Daybook update channel

Package com.marcel.dayboox. Updates are checked on launch or manually in Settings. The original app and daybook-update.json remain unchanged. Android requires installation approval.

Version 1.13 (code 14), replacing 1.12: personal journal stays open, category cards expand on demand, Bible and spiritual pads use the same note ID when possible; older independent entries remain accessible. Missed-day marks have been corrected to fit inside their cells; neutral dates no longer show marks. Accountability calendar displays five slots, distinguishing completed, partial, missed, pending and not applicable. Selected date displays daily habit names and time totals. Calendar reminders support one-time notifications and annual calendar dates, not recurring notifications. Reminder metadata is stored in the existing backed-up note store; cancelling preserves the associated quick note. Browser interaction tests passed; actual-device testing remains outstanding.

The signed APK is reconstructed exactly from the previous signed release and public delta, then checked for SHA-256, certificate, package and version before publication. The previous native classes.dex is unchanged. Future releases must retain the same signer and separate feed; never commit signing keys.
