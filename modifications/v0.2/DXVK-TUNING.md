# DXVK tuning and setting help

Open **DXVK → DXVK tuning**. Each information button explains the setting's
purpose, potential performance effect and tradeoffs. The controls follow the
selected bundled renderer's option catalog; unsupported controls are hidden.
Specialized compatibility and synchronization options start under **Show advanced
DXVK options**. Imported renderers retain the text editor because their support
is unknown.

Start with Default, compare the same scene, and change one setting at a time.
Lower resolution, textures or expensive graphics effects are usually more useful
starting points for a GPU/memory bottleneck than experimental synchronization
flags. There are no measured FPS gains or universal GPU settings claimed here.

**Default** removes a global override and uses the managed/renderer policy.
**Automatic**, **Off** and **None** can be explicit values and remain in the file.
Memory values use **MiB**, where 1024 MiB = 1 GiB. Reported VRAM/shared memory and
the actual allocation budget are separate controls; raising a report does not
create RAM. Keep automatic budgets unless investigating a measured problem.

Buttons, **Edit dxvk.conf** and **View effective configuration** share the same
active text for the selected game folder. The editor refreshes buttons after
saving. Custom values are displayed accurately even when absent from a preset
list. Saving or using a tuning control enables saved overrides. Turning overrides
off preserves saved text and displays managed/default active values instead.
Comments and per-application sections remain intact; matching sections can take
priority over the global values displayed by buttons. Reset DXVK tuning removes
only its global keys and retains those sections and Performance settings.

**Direct3D compatibility limit** is an exposed feature-level cap. DX10.1 is
experimental, still runs through DXVK/Vulkan and can prevent this engine from
starting. It is not a second graphics backend or a way to remove Vulkan feature
requirements. If startup fails after changing it, restore Default or DX11.

The Performance page continues to own FPS, VSync and maximum frame latency.
Explicit config values are reflected there; `dxvk.maxFrameRate` takes precedence
over `dxgi.maxFrameRate`. Changing the FPS control clears both global aliases.
Display requests and Android hints use that same active global target (or the
existing output target during frame generation). Matching executable sections
remain visible in the effective text and can impose their own renderer values.

## Compatibility

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Direct3D compatibility limit**<br>`d3d11.maxFeatureLevel` | Limits the feature level DXVK exposes. DX10.1 is experimental and may prevent this engine from starting. Use Default to remove the limit. Rendering continues through Vulkan. | A compatibility limit can change which features the game requests. DX10.1 can also prevent startup. Lowering it does not remove the renderer's Vulkan requirements or guarantee faster gameplay. |

## Memory

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Reported VRAM**<br>`dxgi.maxDeviceMemory` | Memory reported to the game. 0 uses the driver value. Actual allocation budget is controlled separately below. | May change texture-streaming decisions when a game misreads available memory. A low report can cause extra streaming; a higher report cannot add physical memory. |
| **Reported shared memory**<br>`dxgi.maxSharedMemory` | Shared memory reported to the game. 0 uses the driver value. This does not reserve additional RAM. | Mainly affects the game's memory estimates. There is no general FPS benefit from increasing it, and physical RAM remains unchanged. |
| **Graphics allocation budget**<br>`dxvk.maxMemoryBudget` | 0 keeps the renderer's budget. A small custom budget can cause heavy stutter or allocation failures. Values are in MiB. | Useful for controlled memory-pressure tests. A forced low budget often increases stalls and can cause failures. Automatic is the recommended starting point. |

## Image quality

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Anisotropic filtering**<br>`d3d11.samplerAnisotropy` | Overrides filtering for all samplers. Higher values sharpen angled textures and can increase GPU work. Reset if an effect renders incorrectly. | Lower filtering can reduce GPU sampling cost. Higher filtering improves angled textures and can cost FPS or alter effects that need a specific sampler. |
| **Tessellation limit**<br>`d3d11.maxTessFactor` | 0 uses the game's tessellation factor. Lower custom limits can reduce geometric detail. | A lower limit may reduce GPU geometry work in scenes that use tessellation. It has little effect when the scene does not use that feature. |
| **Texture LOD bias**<br>`d3d11.samplerLodBias` | Positive values favor lower-detail texture mips. Negative values sharpen textures and can increase workload. | Positive bias may reduce texture bandwidth and cache demand with softer textures. Changing sampling bias alone does not remove the allocated high-resolution mip data. |
| **Force MSAA off**<br>`d3d11.disableMsaa` | When enabled, overrides the game's MSAA setting. | Can reduce GPU work, bandwidth and render-target memory when MSAA is active. Edge quality decreases and some rendering paths may need their normal MSAA behavior. |

## Shader compilation and caches

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Shader compiler workers**<br>`dxvk.numCompilerThreads` | 0 lets the renderer choose. More workers can increase CPU contention, memory use and heat. | More workers can shorten shader preparation but compete with the game and raise heat/memory use. Automatic or a smaller count may give steadier gameplay on limited CPUs. |
| **Additional shader compiler workers**<br>`dxvk.numShaderCompilerThreads` | A separate worker setting exposed by this renderer. 0 uses its automatic policy. | More workers can shorten shader preparation but compete with the game and raise heat/memory use. Automatic or a smaller count may give steadier gameplay on limited CPUs. |
| **Async shader compilation**<br>`dxvk.enableAsync` | Available only in renderer builds that implement this option. Reset uses the existing launcher/renderer default. | Can reduce shader-compilation pauses in supported builds. Some objects or effects may appear only after their shaders finish compiling. Results depend on the renderer and scene. |
| **Shader state cache**<br>`dxvk.enableStateCache` | Allows this renderer to reuse its shader state cache across launches. | A reusable cache can reduce repeated preparation on later runs. It does not guarantee higher FPS after all shaders are already ready. |
| **Graphics pipeline libraries**<br>`dxvk.enableGraphicsPipelineLibrary` | Uses driver pipeline-library support when available. Auto includes the renderer's automatic/background optimization policy. | Can reduce pipeline-compilation hitches on compatible drivers. Renderer builds use different policies; compare with the same scene and cache state. |

## Advanced renderer settings

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Memory defragmentation**<br>`dxvk.enableMemoryDefrag` | Automatic respects the renderer's driver policy. This is a troubleshooting setting; measure before forcing it on or off. | Can reclaim wasted allocation space but moves memory and may cause stalls or driver-specific problems. Automatic follows the renderer's compatibility policy. |
| **Tiled GPU optimizations**<br>`dxvk.tilerMode` | Changes submission heuristics for tile-based GPUs. Keep Automatic or Default unless testing a specific device. | May improve submission behavior on some mobile GPUs and reduce it on others. Its effect needs a device comparison; forcing it is not a universal mobile optimization. |
| **Async game/renderer interop**<br>`dxvk.gtaAsyncInterop` | Specific to the bundled custom renderer. Compare stability on your device before overriding its default. | May reduce synchronization waits between the engine and custom renderer. Changes can affect correctness or stability, so compare both visuals and frame pacing. |

## Advanced shader and cache settings

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Combined async / pipeline cache**<br>`dxvk.gplAsyncCache` | Controls the custom renderer's cache support for async compilation and graphics pipeline libraries. | A reusable cache can reduce repeated preparation on later runs. It does not guarantee higher FPS after all shaders are already ready. |
| **Pipeline lifetime tracking**<br>`dxvk.trackPipelineLifetime` | Allows unused graphics pipeline libraries to be released more aggressively. It applies when pipeline libraries are supported and enabled. | May reduce pipeline-memory pressure. Recreating released work can add CPU cost or stutter, and the option has no effect when pipeline libraries are unused. |

## Advanced memory and buffers

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Descriptor update templates**<br>`dxvk.enableDescriptorUpdateTemplates` | Changes how resource bindings are sent to the Vulkan driver. Performance benefits reported for 32-bit Wine do not directly carry over to this ARM64 app. | Can reduce or increase driver-side binding overhead. The result is driver-dependent; there is no established FPS improvement for this port. |
| **Raw storage buffers**<br>`dxvk.useRawSsbo` | Changes the implementation of raw and structured buffer views. Automatic respects hardware alignment. Forcing On can be incompatible with devices that need larger buffer-offset alignment. | Can reduce buffer-view overhead on suitable hardware. Forcing it on incompatible alignment can break rendering; Automatic is the appropriate starting point. |
| **Cached dynamic resources**<br>`d3d11.cachedDynamicResources` | Places selected dynamic resources in CPU-cached memory. Useful when CPU readback is expensive; transfers and GPU access can behave differently. | May speed CPU readback while increasing GPU access or transfer cost. Choose it for a measured readback bottleneck, not as a general memory boost. |
| **Disable direct image mapping**<br>`d3d11.disableDirectImageMapping` | Works around mapped-image row-pitch problems that can corrupt textures. It changes the image transfer path. | A texture-correctness workaround. The alternate transfer path may cost performance; it is not a general FPS optimization. |
| **Clear released mapped memory**<br>`dxvk.zeroMappedMemory` | Zeros mapped suballocations when released. This is a debugging workaround for uninitialized buffer data. | Usually adds substantial CPU work. Use it to investigate incorrect buffer initialization, not to improve FPS. |

## Advanced image quality

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Clamp negative texture LOD bias**<br>`d3d11.clampNegativeLodBias` | Clamps negative texture bias to zero after the additional LOD bias is applied. This can reduce excessively sharp or shimmering textures. | May reduce texture shimmering and sampling pressure in affected games, with softer detail. It may have no effect when the game uses no negative bias. |
| **Per-sample shading with MSAA**<br>`d3d11.forceSampleRateShading` | Runs shading per MSAA sample rather than per pixel. This can improve some edges and effects at substantially greater GPU cost. | Primarily a quality option. It normally increases GPU work with MSAA and can reduce FPS significantly. |

## Advanced synchronization

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Relaxed shader barriers**<br>`d3d11.relaxedBarriers` | Skips some synchronization around shader memory writes. This is experimental: incorrect synchronization can cause artifacts or GPU failures. | May reduce synchronization cost, but can introduce rendering errors or device loss. Keep Default unless performing a controlled stability/performance test. |
| **Relaxed graphics barriers**<br>`d3d11.relaxedGraphicsBarriers` | Relaxes memory-write synchronization in graphics shaders. Compute shaders keep their normal barrier policy. | May reduce synchronization cost, but can introduce rendering errors or device loss. Keep Default unless performing a controlled stability/performance test. |
| **Extra compute memory barriers**<br>`d3d11.forceComputeUavBarriers` | Adds synchronization for read/write resources in compute shaders. Use it when investigating shader synchronization errors. | Adds synchronization and may reduce shader/compiler efficiency. Useful for correctness investigation; normally not a performance improvement. |
| **Strict shared shader memory access**<br>`d3d11.forceVolatileTgsmAccess` | Adds stricter ordering around thread-group shared memory. It is a compatibility workaround for unsynchronized shader access. | Adds synchronization and may reduce shader/compiler efficiency. Useful for correctness investigation; normally not a performance improvement. |
| **D3D context locking**<br>`d3d11.enableContextLock` | Forces multithread protection for the D3D context to investigate renderer race conditions. It does not repair arbitrary engine memory corruption. | Can help investigate context races. Locking adds overhead and may lower CPU throughput; it is not a fix for every crash. |
| **DXVK frame pacing mode**<br>`dxvk.framePace` | Controls CPU/GPU overlap in the custom GPLALL renderer. Minimum latency waits more aggressively and can lower throughput. These modes keep the existing FPS cap. | Trades input latency, CPU/GPU overlap and throughput. Minimum latency can reduce FPS; smoother or more responsive output needs measurement on the selected device. |
| **CPU frame overlap**<br>`dxvk.lowLatencyAllowCpuFramesOverlap` | Allows CPU processing of adjacent frames to overlap in the custom renderer's low-latency pacing mode. | Overlap can improve throughput. Disabling it may lower queue latency but can also lower FPS. It applies only to the relevant low-latency mode. |

## Advanced shader correctness

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **Initialize shared shader memory**<br>`d3d11.zeroInitWorkgroupMemory` | Clears workgroup memory before shader use. This can help shaders that depend on uninitialized values. | A shader-correctness workaround that adds initialization work and may reduce FPS. |
| **Stable vertex position calculations**<br>`d3d11.invariantPosition` | Uses invariant position calculations to help prevent depth flicker or z-fighting between passes. | Primarily prevents visible depth conflicts. It may carry a small performance cost; turning it off can reintroduce flicker. |
| **Precise shader floating-point behavior**<br>`d3d11.floatControls` | Requests the shader floating-point behavior used by this renderer for compatibility and consistent results. | Primarily a compatibility choice. Relaxing behavior is not a guaranteed speedup and may change shader results. |

## On-screen information

| Control / config key | Purpose | Expected effect and tradeoff |
|---|---|---|
| **DXVK information overlay**<br>`dxvk.hud` | Shows renderer information during gameplay. Its frame-time display describes the renderer's own measurements. | Helps compare renderer behavior and diagnose slowdowns. Showing extra measurements adds some overhead; the overlay itself does not improve FPS. |

## Other controls

Information buttons also cover graphics quality, population/distance, render
scale, quality restoration, FPS/VSync/queue latency, display matching, Android
hints, Turnip memory/One UI controls, driver testing, shader-cache cleanup,
configuration editing/import and the simplified diagnostic flow. Quality changes
trade visual detail for workload. Driver workarounds address compatibility and
are not presented as established FPS improvements. Diagnostics measure and
explain problems; they do not themselves optimize gameplay.

## References and limits

Option presence comes from each actual bundled renderer's ELF strings, generated
into `xhyn-dxvk-options.json` during the build. Common/custom GPLALL types, units
and semantics were compared against the published GPLALL 2.6.8-2 sources and its
configuration comments; reference URLs and content hashes are in
[dxvk-option-references.json](dxvk-option-references.json). Sarek's additional
compiler-worker key is exposed only when its binary advertises it. GPLALL pacing
mode values are not assumed for Sarek merely because it contains the same key.

The unchanged upstream configuration parser is exercised by the host integration
tests, including values produced by the UI writer. Android-view tests exercise
button/editor synchronization, custom ranges, renderer changes, defaults,
information buttons and disabled overrides. These do not prove GPU behavior or
performance on physical devices. The renderer's driver requirements still apply.
