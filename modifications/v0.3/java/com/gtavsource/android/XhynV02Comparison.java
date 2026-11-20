package com.gtavsource.android;

import java.io.File;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/** Compares recorded counters only when renderer, driver and settings agree. */
public final class XhynV02Comparison {
    private static String read(File directory,String name)throws Exception{return XhynV02Config.read(new File(directory,name));}

    public static String history(File sessions) {
        File[] attempts=sessions.listFiles(file->file.isDirectory()&&!Files.isSymbolicLink(file.toPath()));
        if(attempts==null || attempts.length==0) return "Turn on Record next game, then repeat the same route to compare runs.";
        Arrays.sort(attempts,Comparator.comparingLong(File::lastModified).reversed());
        StringBuilder text=new StringBuilder();
        for(File attempt:attempts){
            File metricsFile=new File(attempt,"graphics-metrics.json");
            if(!metricsFile.isFile()) continue;
            try {
                JSONObject details=new JSONObject(read(attempt,"session.json"));
                JSONObject metrics=new JSONObject(read(attempt,"graphics-metrics.json"));
                String driver=metrics.optString("gpu","").trim();
                String cap=details.optString("render_fps_cap","Unavailable");
                if(cap.equals("0"))cap="Unlimited";
                if(cap.equals("-1"))cap="Game default";
                text.append(DateFormat.getDateTimeInstance().format(new Date(details.getLong("start_ms")))).append('\n')
                        .append(details.optString("renderer_id","Renderer unavailable")).append(" · ")
                        .append(driver.isEmpty()?"GPU unavailable":driver).append('\n')
                        .append("Game FPS cap: ").append(cap);
                if(details.optInt("generated_output_target")>0)
                    text.append(" · Generated output target: ").append(details.optInt("generated_output_target")).append(" FPS");
                text.append('\n');
                if(metrics.optDouble("displayRefreshHz",0)>0)
                    text.append(String.format(Locale.ROOT,"Screen refresh: %.1f Hz%n",metrics.optDouble("displayRefreshHz")));
                if(metrics.optLong("presentIntervalSamples")>0) {
                    long median=metrics.optLong("presentIntervalP50Ns");
                    if(median>0) text.append(String.format(Locale.ROOT,"Median present interval: %.2f ms%n",median/1e6));
                    text.append(String.format(Locale.ROOT,"95th-percentile present interval: %.2f ms%n",metrics.optLong("presentIntervalP95Ns")/1e6));
                    if(metrics.has("presentIntervalsOver100ms"))
                        text.append("Present gaps of 100 ms or longer: ").append(metrics.optLong("presentIntervalsOver100ms")).append('\n');
                }else text.append("No presentation intervals were recorded.\n");
                long memory=peakPss(new File(attempt,"memory.csv"));
                if(memory>0)text.append(String.format(Locale.ROOT,"Peak sampled app memory: %.1f MiB%n",memory/1024.0));
                String launch=read(attempt,"java-launch.json");
                if(!launch.isEmpty()){
                    JSONObject progress=new JSONObject(launch);
                    if("First presentation observed".equals(progress.optString("last_phase")))
                        text.append(String.format(Locale.ROOT,"First observed present: %.1f s after launch%n",progress.optLong("phase_elapsed_ms")/1000.0));
                }
                text.append('\n');
            }catch(Exception unavailable){
                // A missing or incomplete recording should not hide other attempts.
            }
        }
        if(text.length()==0)return "No recorded runs yet. Turn on Record next game before starting the game.";
        return text+"Present intervals describe calls to present frames. With frame generation enabled they may include generated frames.";
    }

    private static long peakPss(File csv) throws java.io.IOException {
        if(!csv.isFile())return 0;
        long peak=0;
        try(BufferedReader reader=Files.newBufferedReader(csv.toPath())){
            reader.readLine();
            for(String row;(row=reader.readLine())!=null;){
                String[] fields=row.split(",",3);
                if(fields.length>1)try{peak=Math.max(peak,Long.parseLong(fields[1]));}
                catch(NumberFormatException incomplete){/* A capture may still be writing the final row. */}
            }
        }
        return peak;
    }
    public static boolean comparable(File first,File second)throws Exception{
        JSONObject a=new JSONObject(read(first,"session.json")),b=new JSONObject(read(second,"session.json"));
        for(String key:new String[]{"renderer_cache_identity","requested_driver","cache"}){
            if(a.optString(key).isEmpty()||!a.optString(key).equals(b.optString(key)))return false;
        }
        if(a.optBoolean("frame_generation")!=b.optBoolean("frame_generation")||a.optBoolean("performance_hints")!=b.optBoolean("performance_hints"))return false;
        String firstIni = read(first, "android.ini");
        if (firstIni.isEmpty() || !firstIni.equals(read(second, "android.ini"))) return false;
        if (!read(first, "effective-dxvk.conf").equals(read(second, "effective-dxvk.conf"))) return false;
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
        row(text,"Median presentation interval (ms)",before,now,"presentIntervalP50Ns",1000000);
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
