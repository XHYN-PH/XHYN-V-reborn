# 0.3 candidate implementation status

The base is GTAV 1.7.57. Its matched engine, LAN binding, room protocol and lifecycle calls are retained. Theme stays at page 21, LAN at 22–24, and About is page 25 under System. The original application and artwork verification remain.

The base's memory controls wrote hidden DXVK_CONFIG overrides after our effective file was prepared. The duplicate controls and second writer are disabled. Memory options use the existing DXVK section/editor. Original entry points remain valid.

The November 5 review selected items 1–15. Investigation does not mean every underlying native defect was repaired:

| Item | Candidate work | Remaining limits |
|---|---|---|
| 1. Heap guard | Recognize 0xb3e1d694, preserve the guard and unwind, add PID/TID and memory context; recording/hints remain opt-in. | Earlier corrupting write remains unidentified. No allocator root-cause repair is claimed. |
| 2. Resource cleanup | Preserve module identities, process/time boundaries, resource logs and matched OS exits; retain targeted valid-call guards. | The failing access does not identify the bad pointer's producer. No pointer masking or arbitrary free suppression. |
| 3. BC copy/readback | Retain the matched layer and unsupported-copy errors. Record later timestamped driver/BC diagnostics while recording. | Custom patch SHA-256 4f2c79500d2be5060ff4656c5d8ef43d46c272d3dd3cab143458d8707012a49d is unavailable. Public upstream is not the exact custom implementation. The 91→133 path is not repaired or GPU-qualified. |
| 4. PanVK | Preserve imports/identities, add job-fault guidance and comparisons with frame generation/hints off. Turnip controls stay scoped to measured Turnip identity. | No bundled or certified PanVK package; exact GPU/kernel/driver combinations need testing. |
| 5. Graphics startup | Explain 0x2f84a8ed as graphics/swapchain initialization failure; remove conflicting memory overrides and improve context. | No general in-place surface/device recovery patch. |
| 6. Scripts/resources | Keep current-attempt missing-native hashes, resource logs and engine identity; give file-specific archive guidance. | No dummy APIs, reconstructed script table or full game-file scan. Matching resources remain necessary. |
| 7. Log slicing | Preserve small known app logs separately before launch. Use file identity and the complete prior prefix up to a bound for unrotated logs. Identical 4 KiB rewrites retain startup context. Legacy snapshots are separate and excluded from current script diagnosis. | Oversized/unrotatable logs have bounded conservative capture with provenance. An identical in-place rewrite of unowned data cannot always be inferred. |
| 8. Code-zero assertion | Treat error_code=0x00000000 as an unresolved assertion; retain stack/resource/build context. | Subsystem/producer remains unknown. Validation is preserved. |
| 9. Shader preparation | Cache identity uses the resolved renderer and actual packaged fingerprint. Add comparisons of matching recordings. Avoid bundled-library re-hashing during normal startup. | Parallel pipeline-call totals are not frame-stall time. No universal worker count or FPS-gain claim. |
| 10. Memory | Record available/total physical RAM and process counters separately; record failed Vulkan allocation size/type/result. Keep controls that reduce requested graphics demand. | Overlapping counters must not be added. A peak is not a leak. No blanket budget or allocator fix. |
| 11. Lifecycle/pacing | Preserve pause/resume integration for display requests, hints, observer and LAN; add event timestamps and retain lifecycle/ABI regressions. | Surface recreation, background survival and real pacing need Android testing. No invalid-surface retry loop. |
| 12. Resolution | Save applied settings.xml and label it as requested game settings; retain adjustable scale, original-value tracking and targeted reset. | Presentation size does not prove actual scene render-target size. |
| 13. Frame generation | Fresh default remains off. Add stable-baseline guidance and distinguish output cadence from rendered FPS. Comparisons reject changed frame-generation settings. | Generated/base frame timing and image quality need physical capture. |
| 14. Audio | Retain qualified header/mixer guards and original-fault/valid-call regressions; improve identity/context. | No general lifetime repair or physical audio soak result. |
| 15. Reports | Resolve actual default renderer IDs, record packaged ELF IDs/hashes, label recording/startup counters, match OS exits by process/PID/time and explain known fatal categories in English. | Missing OS records do not establish clean exits; ambiguous matches are not attributed. |

The unselected item 16 extended texture probe is not added. The existing small optional graphics check does not qualify gameplay or BC copying across a GPU vendor.

Cloud validation covers compilation, configuration/parser behavior, English presentation, log/identity regressions, the patched launcher under Robolectric, ARM64 guard/ABI emulation, native forwarding/lifecycle mocks and sanitizers. Physical GPU execution, two-device LAN, sustained gameplay and audio soak tests remain unavailable. Build reports record which checks actually ran.
