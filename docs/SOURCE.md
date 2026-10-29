# Source and native-port explanation

## Native execution and graphics translation

**XHYN V runs its engine as native ARM64 Android code.** Our launcher loads
`libgtav.so`, a 64-bit ARM64 (`EM_AARCH64`) Android shared library, through SDL's
native entry point. The runtime exposes `SDL_main` and Android JNI interfaces
and links to Android/SDL platform libraries. We document these architecture
details in the [runtime package inventory](../verification/english-1.7.4/native-package.json).

Our work builds on the original Android port. We maintain the XHYN V edition's
launcher changes, English presentation, branding, configuration and language
patches. This is distinct from claiming authorship of the original engine or
all of its third-party libraries.

Graphics translation is a different layer. DXVK translates Direct3D calls into
Vulkan; its upstream [DXVK Native documentation](https://github.com/doitsujin/dxvk#dxvk-native)
explicitly describes using it for native ports without Wine. Native ARM64 CPU
execution can therefore coexist with Direct3D-to-Vulkan translation. Calling
that graphics path a compatibility layer does not establish x86 CPU emulation.
Our runtime also includes native Vulkan components. Rendering paths can use
different backends; a native component does not mean every pass bypasses DXVK.

GPU restrictions, BC/ASTC texture support and Vulkan driver requirements are not
CPU-architecture evidence. A native port can have limited GPU compatibility.
Similarly, closed or incomplete source publication does not determine which
instruction set a binary executes. It does limit independent reproducibility and
provenance review, which remain separate questions.

Our architecture is **a native ARM64 Android port with graphics API
translation where needed**. We build on the original port and retain its
applicable credits and licensing; this is an unofficial project rather than an
official Rockstar Android release or a complete public engine source tree. The Android ARM64 ABI is documented by
[Android's NDK guide](https://developer.android.com/ndk/guides/abis#arm64-v8a).

## Why does this use DXVK?

There are two separate parts:

| Part | Role in this build |
|---|---|
| Game CPU code | Native ARM64 Android ELF libraries, including `libgtav.so`, loaded through the Android/SDL launcher |
| Graphics | Direct3D 11/DXGI calls handled by the bundled native DXVK libraries, which use Vulkan |

DXVK is a graphics API implementation. Its upstream documentation explicitly supports [native game ports without Wine](https://github.com/doitsujin/dxvk#dxvk-native). Its presence is not evidence of CPU or console emulation. Similarly, whether source is publicly available does not determine whether a program executes native machine code.

XHYN V 0.1 also uses ARM64 ELF shared libraries, with SDL's `nativeRunMain` as the native engine entry point. Our 0.1 and 1.7.4 modification layers target different engine builds; their fixed native offsets are not interchangeable.

## What source is included?

The current [0.2 modification layer](../modifications/v0.2) contains Java/C helpers, probe shaders, translations, guarded binary/UI patches, a complete build driver and checks for the 1.7.26 runtime. New code is MIT licensed under its [defined scope](../modifications/v0.2/PROVENANCE.md). It does not compile the original engine. See [implementation status](../modifications/v0.2/STATUS.md) for testing limits.

The earlier [1.7.4 English modification layer](../modifications/english-1.7.4) includes four new Java helpers, guarded binary/UI patches, a build driver, translation data and host/native/package tests. Its new helper and tooling code is MIT licensed under the [documented scope](../modifications/english-1.7.4/PROVENANCE.md). Our guarded patch workflow targets an exact base build, identified by its SHA-256; it does not compile the complete original game engine. The [1.7.4 verification records](../verification/english-1.7.4) distinguish checks performed from untested Android behavior.

The older [modifications directory](../modifications) contains the Java helpers, JNI language bridge, exact-build patch/hook scripts, translation mapping, compile-only stubs and verification scripts used for our changes. They cover language selection, configuration, cache identity, display requests, session handling and diagnostics.

The [verification directory](../verification) contains package checks, native patch bytes/addresses, the language emulation matrix, regression checks and host-test output. These can help review our changes. They do not establish that gameplay works on a phone.

## What is unavailable?

- The complete original game engine and Android-base source, with verified provenance and redistribution permission.
- The complete exact patch sets for all renderer/frame-generation binaries; known upstream revisions and notices are retained, while our new guard source is published.
- A complete public rebuild from source of the entire APK.

Older source code was available during development, but it does not fully match the engine and custom DXVK build used here. This public repository contains our modification layer. Proprietary engine code, decompiled original launcher code, game data, private signing material and private development checkpoints are excluded.

Upstream open-source component links are listed in [CREDITS.md](CREDITS.md). An upstream repository is not a substitute for the missing exact custom changes. This repository publishes our modification layer, not the complete original engine.

## Source licensing

Newly written code in the versioned modification directories has MIT licensing with explicit scope. Older materials retain their existing terms. Publication does not relicense GTA V, the game engine, original launcher, artwork, translation inputs or third-party components. No blanket open-source license is claimed for the full APK. Existing third-party notices remain applicable. A native port still has copyright and dependency-license obligations; the word "native" does not remove them.
