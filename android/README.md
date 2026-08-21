# ValuePilot Android v101.1

Open this directory in a current Android Studio, or run the repository release workflow.

Requirements:

- Android SDK/target API 36
- JDK 17 and Gradle 9.5
- Android 6.0+ for accessibility scanning
- Android 11+ for the optional screenshot OCR fallback

The minimized UI is one VP bubble. Opening it hides the bubble and shows a draggable consumer bottom sheet with contextual match count, relevant ranking choices, Filters, Rescan, readable virtualized rows, and whole-row exact-product reopening.

Accessibility collection is automatic for newly visible product cards. Event signatures are coalesced, scanning is one bounded tree pass, unchanged fingerprints are ignored, changed snapshots parse in the background, and results live in a query/store/page-scoped incremental store. Explicit off-screen collection and bundled on-device OCR are advanced controls under Filters.

Build and verify:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk
```

The merged app declares neither INTERNET nor ACCESS_NETWORK_STATE. `local_ai_model.json` is packaged in assets, and `com.google.mlkit:text-recognition` supplies the bundled recognizer rather than a downloadable Play Services model.

Automated fixtures cover 20/60/100/160/250/500 products. Physical Motorola Edge 2025 validation remains required; see the root state and performance documents.
