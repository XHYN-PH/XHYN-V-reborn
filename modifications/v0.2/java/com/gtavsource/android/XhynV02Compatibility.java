package com.gtavsource.android;
import org.json.JSONObject;

/** Advisory only. It must never become a mandatory game-folder or launch gate. */
public final class XhynV02Compatibility {
    public static String advice(JSONObject result,String renderer){
        if(result==null)return "Run the optional graphics check to identify the selected driver.";
        if(!"PASS".equals(result.optString("status")))return "The small graphics test did not finish successfully. Read its message before trying a different renderer or driver.";
        StringBuilder text=new StringBuilder("The small graphics test passed. Gameplay still needs testing.");
        if("upstream".equals(renderer)){
            if(result.optInt("apiVersion")<((1<<22)|(3<<12)))text.append("\nThis upstream DXVK build needs a newer Vulkan feature set. Try the bundled Sarek renderer as a compatibility comparison.");
            if(!result.optBoolean("dualSrcBlend",false))text.append("\nThe driver does not report dual-source blending. Upstream DXVK may reject it; compare with Sarek.");
            if(result.has("loadStoreOpNone")&&!result.optBoolean("loadStoreOpNone",false)&&result.optInt("apiVersion")<((1<<22)|(4<<12)))text.append("\nA load/store feature used by upstream DXVK was not reported. This matches a known Xclipse startup failure; compare with Sarek.");
        }
        if(!result.optBoolean("reportedBcCompression",false))text.append("\nNative BC texture compression was not reported. Texture conversion may use more memory. Test textures and readback before treating this combination as compatible.");
        if(result.optInt("vendorId")==0x144d)text.append("\nFor Xclipse, start with the phone's system Vulkan driver.");
        if("custom".equals(renderer))text.append("\nImported renderer requirements are unknown until that exact build is tested.");
        return text.toString();
    }
    private XhynV02Compatibility(){}
}
