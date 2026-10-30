# 0.2 Hotfix 2 developer verification

- Version 0.2-hotfix2 / code 202; existing app ID and release certificate.
- 31 host Android-view tests cover navigation, graphics, tuning/editor/help and report flows.
- 319 configuration/help checks and 33 unchanged upstream DXVK parser checks passed.
- 69 native forwarding/accounting/lifecycle checks, including concurrent allocations,
  passed normally and under AddressSanitizer/UndefinedBehaviorSanitizer.
- 26 ARM64 trampoline ABI checks passed at two ASLR bases.
- Existing host/guard/shader checks, signature and 8,150 final artifact checks passed.

No physical-device gameplay, Vulkan rendering or Android 12+ hint qualification
was available. Presentation intervals are not rendered/GPU frame times; allocation
tracking covers observed calls only. DX10.1 is an experimental feature-level cap,
not a second rendering backend. Deeper engine changes and old native faults
remain unresolved. Full status: docs/issues/0.2-hotfix2-work-status.md.

Detailed records and source hashes: verification/0.2-hotfix2.
