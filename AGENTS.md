# Repository Guidelines

## Project Structure & Module Organization

FloatyAnswer is a single-module Android application. Kotlin sources live under `app/src/main/java/com/hwb/aianswerer/`. Capture and floating-window orchestration sits at the package root; integrations are in `api/`, settings in `config/` and `providers/`, data types in `models/`, and Jetpack Compose code in `ui/`. Resources are in `app/src/main/res/`, provider presets in `app/src/main/assets/provider_data.json`, and JVM tests in `app/src/test/java/`. Dependencies are centralized in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands

Use JDK 17 and an Android SDK. Copy `local.properties.template` to `local.properties`, set `sdk.dir`, and keep API keys and signing credentials local.

- `./gradlew assembleDebug` builds an installable debug APK.
- `./gradlew testDebugUnitTest` runs the JUnit, MockK, coroutine-test, and Robolectric suite.
- `./gradlew lint` runs Android lint.
- `./gradlew koverXmlReportDebug` generates XML coverage at `app/build/reports/kover/report.xml`.
- `./gradlew assembleRelease` creates the minified release APK; signing requires local credentials.

Run lint and unit tests before opening a pull request.

## Coding Style & Naming Conventions

Follow Kotlin's official style (`kotlin.code.style=official`) with four-space indentation. Use `PascalCase` for classes, Composables, and files; `camelCase` for functions and properties; and `UPPER_SNAKE_CASE` for constants. Android resources use lowercase snake case, such as `ic_notification.xml`. Keep UI in reusable Composables and isolate API, storage, and capture logic in their existing packages. Prefer structured coroutine scopes and injectable dispatchers. No separate formatter is configured; use Android Studio formatting and resolve lint findings.

## Testing Guidelines

Name test classes and files `*Test.kt`, with test methods describing behavior or regressions. Add focused tests beside the matching package, mock external services with MockK or MockWebServer, and use `runTest` for coroutine timing. Every bug fix should include a regression test when practical. Kover is configured, but no minimum coverage threshold is enforced.

## Commit & Pull Request Guidelines

History follows Conventional Commit prefixes such as `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`, and `ci:`. Write an imperative, specific subject; Chinese or English is acceptable, but keep each commit focused. Pull requests should explain the problem and solution, list validation commands, link relevant issues, and include screenshots or recordings for UI or floating-window changes. Update both `README.md`/`README_EN.md` or both changelogs when user-facing behavior changes.

## Security & Configuration

Never commit `local.properties`, API keys, keystores, or credentials. Avoid logging request secrets or recognized user content. Treat changes to accessibility, screen capture, overlay permissions, and encrypted storage as security-sensitive and document their impact in the pull request.
