# ValuePilot Browser v101

This Manifest V3 extension has separate Chromium and Firefox manifests. It ranks grocery and restaurant value with exact local math plus a compact, visibly labeled local-AI fallback.

```bash
npm ci
npm run check
```

`npm run check` verifies generated model files, runs all engine/model/DOM integration tests, creates clean target directories under `dist/`, and validates the Firefox build with `web-ext`.

Use `npm run build:model` after changing `training/local_ai_corpus.json`. The generated JavaScript model and Android JSON asset must stay in sync.

The extension has no background service, remote endpoint, analytics, or API key. The only extension permission is local `storage`; broad host access is required solely to inject the user-facing comparison overlay on shopping/menu pages.
