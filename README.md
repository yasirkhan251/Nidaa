# Nidaa

A native Android Salah and personal productivity companion.

## Technology
- Kotlin
- Jetpack Compose + Material 3
- Android Gradle Plugin / Gradle Kotlin DSL
- AlAdhan Prayer Times API
- Google Play Services Location

## Current implementation
- Native Compose dashboard and navigation
- Live prayer-time fetching from AlAdhan using device location (with a manual-coordinate fallback)
- Calculation method and Asr school settings
- Editable local Jamaat timetable
- Local reminders and task list
- Notes and focus timer screens

## Important
AlAdhan provides calculated prayer-start times, not mosque-specific Iqamah times. Jamaat values in this app are user-entered local settings until a mosque-specific source is configured.

## Open in Android Studio
Open this repository as a Gradle project. Android Studio may offer to generate/sync the Gradle wrapper on first import. Use JDK 17 and Android SDK 36. Minimum Android version: Android 11 (API 30).

## Permissions
The app requests internet and foreground location access. Notification permission is requested on Android 13+. Exact alarm scheduling may require additional user approval on Android 12+.
