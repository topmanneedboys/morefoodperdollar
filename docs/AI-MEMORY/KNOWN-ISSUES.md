# Known Issues

Updated: 2026-08-20

- Motorola Edge 2025 performance and live shopping-app navigation are not yet device-verified. This is the remaining milestone gate.
- Accessibility semantics vary by third-party app. Hidden text, changing paths, ambiguous duplicates, and nonstandard scrolling can cause safe navigation refusal.
- Off-screen navigation is bounded to 90 steps in each direction and does not restore the original position after a failed attempt.
- Secure windows can block OCR screenshots.
- The APK is debug-signed.
- The uploaded v101 and local v101.1 APKs have different debug certificates, so v101 must be uninstalled before installing this testing build.
- Lint notes API 37 and RecyclerView 1.4.0 availability; the verified build currently uses compile/target 36 and RecyclerView 1.2.1.

See root `KNOWN_ISSUES.md` for details. No backend or iOS work has started.
