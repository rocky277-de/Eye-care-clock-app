# Security build variants

Eye Care now has two Android build variants:

- **standard**: normal timer/focus/checklist/overlay functionality. It does not package the AccessibilityService.
- **shorts**: includes the optional YouTube Shorts and selected-app blocking AccessibilityService.

The default CI workflow builds the standard variant for ordinary sideload testing. This reduces unnecessary sensitive capabilities in the APK used for normal testing.

The Shorts variant remains available for users who explicitly need that feature. It still requires the existing in-app disclosure and Android Accessibility consent flow.

This does not guarantee that every security scanner will accept a sideloaded APK. Google Play Protect can still evaluate apps from outside Google Play. Keep Play Protect enabled and use the standard build when the AccessibilityService is not needed.
