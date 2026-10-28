# XHYN V 1.7.4 English modification layer

This directory publishes the English presentation helpers, exact-build language
patch, complete translation data, build driver and host/native/package checks for
the 1.7.4-named APK. [Verification](../../verification/english-1.7.4/README.md)
records what passed and what still needs an Android device.

The new helper and tooling code is MIT licensed. Read [provenance and license
scope](PROVENANCE.md); this is not the original launcher/engine source or a source
build of the complete game. The base APK, game data and signing keys are separate
inputs. `build.py` patches an exact binary and compiles only our added helpers.

## Inputs and tools

- Exact input APK SHA-256:
  `d2321c36b9d9028c01ffae70214aa12e544fa595b923ae855564458a1b1d1bb5`.
  Its filename says 1.7.4; its original manifest reports 1.7.1/code 84.
- JDK 17, Apktool 2.12.1, and the jars in
  [android-toolchain-manifest.json](../android-toolchain-manifest.json).
  `build.py` verifies every jar against the published hashes.
- Python 3.11+ and `python -m pip install -r requirements.txt` in your own virtual
  environment. Tool versions are also recorded in
  [toolchain-manifest.json](../toolchain-manifest.json).
- The repository root's existing `assets/xhyn-v.png` logo.
- Your own keystore, alias and protected password file for a signed build.

Do not use patches/offsets from the 0.1 build on this engine. The native patch
rejects any other engine SHA-256. Start each build in a new work directory. The
source checkout is not modified, and no game data or tool download is automatic.

## Build and check

From the repository root, with the Python dependencies available:

```bash
python modifications/english-1.7.4/build.py \
  --input-apk /path/to/original.apk \
  --work-dir /path/to/new-build-directory \
  --java-home /path/to/jdk-17 \
  --tools-dir /path/to/verified-android-jars \
  --apktool /path/to/apktool.jar \
  --keystore /private/path/signing.p12 \
  --key-alias your-alias \
  --password-file /private/path/password.txt
```

The driver decodes the base, applies guarded native and presentation changes,
translates INI comments/assets, compiles only the four Java helper sources,
assembles the APK, runs the host/ARM64 checks, signs with 16 KiB native-entry
alignment, verifies the signature and runs the final preservation audit. Logs,
reports, checksums and APKs stay in the build directory. Signing passwords are
passed as file references and are not read or copied by the build driver.

Omit all three signing options for an unsigned APK. Host and ARM64 checks still
run, but signature verification and the complete final APK/alignment audit are
explicitly skipped. You must sign and verify an installable APK separately.

The host test harness is never included in the helper DEX. No Android SDK stubs
are packaged. Helper JNI/class names retain the original namespaces; the app ID
is `com.xhynph.xhynv.reborn174`, the label is **XHYN V**, and the version name is
`1.7.4-en`.

## Scope

Translations occur at display boundaries; parser strings, command/model IDs,
editable user text and actual filesystem paths remain available unchanged. New
catalog items without an established English name use readable model identifiers,
following the earlier English APK's approach. The native patch selects English
`american` text and `efigs` fonts and removes the Chinese system-locale gate.

Original native diagnostics can remain Chinese internally. English game text and
fonts must be present in the user's compatible game files. Recorded host and
instruction checks do not establish Android UI behavior, GPU compatibility,
performance or gameplay. Keep this a test build until device testing is complete.
