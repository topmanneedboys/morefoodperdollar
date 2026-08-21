# Crash root-cause checkpoint

## Known symptoms

On a Motorola Edge 2025 the live Android implementation has shown “ValuePilot keeps stopping”, hangs, lag at larger result counts, and overlay/bubble teardown glitches. No AndroidRuntime stack trace, ANR trace, tombstone, or bugreport is present in this repository, so a single root cause is not confirmed.

## Confirmed facts in the current code

- `OverlayController` owns `WindowManager` views from an Accessibility service. `addView()` is not guarded against token, permission, or lifecycle failure.
- The service schedules Handler callbacks and executor work whose completion can outlive an overlay or service lifecycle. `onDestroy()` removes the refresh callback, but not every queued callback.
- Executor submission can race executor shutdown and throw `RejectedExecutionException`.
- screenshot/OCR and navigation callbacks cross service lifecycle boundaries.
- navigation candidate parsing currently invokes deterministic analysis from a main-thread callback.
- continuously delivered Accessibility events create an intrinsically noisy, retailer-dependent input stream.

These are real defects/risk areas, but none proves which event caused the reported device crash.

## Unconfirmed hypotheses

1. `WindowManager.BadTokenException`, `SecurityException`, or `IllegalStateException` during overlay add/remove/update.
2. A stale Handler, screenshot, OCR, or executor callback touching disposed UI/service state.
3. `RejectedExecutionException` during shutdown.
4. main-thread node traversal/parsing causing an ANR that appears to the user as a crash.
5. resource pressure from repeated capture and callback accumulation.

## Evidence required from the next device run

Record app version/commit, Android build, exact time, foreground retailer, action sequence, and approximate product count. Then capture:

```sh
adb logcat -c
# reproduce once
adb logcat -b crash -d > valuepilot-crash.txt
adb logcat -v threadtime -d > valuepilot-logcat.txt
adb shell dumpsys activity processes > valuepilot-processes.txt
adb shell dumpsys window > valuepilot-window.txt
adb bugreport valuepilot-bugreport.zip
```

For a hang, collect an ANR trace/bugreport while the app is unresponsive. Do not infer a solved crash from a successful build or emulator run.
