package com.gtavsource.android;
import java.util.Locale;

/** Explanations from observed errors; absence of a match is not a successful test. */
public final class XhynV02Advice {
    public static String explain(String source){
        String text=source.toLowerCase(Locale.ROOT);
        if(text.contains("feature_limit=10_1")&&(text.contains("0xe79d84ff")||text.contains("no suitable adapter")||text.contains("no adapters found")))return "The graphics device could not start while a DX10.1 compatibility limit was active. In DXVK tuning, set Direct3D compatibility limit to Default or DX11, then restart. This engine may require feature level 11.0. Save a report if it still fails.";
        if(text.contains("is not registered")||text.contains("script / engine api mismatch"))return "The game scripts asked for functions this engine does not provide. Use game and script files that match this build. Save a report so the failing script can be identified.";
        if(text.contains("0xe79d84ff")||text.contains("no suitable adapter")||text.contains("no adapters found")||text.contains("dualSrcBlend".toLowerCase(Locale.ROOT)))return "The selected graphics renderer could not use this GPU or driver. Run the optional graphics check in Vulkan Drivers, then try a compatible renderer or driver. A successful small test does not guarantee the game will run.";
        if(text.contains("vk_error_device_lost")||text.contains("0x9cf7bd06")||text.contains("dxgi_error_device_reset"))return "The graphics device stopped responding or could not start. Close the game, then try a compatible driver or renderer. Lower graphics settings may reduce load. Save a report if it happens again.";
        if(text.contains("failed to allocate")||text.contains("allocation failed")||text.contains("out of device memory"))return "A graphics memory allocation failed. Try lower textures, resolution and anti-aliasing. This can reduce memory demand; the report is needed to determine the cause.";
        if(text.contains("0x8392273d")||text.contains("invalid resource mapping"))return "The engine could not use a game resource safely. The cause may be incompatible data or an engine resource-handling problem. Save the report so the affected resource can be investigated.";
        if(text.contains("xhyn_native_error_v1"))return "The game engine stopped after an error. A report was saved on this device. Share it so the cause can be investigated.";
        if(text.contains("could not prepare")||text.contains("saved dxvk.conf overrides could not be applied"))return "Some graphics settings could not be applied. Your saved configuration was kept. Review dxvk.conf or open technical details for the exact setting.";
        return "There is no saved explanation for the last attempt yet. Turn on Record next game, reproduce the problem, then choose Save report to share.";
    }
    private XhynV02Advice(){}
}
