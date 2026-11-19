# Changelog

All notable changes to FocusForce+ are recorded here. Dates are ISO (YYYY-MM-DD).

## [0.9.4-beta] - 2026-11-19

The last big update before 1.0: a new look, a home screen that shows your day in one list, speed bumps, lists for shopping and packing, your calendar and tasks apps, widgets, and a blocker that can skip just Reels and Shorts.

### Added
- **Speed bumps.** Before you tick off a todo, snooze, reschedule, stop a routine or end focus early, or pause a block, the app can ask again, make you wait (a minute at most) and give you a small task: hold a button, drag a slider or a path with two bends, or solve a sum. The parts add up. Defaults per action live in Settings > Speed bumps, a todo or routine can have its own, and one switch turns them all off
- **The todo alarm goes step by step:** Done first, then high priority asks whether it is really done, then the slider finishes it
- **Lists** for shopping and packing: several items at once with commas, amounts typed with the item ("2 milk", "flour 500 g"), sections that follow your way through the shop, suggestions from earlier items, sharing as text, duplicating, and a shopping mode that keeps the screen on
- **Lists work with todos and routines.** "Remind me" makes a todo for a list, and its alarm shows the open items to tick off. A routine step can carry a list, like a packing list for "Pack the bag", with "Start fresh" to untick it
- **Your calendar (optional).** Today's events on Home, todos with a date as events in a calendar you pick, and block groups that block while an event is on. Nothing from the calendar leaves the phone through the app
- **Tasks apps (optional).** Two-way sync with one list in OpenTasks or Tasks.org: titles, dates, priority, notes and done. With DAVx5 there, your todos reach Nextcloud and other CalDAV servers while FocusForce+ itself stays offline
- **Widgets:** Today (what is left, by time), Quick focus (one tap starts 15, 25 or 50 minutes) and a list to tick off from the home screen. Settings > Widgets places one
- **Skip Reels and Shorts (#2).** A separate, optional service leaves Instagram Reels and YouTube Shorts when they open and keeps the rest of both apps usable. It is off by default and explains itself before the system setting
- **Blocks.** The blocker is built around blocks: a set of apps, or a single app, with one rule. Always, a shared daily limit or a schedule, plus routines, focus sessions and calendar events
- **Your own bar (#3).** Choose which modules appear in the bottom bar and in which order, or switch a module off completely. The setup guide asks once at the end
- **Duplicate routines and reuse steps (#4).** Long-press a routine to duplicate it, copy steps from another routine, or start from a few common ones. Todos, focus sessions and lists can be duplicated too
- **Routines** set their own heads-up time (or none), can switch on Do Not Disturb, and can block only some groups, like focus sessions
- **A trash** for deleted todos, routines, focus sessions and lists, kept for as long as you choose
- **Themes:** six ready-made ones, two of them light, or your own five colours, saved under a name. **Aero** is a real Frutiger Aero style: frosted glass that blurs what is behind it, glossy see-through buttons, text with a soft glow, and an underwater photo full of rising bubbles behind everything (photo by TauchSport_Steininger, CC BY 2.0). Buttons are glass pills and the bottom bar floats over the photo. Copy, send or paste a theme as text
- **Sharing and start links.** Share text from any app to make a todo or list items. `focusforceplus://` links start focus or a routine, or add a todo or list items, from Tasker, shortcuts or NFC tags. They are off until you switch them on and can only start and add
- **Nightly backups** into a folder you choose, keeping the newest 3 to 30 files. An import can be undone: what was in the app before is kept
- Icons for todos, focus sessions, lists and blocks, shown in their notifications too. Every module has its own status-bar glyph
- Help pages for every module, rewritten for how things work now
- Long-press the app icon for New todo, Focus and Lists
- A todo search that finds nothing offers to add what you typed as a todo
- A notification when a nightly backup could not be saved, so a lost folder does not go unnoticed
- **Running sessions are a timeline.** A routine's notification draws one section per step (done, running, still ahead) with the countdown of the current step and Done and +5 min right there. Focus sessions and sleep mode get the same, and all three stay readable on the lock screen
- **Live updates:** on Android 16 a running session can be promoted into the status-bar chip. Whether a phone shows it is up to the manufacturer: Samsung's Now Bar takes its own apps only, and Settings > Permissions says so instead of promising it
- Notifications share one look: same accent, same icon, and a label telling you which part of the app is talking. The system's notification settings group them into Alarms, Running sessions and Reminders

### Changed
- **A new look:** flat cards, one row style, the Inter typeface and a new logo
- **Home shows your day in one list:** how much of today is done, what is up next with Start or Done right there, then routines, todos and calendar events by time, and todos without a date. The blocker and sleep mode can be switched from there too
- **Statistics** compare the last seven days with the seven before, show streaks, and let you tap a day; a dashed line marks the average
- **Navigation:** the bottom bar scrolls sideways and has an Edit entry. Settings and Statistics can be added to it
- Every form keeps its Save button in view and shows when there is more below
- Focus: quick focus at the top, saved sessions below. The running notification opens the session instead of ending it
- Screen time comes from Android's usage events, so a day starts at midnight and the home screen does not count as use
- Todo reminders run through one engine. Low is one notification, medium comes back a couple of times, high rings again every few minutes, all within set limits. A medium or high todo that is swiped away comes back quietly, and a snoozed one counts down to when it rings again
- Backups include lists, speed bumps, themes and the new settings (backup format 3; older files still import)
- Deleting anything asks first
- Focus, sleep mode and routines share Do Not Disturb: it stays on while any of them needs it, and a change you made by hand stays
- Check for updates opens the list of releases, betas included, and the setup guide says to switch on "Include prereleases" in Obtainium
- **Screen titles moved into the app bar.** Every tab used to repeat its title below the header, which cost about two rows of every list
- Daily todo reminder times are no longer capped at three

### Fixed
- **A tap on a tab could do nothing** when Settings or Statistics had been opened over it
- **Do Not Disturb could stay on for good on Android 15 and later,** where the app's own Do Not Disturb mode was set back instead of switched off. A mode left on that way is now switched off at app start
- **A snooze could be spent twice** when the alarm screen and the notification acted on the same alarm
- **Done todos vanished** from the list
- **Focus sessions ran long with the screen off,** because the countdown stopped while the phone slept
- **Do Not Disturb could stay on** after Android killed the app during a focus session. Sessions now pick up where they were, and Do Not Disturb is put back when they can't
- Saving a paused routine switched it back on, and editing a focus session lost its icon
- Snoozing a routine before it started did nothing
- **A running session was missing from the lock screen** on phones set to hide silent notifications there. Sessions now use a quiet channel that is not silent
- **The backup file was missing your settings.** Reminder times, default priority, alarm sound, blocker switches and the whole sleep schedule were never exported, so restoring a backup reset them. Older backup files still import
- **The lock-screen alarm permission asked forever** on phones that do not have it (Samsung One UI). The app asks once at most and says that alarms work anyway
- A second todo alarm arriving while one was on screen showed the wrong todo
- **Invincible Mode had gaps.** A locked block could still be switched off, and the blocker's master switch worked while an invincible focus session, routine or night was blocking apps. Every switch that weakens blocking now asks in one place
- **An app taken out of a block stayed blocked around the clock,** and so did the apps of a deleted block
- Renaming a block to a name that was taken crashed the app
- Starting a routine ended the one that was running, also an invincible one
- Dragging a routine step moved it by one place only
- A failed backup import could leave every alarm switched off until the next start. An import now also waits while a session runs or Invincible Mode holds a block
- Statistics: the day that is still running pulled the daily average down

[0.9.4-beta]: https://github.com/karOS555/FocusForcePlus/releases/tag/v0.9.4-beta

## [0.9.3-beta] - 2026-08-06

Sleep mode, and a round of fixes for reminders that were too easy to escape.

### Added
- **Sleep mode**: one nightly schedule with bedtime and wake-up, an optional wind-down reminder, Do Not Disturb, and blocking of the app groups you choose until morning. Find it under the new Focus & Sleep tab
- Bedtime and wake-up can each trigger one of your routines with the full routine alarm — pick an existing routine or create one straight from the picker and it links itself
- Sleep mode can optionally set up to three backup alarms in your phone's own clock app, spaced a few minutes apart in case you sleep through the first. They ring even if FocusForce+ isn't running
- App groups can now carry a blocking schedule: a daily time window on the weekdays you pick, overnight windows included
- App groups can now decide "block during routines" and "block during focus" for all their members at once, including apps you add to the group later

- A page at the end of the setup guide with a thank-you, the story behind the app, and links to leave a star or feedback on GitHub

### Changed
- Focus and sleep now share one tab, "Focus & Sleep", each with its own guide behind the question mark
- The home screen shows tonight's sleep schedule once it is set up
- The README now leads with why the app exists in the first place

### Fixed
- **Routines kept running when you left the app.** The step timer lived in the screen, so swiping the app away or turning the screen off quietly froze it and no alarm came when a step ran out. The session now runs in the background and wakes you with sound, vibration and a full-screen alert, and keeps reminding you while a step is over time
- **Alarm notifications no longer disappear when swiped away.** On Android 14 and up they could simply be wiped off; high-priority todo alarms and routine start alarms now come back, and a swiped medium alarm turns into a persistent reminder instead of vanishing
- **Undated todos with medium or high priority never showed their alarm screen.** They now ring at each daily reminder time, one after another with a gap you can set under Settings > Todos, so several open todos don't all go off at once
- Alarms survive app updates and clock or timezone changes, not just restarts, with a twice-daily safety check in case the system dropped them
- Your clock app can never be blocked, so waking up never depends on this app
- A restart during a routine now says the routine was interrupted instead of pretending it kept running
- The dots at the bottom of the setup guide showed a fixed five pages no matter how many there actually were

### Efficiency
- Routine and focus countdown notifications use the system timer instead of redrawing every second, which noticeably cuts their background work

[0.9.3-beta]: https://github.com/karOS555/FocusForcePlus/releases/tag/v0.9.3-beta

## [0.9.2-beta] - 2026-07-07

More fixes and polish from testing the beta on a real device.

### Added
- Onboarding now has a page explaining how to keep the app updated, since it has no internet access to check on its own: manually from the releases page, or automatically through Obtainium
- Settings > About: a "Check for updates" link that opens the latest release, and a "Report a bug or suggest an idea" link for beta feedback

### Fixed
- Full-screen alarm permission on phones that don't ship that dedicated settings screen (Samsung One UI, for one) now opens the app-info page instead of the plain notification screen that has no such toggle; the prompt also makes clear the permission is optional and alarms still work without it

[0.9.2-beta]: https://github.com/karOS555/FocusForcePlus/releases/tag/v0.9.2-beta

## [0.9.1-beta] - 2026-07-06

First round of fixes from testing the beta on a real device.

### Fixed
- Crash when opening the full-screen-alarm permission on devices that don't ship that settings screen; all permission screens now fall back gracefully
- Added clear guidance for enabling the accessibility service on directly-installed (sideloaded) builds, where Android blocks it behind "restricted settings"
- Status-bar notification icon no longer falls back to the generic Android icon at small sizes
- Alarms and reminders survive strict OEM background restrictions (e.g. Samsung) instead of silently failing

[0.9.1-beta]: https://github.com/karOS555/FocusForcePlus/releases/tag/v0.9.1-beta

## [0.9.0-beta] - 2026-07-06

First public beta. The whole app is built and has been tested on a real device. Distributed as a downloadable APK; no Play Store yet.

### Added
- Routines with multi-step timers, alarm-style start, snooze and reschedule limits, and per-routine Invincible Mode
- To-do planner with priority-based reminders, a daily digest, recurring items, and checklists
- App blocker via an accessibility service, with daily limits, time windows (including overnight), and per-rule Invincible Mode
- App groups with one shared daily limit, plus quick-fill templates for Social Media, Games, and Entertainment
- Focus mode with Do Not Disturb, notification silencing, app or group blocking, presets per session type, and scheduling
- Home dashboard, weekly statistics, onboarding with a permission walkthrough, and an expanded settings screen
- Tamper Protection: an optional daily change window that guards your lock settings
- JSON export and import for all your data
- A quick-settings tile for starting a focus session

[0.9.0-beta]: https://github.com/karOS555/FocusForcePlus/releases/tag/v0.9.0-beta
