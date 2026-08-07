# Changelog

All notable changes to FocusForce+ are recorded here. Dates are ISO (YYYY-MM-DD).

## [0.9.4-beta] - 2026-08-08

A notification rework, more room on every screen, and a handful of fixes.

### Added
- **Running sessions are now a timeline.** A routine's notification draws one section per step — done, running, still ahead — with the countdown of the current step and Done / +5 min / Cancel right there. Focus sessions and sleep mode get the same treatment
- A running routine, focus session or sleep window stays readable on the lock screen, with its timeline and countdown — no unlocking to see where you are
- **Live updates**: on Android 16 the same session can be promoted into the status-bar chip. Whether a phone actually shows it is up to the manufacturer — Samsung's Now Bar takes its own apps only, and Settings > Permissions says so plainly instead of promising it
- The home screen now carries App Blocker and Sleep mode as switchable rows: what they are doing right now, and an on/off switch without opening a tab first. Sleep mode also gets its own live banner while the night is running
- Notifications share one look now: same accent, same icon, and a label telling you which part of the app is talking. The system's notification settings group them into Alarms, Running sessions, and Reminders

### Changed
- **Screen titles moved into the app bar.** Every tab used to repeat its title below the header, and a stray inset ate another 55dp — the todo list showed three entries on a large phone where it now shows five
- Daily todo reminder times are no longer capped at three
- Swiping a daily reminder for a high or medium priority todo brings it back, the same way its alarm does. Low priority stays an ordinary notification
- Snoozing a todo no longer makes the replacement notification buzz again, and it counts down to when it will re-fire

### Fixed
- **A running session was missing from the lock screen.** Its notification sat on a low-importance channel, which Android counts as "silent" - and phones set to hide silent notifications on the lock screen dropped it entirely. Sessions now use a quiet-but-not-silent channel and show their full content on the lock screen, which is exactly where a running routine is worth seeing
- The status bar icon was a lightning bolt. It is the FocusForce+ target now
- **The backup file was missing your settings.** Reminder times, default priority, auto-delete, alarm spacing, blocker switches, alarm sound and vibration, and the entire sleep schedule were never exported — restoring a backup silently reset them. Older backup files still import; they just leave your current settings alone
- **A snooze on the todo alarm screen could be spent twice.** A second tap while the first was still saving burned another snooze; every action on that screen now runs exactly once, and the screen closes even if the system refuses the background service
- **The lock-screen alarm permission asked forever on phones that don't have it.** Samsung One UI ships no full-screen-notification screen, so "Grant" led to a page with nothing to turn on. The app now detects that, asks at most once, and says plainly that alarms work anyway
- A second todo alarm arriving while one was already on screen showed the wrong todo

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
