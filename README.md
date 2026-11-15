# Due Dates

A simple, private Android app for recurring and one-time due dates, such as bills and payments. It reminds you; it does not track money. No amounts, no accounts, no internet.

## Features

### Reminders
- **Frequencies:** Once, Weekly, Monthly, Yearly.
- **Monthly modes:**
  - Day of month (for example, the 20th).
  - Business day number from 1 to 23 (for example, the 5th business day), counting Monday to Friday. Holidays are not considered.
  - Last business day of the month.
- **How long:** Forever, or a fixed number of repetitions (N times). Defaults when choosing N times: weekly 4, monthly 12, yearly 5.
- **Fields:** title, optional note, first due date (date picker), icon, color, and "Notify on weekends".
- **Icons and colors:** 12 icons (bill, card, home, cart, power, water, internet, phone, car, health, school, TV) and 6 colors.
- **Date preview** when creating a recurring reminder: lists the generated dates, with adjusted dates highlighted, and a note when the series continues indefinitely.
- **Date adjustments:** days 29, 30, or 31 use the last day of shorter months. Yearly on February 29 uses February 28 in non-leap years. A business day number larger than the month's business days uses the last business day.
- **Past dates allowed:** the reminder is created already overdue.

### Occurrences
- Each date of a reminder is an occurrence with its own state: pending, overdue, or done.
- Overdue means the date has passed and it is not done. There is no day limit.
- Mark as done or undo it. The completion date is recorded. Completing before the due date is allowed.
- **Edit and delete:**
  - Recurring reminders ask "Only this" or "This and following".
  - "Only this" turns that occurrence into an exception that leaves the series.
  - "This and following" splits the series at that month and keeps the remaining number of repetitions.
  - In a business-day series, a date change under "This and following" is ignored. The other fields apply.
  - One-time reminders edit and delete directly.
- Occurrences already marked as done are never changed by edits or deletes of the series.
- **Undo:** a snackbar appears after marking, deleting, or editing.

### Main screen
- Months in a horizontal pager, opening on the current month. Past months work as the history, using the same screen.
- Each month shows only its own occurrences, in three sections: Overdue, Upcoming, Done.
- Header with the year, a large month name, and a summary such as "2 overdue · 3 upcoming".
- Overdue cards in red, with how many days overdue. Done cards show the due date and the completion date.
- A banner "N overdue in previous months" takes you to the oldest month with an overdue item.
- Navigation limits: the first month is the earliest reminder. The last month is the last scheduled one, or 12 months ahead when a reminder never ends. A note appears at both ends.
- **Overview sheet:** tap the year or month in the header to see every month grouped by year, with counts of overdue, upcoming, and done. Tap a month to go there.
- Tap a card for Edit and Delete. Use the check box to mark as done.

### Notifications
- A daily check at the time you choose in Settings (default 12:00 AM), scheduled with WorkManager. It survives reboots.
- One notification per pending or overdue occurrence, repeated every day until you mark it as done.
- **Mark as done** button in each notification.
- **Grouping:** up to 3 pending items arrive as individual notifications. With 4 or more, a summary notification ("N pending reminders") groups them, and each one keeps its own Mark as done button. The summary updates as you finish items.
- **Weekends:** a reminder with "Notify on weekends" turned off is skipped on Saturday and Sunday, and notified again on Monday. The due date itself does not change.
- Notifications are approximate. Android may delay them to save battery.
- Notification permission is requested on Android 13 and later. No exact-alarm permission is used.

### Home screen widget
- Lists overdue and upcoming pending reminders, with a color marker, title, date, and days overdue.
- A check box marks an item as done. Tapping anywhere else opens the app.
- Shows "All caught up" when nothing is pending.
- Follows the system colors and refreshes when data changes, at the notification check, and periodically.

### Settings
- Notification time, with a time picker and a note about possible delays.
- Theme color: wallpaper colors (Android 12 and later) or one of 6 colors. The app is always dark.
- Backup: export all data to a JSON file, or import a file. Import replaces all current data and asks for confirmation.
- Send a test notification (arrives in about 10 seconds).

### Data and privacy
- All data is stored locally in a Room database. The app has no internet permission.
- No accounts, analytics, or ads. See `PRIVACY_POLICY.md` and `PRIVACY_POLICY.pt-BR.md`.
- Only rules and occurrences with their own state are stored. Other dates are calculated when a month is shown, so recurring reminders have no end of navigation and no database growth.

## Tech

Kotlin, Jetpack Compose with Material 3, Room, WorkManager, Jetpack Glance. Minimum SDK 26 (Android 8.0), target SDK 35. Interface in English (US).

| File | Purpose |
| --- | --- |
| `Data.kt` | Room entities, DAO, database and migration |
| `Logic.kt` | Recurrence rules, occurrence generation, edit and delete logic |
| `Notifier.kt` | Scheduling, notifications, grouping, Mark as done receiver |
| `MainActivity.kt` | Activity and ViewModel (undo, backup, settings) |
| `Ui.kt` | Screens, sheets and dialogs |
| `Settings.kt` | Preferences and theme palettes |
| `Icons.kt` | Reminder icons and colors |
| `Backup.kt` | JSON export and import |
| `DueWidget.kt` | Home screen widget |

## Build

1. Open the project folder in Android Studio.
2. Set Gradle JDK to the embedded JetBrains Runtime (JDK 17 to 21). The Gradle version in use does not support JDK 25.
3. Sync, connect a phone with USB debugging enabled, and run.

The app name is in `app/src/main/res/values/strings.xml` (`app_name`).

## Known limits

- Holidays are not considered for business days.
- Notifications are approximate by design (no exact-alarm permission).
- A recurring reminder that never ends is shown up to 12 months ahead.
- Editing the date of a one-time reminder, or using "Only this", changes only that occurrence.
- Undo restores the whole state from before the action, so changes made in between are also reverted.
- The widget uses system colors, not the in-app theme color.

## Not included yet

Snooze, advance notice, search and filters, recurrences such as every N months, per-reminder notification time, holiday calendars, light theme.

## Before publishing

- Change `applicationId` and the package (`app.duely`) to the final name. It cannot change after release.
- Check that the name is available on Google Play and in trademark databases.
- Host the privacy policy at a public URL, fill in the contact placeholders, and add the link to the store listing.
- In Play Console, answer the Data safety form as "no data collected" and complete the content rating.
- Create a signed release build (Build > Generate Signed App Bundle).
- Replace the placeholder icon if you want a custom design, and add screenshots.
