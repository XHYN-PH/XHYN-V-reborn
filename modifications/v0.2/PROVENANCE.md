# Provenance and license scope

The MIT license in this directory covers the newly written Java helper classes,
Python build/patch/audit/test code, the new C helper and probe shaders, Java host checks, and new explanatory build
documentation. It does not license the APK as a whole or any original engine,
launcher implementation, renderer, artwork, game resource or third-party code.

The input APK is supplied separately and verified by SHA-256. Neither the base
APK nor its decoded/decompiled code is included in this source directory. The
older proprietary source archive is excluded. Small expected-byte guards and
interface names in patch scripts describe the exact binary being modified; they
are not a publication of its engine implementation.

The translation dictionary combines the earlier XHYN V English release,
repository translation data, new English translations, and readable model labels
derived from the supplied APK's model identifiers. Chinese source labels, model
identifiers and retained third-party text remain subject to their original
applicable terms. The new-code MIT license does not claim ownership of those
inputs or grant rights in GTA V. The bundled English FFmpeg notes translate the
upstream build description and retain its licensing statements.

The purple V artwork is reused from `assets/xhyn-v.png` at the repository root,
with its outside black background removed at the maintainer’s request. No
additional artwork license is assigned here. Original developer credits and
third-party license notices remain applicable. The existing root notices are
retained. Exact corresponding source and relinking materials for every bundled
third-party binary have not been independently established by this publication.

Private signing keys/passwords, credentials, game data, decoded APK trees,
original native libraries, SDK tools and private checkpoints are not part of the
public source package. Release APKs are binary test builds, separate from this
source-code license.

Upstream parser/header fixtures under tests/reference retain their own recorded licenses. The new-code MIT scope does not replace those terms.
