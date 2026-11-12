# NotchFlow implementation update

## Changes made

1. **Island motion and origin** — IslandPill now uses the requested `spring(dampingRatio = 0.7f, stiffness = 400f)` for visibility, expansion, drag settling, and vertical activity transitions. Its scale TransformOrigin is centered at `(0.5f, 0.5f)`.
2. **Incoming-call overlay suppression** — OverlayService observes the existing READ_PHONE_STATE call-state tracker. It removes the overlay window while `CALL_STATE_RINGING` is active and reattaches it when the call becomes active or idle. It does not interfere with the dialer or call controls.
3. **Call timer sizing** — The expanded call row uses `animateContentSize()` and the active call timer remains rendered in the island.
4. **Notification previews** — The existing user-authorized NotificationListenerService now reads notification title/text and exposes the latest item to IslandController. A notification activity can be expanded in the island. Android does not provide a reliable universal API for suppressing another app's notification/heads-up UI, so this implementation does not cancel or hide the source notification.
5. **Floating glass navigation** — The main bottom navigation is now a floating, inset, circular bar with a translucent black surface and subtle white border.

## Permissions and privacy

- Notification previews require the user to enable NotchFlow under Android's Notification access settings.
- Call-state suppression requires the existing READ_PHONE_STATE permission.
- Notification title/text are held in process memory for the latest preview; they are not uploaded. The project still has no INTERNET permission.

## Build verification

A local Gradle build could not be run in this environment because the supplied project does not include `gradlew` and no system `gradle` executable is installed. Please run `./gradlew :app:assembleDebug` in Android Studio or a Gradle-enabled environment to verify against your installed Android SDK.

---

## UI/UX structural fixes (slim island pass)

1. **Slim, wide expanded state** – The expanded island is now `screenWidth − 2 × 14dp` wide (`OverlayGeometry` / `EXPANDED_SIDE_MARGIN_DP`) and thin: music 96dp, call 64dp, charging 62dp, timer/stopwatch 72dp, notification 78dp. Every panel (`MusicExpanded`, `CallExpanded`, `ChargingExpanded`, `TimerExpanded`, `StopwatchExpanded`, `NotificationExpanded`) was re-laid-out as a single wide row that keeps the centre (camera) clear. Page dots moved to a vertical strip on the right edge.
2. **Gesture routing** – tap / vertical swipe / horizontal swipe are handled by ONE `awaitEachGesture` detector on the absolute root `Box` of `IslandPill`. Swipe down on the compact pill switches activity, swipe up collapses (or dismisses when compact, if enabled in settings).
3. **Call symmetry + End Call** – `CompactCall`: `[call icon 20dp] [timer, weight(1f), centred] [Spacer 20dp]`. `CallExpanded` has the timer on the left and a red 44dp `IslandIconButton` (End call) on the right. New `IslandActions.endCall()` → `CallTracker.endCall()` uses `TelecomManager.endCall()` (needs the new `ANSWER_PHONE_CALLS` permission, requested together with `READ_PHONE_STATE`); without it, the Phone app opens instead.
4. **Window centring** – `OverlayService` pins `gravity = TOP | CENTER_HORIZONTAL`, `x = 0` at creation and on every `updateViewLayout`. Window widths are parity-matched to the screen so the centre never lands on a half pixel.
5. **Animation physics** – one shared `islandSpring()` = `spring(dampingRatio = 0.7f, stiffness = 400f)` drives visibility, expand/collapse, height, drag settle and activity transitions. All island layers explicitly use `TransformOrigin(0.5f, 0.5f)`; pill width is parity-matched to the window so the spring overshoot is symmetric.

Not compiled in the authoring environment (no Gradle / Android SDK there) – please run `./gradlew :app:assembleDebug`.

## Liquid-glass bottom bar
`BottomBar` (AppRoot.kt) is now a floating pill: 20dp side / 14dp bottom margins above the nav bar, `CircleShape`, `Color.Black.copy(alpha = 0.6f)` fill, a faint top-lit white sheen gradient, and `BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))`. The selected tab is a translucent white capsule. `Modifier.blur` is intentionally not used (it would blur the bar's own icons, not the content behind it).
