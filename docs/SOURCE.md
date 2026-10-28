# Source and native-port explanation

## Native execution and graphics translation in 1.7.4

Here, **native** describes the CPU target and Android loading path. The
[package inventory](../verification/english-1.7.4/native-package.json) records the
released APK's ARM64 (`EM_AARCH64`) 64-bit ELF game library, its `SDL_main` export,
Android JNI entry points and Android/SDL dependencies. No Windows `.exe`/`.dll`
payload or package entries matching the usual Wine, Box64, FEX or QEMU runtime
names were found. This is evidence about the inspected package, not a proof of
every possible code path or a reproduction of the complete engine build.

Graphics translation is a different layer. DXVK translates Direct3D calls into
Vulkan; its upstream [DXVK Native documentation](https://github.com/doitsujin/dxvk#dxvk-native)
explicitly describes using it for native ports without Wine. Native ARM64 CPU
execution can therefore coexist with Direct3D-to-Vulkan translation. Calling
that graphics path a compatibility layer does not establish x86 CPU emulation.
The APK also includes native Vulkan components; their presence alone is not a
claim that every render pass bypasses DXVK.

GPU restrictions, BC/ASTC texture support and Vulkan driver requirements are not
CPU-architecture evidence. A native port can have limited GPU compatibility.
Similarly, closed or incomplete source publication does not determine which
instruction set a binary executes. It does limit independent reproducibility and
provenance review, which remain separate questions.

The description used here is **a native ARM64 Android port with graphics API
translation where needed**. It is not a claim of an official Rockstar Android
release, a clean-room rewrite, a fully Vulkan-only renderer or complete public
engine source. The Android ARM64 ABI is documented by
[Android's NDK guide](https://developer.android.com/ndk/guides/abis#arm64-v8a).

## Why does this use DXVK?

There are two separate parts:

| Part | Role in this build |
|---|---|
| Game CPU code | Native ARM64 Android ELF libraries, including `libgtav.so`, loaded through the Android/SDL launcher |
| Graphics | Direct3D 11/DXGI calls handled by the bundled native DXVK libraries, which use Vulkan |

DXVK is a graphics API implementation. Its upstream documentation explicitly supports [native game ports without Wine](https://github.com/doitsujin/dxvk#dxvk-native). Its presence is not evidence of CPU or console emulation. Similarly, whether source is publicly available does not determine whether a program executes native machine code.

In XHYN V 0.1, the inspected payloads are ARM64 ELF shared libraries. The launcher enters the native engine through SDL's `nativeRunMain`. The packaged-library inspection did not identify a bundled Wine, FEX, Box64 or QEMU runtime. This is evidence about the inspected package, not a claim that the complete original build process or every upstream dependency has been independently reproduced.

## What source is included?

The current [1.7.4 English modification layer](../modifications/english-1.7.4) includes four new Java helpers, guarded binary/UI patches, a build driver, translation data and host/native/package tests. Its new helper and tooling code is MIT licensed under the [documented scope](../modifications/english-1.7.4/PROVENANCE.md). It requires the exact privately supplied base APK and is not a complete game-engine source build. The [1.7.4 verification records](../verification/english-1.7.4) distinguish checks performed from untested Android behavior.

The older [modifications directory](../modifications) contains the Java helpers, JNI language bridge, exact-build patch/hook scripts, translation mapping, compile-only stubs and verification scripts used for our changes. They cover language selection, configuration, cache identity, display requests, session handling and diagnostics.

The [verification directory](../verification) contains package checks, native patch bytes/addresses, the language emulation matrix, regression checks and host-test output. These can help review our changes. They do not establish that gameplay works on a phone.

## What is unavailable?

- The complete original game engine and Android-base source, with verified provenance and redistribution permission.
- The exact custom DXVK patch set used for the renderer binaries.
- A complete public rebuild from source of the entire APK.

Older source code was available during development, but it does not fully match the engine and custom DXVK build used here. This public repository contains our modification layer. Proprietary engine code, decompiled original launcher code, game data, private signing material and private development checkpoints are excluded.

Upstream open-source component links are listed in [CREDITS.md](CREDITS.md). An upstream repository is not a substitute for the missing exact custom changes. This repository must not be described as the complete open-source GTA V port.

## Source licensing

The newly written code in `modifications/english-1.7.4` has an MIT license with explicit scope. Older materials retain their existing terms. Publication does not relicense GTA V, the game engine, original launcher, artwork, translation inputs or third-party components. No blanket open-source license is claimed for the full APK. Existing third-party notices remain applicable. A native port still has copyright and dependency-license obligations; the word "native" does not remove them.
