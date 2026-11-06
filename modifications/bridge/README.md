# XHYN V Bridge

XHYN V Bridge is the English virtual LAN companion for XHYN V. Players join the same Bridge network, then use the host's virtual IPv4 in the game's LAN menu. The game and Bridge are separate apps. Bridge does not provide GTA Online or game files.

This edition is based on [Astral 2.9.60](https://github.com/ldoubil/astral/tree/v2.9.60), with English dialogs, tools, settings, notifications and widgets; XHYN branding; offline connection tips; and a separate Android identity. Known native network messages are translated at the presentation boundary, and the server-start log omits room credentials. Existing `astral://` room links and room/network formats remain compatible. Updates open the XHYN release page so an upstream Astral installer cannot replace Bridge.

The source patch, compatibility tests and notices are included here. Astral's PolyForm Noncommercial license and required notice apply; this directory is not covered by the repository's MIT license. Original dependency notices remain available inside the app under **Licenses and credits**.

## Prepare and build

Use Python 3.12+, JDK 17, Flutter **3.38.4**, Android platforms 34/35/36, build tools 36.0.0 and NDK **28.2.13676358**. The Gradle wrapper uses 8.14. Keep normal HTTPS and package checksum verification enabled. `inputs.json` pins the source revision and private input APK. The original networking library is reused unchanged; proprietary/private input APKs and signing keys are not included in this source directory.

```sh
git clone --depth 1 --branch v2.9.60 https://github.com/ldoubil/astral.git /tmp/astral-upstream
python modifications/bridge/prepare.py \
  --upstream-checkout /tmp/astral-upstream \
  --input-apk /path/to/companion.apk \
  --work-dir /tmp/xhyn-bridge-build
cd /tmp/xhyn-bridge-build/source
export ALT_BUILD=true
export XHYN_BRIDGE_NATIVE_DIR=/tmp/xhyn-bridge-build/native
export PUB_HOSTED_URL=https://pub.flutter-io.cn
flutter pub get --enforce-lockfile
flutter analyze --no-pub lib test
flutter test --no-pub --reporter expanded
flutter build apk --release --target-platform android-arm64 --no-pub
```

Set `sdk.dir` in `android/local.properties` if the SDK is not found automatically. The package ID defaults to `com.xhynph.xhynv.bridge`. The alternate build omits the upstream Firebase configuration. Without a private `android/key.properties`, the build produces an unsigned release APK; sign it separately with your own key. Keys must stay outside public source and archives.

The tests cover English resource keys/placeholders, language selection, native error presentation, credential redaction, existing room-link round trips and network configuration serialization. Android VPN permission, background survival and live two-device play still require physical-device testing. The supplied native backend matches the source's Flutter Rust Bridge content hash; that does not establish live connectivity or game synchronization.

To audit a signed artifact, install the Python audit dependencies from `modifications/v0.3/requirements.txt`, then run:

```sh
python modifications/bridge/audit.py --apk /path/to/XHYN-V-Bridge-0.1.0.apk \
  --input-apk /path/to/companion.apk --source-dir /tmp/xhyn-bridge-build/source \
  --report /tmp/bridge-audit.json
```

The audit verifies the XHYN release certificate. A locally signed build using a different key requires a separately reviewed certificate policy; do not replace expected checksums to make a failing audit pass. The audited build results are recorded in [VERIFICATION.json](VERIFICATION.json).
