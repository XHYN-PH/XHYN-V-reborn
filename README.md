<p align="center"><img src="assets/xhyn-v.png" width="180" alt="XHYN V purple logo"></p>

# XHYN V

**XHYN V is our community-maintained GTA V project for Android**, maintained by **XHYN-PH**. We bring game setup, touch controls, graphics configuration, a Story Mode trainer and file management together in one Android app. Our work focuses on the launcher experience, translations, branding, configuration and language fixes.

**[Download XHYN V](https://github.com/XHYN-PH/XHYN-V-reborn/releases/tag/v1.7.4-en)** · **[Buy GTA V for PC](https://store.rockstargames.com/game/buy-gta-v)** · **[Source information](docs/SOURCE.md)**

## What does this project provide?

XHYN V combines an Android launcher and native ARM64 runtime with our interface, branding and language updates. Select your compatible game-data folder, configure your controls and graphics, and launch directly from the app.

**XHYN V does not supply GTA V game files, RPF archives or links to download them.** You must own the game and provide compatible files yourself.

[**Buy the real game from the official Rockstar Store →**](https://store.rockstargames.com/game/buy-gta-v)

Rockstar's PC purchase includes **GTAV Enhanced and GTAV Legacy**. This port expects compatible **Legacy** files; ownership does not make every game version or Enhanced data set compatible with it. Follow the version requirements for the build you install.

## Is this a native port or an emulator?

**XHYN V runs its game engine as native ARM64 Android code.** Our build loads the 64-bit `libgtav.so` game library through Android/SDL's native entry point. Native describes the CPU code that runs the game; graphics translation is a separate part of the architecture. See [how our runtime works](docs/SOURCE.md).

**We use DXVK to translate graphics calls.** On the DXVK path, Direct3D 11/DXGI calls are translated into Vulkan while the game code runs as ARM64 instructions. [DXVK Native](https://github.com/doitsujin/dxvk#dxvk-native) supports native game ports **without Wine**. Using a graphics compatibility layer does not turn native game code into CPU emulation.

Our DXVK rendering path:

```text
Android/SDL → ARM64 game engine
                  └─ Direct3D 11/DXGI → DXVK → Vulkan → GPU driver
```

**GPU support is a separate question.** Our renderer depends on driver features, texture formats and Vulkan behavior. Support for a particular GPU family does not change the CPU architecture of the game runtime.

XHYN V is therefore **a native ARM64 Android port with graphics API translation where needed**. We build on the original Android port and maintain this edition's improvements. Native execution does not imply that every rendering path was rewritten directly for Vulkan or that the complete engine source is public.

## Features

- **Game setup:** select a game folder, manage resources, and access archive extraction/moving tools.
- **Touch and controller controls:** selectable/editable layouts, controls for different game states, camera/aim options, gyroscope and tilt-steering settings.
- **Graphics configuration:** bundled/imported DXVK selection, Vulkan driver options, BC texture decoding, HDR, shader preparation and frame-rate limits.
- **Story Mode trainer:** character, vehicle, weapon and world options, vehicle customization, favorites and presets.
- **Mods and custom radio:** resource-mod import interfaces, supported audio importing, previews and Self Radio management. ASI/DLL loading is not provided.
- **Diagnostics:** readable error explanations, local reports, performance-monitoring options and diagnostic export.
- **XHYN V interface:** English menus and trainer presentation, the purple V branding, and no Chinese system-language requirement. Some catalog entries use readable model identifiers.

We maintain XHYN V's interface, configuration, branding and language patches on top of the original Android port. Compatibility and performance depend on your device, driver, settings and game data.

## Requirements and installation

- **Android 11 or newer**, an **ARM64** device and a compatible **Vulkan** driver.
- Your own compatible **GTA V Legacy** files, including English text and fonts. The project's documented baseline is **1.61 / build 2699**.
- Enough storage for the game, caches and imported content.

1. Download and install the APK from the [release page](https://github.com/XHYN-PH/XHYN-V-reborn/releases/tag/v1.7.4-en).
2. Grant the requested file access and select the folder containing `common.rpf`, `x64` and `update`.
3. Start with the default settings. Fully close and reopen the game after changing settings that require a restart, such as the driver or DXVK runtime.

Our current release is a **development/test build**, using app ID `com.xhynph.xhynv.reborn174`. Earlier editions with different app IDs remain separate installations. Device compatibility and gameplay are still being established; performance varies by setup.

## Source and credits

We publish [our helper classes, patches, build tools and tests](modifications/english-1.7.4), with **MIT licensing** for the newly written code under its [defined scope](modifications/english-1.7.4/PROVENANCE.md). Our public code covers XHYN V's modification layer. The original game engine, original launcher implementation and exact custom renderer source remain outside this repository, with their own applicable terms.

Credit for the original Android port, engine and third-party components belongs to their authors. See [source information](docs/SOURCE.md) and [credits/notices](docs/CREDITS.md).

For problems, include your phone/GPU, Android version, driver, renderer, game-data version and reproduction steps. Attach relevant diagnostics after removing personal information.

This project is unofficial and is not affiliated with Rockstar Games or Take-Two. GTA V and its associated assets belong to their respective rights holders.
