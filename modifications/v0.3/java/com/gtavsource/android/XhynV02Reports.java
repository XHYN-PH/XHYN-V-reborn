package com.gtavsource.android;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Explicit user export; no automatic network transmission. */
public final class XhynV02Reports {
    public static final String[] RUNTIME_LOGS = {"Config/driver-status.txt", "Config/dxvk-status.txt", "gtav_tty.log", "gtav_last_error.log", "gtav_error_pending.txt", "gtav_exit_info.log", "gtav_resource_errors.log", "error.log", "gtav.log", "crash.log"};
    public static final Set<String> OWNED_LOGS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("Config/driver-status.txt","Config/dxvk-status.txt","gtav_tty.log","gtav_last_error.log","gtav_error_pending.txt","gtav_exit_info.log","gtav_resource_errors.log")));
    public static String make(Context context) throws Exception {
        String id = context.getSharedPreferences("xhyn-v02", 0).getString("lastSession", "");
        if (!id.matches("[A-Za-z0-9-]{1,100}")) {
            File startup=new File(context.getFilesDir(),"xhyn-launch-error.json");
            if(!startup.isFile())throw new IOException("No report is ready yet. Turn on Record next game, start the game and reproduce the problem, then return here to save the report.");
            File out=new File(context.getCacheDir(),"xhyn-last-session.zip");
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(out))){addText(zip,"README.txt","Startup failed before a game session was created. This report contains that setup failure.\n");add(zip,"startup-error.json",startup,65536);}
            return out.getAbsolutePath();
        }
        File session = new File(context.getFilesDir(), "xhyn-sessions/" + id);
        if (!session.isDirectory()) throw new IOException("That session report is no longer available");
        File out = new File(context.getCacheDir(), "xhyn-last-session.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            addText(zip, "README.txt", "XHYN V 0.3 session " + id + "\nSession files identify one attempt. Prior driver readiness results are explicitly separate.\nNative guards prevent specific invalid calls; they do not establish the cause of audio corruption.\nMissing script native/API errors require matching scripts and engine interfaces. Vulkan device loss requires driver/device investigation.\nNo automatic upload. Paths and device details may be included; review before sharing.\n");
            File[] files = session.listFiles(); if (files != null) for (File file : files) if (file.isFile()) add(zip, "session/" + file.getName(), file, 2 * 1024 * 1024);
            addText(zip,"session/measurement-guide.txt","Driver initialization and Config/driver-status.txt may contain startup-only BC counters. When recording is enabled, driver-runtime-latest.txt is a timestamped later snapshot, not necessarily the instant of a crash. Recording changes measurement overhead. Compare runs with it both on and off. The basic optional graphics check is not a game/BC-copy qualification.\n");
            try{addText(zip,"session/recording-comparison.txt",XhynV02Comparison.make(session));}catch(Exception unavailable){addText(zip,"session/recording-comparison.txt","No comparable recording is available.\n");}
            String error = XhynV02Runtime.lastError(context);
            addText(zip, "session/engine-error.txt", error);
            File startup=new File(context.getFilesDir(),"xhyn-launch-error.json");
            if(startup.isFile())add(zip,"separate-last-startup-error.json",startup,65536);
            File probe = XhynV02Runtime.probeFile(context); if (probe.isFile()) add(zip, "prior-driver-check.json", probe, 65536);
            // Existing runtime reports are included only if their timestamp belongs to this attempt.
            RuntimeConfig cfg = XhynV02Runtime.config();
            File runtimeRoot = new File(XhynV02Config.read(new File(session,"game-root.txt")));
            long began = new org.json.JSONObject(XhynV02Config.read(new File(session, "session.json"))).getLong("start_ms");
            try{
                org.json.JSONObject attempt=new org.json.JSONObject(XhynV02Config.read(new File(session,"session.json")));
                addText(zip,"session/os-process-exit.json",XhynV02ExitInfo.collect(context,attempt.getInt("pid"),began,System.currentTimeMillis()).toString(2));
            }catch(Exception unavailable){addText(zip,"session/os-process-exit-unavailable.txt","OS exit details could not be read: "+unavailable.getClass().getSimpleName()+"\n");}
            org.json.JSONObject snapshots = new org.json.JSONObject(XhynV02Config.read(new File(session, "log-start.json")));
            List<String> absent = new ArrayList<>();
            StringBuilder compatibility = new StringBuilder();
            for (String name : RUNTIME_LOGS) {
                File file = new File(runtimeRoot, name);
                String snapshot=snapshots.optString(name,"0:0:");
                boolean legacy=!snapshot.startsWith("v2:");
                byte[] current=XhynV02LogSlice.current(file,snapshot,began,2*1024*1024);
                if(current.length>0){zip.putNextEntry(new ZipEntry((legacy?"separate-legacy-runtime/":"runtime/")+name.replace('/','_')));zip.write(current);zip.closeEntry();if(!legacy)compatibility.append(new String(current,StandardCharsets.UTF_8)).append('\n');}
                else absent.add(name);
            }
            File[] previous=new File(session,"previous").listFiles();
            if(previous!=null)for(File old:previous)if(old.isFile())add(zip,"previous-attempt/"+old.getName(),old,2*1024*1024);
            addText(zip,"previous-attempt/README.txt","These files were present before this attempt. They are not current-session evidence. Unrotated or oversized files use bounded conservative capture; see session/log-preparation.json. Legacy 4 KiB snapshots are exported separately and excluded from current script compatibility analysis.\n");
            addText(zip,"session/script-compatibility.txt",XhynV02LogSlice.compatibility(compatibility.toString()));
            addText(zip, "runtime/NOT-CURRENT.txt", "Absent or older than this attempt:\n" + String.join("\n", absent) + "\n");
        }
        return out.getAbsolutePath();
    }
    private static void addText(ZipOutputStream zip, String name, String value) throws Exception { zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    private static void add(ZipOutputStream zip, String name, File file, long limit) throws Exception {
        if (Files.isSymbolicLink(file.toPath()) || file.length() > limit) { addText(zip, name + ".omitted.txt", "File exceeds the report limit or is a symbolic link.\n"); return; }
        zip.putNextEntry(new ZipEntry(name)); try (InputStream in = new FileInputStream(file)) { byte[] buffer = new byte[8192]; int count; long total=0; while ((count=in.read(buffer))!=-1) { total+=count; if(total>limit) throw new IOException("Report input grew beyond its limit"); zip.write(buffer,0,count); } } zip.closeEntry();
    }
    private XhynV02Reports() { }
}
