# Contributing to FocusForce+

Thanks for wanting to help. The project is in beta and moving fast, but bug reports, ideas, and pull requests are all welcome.

## The quickest way to help

You don't need to write code to be useful:

- **Found a bug?** Open a [bug report](../../issues/new?template=bug_report.md). The more detail (device, Android version, steps), the faster it gets fixed.
- **Got an idea?** Open a [feature request](../../issues/new?template=feature_request.md).
- **Just testing?** Even a note saying "this worked, this didn't" on your device helps a lot right now.

Anyone with a GitHub account can open an issue. You don't need special access.

## Running the project locally

1. Clone the repo.
2. Open it in Android Studio (a recent stable version).
3. It uses the Gradle wrapper, so it pulls the right Gradle and JDK 17 setup on its own. Just let it sync.
4. Run it on an emulator or a real device. For anything touching the app blocker, focus mode, or alarms, use a real device, because emulators can't do accessibility, Do Not Disturb, or lock-screen alarms properly.

The app targets Android 8.0 (API 26) and up.

## How the code is laid out

It's MVVM with a clean-ish separation:

- `data/` holds the Room database, entities, DAOs, and repositories.
- `ui/screens/` has one package per feature (routine, todo, blocker, focus, home, settings, onboarding, stats), each with its screens and view models.
- `service/` has the foreground services, alarm receivers, and the accessibility service.
- `compliance/` holds the rules that keep the app within Google Play policy (limits, the block-decision logic, tamper protection). These are pure functions with unit tests.
- `util/` is the shared helpers (alarm scheduling, notifications, backup, permissions).

## Style and conventions

- **Kotlin and Jetpack Compose**, Material 3, following the patterns already in the codebase.
- **All user-facing text is in English.** Labels, buttons, errors, empty states, everything.
- Don't use smart quotes inside Kotlin string literals. Use escaped ASCII quotes.
- **Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):** `feat:`, `fix:`, `docs:`, `chore:`, and so on, with an optional scope like `feat(blocker):`.
- Keep pull requests focused. One change per PR is much easier to review than ten mixed together.

## Design principles (please don't break these)

FocusForce+ has a few hard rules. They exist so the app stays honest with users and stays within Google Play policy. A PR that crosses one of these won't be merged as-is:

- **Everything stays local.** The app has no internet permission and never will. Don't add analytics, crash reporting, or any SDK that phones home.
- **Invincible Mode is friction, not a trap.** It can make impulsive bypassing annoying, but the user must always be able to uninstall the app and reach their system settings. Never block uninstalling, never use Device Admin, never fight the user out of Android's own settings.
- **The accessibility service reads nothing.** It only detects which app came to the foreground. It must not read screen content.
- **Ask for the fewest permissions possible,** and always explain a sensitive one before sending the user to grant it.
- **No medical claims.** The app is a self-help tool, not a treatment. Don't describe it as diagnosing, treating, or curing anything.

If you're not sure whether a change is okay, open an issue and ask before writing a lot of code.

## Pull request workflow

1. Fork the repo and make a branch off `main`.
2. Make your change, and run the app to check it actually works (not just that it builds).
3. Run the unit tests: `./gradlew testDebugUnitTest`.
4. Open a PR against `main` and fill in the template.
5. Expect a review and maybe some back and forth. That's normal.

## Being decent

Be kind and assume good faith. This is a small project built to help people who struggle with focus, so let's keep the space welcoming. Harassment or hostility isn't tolerated.

Thanks again for being here.
