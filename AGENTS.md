# Repository Guidelines

## Project Overview

This repository contains the Santevi Android application. It is a single-module Android project: application code is in `app/src/main/java/com/example/`, organized into `data/` and `ui/`; Android resources and assets are in `app/src/main/res/`. Unit tests are in `app/src/test/`, and device tests are in `app/src/androidTest/`.

## Development Practices

- Use Kotlin and Jetpack Compose for UI development. Follow Material Design 3 and the existing Santevi emerald color palette; inspect the theme and nearby components before introducing styles.
- Preserve existing Firebase Authentication, Cloud Firestore, Cloud Storage, and Firebase Cloud Messaging integrations. Preserve working screens, navigation, and app behavior when making changes.
- Inspect relevant source, resource, and configuration files before editing. Keep changes scoped to the requested feature and explain which files changed and why when reporting completed work.
- Do not delete files, change dependencies, or modify Firebase security-sensitive configuration (including rules, service configuration, or authentication settings) without first asking the user.
- Never expose API keys, credentials, tokens, or private configuration values in code, logs, documentation, or responses. Keep secrets out of version control.

## Build and Run

Open the project root in Android Studio, sync Gradle, select the `app` run configuration, and run it on an emulator or connected device. Gradle 9.6.0 is specified in `gradle/wrapper/gradle-wrapper.properties`, but wrapper launchers and the wrapper JAR are unavailable. Use Android Studio for Gradle sync, compilation, and running the app when the wrapper is unavailable. If a compatible system Gradle is installed, `gradle assembleDebug` builds the debug APK, `gradle test` runs local tests, and `gradle connectedAndroidTest` runs device tests.

## Code and Tests

Follow the official Kotlin style configured in `gradle.properties` and match the existing two-space indentation in Kotlin and Gradle Kotlin DSL files. Use `PascalCase` for classes and composables, `camelCase` for functions and properties, and existing suffix patterns such as `Screen`, `ViewModel`, and `Repository`. Tests use JUnit, AndroidX test libraries, and Robolectric; place local tests in `app/src/test/` and emulator-dependent tests in `app/src/androidTest/`. No separate formatter or linter configuration is present.

## Changes and Contributions

Use concise, imperative commit subjects (for example, `Improve sign-in validation`); no established history-based convention is available. Pull requests should describe the change, summarize validation, link related issues when relevant, and include screenshots for UI changes.
