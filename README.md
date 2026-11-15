# Due Dates

A simple, private Android app for recurring and one-time due dates, such as bills and payments. It reminds you; it does not track money. No amounts, no accounts, no internet.

See the [changelog](CHANGELOG.md) for the full list of features by version.

## Install

1. Open the [Releases](../../releases) page and download the latest `DueDates-*.apk`.
2. Open the file on your phone and allow installing from this source if Android asks.
3. Requires Android 8.0 or later.

Updates install over the previous version as long as they come from the same releases.

## Overview

- One-time and recurring reminders, with flexible repeat rules.
- Each date is tracked on its own: pending, overdue or done.
- Monthly view with history, overview and search.
- Daily notifications with a "Mark as done" button.
- Home screen widget.
- Light and dark themes, and JSON backup and restore.

## Tech

Kotlin, Jetpack Compose with Material 3, Room, WorkManager and Jetpack Glance. Minimum SDK 26 (Android 8.0), target SDK 35. Interface in English (US).

## Build

1. Open the project folder in Android Studio.
2. Set Gradle JDK to the embedded JetBrains Runtime (JDK 17 to 21). The Gradle version in use does not support JDK 25.
3. Sync, connect a phone with USB debugging enabled, and run.

## Known limits

- Holidays are not considered for business days.
- Notifications are approximate by design (no exact-alarm permission).
- A recurring reminder that never ends is shown up to 12 months ahead.
- Undo restores the whole state from before the action, so changes made in between are also reverted.
- The widget follows the system theme, not the in-app light or dark choice.

## Privacy

The app stores everything locally on your device. It has no internet permission, no accounts, no analytics, and no ads, and it never sends data anywhere. The only permission it asks for is notifications. If automatic backup is enabled on your device, Android may include the app data in your Google backup, which Google handles under its own terms. Files created with Export are saved where you choose.

## License

Released under the [MIT License](LICENSE).
