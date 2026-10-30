# XHYN V 0.2 modification source

Java interface/configuration helpers, a C reporting/probe helper, shaders,
translations, guarded binary patches, the complete build driver and tests for
version 0.2 Hotfix 2. See [scope](scope.json), [implementation status](STATUS.md) and
[provenance](PROVENANCE.md). Original engine/launcher code is a separate input.

The About page inside System, directly below Game Resources, uses the links in `XhynV02Menu.java`. The original
game-download page and its components are retained, with English presentation.

## Inputs and build

- Exact input APK SHA-256:
  `da730ee5b153cdf9a162d2dacd7bec45f0bf8a143f928a29d359c5c97ae51191`.
- Linux x86-64, JDK 17, host C/C++ compilers, Apktool 2.12.1 and the jars pinned
  in [android-toolchain-manifest.json](../android-toolchain-manifest.json).
- Android NDK **r27c / 27.2.12479018** from
  <https://dl.google.com/android/repository/android-ndk-r27c-linux.zip>.
  Official SDK catalog SHA-1: `090e8083a715fdb1a3e402d0763c388abb03fb4e`.
- Python 3.11+ with `python -m pip install -r modifications/v0.2/requirements.txt`.
- Robolectric 4.14.1 and JUnit 4.13.2 dependencies resolved from `tests/ui/pom.xml`,
  plus Enjarify revision `6364dfb30f7862873b84ec5fad1d1286c526ba0b`.
  Source/archive hashes are pinned in `tests/ui/toolchain.json`.
- Root artwork `assets/xhyn-v.png`; private signing inputs for a signed build.

Resolve the UI-test classpath with Maven and extract the pinned Enjarify revision:

```bash
mvn -f modifications/v0.2/tests/ui/pom.xml dependency:build-classpath -Dmdep.outputFile=/absolute/path/ui-classpath.txt
```

From the repository root:

```bash
python modifications/v0.2/build.py \
  --input-apk /path/to/original-1.7.26.apk \
  --work-dir /path/to/new-build-directory \
  --java-home /path/to/jdk-17 \
  --tools-dir /path/to/verified-android-jars \
  --apktool /path/to/apktool-2.12.1.jar \
  --ndk-root /path/to/android-ndk-r27c \
  --ui-classpath-file /absolute/path/ui-classpath.txt \
  --enjarify-root /path/to/pinned-enjarify \
  --keystore /private/path/release.p12 \
  --key-alias YOUR_ALIAS \
  --password-file /private/path/password.txt
```

Use `--verify-only` to stop after all checks, before any APK packaging or signing.
Always use a fresh work directory. The driver verifies APK/tool hashes, decodes,
patches, compiles only the new helpers, runs checks, packages, signs and audits.
Compile-only stubs are not packaged. Signing keys/passwords are never copied.
Omit all three signing arguments for an unsigned build; final signature/alignment
qualification then remains incomplete. The new native helper targets API 30 with
16 KiB segment alignment; this does not convert other base libraries' alignment.

## Configuration and runtime behavior

Saved overrides live at `Config/dxvk.conf` under the selected game root. Disabling
them preserves their text. Generated defaults plus enabled overrides produce one
private file, selected by `DXVK_CONFIG_FILE`. After the base profile runs,
`DXVK_CONFIG` is set to an empty, non-null string: unsetting it would let the
engine insert a competing setting. Frame-rate aliases are cleared. Executable
sections remain intact and may override global controls. The launch hook checks
that the effective file and environment remain consistent. Invalid saved overrides
are retained and can fall back to managed settings for that launch, with a notice.
Disabled overrides are not read. Graphics keys use the base INI writer's lowercase
format and map to the original XML field names when applied.

Managed controls emit keys found in each bundled renderer's ELF option strings.
Unknown edited/imported options are disclosed; support in arbitrary imported
renderers is not assumed. Upstream parser fixtures retain their own license in
`tests/reference`.

The native helper checks exact engine entry bytes before installing its fatal
handler. It writes code/session/PID/time and a bounded unwind trace, then exits
with status 73. This records graphics/resource failures and prevents continuation
after a fatal error; it does not repair a lost GPU or corrupt game resource.

Driver import/selection remains solely on the existing Vulkan Drivers page.
Readiness testing is optional, including for an existing custom-driver selection.
Custom driver admission checks Android API, ARM64 ELF and archive paths, and uses
the base driver manager's existing fallback path on an invalid/missing package. A
separate `:xhyn_probe` process draws/presents two test frames, with a 20-second
watchdog. A pass proves that small test, not D3D11 completeness, texture correctness
or gameplay. Imported PanVK/RADV and Xclipse paths remain experimental.

Reports keep config/package/runtime identities per attempt and exclude old
append-only log content. Requested capture samples memory, thermal state and
process I/O every two seconds, stops at its deadline, and retains eight attempts.
Kernel process I/O counters are not engine streaming throughput.

Hotfix 2 adds a 0–100% scale slider (0 restores recorded game dimensions, with a
320 × 180 minimum for positive scales), targeted original-quality restoration,
and a simpler record-next-game/report interface. Older releases did not record
their original quality attributes. The editor and effective view share one active
configuration; untouched generated fields remain managed when the editor saves.

The DXVK page exposes supported tuning controls with information buttons,
including distinct reported-memory and allocation-budget values. Specialized
controls start collapsed. Each uses the same config writer as the editor, with
explicit Off/Automatic values distinguished from Default (delete override).
The editor refreshes control values after saving. Graphics, FPS, driver and
diagnostic controls also explain purpose and tradeoffs. See
[DXVK-TUNING.md](DXVK-TUNING.md) for all controls and semantic references.
DX10.1 is an experimental feature-level cap, not a second rendering backend;
it may prevent startup. The global FPS alias precedence is reflected in the UI,
display requests and hints. Explicit file values clear conflicting HUD/async/cache
environment aliases, while default values preserve the existing renderer policy.

The optional native observer is installed only for requested recording or Android
performance hints. It preserves the loader/BC dispatch and uses a guarded atomic
entry branch with an ABI-tested trampoline. Tables and interval history are
bounded. Presentation cadence is not rendered/GPU frame time; driver budgets are
not process-owned memory. Android hints are off by default and require Android
12+. Display requests reuse the existing FPS/output target. The app is shell
profileable for developer CPU tools. See the [22-item implementation status](../../docs/issues/0.2-hotfix2-work-status.md)
for remaining engine work and device qualification.

The prepackage UI suite executes original patched launcher DEX through a JVM
translation and the new helpers from the exact javac output used by D8. It tests
Android framework views, click targets, all page headers, config persistence and
startup error paths on API 30 under Robolectric. Converter-only adjustments are
recorded; converted classes never enter the APK. Physical Android UI, GPU and
gameplay tests remain necessary for device qualification.
