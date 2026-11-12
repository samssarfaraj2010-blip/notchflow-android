# NotchFlow (formerly Dynamic Island) for Android

_Not just a notch. A whole new way to interact._

Original Kotlin + Jetpack Compose app. Draws a Dynamic-Island-style pill as a system overlay with Music, Charging, Timer and Stopwatch activities. Fully offline: **no INTERNET permission**.

## Project structure
```
DynamicIsland/
├─ settings.gradle.kts            project name + modules
├─ build.gradle.kts               plugin versions (AGP 8.7.3, Kotlin 2.0.21)
├─ gradle.properties              Gradle/AndroidX flags
├─ gradle/wrapper/gradle-wrapper.properties   Gradle 8.9
├─ .github/workflows/build-apk.yml            builds an APK on GitHub (no Android Studio needed)
└─ app/
   ├─ build.gradle.kts            app config + dependencies (minSdk 30, targetSdk 35)
   └─ src/main/
      ├─ AndroidManifest.xml      permissions, services, receivers (non-exported unless required)
      ├─ res/                     theme, strings, launcher icon, notification icon
      └─ java/com/dynamicisland/
         ├─ DynamicIslandApp.kt, MainActivity.kt, Permissions.kt
         ├─ core/     Models, SettingsRepository, IslandRuntime, IslandController (activity state + priority),
         │            Clocks (Timer/Stopwatch), BatteryTracker, MediaTracker, Receivers
         ├─ overlay/  OverlayService (foreground service + overlay window), OverlayGeometry, IslandHostView
         └─ ui/       theme/, components/, island/ (IslandPill = animation engine, IslandContent), screens/
```

## Put it on GitHub (use a computer browser)
1. Unzip `DynamicIsland.zip`. Open the unzipped `DynamicIsland` folder.
2. On github.com open your empty repository, click **Add file → Upload files**.
3. Select **everything inside** the `DynamicIsland` folder (including the hidden `.github` and `.gitignore`) and drag it into the page. Folders keep their structure.
4. Wait for the upload bar, then click **Commit changes**.
5. Verify: the repo root must show `app`, `gradle`, `.github`, `build.gradle.kts`, `settings.gradle.kts`. Open `app/src/main/java/com/dynamicisland/core` and confirm the .kt files are there.

## Get the APK, option A: GitHub builds it
Repo → **Actions** → "Build debug APK" → latest run → download **DynamicIsland-debug-apk** at the bottom → unzip → `app-debug.apk`. (If the run fails, open it and send me the red error text.)

## Option B: Android Studio
1. Install Android Studio (current stable). **File → Open** → pick the project folder (or **Get from VCS** with your repo URL).
2. Let Gradle sync finish (first time downloads a lot). If it asks to use a different Gradle, accept.
3. **Build → Build Bundle(s)/APK(s) → Build APK(s)**. Click **locate** when done.

## Install on the phone
Copy `app-debug.apk` to the phone, open it, allow "install unknown apps" for your file manager/browser when asked. Or enable USB debugging and press Run in Android Studio.

## First run
1. Finish onboarding, allow **Display over other apps**.
2. For Music: grant **media access** (notification access). Android 13+ blocks this for sideloaded apps until you open **App info → ⋮ → Allow restricted settings**.
3. Turn the island on from Home. Start the timer from Features, or play music.

## Honest status
Written without access to an Android SDK/device, so it has **not been compiled or run**. Expect to fix a few compile errors on first build; send me the log. See the checklist below for what must be verified on the phone.

### Known limitations
- Overlay windows don't appear on the lock screen or over system UI such as the notification shade (Android restriction).
- Full-screen video/game hiding relies on the overlay window's status-bar inset; untested, may not trigger on every app. Landscape is hidden by default as the safe fallback.
- The overlay window resizes with the pill during animation (needed so empty areas never block touches); verify smoothness at 120 Hz.
- Timer completion: vibration only (no sound).
- Not a replacement for Nothing OS SystemUI.

### Device test checklist (none performed yet)
Fresh install, permission granted/denied/revoked, music play/pause/track change/missing artwork/app closed, charger in/out/full, timer start/pause/resume/finish with screen off, stopwatch start/pause/resume/reset, multiple activities and swiping, rotation, fullscreen video/game, rapid taps/swipes, process kill, reboot, settings persistence and reset.


## NotchFlow engineering update (2026-11-11)

This update keeps the existing project architecture and applies targeted fixes rather than replacing the app.

- The overlay keeps its top edge aligned to the cutout-derived Y position during expansion; horizontal growth is centred with `Gravity.CENTER_HORIZONTAL`, avoiding a changing X anchor as width changes.
- Music horizontal gestures now follow the requested mapping: swipe left = next track; swipe right = previous track. Swipe down switches activity; swipe up collapses an expanded island (and can dismiss a dismissible compact activity).
- Charging expansion is redesigned as a wide, slim horizontal panel (84dp target height) with a horizontal battery progress indicator.
- Ringing calls are not drawn over the native incoming-call UI. Once the call becomes active, the island can show a compact ongoing-call timer. The expanded call panel opens the native Phone app for call management.

### Platform limitations and remaining implementation work

An ordinary third-party Android app cannot reliably determine every moment when the Google Phone in-call screen is foregrounded without using an accessibility service with window/event access or becoming the default dialer. This project deliberately does not broaden accessibility access to inspect other apps. Therefore the active-call pill may also appear while the native in-call screen is visible.

Ending a cellular call directly requires privileged/default-dialer or supported `InCallService` capabilities; the app does not display a fake End Call control. Caller names are not available from the current phone-state-only permission model.

Independent call-pill X/Y/width/height/corner-radius controls and their live preview are not yet implemented. Bluetooth connection activity, notification-content activities, a true shared-element music-app-to-island morph, and the in-app glass footer also remain unimplemented. Existing media-session metadata/control support and the existing timer, stopwatch, charging and music activities are preserved.

### Verification

Source-level changes were reviewed and the ZIP archive was integrity-checked. An Android Gradle build and device tests were not run in this environment because the project has no `gradlew` script or wrapper JAR and no installed Gradle distribution / Android SDK. Test on the Nothing Phone (3a) Pro before treating this build as release-ready.
