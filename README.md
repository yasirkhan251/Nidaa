# Nidaa

Nidaa is a native Android Salah and personal-organisation companion built with Kotlin and Jetpack Compose.

## Project setup
- Package: `com.yasirkhan.nidaa`
- Minimum SDK: Android 11 (API 30)
- Compile SDK: 36
- Target SDK: 35
- JDK: 17
- UI: Jetpack Compose + Material 3
- Prayer data: AlAdhan Prayer Times API

## Included in this initial source milestone
- Native Compose application with Home, Salah, Reminders, Planner and More tabs
- Foreground location permission flow and current-location prayer-time lookup
- AlAdhan daily timings request using selected calculation method and Asr school
- Manual Jamaat/Iqamah timetable editor
- Local text editors for reminders, tasks/planner notes and journal
- Local SharedPreferences storage for these simple settings and text entries

## Important limitations
This is the first working-source milestone, not yet the finished application. Prayer times are fetched when the user taps refresh; automatic daily refresh, monthly timetable, next-prayer countdown, adhan audio, scheduled alarms, notification channels, reboot/time-zone rescheduling, Room database, DataStore, recurring reminder scheduling, calendar UI, focus sessions and backup/restore are still to be implemented and tested.

AlAdhan returns calculated prayer-start times. Mosque-specific Jamaat/Iqamah times are not supplied by that API; enter the timetable announced by your mosque.

## Open in Android Studio
Open this repository as an Android Gradle project. Use JDK 17 and install Android SDK Platform 36. If Android Studio asks to create or configure a Gradle wrapper, use Gradle 8.9. Sync, build, and run on an Android 11+ device.

The Gradle wrapper JAR is not included in this initial commit; the wrapper can be generated from Android Studio/Gradle setup.
