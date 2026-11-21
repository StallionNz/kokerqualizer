# Contributing to Kokerqualizer

Thank you for your interest in improving **Kokerqualizer**!

## Ways to Contribute

- **Bug Reports**: Use the Bug Report template in `.github/ISSUE_TEMPLATE/bug_report.md` and include your Android version, device model (e.g., Samsung One UI version), audio output route (Bluetooth, USB-C, Speaker), and steps to reproduce.
- **Feature Requests**: Open an issue describing the audio or UX improvement and how you'd use it.
- **Pull Requests**: Fork the repo, make focused changes on a feature branch, and open a PR with a clear description.

## Coding Guidelines

- **Pure Java 8 + Android Framework only**: Keep zero external libraries, zero AndroidX dependencies, and zero Gradle requirements so the APK stays lightweight and builds cleanly with `aapt2`, `javac --release 8`, and `d8`.
- **Target & Min SDK**: Maintain compatibility from `minSdk=28` (Android 9) through `targetSdk=36` (Android 16), including edge-to-edge system bar insets, predictive back (`OnBackInvokedCallback`), and Android 14–16 foreground service rules.
- **Zero-Allocation Rendering**: Never allocate `Paint`, `Path`, `RectF`, or `Shader` objects inside `onDraw()` loops; cache shaders on size/color changes.
- **Compiler Cleanliness**: Ensure `javac --release 8 -Xlint:all -Xlint:-options -Xlint:-deprecation` passes with zero warnings.
- **Versioning**: Bump `versionCode` and `versionName` in `app.properties` and update `CHANGELOG.md` for release PRs.

## Pull Request Checklist

- [ ] Code compiles cleanly with zero lint warnings.
- [ ] Tested on portrait and landscape orientations (including Car Mode and Floating Orb if touched).
- [ ] No secrets, keystores, personal tokens, or build artifacts committed.
- [ ] Documentation (`README.md` / `CHANGELOG.md`) updated if user-facing behavior changed.
