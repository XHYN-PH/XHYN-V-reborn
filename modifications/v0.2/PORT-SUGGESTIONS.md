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
