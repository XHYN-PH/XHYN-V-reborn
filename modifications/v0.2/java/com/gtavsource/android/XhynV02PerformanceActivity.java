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

/** Settings are saved for the next full game launch. */
public final class XhynV02PerformanceActivity extends Activity {
    private RuntimeConfig cfg;
    private LinearLayout body;
    private TextView probeStatus;
    private static final int IMPORT_CONFIG = 502, EXPORT_REPORT = 503, IMPORT_DRIVER = 504, PROBE = 505;
    private String exportReport;
    private final java.util.concurrent.ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static void open(Activity activity) {
        if (activity instanceof GameMenuActivity && ((GameMenuActivity)activity).config != null)
            activity.getSharedPreferences("xhyn-v02", 0).edit().putString("gameRoot", ((GameMenuActivity)activity).config.root.getPath()).apply();
        activity.startActivity(new Intent(activity, XhynV02PerformanceActivity.class));
    }
    public static void addToPage(GameMenuActivity activity) {
        Button button = new Button(activity); button.setText("XHYN V · Performance and drivers"); button.setAllCaps(false);
        button.setOnClickListener(v -> open(activity)); activity.content.addView(button, 0);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { cfg = XhynV02Runtime.config(); }
        catch (Exception failure) { error(failure); finish(); return; }
        draw();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); io.shutdown(); super.onDestroy(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void text(String value, int size) { TextView v = new TextView(this); v.setText(value); v.setTextColor(0xffeeeeee); v.setTextSize(size); v.setPadding(0, dp(7), 0, dp(7)); body.addView(v); }
    private void section(String title) { text(title, 21); }
    private void button(String title, Runnable action) { Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); body.addView(b); }
    private void error(Throwable problem) { if (!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this).setTitle("XHYN V").setMessage(problem.getMessage() == null ? problem.toString() : problem.getMessage()).setPositiveButton("Close", null).show(); }
    private void info(String title, String value) { new AlertDialog.Builder(this).setTitle(title).setMessage(value).setPositiveButton("Close", null).show(); }
    private void put(String section, String key, String value) {
        RuntimeConfig live = XhynV02Runtime.config(); live.put(section, key, value); live.save(); cfg = live;
    }
    private void removeOverride(String key) throws Exception { File f = XhynV02Runtime.saved(cfg); String before = XhynV02Config.read(f); if (!before.isEmpty()) XhynV02Config.write(f, XhynV02Config.setGlobal(before, key, null)); }
    private void toggle(String title, String section, String key, boolean defaultValue, Runnable changed) {
        Switch value = new Switch(this); value.setText(title); value.setTextColor(0xffeeeeee); value.setPadding(0, dp(8), 0, dp(8));
        value.setChecked(cfg.getBoolean(section, key, defaultValue));
        value.setOnCheckedChangeListener((v, on) -> { try { put(section, key, Boolean.toString(on)); if (changed != null) changed.run(); } catch (Exception failure) { error(failure); } }); body.addView(value);
    }
    private void choice(String title, String[] labels, String[] values, String section, String key, String fallback, String dxvkKey) {
        text(title, 15); Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels); spinner.setAdapter(adapter);
        String saved = cfg.get(section, key, fallback); int selected = Arrays.asList(values).indexOf(saved); spinner.setSelection(Math.max(0, selected));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) { }
            public void onItemSelected(AdapterView<?> p, View view, int position, long id) {
                if (values[position].equals(cfg.get(section, key, fallback))) return;
                try { if (dxvkKey != null) removeOverride(dxvkKey); if (key.equals("compiler_workers")) removeOverride("dxvk.numShaderCompilerThreads"); put(section, key, values[position]); if(key.equals("vsync"))put("xhyn_graphics","VSync",values[position]); }
                catch (Exception failure) { error(failure); }
            }
        }); body.addView(spinner);
    }

    private void draw() {
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(22), dp(12), dp(22), dp(20)); body.setBackgroundColor(0xff17131f); scroll.addView(body); setContentView(scroll);
        section("XHYN V 0.2"); text("Changes apply after a full game restart. Graphics overrides apply once the game has created save/settings.xml. Close the game before changing its settings.", 14);
        button("Back", this::finish);
        section("Rendering and memory");
        button("Resolution / render scale", this::resolution);
        button("Use lower graphics settings", () -> new AlertDialog.Builder(this).setTitle("Use lower graphics settings?").setMessage("Sets textures, shadows, reflections, grass, water, particles and post effects to Normal, and disables MSAA, SSAO and long shadows. No FPS increase is guaranteed. You can change each setting below.").setPositiveButton("Apply", (d,w) -> {
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
        section("Frame rate and shader compilation");
        boolean generated=StableFrameProfile.framegen(cfg);
        button(generated ? "Rendered FPS: " + StableFrameProfile.base(cfg) + " · set by frame generation" : "Rendered FPS cap: " + XhynV02Runtime.frameLimit(cfg), generated ? () -> info("Frame generation", "Change the rendered and output targets on the launcher's frame-generation page, or turn frame generation off to choose a separate rendered FPS cap.") : this::fps);
        choice("VSync", new String[]{"Off", "On"}, new String[]{"0", "1"}, "xhyn", "vsync", "0", "dxgi.syncInterval");
        choice("Maximum frame latency", new String[]{"1 frame", "2 frames", "3 frames"}, new String[]{"1", "2", "3"}, "xhyn", "latency", XhynV02Runtime.defaultLatency(cfg), "dxgi.maxFrameLatency");
        choice("Shader compiler workers", new String[]{"Automatic", "1", "2", "3", "4", "6", "8"}, new String[]{"0", "1", "2", "3", "4", "6", "8"}, "xhyn", "compiler_workers", "0", "dxvk.numCompilerThreads");
        toggle("Async shader compilation (supported DXVK builds only)", "xhyn", "async", false, () -> { try { removeOverride("dxvk.enableAsync"); } catch (Exception e) { error(e); } });
        text("Async is ignored when the selected renderer does not implement it. More compiler workers can increase memory use and heat. Generated frames are separate from rendered FPS; frame generation remains in its own launcher page and starts disabled on fresh installs.", 13);
        section("dxvk.conf");
        toggle("Enable saved dxvk.conf overrides", "xhyn", "dxvk_conf_enabled", true, null);
        button("Edit dxvk.conf", this::editor);
        button("Import dxvk.conf", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT_CONFIG));
        button("View effective configuration", () -> { try { showText("Effective dxvk.conf", XhynV02Runtime.preview(this, XhynV02Runtime.config())); } catch (Exception e) { error(e); } });
        text("Saved overrides take priority. Changing a managed control removes its matching global override. Executable sections remain active and can still override controls. Turning the file off keeps your edits.", 13);
        section("Vulkan drivers");
        text("System Vulkan is the starting point for Xclipse. PanVK for Mali and imported RADV for Xclipse are experimental and require compatible Android drivers. Device brand and a ZIP name do not prove GPU support.", 14);
        button("Use System Vulkan · including Xclipse", () -> { put("vulkan", "driver", "system"); toast("System Vulkan selected; restart the game"); });
        button("Import Android Vulkan driver · Turnip / PanVK / RADV", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT_DRIVER));
        button("Choose imported driver", this::chooseDriver);
        button("Check selected driver: draw and present", this::probe);
        probeStatus = new TextView(this); probeStatus.setTextColor(0xffcccccc); body.addView(probeStatus); updateProbe();
        choice("Turnip memory mode · requires driver check", new String[]{"Automatic", "GMEM", "SYSMEM"}, new String[]{"0", "1", "2"}, "xhyn", "turnip_memory", "0", null);
        toggle("Turnip One UI workaround · requires driver check", "xhyn", "one_ui_workaround", false, null);
        text("Turnip controls apply only after the selected package was identified as Qualcomm Turnip. They do not apply to Qualcomm's stock driver, PanVK or Samsung Vulkan. Restart after changing a driver or these controls.", 13);
        section("Caches and reports");
        button("Clear XHYN shader caches", () -> new AlertDialog.Builder(this).setTitle("Clear shader caches?").setMessage("The game must be closed. Shaders will compile again on your next launch; this can temporarily increase stutter.").setPositiveButton("Clear", (d,w) -> { try { XhynV02Runtime.clearCaches(this); toast("Shader caches cleared"); } catch (Exception e) { error(e); } }).setNegativeButton("Cancel", null).show());
        choice("Capture duration", new String[]{"30 seconds", "2 minutes", "5 minutes", "20 minutes"}, new String[]{"30", "120", "300", "1200"}, "xhyn", "capture_seconds", "30", null);
        toggle("Capture the next game attempt", "performance", "capture", false, null);
        text("Capture stops at the chosen deadline. Normal play adds no periodic memory sampler. Reports keep the last eight attempts and label each driver, binary and configuration separately.", 13);
        button("Last engine error / device loss", () -> showText("Last engine error", XhynV02Runtime.lastError(this)));
        button("Export most recent session report", this::export);
        text("A missing script native indicates a game/script compatibility problem. Changing graphics drivers cannot repair an unknown native API. Reports include the most recent session and any available current error text.", 13);
        button("About XHYN V", () -> XhynV02Menu.about(this));
    }

    private static String readable(String value) { return value.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' '); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void showText(String title, String value) { TextView text = new TextView(this); text.setText(value); text.setTextIsSelectable(true); text.setTextColor(0xffeeeeee); text.setPadding(dp(16), dp(10), dp(16), dp(10)); ScrollView scroll = new ScrollView(this); scroll.addView(text); new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).show(); }

    private void resolution() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        EditText width = number(panel, "Width (320..7680)", cfg.get("xhyn_graphics", "ScreenWidth", "1280"));
        EditText height = number(panel, "Height (180..4320)", cfg.get("xhyn_graphics", "ScreenHeight", "720"));
        EditText scale = number(panel, "Render scale % (10..100)", "100");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Render resolution").setView(panel).setPositiveButton("Save", null).setNegativeButton("Cancel", null).setNeutralButton("Keep game setting", (d,w) -> { put("xhyn_graphics", "ScreenWidth", ""); put("xhyn_graphics", "ScreenHeight", ""); }).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try { int w = Integer.parseInt(width.getText().toString()), h = Integer.parseInt(height.getText().toString()); if (w < 320 || w > 7680 || h < 180 || h > 4320) throw new IOException("Use a width of 320..7680 and a height of 180..4320"); int[] dimensions = XhynV02Graphics.resolution(w, h, Integer.parseInt(scale.getText().toString())); put("xhyn_graphics", "ScreenWidth", Integer.toString(dimensions[0])); put("xhyn_graphics", "ScreenHeight", Integer.toString(dimensions[1])); toast(dimensions[0] + " × " + dimensions[1] + " saved"); dialog.dismiss(); } catch (Exception e) { error(e); }
        })); dialog.show();
    }
    private EditText number(LinearLayout parent, String hint, String value) { EditText field = new EditText(this); field.setHint(hint); field.setText(value); field.setInputType(InputType.TYPE_CLASS_NUMBER); parent.addView(field); return field; }
    private void fps() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); EditText value = number(panel, "0 = unlimited; otherwise 10..240", Integer.toString(XhynV02Runtime.frameLimit(cfg)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Rendered FPS cap").setView(panel).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> { try { int cap = Integer.parseInt(value.getText().toString()); if (cap != 0 && (cap < 10 || cap > 240)) throw new IOException("Choose 0, or 10..240 FPS"); removeOverride("dxgi.maxFrameRate"); put("performance", "frame_limit", Integer.toString(cap)); dialog.dismiss(); draw(); } catch (Exception e) { error(e); } })); dialog.show();
    }
    private void editor() {
        try {
            EditText field = new EditText(this); field.setText(XhynV02Config.read(XhynV02Runtime.saved(cfg))); field.setTextSize(13); field.setTypeface(android.graphics.Typeface.MONOSPACE); field.setMinLines(8); field.setGravity(Gravity.TOP); field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            ScrollView scroll = new ScrollView(this); scroll.addView(field);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("dxvk.conf · saved overrides").setView(scroll).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
            dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> { try { saveConfig(field.getText().toString()); dialog.dismiss(); } catch (Exception e) { error(e); } })); dialog.show();
        } catch (Exception e) { error(e); }
    }
    private void saveConfig(String text) throws Exception {
        XhynV02Config.validate(text); XhynV02Config.write(XhynV02Runtime.saved(cfg), text);
        List<String> unknown = XhynV02Config.unsupported(text, XhynV02Runtime.supported(this, cfg));
        if (!unknown.isEmpty()) info("Saved with unsupported options", "The selected renderer does not advertise these options. They may be ignored:\n" + String.join("\n", unknown)); else toast("dxvk.conf saved for the next game launch");
    }
    private void chooseDriver() {
        File[] files = cfg.drivers.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip"));
        if (files == null || files.length == 0) { info("Drivers", "Import an Android Vulkan driver ZIP first."); return; }
        Arrays.sort(files, Comparator.comparing(File::getName)); String[] names = new String[files.length]; for (int i = 0; i < names.length; i++) names[i] = files[i].getName();
        new AlertDialog.Builder(this).setTitle("Experimental custom Vulkan driver").setItems(names, (d,w) -> {
            io.execute(() -> { try { XhynV02DriverPackage.validate(files[w], Build.VERSION.SDK_INT, android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)); runOnUiThread(() -> { put("vulkan", "driver_zip", names[w]); put("vulkan", "driver", "custom"); updateProbe(); toast("Driver selected; check it before playing"); }); } catch (Exception e) { runOnUiThread(() -> error(e)); } });
        }).setNegativeButton("Cancel", null).show();
    }
    private void updateProbe() {
        JSONObject record = XhynV02Runtime.lastProbe(this, cfg);
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
        handler.postDelayed(() -> { if (!isFinishing()) startActivityForResult(new Intent(this, XhynV02ProbeActivity.class), PROBE); }, 800);
        // A native driver can terminate its process without returning an Activity result.
        handler.postDelayed(() -> { if (!isFinishing()) updateProbe(); }, 22000);
    }
    private void export() {
        io.execute(() -> {
            try {
                exportReport = XhynV02Reports.make(this);
                runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, "XHYN-V-session.zip"), EXPORT_REPORT));
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PROBE) { updateProbe(); return; }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); io.execute(() -> {
            try {
                if (request == IMPORT_CONFIG) {
                    byte[] bytes = readBounded(uri, XhynV02Config.LIMIT); String text = new String(bytes, StandardCharsets.UTF_8);
                    XhynV02Config.validate(text); XhynV02Config.write(XhynV02Runtime.saved(cfg), text); runOnUiThread(() -> { try { saveConfig(text); } catch (Exception e) { error(e); } });
                } else if (request == IMPORT_DRIVER) {
                    File temp = File.createTempFile("xhyn-driver-", ".zip", getCacheDir());
                    try {
                        try (InputStream input = getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(temp)) { byte[] buffer = new byte[65536]; int count; long total = 0; while ((count = input.read(buffer)) != -1) { total += count; if (total > 128L * 1024 * 1024) throw new IOException("Driver ZIP exceeds 128 MiB"); output.write(buffer, 0, count); } }
                        XhynV02DriverPackage.validate(temp, Build.VERSION.SDK_INT, android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE));
                        String name = "driver-" + XhynV02Runtime.hash(temp).substring(0, 16) + ".zip"; cfg.drivers.mkdirs(); java.nio.file.Files.move(temp.toPath(), new File(cfg.drivers, name).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        runOnUiThread(() -> { put("vulkan", "driver_zip", name); put("vulkan", "driver", "custom"); updateProbe(); toast("Driver imported. Run the driver check; restart before playing."); });
                    } finally { temp.delete(); }
                } else if (request == EXPORT_REPORT && exportReport != null) {
                    try (InputStream input = new FileInputStream(exportReport); OutputStream out = getContentResolver().openOutputStream(uri, "w")) { byte[] buffer = new byte[65536]; int count; while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count); } runOnUiThread(() -> toast("Session report exported"));
                }
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }
    private byte[] readBounded(Uri uri, int limit) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) { byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("File exceeds 64 KiB"); output.write(buffer, 0, count); } return output.toByteArray(); }
    }
}
