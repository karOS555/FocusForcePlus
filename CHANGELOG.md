# Changelog

All notable changes to FocusForce+ are recorded here. Dates are ISO (YYYY-MM-DD).

## [0.9.1-beta] - 2026-07-06

First round of fixes from testing the beta on a real device.

### Fixed
- Crash when opening the full-screen-alarm permission on devices that don't ship that settings screen; all permission screens now fall back gracefully
- Added clear guidance for enabling the accessibility service on directly-installed (sideloaded) builds, where Android blocks it behind "restricted settings"
- Status-bar notification icon no longer falls back to the generic Android icon at small sizes
- Alarms and reminders survive strict OEM background restrictions (e.g. Samsung) instead of silently failing

[0.9.1-beta]: https://github.com/karOs555/FocusForcePlus/releases/tag/v0.9.1-beta

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

[0.9.0-beta]: https://github.com/karOs555/FocusForcePlus/releases/tag/v0.9.0-beta
