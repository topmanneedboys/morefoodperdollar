# ValuePilot Android v101

Open this directory in a current Android Studio, or run the repository release workflow.

Requirements:

- Android SDK/target API 36
- JDK 17 and Gradle 9.5
- Android 6.0+ for accessibility scanning
- Android 11+ for the optional screenshot OCR fallback

The floating overlay supports exact value modes, AI food/meat estimates, a maximum-spend filter, Food only, No pork, loaded-content scanning, explicit lazy-list traversal, and bundled on-device OCR.

The app declares no INTERNET permission. `local_ai_model.json` is packaged in assets, and `com.google.mlkit:text-recognition` supplies the bundled recognizer rather than a downloadable Play Services model.
