# Phase 13 — Release Validation Test Matrix

Use this checklist on a real Android 12–14+ device before treating v1.4 as release-ready.

## Build / CI
- [ ] GitHub Actions completes the debug compile/validation job.
- [ ] If release signing secrets are configured, signed APK and AAB are generated.
- [ ] APK installs without replacing or corrupting existing settings.
- [ ] App version reports 1.4 / versionCode 5.

## Timer
- [ ] Start with default 20 min work interval.
- [ ] Pause preserves the remaining time.
- [ ] Resume continues from the saved remaining time.
- [ ] Stop cancels the active timer and resets to the configured work duration.
- [ ] A stale/duplicate alarm does not open an early break.
- [ ] Changing work/rest settings persists after app restart.

## Background / Recovery
- [ ] Lock the screen while the timer is running; timer continues.
- [ ] Background the app; timer state is restored when reopened.
- [ ] Reboot while a work timer is active; recovery resumes without counting reboot downtime as focus.
- [ ] Update/reinstall the app while a running timer exists and verify package-replacement recovery behavior.
- [ ] Test with battery optimization enabled and disabled.
- [ ] Test with exact-alarm access allowed and not allowed.

## Break / Overlay
- [ ] Break overlay appears when the work interval expires and overlay permission is granted.
- [ ] Start Rest begins the countdown.
- [ ] Skip records a skipped break.
- [ ] Completing a rest records a completed break.
- [ ] Repeated Start/Skip taps do not double-count.
- [ ] Overlay position remains saved when the setting is enabled.
- [ ] Notification actions Start Rest and Skip work.

## Focus Mode
- [ ] Focus time counts only during work.
- [ ] Pausing/stopping ends the active focus interval correctly.
- [ ] Focus statistics remain correct across midnight.
- [ ] Long-break selection works after the configured session count.
- [ ] Disabled Focus Mode stops further focus accumulation.

## Daily Checklist
- [ ] Add task.
- [ ] Complete/uncomplete task.
- [ ] Delete task.
- [ ] Clear completed tasks.
- [ ] Tasks survive app restart.
- [ ] Today's tasks remain separated from another date.

## Shorts / Accessibility
- [ ] Accessibility service can be enabled.
- [ ] Selected blocked apps are persisted.
- [ ] Blocked-attempt counts update during Focus Mode.
- [ ] Disabling the blocker stops its blocking behavior.

## Release gate
Do not call the release validated until the critical Timer, Background/Recovery, Break/Overlay, and Focus Mode checks above pass on the target Android device.
