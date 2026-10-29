# XHYN V 0.2 modification source

Java interface/configuration helpers, a C reporting/probe helper, shaders,
translations, guarded binary patches, the complete build driver and tests for
version 0.2. See [scope](scope.json), [implementation status](STATUS.md) and
[provenance](PROVENANCE.md). Original engine/launcher code is a separate input.

The About button beside System uses the links in `XhynV02Menu.java`. The original
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
- Root artwork `assets/xhyn-v.png`; private signing inputs for a signed build.

From the repository root:

```bash
python modifications/v0.2/build.py \
  --input-apk /path/to/original-1.7.26.apk \
  --work-dir /path/to/new-build-directory \
  --java-home /path/to/jdk-17 \
  --tools-dir /path/to/verified-android-jars \
  --apktool /path/to/apktool-2.12.1.jar \
  --ndk-root /path/to/android-ndk-r27c \
  --keystore /private/path/release.p12 \
  --key-alias YOUR_ALIAS \
  --password-file /private/path/password.txt
```

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
that the effective file and environment remain consistent.

Managed controls emit keys found in each bundled renderer's ELF option strings.
Unknown edited/imported options are disclosed; support in arbitrary imported
renderers is not assumed. Upstream parser fixtures retain their own license in
`tests/reference`.

The native helper checks exact engine entry bytes before installing its fatal
handler. It writes code/session/PID/time and a bounded unwind trace, then exits
with status 73. This records graphics/resource failures and prevents continuation
after a fatal error; it does not repair a lost GPU or corrupt game resource.

Custom driver admission checks Android API, ARM64 ELF and archive paths. A
separate `:xhyn_probe` process draws/presents two test frames, with a 20-second
watchdog. A pass proves that small test, not D3D11 completeness, texture correctness
or gameplay. Imported PanVK/RADV and Xclipse paths remain experimental.

Reports keep config/package/runtime identities per attempt and exclude old
append-only log content. Requested capture samples memory, thermal state and
process I/O every two seconds, stops at its deadline, and retains eight attempts.
Kernel process I/O counters are not engine streaming throughput.

Host, shader, ARM64 emulation and artifact checks do not execute Android UI or
physical GPU/gameplay tests. Those remain necessary for device qualification.
