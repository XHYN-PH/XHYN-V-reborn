# 0.2 Hotfix 1 developer verification

- Version 0.2-hotfix1 / code 201; existing app ID and release certificate.
- Thirteen API 30 Android-view regression tests passed before packaging, covering
  navigation, downloads-page construction, separated settings and setup errors.
- Original Diagnostics and download components retained. About is a new System
  child below Game Resources. The existing Vulkan Drivers page is the sole picker.
- Graphics INI key saving, anonymous adapter inheritance and misleading startup
  failures are covered by regression checks.
- Host/config/parser/native/ARM64/shader, signature and artifact checks passed.

Android-view execution used a JVM test representation and new-helper javac classes;
no converted original code enters this APK or public source. Physical-device UI,
GPU gameplay and remote downloads still need testing. Older memory, pointer and
Vulkan-driver failures are not claimed repaired by this hotfix.

Detailed records: verification/0.2-hotfix1 and docs/issues/2026-10-30-crash-triage.md.
