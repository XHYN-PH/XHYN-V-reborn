# 0.2 Hotfix 2 verification

Version 0.2-hotfix2 / code 202 uses the existing app ID and release certificate.

Thirty-one API 30 Android-view regression tests passed before packaging. They
execute original patched launcher DEX through a JVM test translation and the
same new-helper javac output passed to D8. They cover navigation, original pages,
all render-scale choices, recorded-default restoration, config editor/view and
tuning-button synchronization, custom memory ranges, supported-renderer changes,
help-button behavior, cancel/reset/disabled values and advanced visibility.

Configuration/schema/help checks passed 319 assertions, and the unchanged native
DXVK parser passed 33 checks including values from the actual UI config writer.
Native forwarding/accounting/lifecycle tests passed 69 checks, including 8,000
concurrent allocation/free pairs. The same cases passed AddressSanitizer and
UndefinedBehaviorSanitizer with errors fatal. ARM64 emulation passed 26 trampoline
ABI checks at two ASLR bases. Original guarded-path checks, host/shader checks,
signing and the final preservation audit also passed (8,150 artifact checks).

The optional observer forwards the selected loader and preserves call arguments,
allocation callbacks and Vulkan results. Its entry update uses one aligned atomic
branch through a nearby veneer. A missing/unsupported hook does not block launch.

Physical Android UI, GPU execution, API 31+ hint behavior, remote downloads and
sustained gameplay were not tested. These checks do not qualify native crashes or
establish an FPS gain. See docs/issues/0.2-hotfix2-work-status.md for all 22 items,
including the engine-side proposals that remain unimplemented. DX10.1 is an
experimental feature-level cap that may prevent startup; it still uses Vulkan.

Published records omit input APKs, keys, raw device reports and translated original
classes. Local build paths are abbreviated as <build>.
