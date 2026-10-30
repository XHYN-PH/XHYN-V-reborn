# 0.2 Hotfix 1 verification

The signed release passed 13 API 30 Android-view regression tests under
Robolectric, using original patched launcher DEX through a JVM test translation
and the exact new-helper javac output used by D8. Coverage includes all page
headers, System click targets, Game Resources, original Diagnostics actions,
separated settings, native-style toggles/dialogs, config persistence, the custom
driver startup regression, disabled/invalid legacy config, and setup error titles.

Constructor/page-route checks, English/graphics/config/driver/log host checks,
upstream DXVK parser checks, ARM64 guarded-path tests, native report tests,
SPIR-V validation, APK signature and final artifact audits also passed.

Version 0.2-hotfix1 / code 201 uses the existing application ID and release key.
No standalone settings Activity or duplicate driver importer remains. Original
classes, method signatures, download components, native libraries, resource IDs
and notices are retained; one native helper is additive.

Physical Android UI, native GPU testing, remote downloads and sustained gameplay
were not executed here. Known older memory/pointer/driver failures remain separate
from these startup/UI fixes; see docs/issues/2026-10-30-crash-triage.md.

No private input APKs, keys, raw crash bundles or converted original classes are
published. Build paths in these records are abbreviated as <build>.
