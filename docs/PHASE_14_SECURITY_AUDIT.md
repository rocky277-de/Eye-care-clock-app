# Phase 14 — Security & Play Protect Audit

## Scope

This phase audits the manifest, AccessibilityService, sensitive permissions, foreground-service declaration, and user disclosure flow after Android/Play Protect blocked a sideloaded APK.

## Findings

### 1. AccessibilityService — sensitive capability
The app uses `BIND_ACCESSIBILITY_SERVICE` to detect YouTube Shorts and selected Focus Mode apps.

- `canRetrieveWindowContent=true` is required by the current implementation.
- The service is not marked `isAccessibilityTool=true`, which is intentional because Eye Care is not primarily an accessibility/disability tool.
- The service is limited in code to blocking functionality.
- A prominent in-app disclosure and affirmative consent flow has now been added before opening Android Accessibility Settings.
- The disclosure explains what on-screen information is accessed, why it is used, and that the app does not send/share that accessibility data.

Google Play requires a prominent in-app disclosure and affirmative consent for non-accessibility-tool uses of AccessibilityService, plus the relevant Play Console declaration.

### 2. Other declared permissions
Current manifest permissions are:

- POST_NOTIFICATIONS — break/status notifications.
- SCHEDULE_EXACT_ALARM — accurate reminder timing when the user allows it.
- VIBRATE — haptic notification behavior.
- SYSTEM_ALERT_WINDOW — break overlay.
- FOREGROUND_SERVICE and FOREGROUND_SERVICE_SPECIAL_USE — persistent timer/overlay behavior.
- RECEIVE_BOOT_COMPLETED — timer recovery after reboot/update.

No SMS, call-log, contacts, location, microphone, camera, storage, or internet permission is declared in the current manifest.

### 3. Foreground service
The overlay service declares:

- foregroundServiceType=`specialUse`
- PROPERTY_SPECIAL_USE_FGS_SUBTYPE=`eye-care-break-overlay`

This matches Android's special-use FGS mechanism, which requires the use case to be declared for review.

### 4. Export surface
MainActivity is exported as the launcher activity. SettingsActivity, TimerReceiver, BootReceiver, and OverlayService are not exported. The AccessibilityService is protected by BIND_ACCESSIBILITY_SERVICE and is not exported.

### 5. Play Protect warning
The audit does not establish that the APK is malicious. The warning is consistent with Android/Play Protect treating sideloaded apps that request sensitive capabilities as higher-risk. AccessibilityService is the most security-sensitive capability in this app.

## Changes made in Phase 14A

1. Added a dedicated AccessibilityService disclosure dialog.
2. Added an explicit user consent action before opening Accessibility Settings.
3. Added an in-app explanation next to the Shorts blocker controls.
4. Added this audit document.
5. Did not add `isAccessibilityTool=true` as a workaround.

## Remaining release gate

The project currently targets API 34. As of August 31, 2026, Google Play requires new apps and app updates to target API 36 (Android 16); an extension can be requested to November 1, 2026. Therefore API 36 migration is a separate required release task before Play submission.

The API 36 migration must be compiled and tested rather than changing only `targetSdk` blindly. Android 15/16 behavior changes, foreground-service behavior, overlay behavior, accessibility behavior, and UI edge-to-edge behavior should be regression-tested.

## Validation checklist

- [ ] Debug build succeeds after Phase 14A changes.
- [ ] Disclosure appears before Accessibility Settings.
- [ ] Cancel keeps the user inside the app.
- [ ] Continue opens Accessibility Settings.
- [ ] Shorts blocker still works after granting accessibility access.
- [ ] Selected Focus apps still block only during an active Focus session.
- [ ] Timer overlay still works.
- [ ] Production release APK/AAB build succeeds with signing secrets.
- [ ] API 36 migration is completed and tested before Play submission.

References:
- Google Play AccessibilityService policy: https://support.google.com/googleplay/android-developer/answer/10964491
- Google Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Android foreground-service types: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Android 16 behavior changes: https://developer.android.com/about/versions/16/behavior-changes-16
