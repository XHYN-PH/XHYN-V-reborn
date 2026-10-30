package com.gtavsource.android;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.system.Os;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Launch-time configuration and bounded, explicitly requested reports. */
public final class XhynV02Runtime {
    public static final class LaunchSetupFailure extends RuntimeException {
        LaunchSetupFailure(String message, Throwable cause) { super(message,cause); }
    }
    public static String launchFailureTitle(RuntimeException failure) {
        return failure instanceof LaunchSetupFailure ? "Graphics setup failed" : "Invalid game folder";
    }
    private static volatile File session;
    private static volatile String sessionId;
    private static ScheduledExecutorService capture;
    private static long captureGeneration;
    private static Context application;
    private static final Object LOCK = new Object();
    public static RuntimeConfig config() { return RuntimeConfig.load(application == null ? RuntimeConfig.GAME_ROOT : new File(application.getSharedPreferences("xhyn-v02", 0).getString("gameRoot", RuntimeConfig.GAME_ROOT.getPath()))); }
    public static File saved(RuntimeConfig cfg) { return new File(cfg.root, "Config/dxvk.conf"); }
    public static File effective(Context context) { return new File(context.getFilesDir(), "xhyn-effective-dxvk.conf"); }
    public static File probeFile(Context context) { return new File(context.getFilesDir(), "xhyn-driver-probe.json"); }

    public static String hash(File file) throws Exception {
        if (!file.isFile() || Files.isSymbolicLink(file.toPath())) throw new IOException("Invalid file: " + file.getName());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[65536]; int count;
            while ((count = in.read(bytes)) != -1) digest.update(bytes, 0, count);
        }
        StringBuilder text = new StringBuilder();
        for (byte value : digest.digest()) text.append(String.format(Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }

    public static String driverKey(RuntimeConfig cfg) throws Exception {
        if (!"custom".equals(cfg.get("vulkan", "driver", "system"))) return "system-" + digest(Build.FINGERPRINT);
        File file = new File(cfg.drivers, cfg.get("vulkan", "driver_zip", ""));
        try {
            if (!file.getCanonicalFile().getParentFile().equals(cfg.drivers.getCanonicalFile()) || !file.isFile()) throw new IOException("Driver package unavailable");
            return hash(file);
        } catch(IOException unavailable) {
            // Leave actual admission/fallback to DriverManager. A missing old ZIP is
            // a driver selection problem, not an invalid game directory.
            return "unavailable-"+digest(cfg.get("vulkan","driver_zip","")+":"+Build.FINGERPRINT);
        }
    }

    private static String digest(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    public static JSONObject lastProbe(Context context, RuntimeConfig cfg) {
        try {
            JSONObject record = new JSONObject(XhynV02Config.read(probeFile(context)));
            if (record.optString("requestedDriverKey").equals(driverKey(cfg))
                    && record.optString("fingerprint").equals(Build.FINGERPRINT)
                    && record.optString("bcnMode").equals(cfg.get("vulkan", "bcn_decode_mode", "auto"))) return record;
        } catch (Exception ignored) { }
        return null;
    }

    /** Runs before driver loading, and only applies Turnip controls to measured Turnip identity. */
    public static void beforeDriver(Context context, RuntimeConfig cfg) {
        try {
            JSONObject record = lastProbe(context, cfg);
            if (record == null) return;
            JSONObject info = record.optJSONObject("result");
            if (info == null || !XhynV02DriverPolicy.isTurnip(info.optInt("vendorId"), info.optInt("driverId"), info.optString("driverName"))) return;
            int mode = cfg.getInt("xhyn", "turnip_memory", 0, 0, 2);
            Os.setenv("TU_DEBUG", XhynV02DriverPolicy.memoryFlags(Os.getenv("TU_DEBUG"), mode), true);
            if (cfg.getBoolean("xhyn", "one_ui_workaround", false))
                Os.setenv("FD_DEV_FEATURES", XhynV02DriverPolicy.oneUiFeatures(Os.getenv("FD_DEV_FEATURES")), true);
        } catch (Exception failure) { Log.w("XHYN V", "Turnip options could not be applied", failure); }
    }

    public static int frameLimit(RuntimeConfig cfg) {
        int value = cfg == null ? 60 : cfg.getInt("performance", "frame_limit", 60, 0, 240);
        return value == 0 ? 0 : Math.max(10, value);
    }
    public static String defaultLatency(RuntimeConfig cfg) {
        for (String entry : StableFrameProfile.config(cfg).split(";"))
            if ("dxgi.maxFrameLatency".equals(XhynV02Config.key(entry))) return entry.substring(entry.indexOf('=')+1).trim();
        return "2";
    }

    private static JSONObject optionCatalog(Context context)throws Exception{
        try (InputStream input = context.getAssets().open("xhyn-dxvk-options.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    private static String rendererId(RuntimeConfig cfg,JSONObject catalog)throws Exception{
        if(!"builtin".equals(cfg.get("dxvk","source","builtin")))return "custom";
        String id=cfg.get("dxvk","builtin_id","");return id.isEmpty()?catalog.getString("defaultId"):id;
    }
    public static String rendererId(Context context,RuntimeConfig cfg)throws Exception{return rendererId(cfg,optionCatalog(context));}
    public static Set<String> supported(Context context, RuntimeConfig cfg) throws Exception {
        JSONObject catalog=optionCatalog(context);String id=rendererId(cfg,catalog);
        org.json.JSONArray keys = catalog.optJSONArray(id);
        if (keys == null) return Collections.emptySet();
        Set<String> out = new HashSet<>(); for (int i = 0; i < keys.length(); i++) out.add(keys.getString(i));
        return out;
    }

    public static String preview(Context context, RuntimeConfig cfg) throws Exception {
        try{return preview(context,cfg,true);}catch(IOException invalidSaved){return preview(context,cfg,false);}
    }
    public static int effectiveFrameLimit(Context context,RuntimeConfig cfg)throws Exception{
        Map<String,String> values=XhynV02Config.globals(preview(context,cfg));
        String value=values.containsKey("dxvk.maxFrameRate")?values.get("dxvk.maxFrameRate"):values.get("dxgi.maxFrameRate");
        if(value==null)return frameLimit(cfg);
        return Integer.parseInt(value);
    }
    public static Set<String> managedKeys(Context context,RuntimeConfig cfg)throws Exception{return XhynV02Config.globals(preview(context,cfg,false)).keySet();}
    private static String preview(Context context, RuntimeConfig cfg, boolean includeSaved) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String entry : StableFrameProfile.config(cfg).split(";")) {
            String key = XhynV02Config.key(entry);
            if (key != null) values.put(key, entry.substring(entry.indexOf('=') + 1).trim());
        }
        Set<String> supported = supported(context, cfg);
        int fps = StableFrameProfile.framegen(cfg) ? StableFrameProfile.base(cfg) : frameLimit(cfg);
        values.put("dxgi.maxFrameRate", Integer.toString(fps));
        values.put("dxgi.syncInterval", cfg.get("xhyn", "vsync", "0"));
        values.put("dxgi.maxFrameLatency", cfg.get("xhyn", "latency", defaultLatency(cfg)));
        values.put("dxvk.numCompilerThreads", cfg.get("xhyn", "compiler_workers", "0"));
        if (supported.contains("dxvk.numShaderCompilerThreads")) values.put("dxvk.numShaderCompilerThreads", cfg.get("xhyn", "compiler_workers", "0"));
        if (!cfg.get("xhyn","async","").isEmpty() && supported.contains("dxvk.enableAsync")) values.put("dxvk.enableAsync", cfg.getBoolean("xhyn", "async", false)?"True":"False");
        if (supported.contains("d3d11.samplerLodBias") && cfg.getBoolean("xhyn", "texture_lod", false)) values.put("d3d11.samplerLodBias", "1.0");
        // Only emit managed options present in this renderer's own option table.
        if (!supported.isEmpty()) values.keySet().retainAll(supported);
        boolean enabled=includeSaved && cfg.getBoolean("xhyn","dxvk_conf_enabled",true);
        return XhynV02Config.effective(values,enabled?XhynV02Config.read(saved(cfg)):"",enabled);
    }

    public static void configure(Activity activity, RuntimeConfig cfg) {
        try {
            activity.getSharedPreferences("xhyn-v02", 0).edit().putString("gameRoot", cfg.root.getPath()).apply();
            // Existing imported drivers must not become unusable merely because a new,
            // optional readiness test has no saved result. DriverPackage still validates loading.
            // Old builds used this filename. Migrate once and never overwrite saved edits.
            File legacy = new File(cfg.root, "Config/xhyn-dxvk.conf");
            String warning="";
            try {
                if(!saved(cfg).exists() && legacy.isFile())XhynV02Config.write(saved(cfg),XhynV02Config.read(legacy));
            } catch(IOException invalidLegacy) {
                warning="The old dxvk.conf could not be imported: "+invalidLegacy.getMessage()+". The original file has been kept.";
            }
            String text;
            try { text=preview(activity,cfg,true); }
            catch(IOException invalidOverrides) {
                warning+=(warning.isEmpty()?"":"\n")+"Saved dxvk.conf overrides could not be applied: "+invalidOverrides.getMessage()+". Managed settings are used for this launch; your saved text has been kept.";
                text=preview(activity,cfg,false);
            }
            XhynV02Config.write(effective(activity), text);
            // Empty, rather than absent: engine SDL_main otherwise inserts its own competing config.
            Os.setenv("DXVK_CONFIG", "", true);
            Os.setenv("DXVK_CONFIG_FILE", effective(activity).getAbsolutePath(), true);
            Os.unsetenv("DXVK_FRAME_RATE"); Os.unsetenv("DXVK_FRAME_PACE");
            // Explicit file values own these controls. Legacy environment aliases
            // must not silently win over the value shown by the tuning/editor UI.
            Set<String> written=new HashSet<>(XhynV02Config.unsupported(text,Collections.emptySet()));
            for(String[] pair:new String[][]{{"DXVK_HUD","dxvk.hud"},{"DXVK_ASYNC","dxvk.enableAsync"},{"DXVK_GPLASYNCCACHE","dxvk.gplAsyncCache"}})
                if(written.contains(pair[1]))Os.unsetenv(pair[0]);
            File cache = new File(activity.getCacheDir(), "xhyn-shaders/" + digest(cfg.get("dxvk", "source", "builtin") + ":" + cfg.get("dxvk", "builtin_id", "") + ":" + cfg.get("dxvk", "package_zip", "") + ":" + rendererHash(cfg) + ":" + driverKey(cfg) + ":" + Build.FINGERPRINT));
            if (!cache.isDirectory() && !cache.mkdirs()) throw new IOException("Cannot prepare shader cache");
            Os.setenv("DXVK_STATE_CACHE_PATH", cache.getAbsolutePath(), true);
            Os.setenv("DXVK_SHADER_CACHE_PATH", cache.getAbsolutePath(), true);
            Os.setenv("MESA_SHADER_CACHE_DIR", new File(cache, "mesa").getAbsolutePath(), true);
            Map<String, String> graphics = new LinkedHashMap<>();
            for (String name : XhynV02Graphics.QUALITY) {
                String value = cfg.get("xhyn_graphics",name.toLowerCase(Locale.ROOT),cfg.get("xhyn_graphics",name,"")); if (!value.isEmpty()) graphics.put(name, value);
            }
            for (String name : Arrays.asList("ScreenWidth", "ScreenHeight", "VSync", "LodScale", "PedLodBias", "VehicleLodBias", "CityDensity", "PedVarietyMultiplier", "VehicleVarietyMultiplier", "Shadow_ParticleShadows", "Shadow_LongShadows", "Shadow_SoftShadows")) {
                String value = cfg.get("xhyn_graphics",name.toLowerCase(Locale.ROOT),cfg.get("xhyn_graphics",name,"")); if (!value.isEmpty()) graphics.put(name, value);
            }
            boolean applied;
            try { applied=XhynV02Graphics.apply(new File(cfg.root,"save"),graphics); }
            catch(Exception invalidOverrides) {
                applied=false;
                warning+=(warning.isEmpty()?"":"\n")+"Graphics overrides were not applied: "+invalidOverrides.getMessage()+". The game's settings file was left intact.";
            }
            begin(activity, cfg, text, cache, applied);
            if(!warning.isEmpty()) {
                XhynV02Config.write(new File(session,"setup-warning.txt"),warning);
                activity.runOnUiThread(()->android.widget.Toast.makeText(activity,"Some graphics overrides could not be applied. See Diagnostics for details.",android.widget.Toast.LENGTH_LONG).show());
            }
        } catch (Exception failure) {
            try {
                java.io.StringWriter detail=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(detail));
                JSONObject report=new JSONObject().put("time_ms",System.currentTimeMillis()).put("pid",android.os.Process.myPid()).put("gameRoot",cfg.root.getPath()).put("message",failure.toString()).put("stack",detail.toString());
                XhynV02Config.write(new File(activity.getFilesDir(),"xhyn-launch-error.json"),report.toString(2));
            } catch(Exception logging) { Log.w("XHYN V","Could not record startup error",logging); }
            throw new LaunchSetupFailure("Could not prepare graphics settings: "+failure.getMessage()+". See Diagnostics for the startup report.",failure);
        }
    }

    private static String rendererHash(RuntimeConfig cfg) throws Exception {
        if (!"custom".equals(cfg.get("dxvk", "source", "builtin"))) return "builtin";
        File file = new File(cfg.dxvk, cfg.get("dxvk", "package_zip", ""));
        if (!file.getCanonicalFile().getParentFile().equals(cfg.dxvk.getCanonicalFile())) throw new IOException("DXVK package must be inside Dxvk");
        return hash(file);
    }

    private static void begin(Context context, RuntimeConfig cfg, String text, File cache, boolean graphicsApplied) throws Exception {
        sessionId = System.currentTimeMillis() + "-" + android.os.Process.myPid() + "-" + UUID.randomUUID().toString().substring(0, 8);
        session = new File(context.getFilesDir(), "xhyn-sessions/" + sessionId);
        if (!session.mkdirs()) throw new IOException("Cannot create session report");
        File sessions = session.getParentFile(); File[] old = sessions.listFiles();
        if (old != null) {
            Arrays.sort(old, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < old.length - 8; i++) removeTree(old[i], sessions);
        }
        JSONObject details = new JSONObject();
        details.put("session", sessionId).put("pid", android.os.Process.myPid()).put("start_ms", System.currentTimeMillis())
                .put("version", "0.2-hotfix2").put("fingerprint", Build.FINGERPRINT).put("model", Build.MANUFACTURER + " " + Build.MODEL)
                .put("requested_driver", driverKey(cfg)).put("renderer_source", cfg.get("dxvk", "source", "builtin"))
                .put("renderer_id", cfg.get("dxvk", "builtin_id", "")).put("renderer_zip_hash", rendererHash(cfg))
                .put("cache", cache.getAbsolutePath()).put("graphics_applied", graphicsApplied)
                .put("scope", "Current attempt only; a prior driver probe is a separate record");
        XhynV02Config.write(new File(session, "session.json"), details.toString(2));
        XhynV02Config.write(new File(session, "effective-dxvk.conf"), text);
        XhynV02Config.write(new File(session, "android.ini"), XhynV02Config.read(cfg.file));
        JSONObject logs = new JSONObject();
        for (String name : XhynV02Reports.RUNTIME_LOGS) logs.put(name, XhynV02LogSlice.snapshot(new File(cfg.root, name)));
        XhynV02Config.write(new File(session,"log-start.json"),logs.toString(2));
        XhynV02Config.write(new File(session,"game-root.txt"),cfg.root.getCanonicalPath());
        context.getSharedPreferences("xhyn-v02", 0).edit().putString("lastSession", sessionId).apply();
        if (cfg.getBoolean("performance", "capture", false)) startCapture(context.getApplicationContext(), cfg);
    }

    public static void librariesLoaded(Activity activity) {
        try {
            if (sessionId == null) throw new IOException("Session was not initialized");
            if (!"".equals(Os.getenv("DXVK_CONFIG")) || !effective(activity).getAbsolutePath().equals(Os.getenv("DXVK_CONFIG_FILE"))
                    || !hash(effective(activity)).equals(hash(new File(session,"effective-dxvk.conf"))))
                throw new IOException("The effective DXVK configuration changed during launch");
            if (!XhynV02Native.install(activity.getFilesDir().getAbsolutePath(), sessionId)) throw new IOException("The engine error handler could not be installed");
            RuntimeConfig cfg=config();XhynV02FrameHints.initialize(activity);
            boolean captureRequested=cfg.getBoolean("performance","capture",false);
            boolean hints=cfg.getBoolean("xhyn","performance_hints",false);
            int target=StableFrameProfile.framegen(cfg)?StableFrameProfile.target(cfg):effectiveFrameLimit(activity,cfg);
            boolean observed=XhynV02Native.observe(captureRequested,hints,target);
            XhynV02Config.write(new File(session,"native-observer-status.txt"),observed?"Requested Vulkan measurement/hint integration initialized. Device execution still requires testing.\n":"Optional Vulkan observer unavailable; the game continues without it.\n");
            XhynV02Config.write(new File(session, "loaded-driver.txt"), DriverManager.currentStatus() + "\n" + DriverManager.runtimeDiagnostics());
            JSONObject environment = new JSONObject();
            for (String key : Arrays.asList("DXVK_CONFIG", "DXVK_CONFIG_FILE", "DXVK_FRAME_RATE", "DXVK_FRAME_PACE", "DXVK_HUD", "DXVK_ASYNC", "DXVK_GPLASYNCCACHE", "DXVK_STATE_CACHE_PATH", "DXVK_SHADER_CACHE_PATH", "MESA_SHADER_CACHE_DIR", "TU_DEBUG", "FD_DEV_FEATURES", "GTAV_DXVK_RUNTIME_DIRECTORY", "GTAV_DXVK_RUNTIME_BUILD_ID"))
                environment.put(key,Os.getenv(key)==null?JSONObject.NULL:Os.getenv(key));
            XhynV02Config.write(new File(session,"effective-environment.json"),environment.toString(2));
            final Context context = activity.getApplicationContext(); final File report = session;
            new Thread(() -> {
                try {
                    JSONObject hashes = new JSONObject();
                    String loaded = Os.getenv("GTAV_DXVK_RUNTIME_DIRECTORY");
                    for (String name : Arrays.asList("libgtav.so", "libgtav_vulkan.so", "libdxvk_d3d11.so", "libdxvk_dxgi.so", "libSDL3.so", "libxhyn_v02.so")) {
                        File file = new File(name.startsWith("libdxvk_") && loaded != null && !loaded.isEmpty() ? loaded : context.getApplicationInfo().nativeLibraryDir, name);
                        if (file.isFile()) hashes.put(name, hash(file));
                    }
                    XhynV02Config.write(new File(report, "library-hashes.json"), hashes.toString(2));
                } catch (Exception failure) { Log.w("XHYN V", "Could not hash session libraries", failure); }
            }, "xhyn-session-hashes").start();
        } catch (Exception failure) { throw new IllegalStateException("XHYN V could not initialize this game session", failure); }
    }

    private static void startCapture(Context context, RuntimeConfig cfg) throws Exception {
        synchronized (LOCK) {
            if (capture != null) capture.shutdownNow();
            final long generation=++captureGeneration;
            final File captureDirectory=session;
            final int seconds = 1200; // One automatic recording policy, including upgrades with an old duration key.
            final long deadline = android.os.SystemClock.elapsedRealtime() + seconds * 1000L;
            final File output = new File(captureDirectory, "memory.csv");
            XhynV02Config.write(output, "elapsed_ms,pss_kib,java_used_bytes,native_heap_bytes,thermal_status,process_read_bytes,process_write_bytes\n");
            final long start = android.os.SystemClock.elapsedRealtime();
            capture = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "xhyn-bounded-capture"));
            context.getSharedPreferences("xhyn-v02", 0).edit().putBoolean("captureActive", true).apply();
            capture.scheduleAtFixedRate(() -> {
                try {
                    synchronized(LOCK){if(generation!=captureGeneration)return;}
                    RuntimeConfig live = RuntimeConfig.readOnly(cfg.root);
                    if (android.os.SystemClock.elapsedRealtime() >= deadline || !live.getBoolean("performance", "capture", false)) { stopCapture(cfg.root,generation); return; }
                    Debug.MemoryInfo memory = new Debug.MemoryInfo(); Debug.getMemoryInfo(memory);
                    Runtime runtime = Runtime.getRuntime(); PowerManager power = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                    long[] counters = ioCounters();
                    String line = (android.os.SystemClock.elapsedRealtime() - start) + "," + memory.getTotalPss() + "," + (runtime.totalMemory() - runtime.freeMemory()) + "," + Debug.getNativeHeapAllocatedSize() + "," + (power == null ? -1 : power.getCurrentThermalStatus()) + "," + counters[0] + "," + counters[1] + "\n";
                    try (FileOutputStream out = new FileOutputStream(output, true)) { out.write(line.getBytes(StandardCharsets.UTF_8)); }
                    try{JSONObject graphics=new JSONObject(XhynV02Native.metrics());graphics.put("elapsed_ms",android.os.SystemClock.elapsedRealtime()-start).put("performanceHints",XhynV02FrameHints.status());
                        XhynV02Config.write(new File(captureDirectory,"graphics-metrics.json"),graphics.toString(2));
                    }catch(LinkageError|RuntimeException unavailable){Log.w("XHYN V","Optional graphics measurements unavailable",unavailable);}
                } catch (Exception failure) { Log.w("XHYN V", "Capture stopped", failure); stopCapture(cfg.root,generation); }
            }, 0, 2, TimeUnit.SECONDS);
        }
    }
    private static long[] ioCounters() {
        long[] values = {-1,-1};
        try (BufferedReader input = new BufferedReader(new FileReader("/proc/self/io"))) {
            String line; int lines=0; while ((line=input.readLine())!=null && ++lines<32) {
                if(line.startsWith("read_bytes:"))values[0]=Long.parseLong(line.substring(11).trim());
                if(line.startsWith("write_bytes:"))values[1]=Long.parseLong(line.substring(12).trim());
            }
        } catch(Exception ignored) { }
        return values;
    }

    private static void stopCapture(File root) {
        stopCapture(root,-1);
    }
    private static void stopCapture(File root,long expectedGeneration) {
        synchronized (LOCK) {
            if(expectedGeneration>=0&&expectedGeneration!=captureGeneration)return;
            ++captureGeneration;
            if (capture != null) { capture.shutdown(); capture = null; }
            try{XhynV02Native.capture(false);}catch(LinkageError ignored){}
            if (application != null) application.getSharedPreferences("xhyn-v02", 0).edit().putBoolean("captureActive", false).apply();
            try { RuntimeConfig cfg = RuntimeConfig.load(root); cfg.put("performance", "capture", "false"); cfg.save(); }
            catch (Exception failure) { Log.w("XHYN V", "Could not reset capture switch", failure); }
        }
    }

    static void removeTree(File file, File root) throws Exception {
        if (Files.isSymbolicLink(file.toPath())) throw new IOException("Refusing a symbolic link");
        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) throw new IOException("Path is outside the cache");
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children == null) throw new IOException("Cannot read directory"); for (File child : children) removeTree(child, root); }
        if (!file.delete() && file.exists()) throw new IOException("Cannot delete " + file.getName());
    }

    public static void clearCaches(Context context) throws Exception {
        if (GTAVActivity.isGameRunning()) throw new IOException("Close the game before clearing shader caches");
        File root = context.getCacheDir(); File owned = new File(root, "xhyn-shaders");
        if (owned.exists()) removeTree(owned, root);
    }

    public static String lastError(Context context) {
        try {
            String value = XhynV02Config.read(new File(context.getFilesDir(), "xhyn-native-error.txt"));
            String last = context.getSharedPreferences("xhyn-v02", 0).getString("lastSession", "");
            if (!last.isEmpty() && value.contains("session=" + last + "\n")) return value;
        } catch (Exception ignored) { }
        return "No engine error report for the most recent attempt.";
    }
    public static String startupError(Context context) {
        try {
            String last=context.getSharedPreferences("xhyn-v02",0).getString("lastSession","");
            String notice=last.matches("[A-Za-z0-9-]{1,100}")?XhynV02Config.read(new File(context.getFilesDir(),"xhyn-sessions/"+last+"/setup-warning.txt")):"";
            String value=XhynV02Config.read(new File(context.getFilesDir(),"xhyn-launch-error.json"));
            return (notice.isEmpty()?"":"Latest session settings notice:\n"+notice+"\n\n")+(value.isEmpty()?"No startup setup error has been recorded.":"Last startup error (see its timestamp):\n"+value);
        }
        catch(Exception error){return "Could not read the startup report: "+error.getMessage();}
    }
    public static String userSummary(Context context){
        String source=lastError(context)+"\n"+startupError(context);
        String id=context.getSharedPreferences("xhyn-v02",0).getString("lastSession","");
        if(id.matches("[A-Za-z0-9-]{1,100}"))try{
            Map<String,String> settings=XhynV02Config.globals(XhynV02Config.read(new File(context.getFilesDir(),"xhyn-sessions/"+id+"/effective-dxvk.conf")));
            source+="\nfeature_limit="+settings.getOrDefault("d3d11.maxFeatureLevel","");
        }catch(Exception unavailable){}
        if(id.matches("[A-Za-z0-9-]{1,100}"))try{
            JSONObject metrics=new JSONObject(XhynV02Config.read(new File(context.getFilesDir(),"xhyn-sessions/"+id+"/graphics-metrics.json")));
            if(metrics.optLong("deviceLossObservations")>0)source+="\nVK_ERROR_DEVICE_LOST";
            else if(metrics.optLong("allocationFailures")>0)source+="\nallocation failed";
        }catch(Exception unavailable){}
        return XhynV02Advice.explain(source);
    }
    public static void resumeGame(Activity activity){
        XhynV02Display.apply(activity);XhynV02FrameHints.resume();try{XhynV02Native.paused(false);}catch(LinkageError ignored){}
    }
    public static void pauseGame(Activity activity){
        try{XhynV02Native.paused(true);}catch(LinkageError ignored){}XhynV02FrameHints.pause();XhynV02Display.release(activity);
    }
    public static void surface(android.view.View view){if(view.getContext() instanceof Activity)XhynV02Display.apply((Activity)view.getContext());}

    public static void initialize(Context context) {
        application = context.getApplicationContext();
        // A capture request is one attempt only. After a process exit it must not run indefinitely on the next launch.
        if (android.app.Application.getProcessName().contains(":xhyn_probe")) return;
        try { RuntimeConfig cfg = config();
            if (cfg.file.isFile() && context.getSharedPreferences("xhyn-v02", 0).getBoolean("captureActive", false)) { stopCapture(cfg.root); context.getSharedPreferences("xhyn-v02", 0).edit().putBoolean("captureActive", false).apply(); }
        } catch (Exception ignored) { }
    }
    public static void refreshMenu(GameMenuActivity activity) {
        try { if (activity.config != null) activity.config = RuntimeConfig.load(activity.config.root); }
        catch (Exception failure) { Log.w("XHYN V", "Could not refresh launcher settings", failure); }
        android.content.SharedPreferences prefs = activity.getSharedPreferences("xhyn-v02", 0);
        String last = prefs.getString("lastSession", "");
        String error = lastError(activity);
        if (!last.isEmpty() && error.startsWith("XHYN_NATIVE_ERROR_V1") && !last.equals(prefs.getString("shownError", "")) && !activity.isFinishing()) {
            prefs.edit().putString("shownError", last).apply();
            String message = userSummary(activity);
            new MenuDialogBuilder(activity).setTitle("Game stopped").setMessage(message + "\n\nOpen Diagnostics to save a report. Use Play to start a fresh attempt.")
                    .setPositiveButton("Close", null).setNeutralButton("Reports and settings", (d,w) -> XhynV02Settings.open(activity)).show();
        }
    }
    public static void gameStopped() {
        XhynV02FrameHints.pause();
        synchronized(LOCK) { if(capture!=null) stopCapture(config().root); }
    }
    private XhynV02Runtime() {}
}
