# Additional crash-log review for 0.2 Hotfix 1

The 12 newly submitted bundles identify **0.1 or 1.7.4-en**, not 0.2. APK asset
metadata can describe a different binary from the one in a trace: the old 0.1
traces identify build ID `6772f303…`, while the 1.7.4 traces identify `f58f1b62…`.
Addresses from the former are not copied into the newer engine. Raw reports,
fingerprints and private data are not included here.

| Bundle / event | Evidence | Conclusion / action |
|---|---|---|
| 20111, 1.7.4-en | Three DXVK allocation failures; about 3.77 GiB allocated against a reported roughly 4.1 GiB budget; SIGSEGV record but no native trace | Memory pressure/allocation failure is real. Exact crash site is unknown; neither a leak nor fragmentation is proved. Working resolution/texture controls and bounded memory capture help investigate. No blanket memory-cap or automatic mod disabling is applied. |
| 21248, 1.7.4-en, Xclipse 530 | Actual TTY says DXVK 3.1.1; missing `VK_KHR_load_store_op_none`; no compatible adapter; native fatal caller passes `0xe79d84ff` | Renderer/driver feature incompatibility, not a game-folder failure. Try a compatible renderer such as the bundled older Sarek, then test gameplay. Do not advertise a missing feature or force an unsupported Vulkan version. The fatal report now names this device-creation failure. |
| 22161, 1.7.4-en, Xclipse | Invalid pointer dereference during resource cleanup, at engine PC `0x2a9d924`; repeated `0x05` data pattern in registers | A genuine native memory/resource-lifetime failure. Cause remains unproven. It is not repaired by ignoring the fault or masking pointer bits. Requires matching-build investigation and a device reproduction. |
| 22013, 22806, 15660, 0.1 | Same old-build PC `0x28b3d4c` and related bad-pointer pattern; 22806 also reports rejected compressed-BC readback | Investigate compressed-texture readback/resource ownership on the matching older binary. The BC message alone does not establish the cause of every event. No old offset is patched into 0.2. |
| 13621 and 13989, 0.1 | Old engine traps in a worker thread; two reports share the later PID, with differing collector exit metadata | Native trap is confirmed. Exact error needs the matching old binary; collector labels alone are insufficient. |
| 8365, 0.1 | Exit reason 2, status 9; paused lifecycle; no native trace | SIGKILL, with cause unknown. Do not label it an out-of-memory crash or a leak without a low-memory kill record. |
| 12306, 1.7.4-en, Adreno 613 | Current TTY rejects a Vulkan 1.0 adapter; selected ZIP names a different GPU range; no current native trace | Driver/API incompatibility needs a correct compatible package. The bundled SDL JNI error is **older than this exit by about 14.5 hours**, so it is not attributed to this event. |
| 2612, 1.7.4-en | Same trace SHA-256 as the earlier submitted device-loss report | Duplicate evidence, not a newly observed failure. GPU device loss remains a driver/runtime investigation; controlled reporting does not recover the device in place. |

## Confirmed 0.2 startup/UI regressions found separately

- Existing custom-driver selections were blocked by a mandatory new probe result,
  and the launcher mislabeled the setup exception as an invalid game folder.
  Readiness checks are now optional; actual driver admission/fallback remains.
- Invalid or oversized old DXVK overrides could block startup. Their text is kept;
  managed settings can be used for that attempt, with a recorded notice.
- Uppercase graphics keys were rejected by the base INI writer. They are now stored
  as valid lowercase keys and mapped to the game's original XML names on launch.
- About reparented the fixed-width System tab and replaced the original Diagnostics
  method. The System tab is untouched; About is new page 21 below Game Resources;
  Diagnostics retains its original telemetry and driver-report actions.
- The translation hook changed an anonymous download adapter's superclass
  constructor to an unrelated final adapter. The original superclass constructor
  is now preserved; translation runs on its rendered views.
- New session exports now include the current `gtav_tty.log`, with prelaunch log
  offsets to exclude old appended content, plus separately labeled startup errors.

Android-view tests execute original patched launcher bytecode through a JVM test
translation, using the same new-helper javac classes passed to D8. They exercise
navigation, click targets, config persistence and setup failure paths. They do not
qualify native GPU behavior, remote game downloads or sustained gameplay.
