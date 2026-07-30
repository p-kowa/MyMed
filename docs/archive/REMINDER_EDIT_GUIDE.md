# How to Edit Reminders

## New Feature: Direct Reminder Editing

Previously, to change a reminder's time or frequency, you had to delete it and create a new one. **Now you can edit reminders directly!**

## Steps to Edit a Reminder

### 1. Open Medication Details
- Go to ⚙️ (Settings/Manage Medications)
- Tap on a medication to view its details
- Tap "⏰ Manage reminders" button

### 2. Click on the Reminder You Want to Edit
- The reminder sheet shows all reminders for this medication
- **Simply tap/click on any reminder** (the card will highlight)
- The reminder card is clickable - tap anywhere on it

### 3. Edit Dialog Opens
The edit dialog appears with all current settings:
- **Time** - Use the time picker to change hour and minute
- **Weekdays** - Tap circles to select/deselect days (Mo, Tu, We, Th, Fr, Sa, Su)
- **Snooze Interval** - Choose snooze duration (5, 10, 15, 30 min)

### 4. Save or Cancel
- **Speichern/Save** - Applies all changes (must have at least one weekday selected)
- **Cancel** - Discards changes

## Other Reminder Actions

While the reminder is visible in the list, you can also:
- **Toggle switch (right side)** - Enable/disable the reminder without editing
- **Delete button (trash icon)** - Remove the reminder

## Error Handling

If an error occurs (e.g., database issue):
- A red error message appears at the top of the reminder list
- The error message shows what went wrong
- Your original reminder is preserved unchanged
- Tap ✕ to dismiss the error message

## Examples

### Example 1: Change reminder time
**Before:** "Aspirin - 08:00, Daily, Snooze 10min"
1. Tap the reminder
2. Change time to 09:00
3. Tap "Save"
**After:** "Aspirin - 09:00, Daily, Snooze 10min"

### Example 2: Change frequency to weekdays only
**Before:** "Aspirin - 10:00, Daily (Mo-Su), Snooze 10min"
1. Tap the reminder
2. Deselect Sa and Su (Saturday and Sunday)
3. Tap "Save"
**After:** "Aspirin - 10:00, Mo-Fr only, Snooze 10min"

### Example 3: Change snooze interval
**Before:** "Aspirin - 14:00, Daily, Snooze 5min"
1. Tap the reminder
2. Select "15m" instead of "5m"
3. Tap "Save"
**After:** "Aspirin - 14:00, Daily, Snooze 15min"

## Troubleshooting

**Q: The reminder card doesn't seem clickable**
- Make sure you're tapping the main card area, not just the toggle switch or delete button
- The toggle/delete buttons are separate and won't open the edit dialog

**Q: I see a red error message**
- The database operation failed. Your reminder wasn't changed.
- Check your device storage (might be full)
- Try again or recreate the reminder if needed

**Q: The "Save" button is greyed out**
- You must select at least one weekday before saving
- Select at least one day (e.g., just Monday) and try again

**Q: Changes aren't showing immediately**
- The app updates automatically (usually within 1 second)
- If the changes don't appear after 5 seconds, close and reopen the medication


