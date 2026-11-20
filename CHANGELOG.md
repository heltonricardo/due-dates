# Changelog

## 1.4

### Added
- **Portuguese (Brazil) translation:** the whole interface, the widget and the notifications are now available in English (US) and Portuguese (Brazil). The app follows the system language. On Android 13 and later, the language can also be set for this app only in the system settings (Settings > Apps > Due Dates > Language).

### Changed
- Dates and times follow the language and region of the device. The notification time picker uses the 12-hour or 24-hour format set in the system.

## 1.3

### Changed
- The application ID is now `app.vercel.heton.duedates`. Android treats it as a new app: export a backup from the old version (Settings > Backup > Export) and import it in this one.

## 1.2

### Added
- **Due today section:** the monthly view now has a separate "Due today" section, between "Overdue" and "Upcoming". It appears only in the current month.
- The month header summary shows the "N due today" count.

### Changed
- "Upcoming" now lists only items due after today.

## 1.1

### Added
- **Repeat every N:** recurring reminders can repeat every N days, weeks, months or years (for example, every 3 days, every 6 months, every 2 years).
- **Daily frequency.**
- **Weekday picker:** weekly reminders now use a M T W T F S S selector. Several days can be chosen, combined with "Every N weeks".
- **Edit repeat settings:** editing a recurring reminder now shows its repeat settings (frequency, interval, weekdays, day of month or business day, repetitions). They apply to "This and following".
- **Change completion date:** done items have a "Change completion date" action.
- **More icons:** building, pet, money, bank, gym and travel (18 in total).

### Changed
- The new and edit reminder sheet can no longer be dismissed by dragging, tapping outside or pressing Back, so typed data is not lost. Use Cancel or Save.
- Backups now include the interval and weekdays. Backups from 1.0 still import.
- The database migrates from 1.0 without data loss.

### Fixed
- The business day field accepted values above 23. It is now capped at 23.

## 1.0

### Reminders
- Frequencies: Once, Weekly, Monthly, Yearly.
- Monthly modes: day of month, business day number (1 to 23, Monday to Friday, holidays not considered), or last business day.
- Duration: forever, or a fixed number of repetitions. Defaults: weekly 4, monthly 12, yearly 5.
- Fields: title, optional note, first due date, icon, color and "Notify on weekends".
- 12 icons and 6 colors.
- Date preview when creating a recurring reminder, with adjusted dates highlighted.
- Date adjustments: days 29 to 31 use the last day of shorter months, February 29 falls back to February 28, and a business day number larger than the month's business days uses the last business day.
- Past dates are allowed. The reminder is created already overdue.

### Occurrences
- Each date has its own state: pending, overdue or done.
- Mark as done or undo it. The completion date is recorded, and completing early is allowed.
- Recurring reminders ask "Only this" or "This and following" when editing or deleting. One-time reminders edit and delete directly.
- "Only this" turns the occurrence into an exception that leaves the series.
- "This and following" splits the series and keeps the remaining repetitions.
- Done occurrences are never changed by edits or deletes of the series.
- Undo snackbar after marking, deleting or editing.

### Main screen
- Months in a horizontal pager, opening on the current month. Past months work as the history.
- Each month shows Overdue, Upcoming and Done sections, with a header summary.
- Overdue cards in red with the number of days overdue.
- Banner "N overdue in previous months" jumps to the oldest month with an overdue item.
- Overview sheet with every month grouped by year and counts per month.
- Search through titles and notes of all reminders, ignoring case and accents, with a "Show in month" shortcut.

### Notifications
- Daily check at a time chosen in Settings, scheduled with WorkManager and surviving reboots.
- One notification per pending or overdue occurrence, repeated daily until done.
- "Mark as done" button in each notification.
- Up to 3 items arrive individually. With 4 or more, a summary notification groups them and updates as items are finished.
- Reminders with "Notify on weekends" off are skipped on Saturday and Sunday and notified again on Monday.
- Notifications are approximate, and no exact-alarm permission is used.

### Home screen widget
- Lists overdue and upcoming reminders with a color marker, title, date and days overdue.
- A check box marks an item as done, and tapping elsewhere opens the app.
- Shows "All caught up" when nothing is pending.
- Follows the system colors and refreshes when data changes.

### Settings and data
- Notification time and light or dark theme (dynamic colors on Android 12 and later).
- Backup: export to and import from a JSON file. Import replaces all data after confirmation.
- Everything is stored locally. No internet permission, accounts, analytics or ads.
