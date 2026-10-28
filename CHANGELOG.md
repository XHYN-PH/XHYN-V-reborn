# Changelog

## 1.7.4 English test build — 28 October 2026

- Added an English presentation layer for the supplied 1.7.4-named APK, using the earlier English release as a reference.
- Removed the exact engine’s Chinese system-language gate and selected English text archives/fonts.
- Applied XHYN V branding and removed the outside black background from the purple V logo.
- Preserved real file paths while translating surrounding text; kept command IDs, model IDs and parser matching inputs intact.
- Published the new helper, patch, build and test code under a scoped MIT license, with translation data and verification records.
- Added a fresh-directory build driver with pinned tool checks and optional private-key signing.

7,093 host checks, 27 ARM64 instruction checks, APK preservation audits and signature verification passed. Android UI and gameplay are not device-tested. Some catalog names use readable model identifiers. The new app ID is `com.xhynph.xhynv.reborn174`; original signing identities and private data do not transfer.

## 0.1 — 16 October 2026

Initial release of XHYN V Reborn, based on the Chinese GTA V Android port.

- Translated launcher text, trainer labels and 89 touch layouts into English.
- Added linked menu and in-game language selection. English is used by default and for unsupported selections.
- Fixed the engine check that required Chinese system language, along with font and text-file selection.
- Added a DXVK configuration switch and editor. Your configuration is preserved when the switch is off.
- Added shader compiler thread and refresh-rate controls.
- Separated graphics caches by driver and renderer to avoid reusing incompatible caches.
- Improved startup errors, crash reports, restart and shutdown handling.
- Added diagnostic export and local performance recording.

Other game languages require their matching text and font files. Some menu translations are incomplete. GTA V Legacy 1.61 / build 2699 remains the supported game version.
