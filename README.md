<p align="center"><img src="assets/xhyn-v.png" width="180" alt="XHYN V purple logo"></p>

# XHYN V

**XHYN V is a community-maintained Android build of the unofficial GTA V port**, based on the original Chinese Android project and maintained by **XHYN-PH**. It brings game setup, touch controls, graphics options, a Story Mode trainer and file management together in one Android app.

**[Download XHYN V](https://github.com/XHYN-PH/XHYN-V-reborn/releases/tag/v1.7.4-en)** · **[Buy GTA V for PC](https://store.rockstargames.com/game/buy-gta-v)** · **[Source information](docs/SOURCE.md)**

## What does this project provide?

The APK provides the Android launcher, native engine/runtime libraries and XHYN V's modifications. You select your own compatible game-data folder, configure the controls and renderer, and launch from the app.

**XHYN V does not supply GTA V game files, RPF archives or links to download them.** You must own the game and provide compatible files yourself.

[**Buy the real game from the official Rockstar Store →**](https://store.rockstargames.com/game/buy-gta-v)

Rockstar's PC purchase includes **GTAV Enhanced and GTAV Legacy**. This port expects compatible **Legacy** files; ownership does not make every game version or Enhanced data set compatible with it. Follow the version requirements for the build you install.

## Is this a native port or an emulator?

For this project, **native describes how the game CPU code runs**. The inspected APK contains a 64-bit ARM64 Android game library, `libgtav.so`, loaded through Android/SDL's native entry point. Package inspection found no Windows game executable or bundled Wine, Box64, FEX or QEMU runtime. See the [technical explanation and package inventory](docs/SOURCE.md).

**DXVK handles graphics, not CPU instructions.** It translates Direct3D 11/DXGI calls into Vulkan. DXVK is often used with Wine, but its own [DXVK Native documentation](https://github.com/doitsujin/dxvk#dxvk-native) explicitly supports native game/application ports **without Wine**. Finding DXVK in an APK does not, by itself, make that APK a CPU emulator.

On the DXVK rendering path, the distinction looks like this:

```text
Android/SDL → ARM64 game engine
                  └─ Direct3D 11/DXGI → DXVK → Vulkan → GPU driver
```

Likewise, **working on one GPU family does not prove emulation**. Native games can still depend on particular driver features, texture formats and Vulkan behavior. GPU compatibility and CPU execution are separate questions.

The precise description is **a native ARM64 Android port using graphics API translation where needed**. That does not mean the entire renderer was rewritten directly for Vulkan, that the complete engine source is public, or that Rockstar made an official Android release. Source availability, authorship and licensing are separate from whether code executes natively.

## Features

- **Game setup:** select a game folder, manage resources, and access archive extraction/moving tools.
- **Touch and controller controls:** selectable/editable layouts, controls for different game states, camera/aim options, gyroscope and tilt-steering settings.
- **Graphics configuration:** bundled/imported DXVK selection, Vulkan driver options, BC texture decoding, HDR, shader preparation and frame-rate limits.
- **Story Mode trainer:** character, vehicle, weapon and world options, vehicle customization, favorites and presets.
- **Mods and custom radio:** resource-mod import interfaces, supported audio importing, previews and Self Radio management. ASI/DLL loading is not provided.
- **Diagnostics:** readable error explanations, local reports, performance-monitoring options and diagnostic export.
- **XHYN V interface:** English menus and trainer presentation, the purple V branding, and no Chinese system-language requirement. Some catalog entries use readable model identifiers.

These features build on the supplied Android port; XHYN-PH maintains this edition and its modifications. Compatibility and performance depend on your device, driver, settings and game data.

## Requirements and installation

- **Android 11 or newer**, an **ARM64** device and a compatible **Vulkan** driver.
- Your own compatible **GTA V Legacy** files, including English text and fonts. The project's documented baseline is **1.61 / build 2699**.
- Enough storage for the game, caches and imported content.

1. Download and install the APK from the [release page](https://github.com/XHYN-PH/XHYN-V-reborn/releases/tag/v1.7.4-en).
2. Grant the requested file access and select the folder containing `common.rpf`, `x64` and `update`.
3. Start with the default settings. Fully close and reopen the game after changing settings that require a restart, such as the driver or DXVK runtime.

The current release is a **development/test build**. It uses the separate app ID `com.xhynph.xhynv.reborn174` and does not update the original publisher's APK or the older XHYN V package. Device compatibility and gameplay are still being established; no universal FPS or stability promise is made.

## Source and credits

The [modification sources and build instructions](modifications/english-1.7.4) are public. Newly written helper, patch, build and test code is **MIT licensed** under its [defined scope](modifications/english-1.7.4/PROVENANCE.md). The complete original game engine, original launcher implementation and exact custom renderer source are not published here. This repository does not make GTA V or the whole APK open source.

Credit for the original Android port, engine and third-party components belongs to their authors. See [source information](docs/SOURCE.md) and [credits/notices](docs/CREDITS.md).

For problems, include your phone/GPU, Android version, driver, renderer, game-data version and reproduction steps. Attach relevant diagnostics after removing personal information.

This project is unofficial and is not affiliated with Rockstar Games or Take-Two. GTA V and its associated assets belong to their respective rights holders.
