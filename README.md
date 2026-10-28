<p align="center"><img src="assets/xhyn-v.png" width="180" alt="XHYN V purple logo"></p>

# XHYN V

An English-first Android edition of the unofficial native GTA V port, maintained by **XHYN-PH**. XHYN V brings the launcher, controls, trainer menus and diagnostics into English, with a consistent interface and a public modification layer you can inspect and improve.

**[Download the 1.7.4 English test build](https://github.com/XHYN-PH/XHYN-V-reborn/releases/tag/v1.7.4-en)** · **[Build and patch sources](modifications/english-1.7.4)** · **[Verification results](verification/english-1.7.4/README.md)** · **[Previous 0.1 release](https://github.com/XHYN-PH/XHYN-V-reborn/releases/tag/v0.1)**

> Bring your own legally obtained, compatible GTA V game files, including English text and fonts. No game data, RPF archives or game-download links are included. This release is a **test build**: Android UI and gameplay have not been tested on a device for this build.

## Why use XHYN V?

The goal is to make this port easier to understand and configure without needing Chinese system settings or guessing what a launcher option means. English labels, file-management prompts, trainer text and error explanations help you set it up and report problems with useful information.

The changes are also reviewable. The new Java helpers, exact-build patches, build driver and tests are published alongside the translation data and verification records. You can see what was changed and rebuild the modification layer when you have the matching base APK and tools.

## Features

The English build preserves the supplied APK's existing features and translates their interfaces. Availability and behavior on a particular phone still require device testing.

| Area | What you can configure or access |
|---|---|
| **English interface** | Launcher pages, dialogs, accessibility labels, download/import prompts, touch-layout descriptions and trainer display text. |
| **English game defaults** | English text archives and fonts are selected. The engine's Chinese Android system-language check is removed; your phone's language is left alone. |
| **Touch and controller setup** | Layout selection/editing, controls for different game states, controller settings, camera/aim options, gyroscope and tilt-steering options. |
| **Graphics options** | Bundled/imported DXVK selection, Vulkan driver selection, BC texture-decoding settings, HDR controls, shader-preparation options and frame-rate limits. |
| **Story Mode trainer** | Interfaces for character, vehicle, weapon and world options, customization, favorites and presets. Some catalog names use readable model identifiers where an established English name is unavailable. |
| **Game-file management** | Game-folder selection, resource extraction/moving and mod-import interfaces. The launcher advertises ZIP, RAR/RAR5 and OIV imports; ASI/DLL loading is not provided. |
| **Custom radio** | Audio-import and preview interfaces for adding supported tracks to Self Radio. Device codec support and the launcher's size/duration limits apply. |
| **Diagnostics** | Error explanations, diagnostic export, performance-monitoring controls and driver/GPU checks, with original diagnostic data retained for investigation. |
| **XHYN V branding** | The purple V logo with a transparent outside background, used in the repository and APK. |

XHYN V is a modification of the original Android port. It does not claim to have created its engine, renderer or inherited launcher features, and it is not a promise of higher FPS or universal device compatibility.

## Requirements and installation

- Android **11 or newer**, an **ARM64** device and a compatible Vulkan graphics driver.
- A compatible, legally obtained GTA V Legacy data set with **English text and fonts**. The project's documented baseline is **1.61 / build 2699**; actual startup and resource compatibility for this English test build still need device validation.
- Sufficient storage for your game files, caches and any imported content.

1. Download the APK and `SHA256SUMS.txt` from the release page.
2. Install **XHYN V**. This build uses the separate app ID `com.xhynph.xhynv.reborn174` and a new signing key; it does not update the original publisher's app or the older XHYN V package.
3. Grant the requested file access and select the game folder containing `common.rpf`, `x64` and `update`.
4. Start with the default settings. No Chinese system-language setting is required.
5. Fully close and reopen the game after changing settings that require a restart, including driver or DXVK selections.

The release version is `1.7.4-en`. The supplied base was named 1.7.4 but reported 1.7.1/code 84 internally; this distinction and the exact input hashes are documented in the verification records.

## What has been checked?

- **7,093 host checks** for translation outputs, formatted errors, catalog identifiers and preservation of real file paths.
- **27 ARM64 instruction checks** for English language/font/text selectors and removal of the locale gate.
- APK rebuild, signature, DEX, resource-ID, ZIP integrity and native-library alignment checks.
- Preservation checks for original classes/method signatures, configuration values, permissions, components and the other twelve native libraries.

These checks do **not** replace running the app on Android. UI layout, storage flows, driver loading, graphics, audio/video, trainer actions, performance and Story Mode remain unverified on a device for this release. Read the [full verification report](verification/english-1.7.4/README.md) before treating it as a daily-use build.

## Source, licensing and how it works

The packaged engine is native ARM64 Android code. DXVK provides Direct3D-to-Vulkan translation, and the supplied base also contains native Vulkan components. The exact full engine/renderer source and original launcher implementation are not published here.

The newly written **1.7.4 English helper, patch, build and test code is MIT licensed**, with a [defined license scope](modifications/english-1.7.4/PROVENANCE.md). Translation inputs, model identifiers, artwork and third-party components retain their applicable terms. This does not make GTA V or the complete APK open source.

See [source information](docs/SOURCE.md), [build instructions](docs/BUILD.md), and [credits and notices](docs/CREDITS.md). The older 0.1 modification sources remain available for reference; their native offsets are not interchangeable with this build.

## Reporting a problem

Include your phone model, GPU, Android version, graphics driver, selected renderer/DXVK, game-data version and steps to reproduce the problem. Attach a diagnostic export or relevant logs after removing personal information. Say whether the problem occurs with default settings and without mods.

XHYN V is unofficial and is not affiliated with Rockstar Games or Take-Two. GTA V and its associated assets belong to their respective rights holders. Credit for the original Android port and third-party libraries belongs to their authors.
