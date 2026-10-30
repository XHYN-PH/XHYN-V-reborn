package com.gtavsource.android;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/** Adds controls to existing launcher pages; never creates a separate settings Activity. */
public final class XhynV02Settings {
    public static final int PERFORMANCE=0, DRIVERS=1, DISPLAY=4, DIAGNOSTICS=9, DXVK=10;
    private final GameMenuActivity activity;
    private RuntimeConfig cfg;
    private LinearLayout body;
    private TextView probeStatus;
    private int page;
    private boolean disposed;
    private static final int IMPORT_CONFIG=502, EXPORT_REPORT=503, PROBE=505;
    private String exportReport;
    private final java.util.concurrent.ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private XhynV02Settings(GameMenuActivity owner) {
        activity=owner;
        cfg=owner.config!=null?RuntimeConfig.load(owner.config.root):XhynV02Runtime.config();
    }
    private static XhynV02Settings get(GameMenuActivity activity) {
        XhynV02Settings value=(XhynV02Settings)activity.xhynSettings;
        if(value==null || value.disposed || activity.config!=null && !value.cfg.root.equals(activity.config.root)) {
            if(value!=null)value.dispose();
            value=new XhynV02Settings(activity);activity.xhynSettings=value;
        }
        return value;
    }
    public static void open(Activity activity) { if(activity instanceof GameMenuActivity)((GameMenuActivity)activity).showPage(DIAGNOSTICS); }
    public static void append(GameMenuActivity activity,int page) {
        XhynV02Settings value=get(activity);value.page=page;
        value.cfg=RuntimeConfig.load(value.cfg.root);activity.config=value.cfg;
        activity.getSharedPreferences("xhyn-v02",0).edit().putString("gameRoot",value.cfg.root.getPath()).apply();
        value.body=MenuStyle.column(activity);value.body.setTag("xhyn-page-"+page);
        activity.content.addView(value.body);value.draw();
    }
    public static void release(GameMenuActivity activity) {
        XhynV02Settings value=(XhynV02Settings)activity.xhynSettings;
        if(value!=null)value.dispose();activity.xhynSettings=null;
    }
    private void dispose(){disposed=true;handler.removeCallbacksAndMessages(null);io.shutdown();}
    public static boolean result(GameMenuActivity activity,int request,int result,Intent data) {
        if(request!=IMPORT_CONFIG && request!=EXPORT_REPORT && request!=PROBE)return false;
        get(activity).activityResult(request,result,data);return true;
    }
    public static void resume(GameMenuActivity activity) {
        XhynV02Settings value=(XhynV02Settings)activity.xhynSettings;
        if(value!=null && !value.disposed){value.cfg=RuntimeConfig.load(value.cfg.root);value.updateProbe();}
    }
    private boolean isFinishing(){return disposed||activity.isFinishing()||activity.isDestroyed();}
    private void runOnUiThread(Runnable action){activity.runOnUiThread(()->{if(!isFinishing())action.run();});}
    private void startActivityForResult(Intent intent,int request){activity.startActivityForResult(intent,request);}
    private android.content.ContentResolver getContentResolver(){return activity.getContentResolver();}
    private File getCacheDir(){return activity.getCacheDir();}
    private int dp(int value){return MenuStyle.dp(activity,value);}
    private void text(String value,int size){body.addView(MenuStyle.text(activity,value,size,0xffb3b5b9));}
    private void section(String value){body.addView(MenuStyle.heading(activity,value));}
    private void button(String title,Runnable action){
        LinearLayout row=MenuStyle.detailRow(body,title,"");
        row.addView(MenuStyle.icon(activity,android.R.drawable.ic_menu_more,title,action));
        row.setOnClickListener(v->action.run());
    }
    private void error(Throwable e){if(!isFinishing())new MenuDialogBuilder(activity).setTitle("XHYN V").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("Close",null).show();}
    private void info(String title,String value){new MenuDialogBuilder(activity).setTitle(title).setMessage(value).setPositiveButton("Close",null).show();}
    private void toast(String value){Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    private static String readable(String value){return value.replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' ');}
    private void put(String section,String key,String value){
        RuntimeConfig live=RuntimeConfig.load(cfg.root);live.put(section,configKey(section,key),value);live.save();cfg=live;activity.config=live;
    }
    private static String configKey(String section,String key){return section.equals("xhyn_graphics")?key.toLowerCase(Locale.ROOT):key;}
    private String option(String section,String key,String fallback){return cfg.get(section,configKey(section,key),fallback);}
    private void removeOverride(String key)throws Exception{
        File file=XhynV02Runtime.saved(cfg);String before=XhynV02Config.read(file);
        if(!before.isEmpty())XhynV02Config.write(file,XhynV02Config.setGlobal(before,key,null));
    }
    private void toggle(String title,String section,String key,boolean fallback,Runnable changed){
        MenuStyle.toggle(body,title,"",cfg.getBoolean(section,key,fallback),on->{
            try{put(section,key,Boolean.toString(on));if(changed!=null)changed.run();}catch(Exception e){error(e);}
        });
    }
    private void choice(String title,String[] labels,String[] values,String section,String key,String fallback,String dxvkKey){
        int selected=Math.max(0,Arrays.asList(values).indexOf(option(section,key,fallback)));
        LinearLayout row=MenuStyle.detailRow(body,title,labels[selected]);row.setTag("xhyn-setting-"+key);
        Runnable choose=()->new MenuDialogBuilder(activity).setTitle(title)
            .setSingleChoiceItems(labels,Math.max(0,Arrays.asList(values).indexOf(option(section,key,fallback))),(dialog,index)->{
                try{
                    if(dxvkKey!=null)removeOverride(dxvkKey);
                    if(key.equals("compiler_workers"))removeOverride("dxvk.numShaderCompilerThreads");
                    put(section,key,values[index]);if(key.equals("vsync"))put("xhyn_graphics","VSync",values[index]);
                    View column=row.getChildAt(0);
                    if(column instanceof ViewGroup && ((ViewGroup)column).getChildCount()>1 && ((ViewGroup)column).getChildAt(1) instanceof TextView)
                        ((TextView)((ViewGroup)column).getChildAt(1)).setText(labels[index]);
                    row.setContentDescription(title+": "+labels[index]);dialog.dismiss();
                }catch(Exception e){error(e);}
            }).setNegativeButton("Cancel",null).show();
        row.addView(MenuStyle.icon(activity,android.R.drawable.ic_menu_more,"Change "+title,choose));row.setOnClickListener(v->choose.run());
    }
    private void draw(){
        if(body==null || isFinishing())return;
        body.removeAllViews();probeStatus=null;
        switch(page){
            case DISPLAY:graphics();break;
            case PERFORMANCE:performance();break;
            case DRIVERS:drivers();break;
            case DXVK:dxvk();break;
            case DIAGNOSTICS:diagnostics();break;
            default:throw new IllegalArgumentException("Not a settings page");
        }
    }
    private void showText(String title,String value){
        TextView text=MenuStyle.text(activity,value,13,0xfff2f2f0);text.setTextIsSelectable(true);text.setPadding(dp(16),dp(10),dp(16),dp(10));
        ScrollView scroll=new ScrollView(activity);scroll.addView(text);
        new MenuDialogBuilder(activity).setTitle(title).setView(scroll).setPositiveButton("Close",null).show();
    }
    private void graphics(){
        section("Rendering and memory");
        button("Resolution / render scale", this::resolution);
        button("Use lower graphics settings", () -> new MenuDialogBuilder(activity).setTitle("Use lower graphics settings?").setMessage("Sets textures, shadows, reflections, grass, water, particles and post effects to Normal, and disables MSAA, SSAO and long shadows. No FPS increase is guaranteed. You can change each setting below.").setPositiveButton("Apply", (d,w) -> {
            try { for (String key : XhynV02Graphics.QUALITY) put("xhyn_graphics", key, "0"); put("xhyn_graphics", "Shadow_ParticleShadows", "false"); put("xhyn_graphics", "Shadow_LongShadows", "false"); put("xhyn_graphics", "Shadow_SoftShadows", "0"); toast("Lower settings saved for next launch"); } catch (Exception e) { error(e); }
        }).setNegativeButton("Cancel", null).show());
        for (String key : XhynV02Graphics.QUALITY) {
            boolean samples = key.equals("MSAA") || key.equals("ReflectionMSAA");
            boolean ssao = key.equals("SSAO");
            choice(readable(key), samples ? new String[]{"Keep game setting", "Off", "2x", "4x", "8x"} : ssao ? new String[]{"Keep game setting", "Off", "Normal", "High"} : new String[]{"Keep game setting", "Normal", "High", "Very high"}, samples ? new String[]{"", "0", "2", "4", "8"} : ssao ? new String[]{"", "0", "1", "2"} : new String[]{"", "0", "1", "2"}, "xhyn_graphics", key, "", null);
        }
        choice("Population density", new String[]{"Keep game setting", "25%", "50%", "75%", "100%"}, new String[]{"", "0.25", "0.5", "0.75", "1.0"}, "xhyn_graphics", "CityDensity", "", null);
        for (String key : new String[]{"PedVarietyMultiplier", "VehicleVarietyMultiplier"}) choice(readable(key), new String[]{"Keep game setting", "25%", "50%", "100%"}, new String[]{"", "0.25", "0.5", "1.0"}, "xhyn_graphics", key, "", null);
        for (String key : new String[]{"LodScale", "PedLodBias", "VehicleLodBias"}) choice(readable(key), new String[]{"Keep game setting", "Low (0)", "Medium (0.5)", "High (1)"}, new String[]{"", "0", "0.5", "1.0"}, "xhyn_graphics", key, "", null);
        toggle("Reduce texture detail at a distance (LOD bias +1)", "xhyn", "texture_lod", false, () -> { try { removeOverride("d3d11.samplerLodBias"); } catch (Exception e) { error(e); } });
        text("Lower texture quality and resolution reduce memory demand. Artificial memory caps are available in dxvk.conf for advanced use; overly small limits can cause stalls or missing textures.", 13);
    }
    private void performance(){
        section("Frame rate and shader compilation");
        boolean generated=StableFrameProfile.framegen(cfg);
        button(generated ? "Rendered FPS: " + StableFrameProfile.base(cfg) + " · set by frame generation" : "Rendered FPS cap: " + XhynV02Runtime.frameLimit(cfg), generated ? () -> info("Frame generation", "Change the rendered and output targets on the launcher's frame-generation page, or turn frame generation off to choose a separate rendered FPS cap.") : this::fps);
        choice("VSync", new String[]{"Off", "On"}, new String[]{"0", "1"}, "xhyn", "vsync", "0", "dxgi.syncInterval");
        choice("Maximum frame latency", new String[]{"1 frame", "2 frames", "3 frames"}, new String[]{"1", "2", "3"}, "xhyn", "latency", XhynV02Runtime.defaultLatency(cfg), "dxgi.maxFrameLatency");
    }
    private void dxvk(){
        choice("Shader compiler workers", new String[]{"Automatic", "1", "2", "3", "4", "6", "8"}, new String[]{"0", "1", "2", "3", "4", "6", "8"}, "xhyn", "compiler_workers", "0", "dxvk.numCompilerThreads");
        toggle("Async shader compilation (supported DXVK builds only)", "xhyn", "async", false, () -> { try { removeOverride("dxvk.enableAsync"); } catch (Exception e) { error(e); } });
        text("Async is ignored when the selected renderer does not implement it. More compiler workers can increase memory use and heat. Generated frames are separate from rendered FPS; frame generation remains in its own launcher page and starts disabled on fresh installs.", 13);
        section("dxvk.conf");
        toggle("Enable saved dxvk.conf overrides", "xhyn", "dxvk_conf_enabled", true, null);
        button("Edit dxvk.conf", this::editor);
        button("Import dxvk.conf", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT_CONFIG));
        button("View effective configuration", () -> { try { showText("Effective dxvk.conf", XhynV02Runtime.preview(activity, XhynV02Runtime.config())); } catch (Exception e) { error(e); } });
        text("Saved overrides take priority. Changing a managed control removes its matching global override. Executable sections remain active and can still override controls. Turning the file off keeps your edits.", 13);
        button("Clear XHYN shader caches", () -> new MenuDialogBuilder(activity).setTitle("Clear shader caches?").setMessage("The game must be closed. Shaders will compile again on your next launch; this can temporarily increase stutter.").setPositiveButton("Clear", (d,w) -> { try { XhynV02Runtime.clearCaches(activity); toast("Shader caches cleared"); } catch (Exception e) { error(e); } }).setNegativeButton("Cancel", null).show());
    }
    private void drivers(){
        section("Vulkan drivers");
        text("System Vulkan is the starting point for Xclipse. PanVK for Mali and imported RADV for Xclipse are experimental and require compatible Android drivers. Device brand and a ZIP name do not prove GPU support.", 14);
        button("Check selected driver: draw and present", this::probe);
        probeStatus = MenuStyle.text(activity,"",13,0xffb3b5b9); body.addView(probeStatus); updateProbe();
        choice("Turnip memory mode", new String[]{"Automatic", "GMEM", "SYSMEM"}, new String[]{"0", "1", "2"}, "xhyn", "turnip_memory", "0", null);
        toggle("Turnip One UI workaround", "xhyn", "one_ui_workaround", false, null);
        text("Turnip controls apply only after the selected package was identified as Qualcomm Turnip. They do not apply to Qualcomm's stock driver, PanVK or Samsung Vulkan. Restart after changing a driver or these controls.", 13);
    }
    private void diagnostics(){
        choice("Capture duration", new String[]{"30 seconds", "2 minutes", "5 minutes", "20 minutes"}, new String[]{"30", "120", "300", "1200"}, "xhyn", "capture_seconds", "30", null);
        toggle("Capture the next game attempt", "performance", "capture", false, null);
        text("Capture stops at the chosen deadline. Normal play adds no periodic memory sampler. Reports keep the last eight attempts and label each driver, binary and configuration separately.", 13);
        button("Last engine error / device loss", () -> showText("Last engine error", XhynV02Runtime.lastError(activity)));
        button("Startup and settings notices", () -> showText("Startup report", XhynV02Runtime.startupError(activity)));
        button("Export most recent session report", this::export);
        text("A missing script native indicates a game/script compatibility problem. Changing graphics drivers cannot repair an unknown native API. Reports include the most recent session and any available current error text.", 13);
    }
    private void resolution() {
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        EditText width = number(panel, "Width (320..7680)", option("xhyn_graphics", "ScreenWidth", "1280"));
        EditText height = number(panel, "Height (180..4320)", option("xhyn_graphics", "ScreenHeight", "720"));
        EditText scale = number(panel, "Render scale % (10..100)", "100");
        AlertDialog dialog = new MenuDialogBuilder(activity).setTitle("Render resolution").setView(panel).setPositiveButton("Save", null).setNegativeButton("Cancel", null).setNeutralButton("Keep game setting", (d,w) -> { put("xhyn_graphics", "ScreenWidth", ""); put("xhyn_graphics", "ScreenHeight", ""); }).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try { int w = Integer.parseInt(width.getText().toString()), h = Integer.parseInt(height.getText().toString()); if (w < 320 || w > 7680 || h < 180 || h > 4320) throw new IOException("Use a width of 320..7680 and a height of 180..4320"); int[] dimensions = XhynV02Graphics.resolution(w, h, Integer.parseInt(scale.getText().toString())); put("xhyn_graphics", "ScreenWidth", Integer.toString(dimensions[0])); put("xhyn_graphics", "ScreenHeight", Integer.toString(dimensions[1])); toast(dimensions[0] + " × " + dimensions[1] + " saved"); dialog.dismiss(); } catch (Exception e) { error(e); }
        })); dialog.show();
    }
    private EditText number(LinearLayout parent, String hint, String value) { EditText field = MenuStyle.input(activity); field.setHint(hint); field.setText(value); field.setInputType(InputType.TYPE_CLASS_NUMBER); parent.addView(field); return field; }
    private void fps() {
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); EditText value = number(panel, "0 = unlimited; otherwise 10..240", Integer.toString(XhynV02Runtime.frameLimit(cfg)));
        AlertDialog dialog = new MenuDialogBuilder(activity).setTitle("Rendered FPS cap").setView(panel).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> { try { int cap = Integer.parseInt(value.getText().toString()); if (cap != 0 && (cap < 10 || cap > 240)) throw new IOException("Choose 0, or 10..240 FPS"); removeOverride("dxgi.maxFrameRate"); put("performance", "frame_limit", Integer.toString(cap)); dialog.dismiss(); draw(); } catch (Exception e) { error(e); } })); dialog.show();
    }
    private void editor() {
        try {
            EditText field = MenuStyle.input(activity); field.setText(XhynV02Config.read(XhynV02Runtime.saved(cfg))); field.setTextSize(13); field.setTypeface(android.graphics.Typeface.MONOSPACE); field.setMinLines(8); field.setGravity(Gravity.TOP); field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            ScrollView scroll = new ScrollView(activity); scroll.addView(field);
            AlertDialog dialog = new MenuDialogBuilder(activity).setTitle("dxvk.conf · saved overrides").setView(scroll).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
            dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> { try { saveConfig(field.getText().toString()); dialog.dismiss(); } catch (Exception e) { error(e); } })); dialog.show();
        } catch (Exception e) { error(e); }
    }
    private void saveConfig(String text) throws Exception {
        XhynV02Config.validate(text); XhynV02Config.write(XhynV02Runtime.saved(cfg), text);
        List<String> unknown = XhynV02Config.unsupported(text, XhynV02Runtime.supported(activity, cfg));
        if (!unknown.isEmpty()) info("Saved with unsupported options", "The selected renderer does not advertise these options. They may be ignored:\n" + String.join("\n", unknown)); else toast("dxvk.conf saved for the next game launch");
    }
    private void updateProbe() {
        if(probeStatus==null || isFinishing())return;
        JSONObject record = XhynV02Runtime.lastProbe(activity, cfg);
        if (record == null || record.optJSONObject("result") == null) { probeStatus.setText("No driver result matches this package and Android build."); return; }
        JSONObject result = record.optJSONObject("result");
        int api = result.optInt("apiVersion");
        String version = ((api >>> 22) & 127) + "." + ((api >>> 12) & 1023);
        String details = result.optString("status") + " · " + result.optString("device", "GPU not identified")
                + "\n" + result.optString("driverName") + " · Vulkan " + version;
        if ("PASS".equals(result.optString("status"))) details += "\nTwo test frames presented. Game compatibility is not established.";
        else details += "\n" + result.optString("error");
        probeStatus.setText(details);
    }
    private void probe() {
        if (GTAVActivity.isGameRunning()) { info("Close the game", "The readiness test runs separately. Close the game first."); return; }
        handler.postDelayed(() -> { if (!isFinishing()) startActivityForResult(new Intent(activity, XhynV02ProbeActivity.class), PROBE); }, 800);
        // A native driver can terminate its process without returning an Activity result.
        handler.postDelayed(() -> { if (!isFinishing()) updateProbe(); }, 22000);
    }
    private void export() {
        io.execute(() -> {
            try {
                exportReport = XhynV02Reports.make(activity);
                runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, "XHYN-V-session.zip"), EXPORT_REPORT));
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }
    private void activityResult(int request, int result, Intent data) {
        if (request == PROBE) { cfg=RuntimeConfig.load(cfg.root);updateProbe();return; }
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); io.execute(() -> {
            try {
                if (request == IMPORT_CONFIG) {
                    byte[] bytes = readBounded(uri, XhynV02Config.LIMIT); String text = new String(bytes, StandardCharsets.UTF_8);
                    XhynV02Config.validate(text); XhynV02Config.write(XhynV02Runtime.saved(cfg), text); runOnUiThread(() -> { try { saveConfig(text); } catch (Exception e) { error(e); } });
                } else if (request == EXPORT_REPORT) {
                    if(exportReport==null)exportReport=new File(getCacheDir(),"xhyn-last-session.zip").getAbsolutePath();
                    try (InputStream input = new FileInputStream(exportReport); OutputStream out = getContentResolver().openOutputStream(uri, "w")) { byte[] buffer = new byte[65536]; int count; while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count); } runOnUiThread(() -> toast("Session report exported"));
                }
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }
    private byte[] readBounded(Uri uri, int limit) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) { byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("File exceeds 64 KiB"); output.write(buffer, 0, count); } return output.toByteArray(); }
    }
}
