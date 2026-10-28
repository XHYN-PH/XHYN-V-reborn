# XHYN V 1.7.4 English test build

Download: XHYN-V-1.7.4-English.apk (88,288,229 bytes; 84.2 MiB).
SHA-256: `60d43f0c73da5647153ada2b3e5af7659e53ee79411a40e7e0a2ef5d72ebb586`.
App name: **XHYN V**. Version: `1.7.4-en` (code 84). Android 11+ / ARM64; target SDK 36.
Application ID: `com.xhynph.xhynv.reborn174`.

The input was the user-supplied APK named GTAV-Master-1.7.4. Its original manifest actually reported version 1.7.1/code 84. The XHYN-PH/XHYN-V-reborn v0.1 release was downloaded, checked against its published SHA256SUMS and used as the English reference.

## Changes

- English presentation dictionary with **4,454 entries**. All 2,349 distinct Chinese launcher literals and 281 additional layout strings found in the inventory have English outputs. Translations are applied at 432 UI display, dialog, accessibility, notification, canvas and text-measurement call sites. Original command IDs, parser matching strings and native error inputs remain available internally.
- Trainer catalog display/search text is adapted before the existing Java catalog parser. As in the reference APK, entries lacking established English display names use readable model identifiers. 1,228 such labels were derived from this binary's label/model pairs; these are readable identifiers, not claims of official localized character/prop names.
- Both calls to the exact engine's Android Chinese-locale gate now accept the device locale. Android's actual system locale is not changed. Native language getters select English ID 1, supported-language checks accept English, text selectors use `american` and font selectors use `efigs`. An isolated native HUD test notification is also in English.
- The repository’s purple V logo now has a transparent outer background. All 11 launcher/foreground/in-app logo resources preserve its dimensions, alpha and appearance on both black and white backgrounds, and all four corners are transparent. Android’s resource compiler may normalize hidden RGB values under fully transparent pixels. Adaptive icons use a transparent background and keep the complete logo inside an inset.
- App label is XHYN V. Chinese HTTP language preferences and Chinese-specific number formatting in the mod browser were changed to English/US presentation. Default INI comments, layout metadata and the included FFmpeg build notes are in English; default configuration keys and values are preserved.
- A regression check caught translation of Chinese words inside displayed filesystem paths. The helper now protects real paths, including paths with spaces; the added check passes. Editable user text is preserved by the widget adapter.

## Verification

- **7,093 host checks passed** against the actual Java translation core and JSON parser. These cover known launcher/layout strings, dictionary outputs, formatted native errors, dynamic text, catalog command/model preservation and path preservation. They do not render Android widgets.
- **27 actual ARM64 instruction checks passed** in Unicorn for English selectors, both bypassed locale calls and supported-language behavior.
- APK reconstruction, all three DEX headers/checksums/signatures, unique class definitions, new hook method resolution, public resource IDs and ZIP CRC checks passed. All 2,807 original classes and all original method signatures are retained; seven helper classes are added.
- The final structural audit passed 67,922 assertions, including recursive layout values. These are structural/preservation assertions, not that many separate gameplay tests.
- Only `libgtav.so` differs among 13 native libraries, and every changed byte is accounted for by the guarded English patch. The other 12 libraries, including DXVK/native Vulkan, are byte-for-byte preserved. ELF headers, segments, dynamic/relocation tables and unwind metadata are preserved. Native ELF segments and uncompressed APK library entries use 16 KiB alignment.
- Existing assets outside the intended localization files, embedded DXVK packages and their metadata, Android permissions/activities/services, SDK requirements and all INI settings were checked for preservation.
- The final APK's v3 signature verifies with a new local RSA test key. The APK is a separate application, so it does not replace the original publisher's installation or inherit its private app data. The signing material is retained privately in the task workspace and is excluded from the patch bundle.
- The saved exact-build native patch script was rerun from the original engine in an isolated reproduction directory and produced identical patched bytes.

## Remaining validation

There is no connected Android device/emulator available here. Android class verification at runtime, launching the UI, on-device text layout, storage permission flows, GPU/driver loading, startup with real game resources, audio/video, trainer actions, performance and Story Mode have **not** been tested. This is a signed **test build**, not a claim that all bugs or regressions have been eliminated.

English game text/font resources are still required from the user's own compatible GTA V files. This APK contains no game data and does not translate or redistribute proprietary resource archives. Original internal strings and raw diagnostics may remain Chinese so their parsing and provenance are preserved; user-entered content and actual filenames are not rewritten. Normal interface text is translated at its display boundary.

Original license notices and developer credits are retained. The FFmpeg document is a translation of the supplied build notes; its historical test results are not new results from this task.

The release includes a public source ZIP from the tagged repository commit. Newly written helper, patch, build and test code is MIT licensed under the stated scope; proprietary engine/base inputs and private signing material are excluded. The public build driver was exercised from a fresh decode and passed the host, ARM64, signing and final APK audits. See the release page for artifact checksums.
