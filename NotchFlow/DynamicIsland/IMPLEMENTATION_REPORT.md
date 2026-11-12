# NotchFlow implementation report — 2026-11-11

## Applied in this source update
- Kept the uploaded Android Studio project and its existing architecture.
- Kept the cutout-derived top Y stable while the window resizes; horizontal growth remains centred by `Gravity.CENTER_HORIZONTAL` rather than changing X offsets.
- Corrected horizontal music gesture mapping: left = next, right = previous. Down = next activity; up = collapse when expanded.
- Redesigned charging expanded UI to a 84dp wide horizontal panel with a horizontal battery progress bar; charging status, percentage, source and temperature remain.
- Prevented the island from competing with the native incoming/ringing call screen; active calls are shown compactly.
- Added an honest `Open Phone` action to the expanded call panel instead of a non-functional End Call button.
- Updated settings gesture description and README.

## Not yet implemented
- Distinguishing Google Phone foreground in-call screen from a background active call. This needs broader accessibility event/window access or default-dialer integration; neither is enabled here.
- Direct End Call action; requires privileged/default-dialer/InCallService capability.
- Independent call-pill X/Y/width/height/corner-radius settings and live preview.
- Bluetooth connection activity.
- Notification-content activity cards.
- Shared-element music-app-to-island artwork morph; current MediaSession tracking remains.
- In-app glass footer effect.
- Full device-level performance and gesture regression testing.

## Build/test status
- Android Gradle build: NOT RUN. No Gradle executable/distribution, wrapper script/JAR, or Android SDK is available in this environment.
- Device tests: NOT RUN.
- ZIP integrity: checked after packaging.

This is an engineering update, not a release-ready claim. Android behavior must be verified on the target device.
