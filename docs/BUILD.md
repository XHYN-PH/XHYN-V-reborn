# Modification-source layout

## 0.2

The current [0.2 source](../modifications/v0.2/README.md) includes the complete exact-base build driver, Java/C helpers, shaders, patches and checks. It targets 1.7.26 and requires NDK r27c alongside the pinned Java/Android tools. Hotfix 1 also requires the pinned Robolectric/JUnit classpath and Enjarify checkout, and runs launcher-view tests before packaging. See its [implementation status](../modifications/v0.2/STATUS.md) for device-testing limits.

## 1.7.4 English test build

The earlier [English modification layer](../modifications/english-1.7.4/README.md) contains a complete build driver for applying these changes to the exact privately supplied APK. It verifies input/tool hashes, compiles only the four new Java helpers, applies guarded language and presentation patches, assembles the APK, and runs host/ARM64 checks. Signed builds also run signature and final preservation audits. Signing keys are user-provided and never part of the public package.

This is a binary patch/rebuild workflow, not compilation of the original game engine. See its README for commands, dependencies and license scope. Recorded output is in [verification/english-1.7.4](../verification/english-1.7.4). Android execution and gameplay still need a device.

## Older 0.1 sources

This repository publishes the modification layer for review. It is not a self-contained engine build or a complete APK rebuild kit.

| Folder | Contents |
|---|---|
| `modifications/java/` | Nine production helper classes, with generated inner classes produced during D8 compilation |
| `modifications/native/` | New ARM64 JNI language bridge |
| `modifications/scripts/` | Patch/hook history, build scripts, host/emulator checks and native frame-capture analyzer |
| `modifications/stubs/` | Minimal app interfaces used at compile time; never packaged |
| `modifications/translation-complete.json` | English display-string mapping |
| `verification/` | Recorded checks from the 0.1 build |

The original scripts use a working directory named `reborn` and refer to a locally decoded base, earlier translation inputs and hash-pinned Android tools. Those private inputs are not part of this public repository. The scripts are preserved as used, so that their guards and integration changes can be inspected; cloning this repository alone does not produce the APK.

The native patch is specific to the original engine SHA-256 pinned in the old patch script and recorded verification files. It rejects other engine builds. Never copy these addresses to another APK or to the experimental native Vulkan engine.

The verification used Java 17, Android API 36/D8/smali tools, Apktool 2.12.1, Zig 0.13.0, Python 3, pyelftools 0.33, Capstone 5.0.9, Unicorn 2.1.4 and Androguard 4.1.4. Tool metadata is in `modifications/toolchain-manifest.json` and `modifications/android-toolchain-manifest.json`.

The source ZIP attached to the release matches this public source tree. Neither it nor GitHub's automatic source archives contain the original game engine, game files or signing key.
