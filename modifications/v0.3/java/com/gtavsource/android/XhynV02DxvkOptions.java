package com.gtavsource.android;

import java.util.*;

/** Known option types/units; visibility still requires the selected binary's catalog. */
public final class XhynV02DxvkOptions {
    public static final class Option {
        public final String group,key,title,help;
        public final String[] labels,values;
        public final boolean memory;
        Option(String group,String key,String title,String help,String[] labels,String[] values,boolean memory){
            this.group=group;this.key=key;this.title=title;this.help=help;this.labels=labels;this.values=values;this.memory=memory;
        }
        public int index(String value){if(value==null)return -1;for(int n=0;n<values.length;n++)if(values[n].equalsIgnoreCase(value))return n;return -1;}
        public String display(String value){
            if(value==null)return "Default";
            if(memory){try{return Integer.parseInt(value)==0?"Automatic":Integer.parseInt(value)+" MiB";}catch(NumberFormatException invalid){return value;}}
            int index=index(value);return index>=0?labels[index]:value+" (custom)";
        }
        public boolean advanced(){return group.startsWith("Advanced");}
        public String information(){return "What it does\n"+help+"\n\nPotential performance effect\n"+effect(key)+"\n\nStart with Default. Compare the same scene after changing one setting. Per-application sections can override global controls.\n\nConfig key: "+key;}
    }
    private static String[] a(String... values){return values;}
    private static Option option(String group,String key,String title,String help,String[] labels,String[] values){return new Option(group,key,title,help,labels,values,false);}
    private static Option memory(String key,String title,String help){return new Option("Memory",key,title,help,a("Automatic","512 MiB","1 GiB","2 GiB","3 GiB","4 GiB","6 GiB","8 GiB","16 GiB"),a("0","512","1024","2048","3072","4096","6144","8192","16384"),true);}
    public static final List<Option> OPTIONS=Collections.unmodifiableList(Arrays.asList(
        option("Compatibility","d3d11.maxFeatureLevel","Direct3D compatibility limit",
            "Limits the feature level DXVK exposes. DX10.1 is experimental and may prevent this engine from starting. Use Default to remove the limit. Rendering continues through Vulkan.",
            a("DX11 (11.0)","DX10.1 (experimental)"),a("11_0","10_1")),
        memory("dxgi.maxDeviceMemory","Reported VRAM","Memory reported to the game. 0 uses the driver value. Actual allocation budget is controlled separately below."),
        memory("dxgi.maxSharedMemory","Reported shared memory","Shared memory reported to the game. 0 uses the driver value. This does not reserve additional RAM."),
        memory("dxvk.maxMemoryBudget","Graphics allocation budget","0 keeps the renderer's budget. A small custom budget can cause heavy stutter or allocation failures. Values are in MiB."),
        option("Image quality","d3d11.samplerAnisotropy","Anisotropic filtering",
            "Overrides filtering for all samplers. Higher values sharpen angled textures and can increase GPU work. Reset if an effect renders incorrectly.",
            a("Off","2x","4x","8x","16x"),a("0","2","4","8","16")),
        option("Image quality","d3d11.maxTessFactor","Tessellation limit",
            "0 uses the game's tessellation factor. Lower custom limits can reduce geometric detail.",a("Game setting","8","16","32","64"),a("0","8","16","32","64")),
        option("Image quality","d3d11.samplerLodBias","Texture LOD bias",
            "Positive values favor lower-detail texture mips. Negative values sharpen textures and can increase workload.",
            a("-2","-1","0","+0.5","+1"),a("-2","-1","0","0.5","1")),
        option("Image quality","d3d11.disableMsaa","Force MSAA off",
            "When enabled, overrides the game's MSAA setting.",a("On","Off"),a("True","False")),
        option("Shader compilation and caches","dxvk.numCompilerThreads","Shader compiler workers",
            "0 lets the renderer choose. More workers can increase CPU contention, memory use and heat.",a("Automatic","1","2","3","4","6","8","12","16"),a("0","1","2","3","4","6","8","12","16")),
        option("Shader compilation and caches","dxvk.numShaderCompilerThreads","Additional shader compiler workers",
            "A separate worker setting exposed by this renderer. 0 uses its automatic policy.",a("Automatic","1","2","3","4","6","8","12","16"),a("0","1","2","3","4","6","8","12","16")),
        option("Shader compilation and caches","dxvk.enableAsync","Async shader compilation",
            "Available only in renderer builds that implement this option. Reset uses the existing launcher/renderer default.",a("On","Off"),a("True","False")),
        option("Shader compilation and caches","dxvk.enableStateCache","Shader state cache",
            "Allows this renderer to reuse its shader state cache across launches.",a("On","Off"),a("True","False")),
        option("Shader compilation and caches","dxvk.enableGraphicsPipelineLibrary","Graphics pipeline libraries",
            "Uses driver pipeline-library support when available. Auto includes the renderer's automatic/background optimization policy.",a("Automatic","On","Off"),a("Auto","True","False")),
        option("Advanced renderer settings","dxvk.enableMemoryDefrag","Memory defragmentation",
            "Automatic respects the renderer's driver policy. This is a troubleshooting setting; measure before forcing it on or off.",a("Automatic","On","Off"),a("Auto","True","False")),
        option("Advanced renderer settings","dxvk.tilerMode","Tiled GPU optimizations",
            "Changes submission heuristics for tile-based GPUs. Keep Automatic or Default unless testing a specific device.",a("Automatic","On","Off"),a("Auto","True","False")),
        option("Advanced renderer settings","dxvk.gtaAsyncInterop","Async game/renderer interop",
            "Specific to the bundled custom renderer. Compare stability on your device before overriding its default.",a("On","Off"),a("True","False")),
        option("Advanced shader and cache settings","dxvk.gplAsyncCache","Combined async / pipeline cache",
            "Controls the custom renderer's cache support for async compilation and graphics pipeline libraries.",a("On","Off"),a("True","False")),
        option("Advanced shader and cache settings","dxvk.trackPipelineLifetime","Pipeline lifetime tracking",
            "Allows unused graphics pipeline libraries to be released more aggressively. It applies when pipeline libraries are supported and enabled.",a("Automatic","On","Off"),a("Auto","True","False")),
        option("Advanced memory and buffers","dxvk.enableDescriptorUpdateTemplates","Descriptor update templates",
            "Changes how resource bindings are sent to the Vulkan driver. Performance benefits reported for 32-bit Wine do not directly carry over to this ARM64 app.",a("On","Off"),a("True","False")),
        option("Advanced memory and buffers","dxvk.useRawSsbo","Raw storage buffers",
            "Changes the implementation of raw and structured buffer views. Automatic respects hardware alignment. Forcing On can be incompatible with devices that need larger buffer-offset alignment.",a("Automatic","On (experimental)","Off"),a("Auto","True","False")),
        option("Advanced memory and buffers","d3d11.cachedDynamicResources","Cached dynamic resources",
            "Places selected dynamic resources in CPU-cached memory. Useful when CPU readback is expensive; transfers and GPU access can behave differently.",a("None","Constant buffers","Vertex and index buffers","All dynamic resources"),a("","c","vi","a")),
        option("Advanced memory and buffers","d3d11.disableDirectImageMapping","Disable direct image mapping",
            "Works around mapped-image row-pitch problems that can corrupt textures. It changes the image transfer path.",a("On","Off"),a("True","False")),
        option("Advanced memory and buffers","dxvk.zeroMappedMemory","Clear released mapped memory",
            "Zeros mapped suballocations when released. This is a debugging workaround for uninitialized buffer data.",a("On","Off"),a("True","False")),
        option("Advanced image quality","d3d11.clampNegativeLodBias","Clamp negative texture LOD bias",
            "Clamps negative texture bias to zero after the additional LOD bias is applied. This can reduce excessively sharp or shimmering textures.",a("On","Off"),a("True","False")),
        option("Advanced image quality","d3d11.forceSampleRateShading","Per-sample shading with MSAA",
            "Runs shading per MSAA sample rather than per pixel. This can improve some edges and effects at substantially greater GPU cost.",a("On","Off"),a("True","False")),
        option("Advanced synchronization","d3d11.relaxedBarriers","Relaxed shader barriers",
            "Skips some synchronization around shader memory writes. This is experimental: incorrect synchronization can cause artifacts or GPU failures.",a("On (experimental)","Off"),a("True","False")),
        option("Advanced synchronization","d3d11.relaxedGraphicsBarriers","Relaxed graphics barriers",
            "Relaxes memory-write synchronization in graphics shaders. Compute shaders keep their normal barrier policy.",a("On (experimental)","Off"),a("True","False")),
        option("Advanced synchronization","d3d11.forceComputeUavBarriers","Extra compute memory barriers",
            "Adds synchronization for read/write resources in compute shaders. Use it when investigating shader synchronization errors.",a("On","Off"),a("True","False")),
        option("Advanced synchronization","d3d11.forceVolatileTgsmAccess","Strict shared shader memory access",
            "Adds stricter ordering around thread-group shared memory. It is a compatibility workaround for unsynchronized shader access.",a("On","Off"),a("True","False")),
        option("Advanced synchronization","d3d11.enableContextLock","D3D context locking",
            "Forces multithread protection for the D3D context to investigate renderer race conditions. It does not repair arbitrary engine memory corruption.",a("On","Off"),a("True","False")),
        option("Advanced synchronization","dxvk.framePace","DXVK frame pacing mode",
            "Controls CPU/GPU overlap in the custom GPLALL renderer. Minimum latency waits more aggressively and can lower throughput. These modes keep the existing FPS cap.",a("Standard frame latency","Low latency","Minimum latency"),a("max-frame-latency","low-latency","min-latency")),
        option("Advanced synchronization","dxvk.lowLatencyAllowCpuFramesOverlap","CPU frame overlap",
            "Allows CPU processing of adjacent frames to overlap in the custom renderer's low-latency pacing mode.",a("On","Off"),a("True","False")),
        option("Advanced shader correctness","d3d11.zeroInitWorkgroupMemory","Initialize shared shader memory",
            "Clears workgroup memory before shader use. This can help shaders that depend on uninitialized values.",a("On","Off"),a("True","False")),
        option("Advanced shader correctness","d3d11.invariantPosition","Stable vertex position calculations",
            "Uses invariant position calculations to help prevent depth flicker or z-fighting between passes.",a("On","Off"),a("True","False")),
        option("Advanced shader correctness","d3d11.floatControls","Precise shader floating-point behavior",
            "Requests the shader floating-point behavior used by this renderer for compatibility and consistent results.",a("On","Off"),a("True","False")),
        option("On-screen information","dxvk.hud","DXVK information overlay",
            "Shows renderer information during gameplay. Its frame-time display describes the renderer's own measurements.",a("Off","FPS","FPS and frame times","FPS, memory and compiler"),a("","fps","fps,frametimes","fps,frametimes,memory,compiler"))
    ));
    public static List<Option> supported(Set<String> keys){List<Option> output=new ArrayList<>();for(Option option:OPTIONS)if(keys.contains(option.key))output.add(option);return output;}
    public static List<Option> supported(Set<String> keys,String renderer){List<Option> options=supported(keys);options.removeIf(option->option.key.equals("dxvk.framePace")&&!renderer.equals("gplall-async"));return options;}
    public static Option find(String key){for(Option option:OPTIONS)if(option.key.equals(key))return option;throw new IllegalArgumentException("Unknown DXVK control: "+key);}
    private static String effect(String key){
        switch(key){
        case "d3d11.maxFeatureLevel":return "A compatibility limit can change which features the game requests. DX10.1 can also prevent startup. Lowering it does not remove the renderer's Vulkan requirements or guarantee faster gameplay.";
        case "dxgi.maxDeviceMemory":return "May change texture-streaming decisions when a game misreads available memory. A low report can cause extra streaming; a higher report cannot add physical memory.";
        case "dxgi.maxSharedMemory":return "Mainly affects the game's memory estimates. There is no general FPS benefit from increasing it, and physical RAM remains unchanged.";
        case "dxvk.maxMemoryBudget":return "Useful for controlled memory-pressure tests. A forced low budget often increases stalls and can cause failures. Automatic is the recommended starting point.";
        case "d3d11.samplerAnisotropy":return "Lower filtering can reduce GPU sampling cost. Higher filtering improves angled textures and can cost FPS or alter effects that need a specific sampler.";
        case "d3d11.maxTessFactor":return "A lower limit may reduce GPU geometry work in scenes that use tessellation. It has little effect when the scene does not use that feature.";
        case "d3d11.samplerLodBias":return "Positive bias may reduce texture bandwidth and cache demand with softer textures. Changing sampling bias alone does not remove the allocated high-resolution mip data.";
        case "d3d11.disableMsaa":return "Can reduce GPU work, bandwidth and render-target memory when MSAA is active. Edge quality decreases and some rendering paths may need their normal MSAA behavior.";
        case "dxvk.numCompilerThreads":case "dxvk.numShaderCompilerThreads":return "More workers can shorten shader preparation but compete with the game and raise heat/memory use. Automatic or a smaller count may give steadier gameplay on limited CPUs.";
        case "dxvk.enableAsync":return "Can reduce shader-compilation pauses in supported builds. Some objects or effects may appear only after their shaders finish compiling. Results depend on the renderer and scene.";
        case "dxvk.enableStateCache":case "dxvk.gplAsyncCache":return "A reusable cache can reduce repeated preparation on later runs. It does not guarantee higher FPS after all shaders are already ready.";
        case "dxvk.enableGraphicsPipelineLibrary":return "Can reduce pipeline-compilation hitches on compatible drivers. Renderer builds use different policies; compare with the same scene and cache state.";
        case "dxvk.enableMemoryDefrag":return "Can reclaim wasted allocation space but moves memory and may cause stalls or driver-specific problems. Automatic follows the renderer's compatibility policy.";
        case "dxvk.tilerMode":return "May improve submission behavior on some mobile GPUs and reduce it on others. Its effect needs a device comparison; forcing it is not a universal mobile optimization.";
        case "dxvk.gtaAsyncInterop":return "May reduce synchronization waits between the engine and custom renderer. Changes can affect correctness or stability, so compare both visuals and frame pacing.";
        case "dxvk.trackPipelineLifetime":return "May reduce pipeline-memory pressure. Recreating released work can add CPU cost or stutter, and the option has no effect when pipeline libraries are unused.";
        case "dxvk.enableDescriptorUpdateTemplates":return "Can reduce or increase driver-side binding overhead. The result is driver-dependent; there is no established FPS improvement for this port.";
        case "dxvk.useRawSsbo":return "Can reduce buffer-view overhead on suitable hardware. Forcing it on incompatible alignment can break rendering; Automatic is the appropriate starting point.";
        case "d3d11.cachedDynamicResources":return "May speed CPU readback while increasing GPU access or transfer cost. Choose it for a measured readback bottleneck, not as a general memory boost.";
        case "d3d11.disableDirectImageMapping":return "A texture-correctness workaround. The alternate transfer path may cost performance; it is not a general FPS optimization.";
        case "dxvk.zeroMappedMemory":return "Usually adds substantial CPU work. Use it to investigate incorrect buffer initialization, not to improve FPS.";
        case "d3d11.clampNegativeLodBias":return "May reduce texture shimmering and sampling pressure in affected games, with softer detail. It may have no effect when the game uses no negative bias.";
        case "d3d11.forceSampleRateShading":return "Primarily a quality option. It normally increases GPU work with MSAA and can reduce FPS significantly.";
        case "d3d11.relaxedBarriers":case "d3d11.relaxedGraphicsBarriers":return "May reduce synchronization cost, but can introduce rendering errors or device loss. Keep Default unless performing a controlled stability/performance test.";
        case "d3d11.forceComputeUavBarriers":case "d3d11.forceVolatileTgsmAccess":return "Adds synchronization and may reduce shader/compiler efficiency. Useful for correctness investigation; normally not a performance improvement.";
        case "d3d11.enableContextLock":return "Can help investigate context races. Locking adds overhead and may lower CPU throughput; it is not a fix for every crash.";
        case "dxvk.framePace":return "Trades input latency, CPU/GPU overlap and throughput. Minimum latency can reduce FPS; smoother or more responsive output needs measurement on the selected device.";
        case "dxvk.lowLatencyAllowCpuFramesOverlap":return "Overlap can improve throughput. Disabling it may lower queue latency but can also lower FPS. It applies only to the relevant low-latency mode.";
        case "d3d11.zeroInitWorkgroupMemory":return "A shader-correctness workaround that adds initialization work and may reduce FPS.";
        case "d3d11.invariantPosition":return "Primarily prevents visible depth conflicts. It may carry a small performance cost; turning it off can reintroduce flicker.";
        case "d3d11.floatControls":return "Primarily a compatibility choice. Relaxing behavior is not a guaranteed speedup and may change shader results.";
        case "dxvk.hud":return "Helps compare renderer behavior and diagnose slowdowns. Showing extra measurements adds some overhead; the overlay itself does not improve FPS.";
        default:return "No measured performance improvement has been established for this setting. Keep Default until a device comparison shows a benefit.";
        }
    }
    private XhynV02DxvkOptions(){}
}
