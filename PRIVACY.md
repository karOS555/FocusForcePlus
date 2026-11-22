# Privacy Policy

_Last updated: 2026-11-22_

FocusForce+ is built to keep your data on your device. This policy explains what that means in plain terms.

## The short version

FocusForce+ collects nothing, sends nothing, and shares nothing. Everything you put into the app stays on your phone. The app has no internet permission, so it has no technical way to transmit your data anywhere. There are no accounts, no cloud sync, no analytics, no crash reporting, and no advertising.

## What the app stores, and where

All of it lives locally in the app's own storage on your device:

- Your routines, todos, lists, focus sessions, and blocks
- App usage time, used only for the daily limits and the stats shown to you
- Your settings and preferences
- A local history of completed routines, focus sessions and todos, for the statistics screen
- Items you deleted, in the app's trash, until you empty it or the retention time runs out

If you uninstall the app, this data is removed with it (unless you exported a backup yourself).

## Permissions and why they exist

Each permission powers one feature and nothing else. You can skip any of them, and the related feature simply stays off.

- **Notifications**: to show routine alarms and to-do reminders.
- **Alarms and reminders (exact alarms)**: so reminders fire on time.
- **Display over other apps**: so an alarm screen can appear over whatever you're doing.
- **Accessibility service (blocker)**: to detect which app you just opened, so a blocked app can be covered. It cannot and does not read your screen content. This is switched off in the service configuration.
- **Accessibility service (Reels and Shorts, optional and off by default)**: if you switch Reels or Shorts off, or give them a daily time, a second, separate service watches only Instagram and YouTube for their short-video player and presses Back when it opens or when the time is used up. It is limited to those two apps in its configuration, and while it runs to the one you switched on. It looks only at the screen ids of the player and reads no text. With a daily time it keeps today's minutes in the player on your phone; nothing else is stored, and nothing is sent. Apps that are only short videos, like TikTok, are not handled by this service: they are blocked as whole apps by the blocker above.
- **Usage access**: to measure how long you used an app, for daily time limits and the screen-time numbers you see.
- **Do Not Disturb access**: so a focus session or the night in sleep mode can silence interruptions, and turn that back off when it ends.
- **Calendar**: optional, asked only from Settings > Calendar, after a screen that explains it. FocusForce+ reads your events to show today's on Home and to block the groups you pick while an event is on, and adds todos with a date to the one calendar you choose. It never changes or deletes events it did not make. If that calendar syncs with an account, the account syncs those events like any other; FocusForce+ itself has no internet access.
- **Tasks (OpenTasks or Tasks.org)**: optional, asked only from Settings > Tasks app, after a screen that explains it. FocusForce+ keeps your todos and the one task list you pick in step, on your phone. If that tasks app syncs the list with a server (for example with DAVx5), that happens in those apps; FocusForce+ itself has no internet access.
- **Set alarms**: optional, for backup alarms in your phone's own clock app in sleep mode.
- **Battery (unrestricted)**: optional, so reminders stay reliable on aggressive battery savers.

None of this information leaves your device.

## Backups

Android may include the app in a device backup you have enabled in your system settings. That backup is handled by Google and your device, not by FocusForce+, and follows your own backup settings. The app's setup state is excluded from backup on purpose, so a fresh install always walks you through setup again.

You can also export all your data to a plain JSON file yourself, from Settings, or let the app write one every night into a folder you choose. Those files go wherever you choose to save them, and they are your responsibility from that point on. If that folder is synced by another app, that app decides where the files go.

## Other apps

- **Sharing**: when you share text to FocusForce+, it is turned into a todo or list items on your phone. Nothing else happens with it.
- **Start links**: `focusforceplus://` links from other apps or NFC tags only work if you switch them on in Settings, and they can only start or add things inside the app. The app never sends anything back.

## Children

FocusForce+ is not directed at children and collects no data from anyone.

## Changes

If this policy ever changes, the updated version will be posted here with a new date at the top.

## Contact

Questions or concerns: open an issue at https://github.com/karOS555/FocusForcePlus/issues
