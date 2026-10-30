# Implementation and device qualification

Items **8, 10 and 16 are excluded**: no new generic rollback/profile UI, full
file/header preflight, or automatic disabling of mods/trainer. Runtime script
error reporting remains selected.

| Items | Implemented | Qualification still needed |
|---|---|---|
| 1–4 | Resolution/scale, quality, population and distance overrides; lower-settings shortcut; optional texture LOD bias | Existing save/settings.xml required; absent optional fields are skipped. Measure quality, memory and FPS. |
| 5–6 | Rendered FPS/VSync/latency; frame-generation targets respected; renderer-specific compiler/async options | An FPS cap does not guarantee that FPS. Imported renderer option support varies. |
| 7, 9 | Preserved separate caches; manual owned-cache cleanup; immutable catalog cached; disabled frame-generation badge avoids polling | Measure startup/cache behavior on devices. Integrity checks remain. |
| 11, 22–25 | System/Xclipse and experimental imported PanVK/Turnip/RADV paths; API/ELF/ZIP checks; actual driver identity; isolated draw/present test | No bundled PanVK/RADV replacement or universal GPU claim. A basic probe cannot qualify the game or its optional graphics features. |
| 12 | Null optional vkCmdSetSampleLocationsEnableEXT dispatch guard in bundled GPLALL | Null and valid dispatch exercised under ARM64 emulation. Test Adreno rendering; other renderer binaries are retained. |
| 13 | Null audio-header rejection through existing cleanup; observed invalid mixer vtable/target guard | Targeted cases reproduced. Root cause of invalid lifetime/corruption is unproven; no general audio-memory repair claimed. |
| 14 | Check null startup allocation before construction | Preserves existing null-return behavior; does not repair invalid resources or memory exhaustion causes. |
| 15, 21 | Fatal error/session/unwind report; controlled exit and next-launch explanation | Does not recover a lost GPU in place. Physical exit/relaunch and driver behavior need testing. |
| 17–19 | Bounded requested memory/thermal/process-I/O capture; matched reports/log slices; automatic capture stop | No normal-play sampler. High RSS alone does not prove a leak; kernel I/O counters may be unavailable. |
| 20 | Current-attempt unregistered script-native hash report; no dummy game APIs | Match scripts/resources to the engine. No native-table reconstruction or compatibility guarantee. |

English presentation covers the newer trainer, frame-generation and retained
download pages. The exact-build locale gate and English font/text selectors are
patched. Original feature classes, method signatures, libraries, resource IDs and
notices remain; one new native helper is added. About inside System, directly below Game Resources, links to XHYN's
Telegram, YouTube and GitHub. Original author/dependency credits remain.

The build runs English, graphics, config, driver and log host checks; an actual
upstream DXVK parser; original-fault/patched-path ARM64 checks; native report and
probe-failure host checks; shader validation; signature and final artifact audits.
Raw user crash bundles, proprietary source and private port sources are excluded.

No physical Android-device UI, GPU probe, sustained gameplay, cross-device FPS comparison
or audio soak test was available in this cloud session. This is a development
prerelease. Device reports should identify GPU, Android build, driver, renderer,
game version and reproduction steps.

The mod-spawn provider authority follows the XHYN application ID. External companion tools targeting the original package authority need to select the XHYN authority instead. Internal references are updated together.

Hotfix 1 additionally checks launcher routes and constructor inheritance before packaging, and runs API 30 Android-view regression tests. The separate settings Activity is removed. Controls are divided across Display, Performance, Vulkan Drivers, DXVK and Diagnostics; the original driver picker is the only importer/selector. Invalid old overrides do not erase saved text or become a folder-validation failure. See the sanitized additional crash triage in docs/issues.
