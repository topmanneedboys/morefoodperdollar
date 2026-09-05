# ValuePilot device validation checklist

Status: `DEVICE PENDING` (2026-09-05)

This is the manual protocol for the current offline Android milestone. CI proves deterministic
logic, packaging, privacy and signing; it cannot prove camera ergonomics, OCR readability, focus
behavior on a real device, TalkBack output, frame timing or store-floor speed. No physical-device
run is claimed until the checklist below is executed and its evidence is recorded.

## Preconditions

1. Use the target Motorola Edge 2025 when available, or record the exact substitute model, Android
   version and display scale.
2. Install the exact debug artifact produced by the verified workflow (or a locally built debug APK)
   and record its commit SHA. The artifact must remain offline; ValuePilot must not be granted a
   network permission or asked to sign in.
3. Start from a clean app state for the first-launch pass, then run a second pass with the same
   device-only history retained. Do not use real personal receipts or accounts in captured evidence.
4. Prepare three ordinary packaged products, two shelf-label/price photos, and a short list such as
   `milk, eggs, rice, chicken, bananas`. Use clearly fictional values when a demo path is needed.

## Consumer flows

- **First value:** launch with no network. Confirm the primary actions are understandable, no
  account/location/contribution prompt appears, and the user can reach Scan & Compare or Good Price
  within roughly one minute.
- **Manual Compare Here:** enter two exact observations (name, package quantity, ISO currency and
  price), select current/member basis as appropriate, confirm like-for-like, and verify unit math
  and unknown/error copy are readable. Change one field and confirm the old result is cleared.
- **Barcode identity handoff:** tap the user-triggered barcode action, choose a catalog identity,
  verify only the name is inserted, and verify the populated block receives focus after the dialog
  closes. Package quantity, price and store facts must still be entered/reviewed manually.
- **Photo/OCR review:** import or capture a shelf/product photo, verify bounded `Review only` rows,
  choose `Add selected` and, when offered, `Add with detected details`. After dismissal, verify the
  first newly added editor block is focused, existing entries are unchanged, and exact review is
  still required. Cancel, retry and rotate during processing; stale results must not edit the draft.
- **Good Price:** use barcode identity lookup, verify the manual product field is focused after the
  identity choice, then enter exact quantity/currency/price and confirm the result says when history
  is insufficient instead of guessing.
- **Home and repeat use:** verify the visible sample remains unmistakably demo-only, partial/no-
  coverage totals remain different from complete totals, and `Shop again` preserves a prior explicit
  refinement. Verify private-memory/history copy says device-only and not live store pricing.
- **Saved and deletion:** confirm identity/history actions do not imply stock or current offers;
  clear private history and verify any dependent result/share card disappears.

## Accessibility and performance

- Run TalkBack through first launch, barcode/photo review, exact entry, result cards and clear/error
  states. Confirm focus order reaches the newly populated field and live-region updates are announced
  once without decorative duplication.
- Repeat the Compare/Home flows at the device's largest supported font and display size. Check that
  action labels, unknown states, quantities and currency values do not clip or require horizontal
  scrolling.
- Exercise five consecutive barcode/photo actions and a representative 20/60/100/160/250/500-item
  synthetic list where the harness supports it. Record frame drops, visible lag, memory pressure and
  whether cancellation returns to an interactive idle state. Use `PERFORMANCE_BUDGETS.md` for the
  numeric targets; do not call a straight-line distance a travel time.

## Privacy and crash evidence

Record the exact commands/output in a private test artifact (not in the repository):

```text
adb devices
adb shell dumpsys package com.valuepilot.app | findstr /I "INTERNET ACCESS_NETWORK_STATE CAMERA"
adb logcat -c
adb logcat -b crash -d > valuepilot-crash.txt
adb logcat -v threadtime -d > valuepilot-logcat.txt
adb shell dumpsys activity processes > valuepilot-processes.txt
adb shell dumpsys window > valuepilot-window.txt
```

If a crash or stale callback appears, preserve the logs before retrying. Stop the run for any crash,
unexpected network prompt, fake live-price/availability claim, stale draft mutation, inaccessible
focus order, clipped exact value, or result that survives deletion of the private memory that
supported it. Follow `CRASH_ROOT_CAUSE.md` for expanded collection when needed.

## Result record

For each run record: date/time, device/Android build, APK commit SHA, network state, fresh/retained
history state, flows exercised, observed focus/latency/accessibility behavior, crash-log outcome and
any screenshots. Mark each item `PASS`, `FAIL` or `NOT RUN`; a green CI run never substitutes for a
`PASS` here. Do not mark Scan & Compare or launch readiness complete until the physical evidence is
reviewed.
