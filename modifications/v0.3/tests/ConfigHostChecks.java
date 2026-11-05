import com.gtavsource.android.XhynV02Config;
import com.gtavsource.android.XhynV02DriverPolicy;
import com.gtavsource.android.XhynV02DxvkOptions;
import com.gtavsource.android.XhynV02SettingHelp;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ConfigHostChecks {
    static int checks;
    static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); checks++; }
    static void invalid(String text, String name) throws Exception {
        try { XhynV02Config.validate(text); throw new AssertionError(name); }
        catch (IOException expected) { check(expected.getMessage().startsWith("Line ") || expected.getMessage().contains("64 KiB") || expected.getMessage().contains("null character"), name); }
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("dxvk.numCompilerThreads", "2");
        defaults.put("dxgi.maxFrameRate", "60");
        defaults.put("dxgi.maxFrameLatency", "2");
        defaults.put("dxvk.hud", "\"fps\"");
        String overrides = "# User comment remains intact\n"
                + "dxvk.numCompilerThreads = 4\ndxvk.hud = \"fps,version\"\n"
                + "dxgi.maxDeviceMemory = 1536\ndxgi.maxSharedMemory = 4096\n"
                + "dxvk.maxMemoryBudget = 1024\nd3d11.samplerAnisotropy = 16\n"
                + "d3d11.maxTessFactor = 16\nd3d11.samplerLodBias = 1.0\n"
                + "d3d11.disableMsaa = True\ndxvk.enableStateCache = True\ndxvk.useRawSsbo = True\n"
                + "d3d11.relaxedBarriers = True\nd3d11.relaxedGraphicsBarriers = True\n"
                + "d3d11.maxFeatureLevel = \"11_0\"\n";
        String effective = XhynV02Config.effective(defaults, overrides, true);
        check(XhynV02Config.globals(effective).get("dxvk.numCompilerThreads").equals("4"), "Saved override wins over generated default");
        check(XhynV02Config.globals(XhynV02Config.effective(defaults, overrides, false)).get("dxvk.numCompilerThreads").equals("2"), "Disabled saved overrides leave managed defaults active");
        check(overrides.startsWith("# User comment"), "Preparing config does not rewrite saved source");
        check(XhynV02Config.effective(defaults,effective,true).equals(effective),"Editing the complete active config is idempotent");
        String repeated=effective;for(int n=0;n<100;n++)repeated=XhynV02Config.effective(defaults,repeated,true);
        check(repeated.equals(effective),"Repeated saves do not duplicate generated settings or grow the file");
        String unchanged=XhynV02Config.editedOverrides(effective,effective,overrides,defaults.keySet());
        check(!XhynV02Config.globals(unchanged).containsKey("dxgi.maxFrameRate"),"Saving the full editor does not freeze a generated frame cap");
        check(XhynV02Config.globals(unchanged).get("dxvk.numCompilerThreads").equals("4"),"Existing explicit overrides remain explicit after editing");
        String changed=XhynV02Config.editedOverrides(effective,XhynV02Config.setGlobal(effective,"dxgi.maxFrameRate","45"),overrides,defaults.keySet());
        check(XhynV02Config.globals(changed).get("dxgi.maxFrameRate").equals("45"),"Changed generated fields become explicit overrides");
        String scoped = "# header\ndxvk.numCompilerThreads = 3\n[app_process64]\ndxvk.numCompilerThreads = 9\n";
        String edited = XhynV02Config.setGlobal(scoped, "dxvk.numCompilerThreads", "5");
        check(XhynV02Config.globals(edited).get("dxvk.numCompilerThreads").equals("5"), "Global edit updates the control value");
        check(edited.contains("[app_process64]\ndxvk.numCompilerThreads = 9"), "Global edit preserves executable-scoped overrides");
        check(edited.contains("# header"), "Global edit preserves comments");
        check(XhynV02Config.hasSections(edited), "Executable section disclosed");
        check(!XhynV02Config.globals(XhynV02Config.setGlobal(edited, "dxvk.numCompilerThreads", null)).containsKey("dxvk.numCompilerThreads"), "Deleting global override leaves scoped value out of global controls");
        check(XhynV02Config.globals("\ufeffdxvk.hud = \"fps,frametimes\"\r\n").get("dxvk.hud").equals("fps,frametimes"), "UTF8 BOM and CRLF accepted");
        check(XhynV02Config.globals("dxvk.hud = \"\"\n").get("dxvk.hud").isEmpty(), "Explicit HUD off retained");
        check(XhynV02Config.unsupported("dxvk.futureOption = True\n", Set.of("dxvk.hud")).equals(List.of("dxvk.futureOption")), "Unknown option is disclosed without claiming support");
        for (String text : Arrays.asList("dxvk.numCompilerThreads = -1", "dxvk.numCompilerThreads = 17", "dxgi.maxFrameRate = nope", "dxgi.maxFrameLatency = 9", "dxvk.hud = True", "d3d11.disableMsaa = perhaps", "d3d11.samplerLodBias = NaN", "d3d11.samplerLodBias = 2", "[broken", "export DXVK_CONFIG=foo", "d3d9.presentInterval = 1", "dxvk.hud = \"unfinished", "dxvk.numCompilerThreads = 2; dxgi.maxFrameRate = 30")) invalid(text, "Invalid config rejected: " + text);
        invalid("dxvk.hud=\0", "Null characters rejected");
        invalid("#" + "a".repeat(65536), "Oversized config rejected");
        Set<String> controlKeys=new HashSet<>();String controls="# tuning fixture\n";
        for(XhynV02DxvkOptions.Option control:XhynV02DxvkOptions.OPTIONS){
            check(controlKeys.add(control.key),"One tuning control per config key: "+control.key);
            check(control.labels.length==control.values.length,"Choice labels match values: "+control.key);
            check(control.information().contains("What it does")&&control.information().contains("Potential performance effect")&&!control.information().contains("No measured performance improvement has been established"),"Specific purpose and performance help: "+control.key);
            for(String value:control.values){String text=XhynV02Config.setGlobal("",control.key,value);XhynV02Config.validate(text);check(value.equals(XhynV02Config.globals(text).get(control.key)),"Preset survives the actual config writer: "+control.key+"="+value);}
            controls=XhynV02Config.setGlobal(controls,control.key,control.values[0]);
        }
        controls=XhynV02Config.setGlobal(controls,"d3d11.maxFeatureLevel","10_1");
        controls=XhynV02Config.setGlobal(controls,"dxgi.maxDeviceMemory","3072");
        controls=XhynV02Config.setGlobal(controls,"dxgi.maxSharedMemory","2048");
        controls=XhynV02Config.setGlobal(controls,"dxvk.maxMemoryBudget","1536");
        controls=XhynV02Config.setGlobal(controls,"d3d11.cachedDynamicResources","vi");
        controls=XhynV02Config.setGlobal(controls,"dxvk.enableAsync","False");
        controls=XhynV02Config.setGlobal(controls,"dxvk.framePace","low-latency");
        Files.writeString(output.resolve("tuning-controls.conf"),controls);
        check(XhynV02DxvkOptions.find("dxgi.maxDeviceMemory").display("1536").equals("1536 MiB"),"Non-preset memory is displayed accurately");
        check(XhynV02DxvkOptions.find("d3d11.samplerAnisotropy").display("3").equals("3 (custom)"),"Non-preset numeric value is not mislabeled as Default");
        check(XhynV02DxvkOptions.supported(controlKeys,"sarek").stream().noneMatch(o->o.key.equals("dxvk.framePace")),"GPLALL pacing mode values are not assumed for Sarek");
        check(XhynV02DxvkOptions.supported(Set.of()).isEmpty(),"Unknown renderer exposes no unverified tuning buttons");
        for(String text:List.of("dxgi.maxDeviceMemory = 65537","dxvk.maxMemoryBudget = -1","d3d11.maxTessFactor = 7","d3d11.maxTessFactor = 65","d3d11.samplerAnisotropy = 17","d3d11.maxFeatureLevel = dx10","d3d11.cachedDynamicResources = z","dxvk.useRawSsbo = yes","d3d11.floatControls = sometimes"))invalid(text,"Invalid tuning value rejected: "+text);
        for(String key:List.of("TextureQuality","ShadowQuality","ReflectionQuality","GrassQuality","ParticleQuality","WaterQuality","PostFX","Tessellation","MSAA","ReflectionMSAA","SSAO","CityDensity","PedVarietyMultiplier","VehicleVarietyMultiplier","LodScale","PedLodBias","VehicleLodBias","vsync","latency","match_refresh","performance_hints","turnip_memory","one_ui_workaround","dxvk_conf_enabled","capture","Resolution / render scale","Restore graphics quality defaults","Clear XHYN shader caches","Save report to share","Edit dxvk.conf"))check(XhynV02SettingHelp.get(key)!=null&&XhynV02SettingHelp.get(key).contains("Potential performance effect"),"Purpose and tradeoffs for other setting: "+key);
        check(XhynV02DriverPolicy.memoryFlags("flushall,gmem,sysmem,other", 0).equals("flushall,other"), "Automatic memory mode preserves unrelated flags");
        check(XhynV02DriverPolicy.memoryFlags("flushall,sysmem", 1).equals("flushall,gmem"), "GMEM replaces only owned memory flags");
        check(XhynV02DriverPolicy.memoryFlags("gmem,flushall", 2).equals("flushall,sysmem"), "SYSMEM replaces only owned memory flags");
        check(XhynV02DriverPolicy.oneUiFeatures("existing=7:enable_tp_ubwc_flag_hint=0").equals("existing=7:enable_tp_ubwc_flag_hint=1"), "One UI workaround merges existing features");
        check(XhynV02DriverPolicy.isTurnip(0x5143, 18, "Mesa"), "Turnip identity uses actual vendor and driver ID");
        check(!XhynV02DriverPolicy.isTurnip(0x13b5, 20, "panvk"), "Turnip options do not apply to PanVK");
        check(!XhynV02DriverPolicy.isTurnip(0x144d, 21, "Samsung"), "Turnip options do not apply to Xclipse");
        check(XhynV02DriverPolicy.family(0x144d, "Samsung Xclipse 940", 21).equals("xclipse"), "Xclipse family recognized");
        check(XhynV02DriverPolicy.family(0x13b5, "Mali-G57 MC4", 20).equals("mali"), "PanVK remains Mali hardware");
        File saved = output.resolve("saved.conf").toFile();
        XhynV02Config.write(saved, overrides);
        check(XhynV02Config.read(saved).equals(overrides), "Saved text survives atomic write/read");
        XhynV02Config.effective(defaults, XhynV02Config.read(saved), false);
        check(XhynV02Config.read(saved).equals(overrides), "Disabling overrides retains the saved file");
        Files.writeString(output.resolve("all-options-effective.conf"), effective);
        String global = XhynV02Config.setGlobal("dxvk.maxMemoryBudget=2048\n", "dxvk.numCompilerThreads", "3");
        global = XhynV02Config.setGlobal(global, "dxvk.hud", "");
        Files.writeString(output.resolve("ui-global.conf"), global);
        Files.writeString(output.resolve("ui-scoped.conf"), global + "[app_process64]\ndxvk.numCompilerThreads = 9\n");
        System.out.println("PASS " + checks + " configuration and driver-policy host checks");
    }
}
