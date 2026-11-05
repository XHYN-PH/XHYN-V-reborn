# Credits and third-party notices

- **XHYN-PH** — XHYN V maintenance, branding, translations and the modification layer in this repository.
- **Original Chinese Android port contributors** — the original Android port and engine adaptation. XHYN V builds on their work.
- **Rockstar Games and respective rights holders** — GTA V and associated game assets/trademarks. No affiliation or endorsement is implied.
- **[DXVK](https://github.com/doitsujin/dxvk)** — Direct3D-to-Vulkan implementation and native-port support. The exact custom fork/patch sources used by the bundled binaries are not available here.
- **[SDL](https://github.com/libsdl-org/SDL)** — native application/platform integration.
- **[libadrenotools](https://github.com/bylaws/libadrenotools)** and **[liblinkernsbypass](https://github.com/bylaws/liblinkernsbypass)** by Billy Laws — optional driver loading. Driver ZIPs are user provided.
- **[Lucide](https://lucide.dev)** — menu icons.
- **[FFmpeg](https://ffmpeg.org)**, **[zlib-ng](https://github.com/zlib-ng/zlib-ng)** and other components — see the original retained notices and build notes.

The [notices folder](../notices) contains the notices retained from the original Chinese Android port. Their presence is not a claim that the complete original dependency licensing or provenance has been independently verified. The proprietary engine source archive and original decompiled launcher source are not published.

The new [1.7.4 English helper/tooling code](../modifications/english-1.7.4) is MIT licensed under its stated scope. Its translations use the earlier XHYN V English release as a reference. Readable model labels retain the supplied APK’s model identifiers. The maintainer requested removal of the outside black logo background; this does not assign a new license to the artwork.

The [0.2 modification layer](../modifications/v0.2) adds XHYN's performance/configuration interface, driver checks, crash guards and native reporting helper. Its own code is MIT licensed under the directory's provenance statement. Original frame-generation components and their shaders, weights and X11 dependencies retain their upstream terms; their notices and provenance records remain in the APK under `assets/framegen` and `assets/lsfg`. These components are not relicensed by this repository.

The [0.3 modification layer](https://github.com/XHYN-PH/XHYN-V-reborn/tree/v0.3-preview1/modifications/v0.3) migrates the XHYN controls and reports onto the matched 1.7.57 multiplayer base. The original LAN implementation and engine remain credited to their authors. Its custom free-roam mode is not Rockstar GTA Online.

**[Astral Project](https://github.com/ldoubil/astral)** and **[EasyTier](https://github.com/EasyTier/EasyTier)** provide the basis of XHYN V Bridge. Required Notice: Copyright Astral Project (https://github.com/ldoubil/astral). Bridge's source changes and distribution retain [PolyForm Noncommercial 1.0.0](https://github.com/XHYN-PH/XHYN-V-reborn/blob/v0.3-preview1/modifications/bridge/LICENSE); the repository MIT license does not relicense them. Original and generated dependency notices are included inside Bridge. The exact native networking backend is preserved.
