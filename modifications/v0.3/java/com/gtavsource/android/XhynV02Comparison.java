package com.gtavsource.android;

import java.io.File;
import java.util.Locale;
import org.json.JSONObject;

/** Compares recorded counters only when renderer, driver and settings agree. */
public final class XhynV02Comparison {
    private static String read(File directory,String name)throws Exception{return XhynV02Config.read(new File(directory,name));}
    public static boolean comparable(File first,File second)throws Exception{
        JSONObject a=new JSONObject(read(first,"session.json")),b=new JSONObject(read(second,"session.json"));
        for(String key:new String[]{"renderer_cache_identity","requested_driver","cache"}){
            if(a.optString(key).isEmpty()||!a.optString(key).equals(b.optString(key)))return false;
        }
        if(a.optBoolean("frame_generation")!=b.optBoolean("frame_generation")||a.optBoolean("performance_hints")!=b.optBoolean("performance_hints"))return false;
        if(!read(first,"effective-dxvk.conf").equals(read(second,"effective-dxvk.conf"))||!read(first,"android.ini").equals(read(second,"android.ini")))return false;
        JSONObject xMetrics=new JSONObject(read(first,"graphics-metrics.json")),yMetrics=new JSONObject(read(second,"graphics-metrics.json"));
        for(String key:new String[]{"vendorId","deviceId","apiVersion","driverVersionRaw"})if(!xMetrics.has(key)||!yMetrics.has(key)||xMetrics.optLong(key)!=yMetrics.optLong(key))return false;
        File x=new File(first,"applied-settings.xml"),y=new File(second,"applied-settings.xml");
        return x.isFile()&&y.isFile()&&XhynV02Config.read(x).equals(XhynV02Config.read(y));
    }
    public static String make(File current)throws Exception{
        String guide="For a useful comparison, record the same route twice for similar lengths of time. Keep the renderer, driver and graphics settings unchanged. The second run may reuse shaders. This tool cannot confirm that both runs used the same scene or prove that a cache was cold.\n\n";
        File metricsFile=new File(current,"graphics-metrics.json");
        if(!metricsFile.isFile())return guide+"No graphics recording is ready for the latest attempt. Turn on Record next game and reproduce the scene.";
        JSONObject now=new JSONObject(XhynV02Config.read(metricsFile));
        long began=new JSONObject(read(current,"session.json")).getLong("start_ms"),latest=Long.MIN_VALUE;
        File selected=null;File[] attempts=current.getParentFile().listFiles();
        if(attempts!=null)for(File candidate:attempts){
            if(candidate.equals(current)||!candidate.isDirectory()||!new File(candidate,"graphics-metrics.json").isFile())continue;
            try{
                long time=new JSONObject(read(candidate,"session.json")).getLong("start_ms");
                if(time<began&&time>latest&&comparable(candidate,current)){latest=time;selected=candidate;}
            }catch(Exception incompatible){/* Incomplete/older formats are not a comparison baseline. */}
        }
        if(selected==null)return guide+"No earlier recording with matching renderer, driver, frame generation and saved graphics settings was found.";
        JSONObject before=new JSONObject(read(selected,"graphics-metrics.json"));
        StringBuilder text=new StringBuilder(guide).append("Earlier: ").append(selected.getName()).append("\nLatest: ").append(current.getName()).append("\n\n");
        row(text,"Recorded duration (seconds)",before,now,"elapsed_ms",1000);
        row(text,"Graphics pipeline requests",before,now,"graphicsPipelineRequests",1);
        row(text,"Summed pipeline creation time (ms)",before,now,"pipelineCreateNs",1000000);
        row(text,"Presentation interval p95 (ms)",before,now,"presentIntervalP95Ns",1000000);
        row(text,"Presentation interval p99 (ms)",before,now,"presentIntervalP99Ns",1000000);
        row(text,"Peak tracked Vulkan allocations (MiB)",before,now,"peakTrackedAllocationBytes",1048576);
        row(text,"Allocation failures",before,now,"allocationFailures",1);
        return text+"\nValues are earlier → latest. Pipeline time adds concurrent calls and is not frame-stall time. Presentation intervals may include generated frames and are not rendered FPS or GPU execution time. Memory counters overlap with other reports. A difference alone does not identify its cause. No settings were changed.";
    }
    private static void row(StringBuilder text,String title,JSONObject a,JSONObject b,String key,double divisor){
        text.append(title).append(": ");
        if(!a.has(key)||!b.has(key))text.append("unavailable");
        else text.append(String.format(Locale.ROOT,"%.2f → %.2f",a.optDouble(key)/divisor,b.optDouble(key)/divisor));
        text.append('\n');
    }
    private XhynV02Comparison(){}
}
