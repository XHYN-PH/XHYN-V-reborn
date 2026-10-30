# Additional performance work to evaluate

These suggestions are separate from the selected 0.2 work and were not enabled
automatically. Related ports were reviewed as references; their private/game-specific
source and fixed offsets are not copied into this publication.

| Suggestion | Reference and potential benefit | First requirement |
|---|---|---|
| Replace confirmed busy-waits with bounded waits | The IV port documents lower CPU use from a short frame-loop sleep, without an FPS increase on one device | Profile this engine's wait loops; arbitrary sleeps can reduce FPS or increase latency. |
| Android performance hints and thermal-aware pacing | Forza separates thermal freshness, sampling and optional platform controls | Compare sustained frame times, heat and power on Adreno, Mali and Xclipse. |
| Driver/renderer recommendations per GPU | Pt4droid and IV/Forza distinguish actual GPU/driver identity and experimental paths | Build a gameplay/texture test matrix; successful loading or a small probe is insufficient. |
| Adaptive resolution with reliable GPU timing | Could reduce GPU load in expensive scenes | Establish stable timing and quality bounds; do not treat CPU, memory or driver stalls as GPU saturation. |

Revisions reviewed:

- XHYN-PH/pt4droid: `2f43a90e3f910be4d59804f3b46a5132cc951d8e`.
- XHYN-PH/XHYN-IV-recomp: `d276d9452b6f20885935b9bd8ddcd7889f525395`.
- XHYN-PH/fh4droid: `38485d24a0782c81ec3d0cb5bfa5426043d5f10c`.

Driver admission, scoped Turnip flags, separate probe processes and bounded
sampling informed the selected implementation. No FPS gain is established by
these source references.

## Expanded review and implementation

The [Hotfix 2 work status](../../docs/issues/0.2-hotfix2-work-status.md) records
all 22 subsequent proposals and explicitly identifies unavailable engine work.
Additional pinned implementations reviewed:

- [OpenMW cell preloading, cancellation and cache expiry](https://github.com/OpenMW/openmw/blob/cbb5b35743824bc898d930792e7bfbbf4fa2dc1c/apps/openmw/mwworld/cellpreloader.cpp).
- [DIII4A texture-conversion caches](https://github.com/glKarin/com.n0n3m4.diii4a/blob/ce49510f3b0c04969eab8ebe8238416e84e4f0bb/doom3/neo/renderer/image/Image_load_compression.cpp).
- [Android Source-engine texture request deduplication and residency](https://github.com/nillerusr/source-engine/blob/ed8209cc35c61fbd8ddff8480962a01c981eef2f/materialsystem/texturemanager.cpp).
- [Xash3D culling and surface/decal batching](https://github.com/FWGS/xash3d-fwgs/blob/3daa67eca9a9f9651b37d63babe6dfc8ec6961ac/ref/gl/gl_rsurf.c).

No game-specific implementation is transplanted. FH4Droid desktop benchmark
numbers do not establish Android speedups, and its native replacements for
recompiled PPC work do not imply equivalent gains in this ARM64 engine.
