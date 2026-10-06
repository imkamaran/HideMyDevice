# Contributing to Hide My Device

Thanks for helping! This guide gets you from fork to merged pull request.

## Ways to help

- **Report bugs**: use the bug report template and include your device, Android version, root solution and LSPosed version. LSPosed logs (filter for `HideMyDevice`) help a lot.
- **Test on your device**: tell us which identifiers worked or didn't, in which app.
- **Improve docs or translations**: strings live in `app/src/main/res/values/strings.xml`. Add a `values-<lang>/strings.xml` for a new language.
- **Write code**: pick an [open issue](https://github.com/imkamaran/HideMyDevice/issues) or a [roadmap](README.md#roadmap) item. For anything big, open an issue first so we can agree on the approach.

## Development setup

1. Fork the repo and clone your fork.
2. Install **JDK 17+** (21 recommended) and the **Android SDK** (platform 36), or just use Android Studio.
3. Build: `./gradlew assembleDebug` (`gradlew.bat` on Windows).
4. Test on a rooted device or emulator with **LSPosed**: enable the module, tick a test app (a device-info app works well), force-stop it and check the values it shows.

## Project layout

| File | Responsibility |
|---|---|
| `Hook.java` | Xposed entry point. All framework hooks live here. |
| `Field.java` | One enum entry per identifier: key, label, validation regex, input type, UI group. |
| `Randomizer.java` | Realistic random values. |
| `MainActivity.java` | Main UI. |
| `AboutActivity.java` | About screen. |

### Adding a new identifier

1. Add an entry to `Field` (choose a stable preference `key`; it's stored in users' settings and export files).
2. Generate a value in `Randomizer.single()`, or in a linked group if it must stay consistent with other fields.
3. Hook the APIs that return it in `Hook`. Prefer `constant(...)` for methods that return a `String`; use `hookAll(...)` with a guard for anything conditional.
4. Install the hook **only when the field has a value**, so unused fields cost nothing.
5. Document it in the README's identifier table.

## Code style

- Java 17, 4-space indentation, match the style of the surrounding code.
- Keep hooks defensive: a missing method or class must never crash the target app. Catch `Throwable` around hook installation and log with the `HideMyDevice: ` prefix.
- Don't do slow work (I/O, network) inside hooked methods.
- User-facing text goes in `strings.xml`.
- Keep the module focused on privacy from tracking. Features whose main purpose is defeating security, anti-fraud or integrity checks won't be merged.

## Pull requests

1. Create a branch: `git checkout -b feature/short-description`.
2. Keep each PR focused on one change.
3. Make sure `./gradlew assembleDebug assembleRelease` passes (CI runs it on every PR).
4. Describe **what** changed, **why**, and **how you tested it** (device, Android version, target app).
5. Add screenshots for UI changes.

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
