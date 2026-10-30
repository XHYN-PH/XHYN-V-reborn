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
    private final java.util.List<View> advanced=new ArrayList<>();
    private boolean showAdvanced;
    private boolean showDxvkAdvanced;
    private boolean disposed;
    private Map<String,String> dxvkValues=Collections.emptyMap(),dxvkExplicit=Collections.emptyMap();
    private Set<String> dxvkSupported=Collections.emptySet();
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
        value.advanced.clear();value.showAdvanced=false;
        if(page==DIAGNOSTICS){for(int n=0;n<activity.content.getChildCount();n++){View child=activity.content.getChildAt(n);if(child.getVisibility()==View.VISIBLE){value.advanced.add(child);child.setVisibility(View.GONE);}}activity.content.addView(value.body,0);}
        else activity.content.addView(value.body);
        value.draw();
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
        if(value!=null && !value.disposed){value.cfg=RuntimeConfig.load(value.cfg.root);if(value.page==DXVK||value.page==PERFORMANCE)value.draw();else value.updateProbe();}
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
        settingHelp(row,title,title);
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
        Switch control=MenuStyle.toggle(body,title,"",cfg.getBoolean(section,key,fallback),on->{
            try{put(section,key,Boolean.toString(on));if(changed!=null)changed.run();if(key.equals("dxvk_conf_enabled"))draw();}catch(Exception e){error(e);}
        });
        if(control.getParent() instanceof LinearLayout)settingHelp((LinearLayout)control.getParent(),title,key);
    }
    private void settingHelp(LinearLayout row,String title,String key){String help=XhynV02SettingHelp.get(key);if(help!=null)row.addView(MenuStyle.icon(activity,android.R.drawable.ic_menu_info_details,"About "+title,()->info(title,help)));}
    private String controlValue(String section,String key,String fallback,String dxvkKey){return dxvkKey!=null&&dxvkValues.containsKey(dxvkKey)?dxvkValues.get(dxvkKey):option(section,key,fallback);}
    private void choice(String title,String[] labels,String[] values,String section,String key,String fallback,String dxvkKey){
        String current=controlValue(section,key,fallback,dxvkKey);int selected=Arrays.asList(values).indexOf(current);
        LinearLayout row=MenuStyle.detailRow(body,title,selected>=0?labels[selected]:current+" (custom)");row.setTag("xhyn-setting-"+key);
        Runnable choose=()->{AlertDialog.Builder builder=new MenuDialogBuilder(activity).setTitle(title)
            .setSingleChoiceItems(labels,Arrays.asList(values).indexOf(controlValue(section,key,fallback,dxvkKey)),(dialog,index)->{
                try{
                    if(dxvkKey!=null)removeOverride(dxvkKey);
                    if(key.equals("compiler_workers"))removeOverride("dxvk.numShaderCompilerThreads");
                    put(section,key,values[index]);if(key.equals("vsync"))put("xhyn_graphics","VSync",values[index]);
                    View column=row.getChildAt(0);
                    if(column instanceof ViewGroup && ((ViewGroup)column).getChildCount()>1 && ((ViewGroup)column).getChildAt(1) instanceof TextView)
                        ((TextView)((ViewGroup)column).getChildAt(1)).setText(labels[index]);
                    row.setContentDescription(title+": "+labels[index]);dialog.dismiss();if(dxvkKey!=null)draw();
                }catch(Exception e){error(e);}
            }).setNegativeButton("Cancel",null);
            if(section.equals("xhyn_graphics"))builder.setNeutralButton("Restore default",(d,w)->restoreGraphics(java.util.Collections.singletonList(key)));
            builder.show();};
        settingHelp(row,title,key);row.addView(MenuStyle.icon(activity,android.R.drawable.ic_menu_more,"Change "+title,choose));row.setOnClickListener(v->choose.run());
    }
    private void draw(){
        if(body==null || isFinishing())return;
        body.removeAllViews();probeStatus=null;
        if(page==DXVK||page==PERFORMANCE)try{
            cfg=RuntimeConfig.load(cfg.root);activity.config=cfg;
            dxvkValues=XhynV02Config.globals(XhynV02Runtime.preview(activity,cfg));dxvkSupported=XhynV02Runtime.supported(activity,cfg);
            try{dxvkExplicit=cfg.getBoolean("xhyn","dxvk_conf_enabled",true)?XhynV02Config.globals(XhynV02Config.read(XhynV02Runtime.saved(cfg))):Collections.emptyMap();}catch(IOException invalidSaved){dxvkExplicit=Collections.emptyMap();}
        }catch(Exception failure){dxvkValues=Collections.emptyMap();dxvkExplicit=Collections.emptyMap();dxvkSupported=Collections.emptySet();text("Could not read DXVK settings: "+failure.getMessage(),13);}
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
        if(!GTAVActivity.isGameRunning())try{XhynV02Graphics.rememberQuality(new File(cfg.root,"save"));}catch(Exception failure){android.util.Log.w("XHYN V","Could not record original quality values",failure);}
        section("Rendering and memory");
        button("Resolution / render scale", this::resolution);
        button("Restore graphics quality defaults",()->new MenuDialogBuilder(activity).setTitle("Restore graphics quality defaults?")
            .setMessage("Restores quality values recorded before changes in this update. Resolution, controls and game saves are kept. Older releases did not record their original values.")
            .setPositiveButton("Restore defaults",(d,w)->restoreGraphics(XhynV02Graphics.qualityKeys())).setNegativeButton("Cancel",null).show());
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
        text("Lower texture quality and resolution reduce memory demand. Texture LOD bias and renderer memory settings are available under DXVK tuning.", 13);
    }
    private void performance(){
        section("Frame rate and shader compilation");
        boolean generated=StableFrameProfile.framegen(cfg);
        String cap=dxvkValues.containsKey("dxvk.maxFrameRate")?dxvkValues.get("dxvk.maxFrameRate"):dxvkValues.getOrDefault("dxgi.maxFrameRate",Integer.toString(XhynV02Runtime.frameLimit(cfg)));
        String capLabel=cap.equals("-1")?"Game / renderer default":cap.equals("0")?"Unlimited":cap;
        button("Rendered FPS cap: "+capLabel+(generated?" · frame generation active":""), generated ? () -> info("Frame generation", "Change the rendered and output targets on the frame-generation page. Explicit dxvk.conf values can override the DXVK cap. The current global cap is "+capLabel+".") : this::fps);
        choice("VSync", new String[]{"Off", "On"}, new String[]{"0", "1"}, "xhyn", "vsync", "0", "dxgi.syncInterval");
        choice("Maximum frame latency", new String[]{"1 frame", "2 frames", "3 frames"}, new String[]{"1", "2", "3"}, "xhyn", "latency", XhynV02Runtime.defaultLatency(cfg), "dxgi.maxFrameLatency");
        toggle("Match display refresh to the FPS target", "xhyn", "match_refresh", true, null);
        toggle("Android performance hints (experimental)", "xhyn", "performance_hints", false, null);
        text("Display matching requests a suitable screen refresh rate. Performance hints help Android schedule the presentation thread on supported Android 12+ devices. Both use the existing FPS target; device testing is still needed.",13);
    }
    private void dxvk(){
        section("DXVK tuning");
        text("These controls read and edit the same active configuration as the text editor. Options follow the selected bundled renderer. Restart the game after changing them.",13);
        String group="";
        String renderer="custom";try{renderer=XhynV02Runtime.rendererId(activity,cfg);}catch(Exception unavailable){}
        List<XhynV02DxvkOptions.Option> options=XhynV02DxvkOptions.supported(dxvkSupported,renderer);
        for(XhynV02DxvkOptions.Option option:options)if(!option.advanced()){
            if(!group.equals(option.group)){group=option.group;section(group);}dxvkOption(option);
        }
        boolean hasAdvanced=false;for(XhynV02DxvkOptions.Option option:options)if(option.advanced())hasAdvanced=true;
        if(hasAdvanced){button(showDxvkAdvanced?"Hide advanced DXVK options":"Show advanced DXVK options",()->{showDxvkAdvanced=!showDxvkAdvanced;draw();});
            if(showDxvkAdvanced){group="";for(XhynV02DxvkOptions.Option option:options)if(option.advanced()){if(!group.equals(option.group)){group=option.group;section(group);}dxvkOption(option);}}}
        if(dxvkSupported.isEmpty())text("Option support is unknown for this renderer. Use the editor with that renderer's documentation; tuning buttons are shown for recognized bundled builds.",13);
        else button("Reset DXVK tuning overrides",()->new MenuDialogBuilder(activity).setTitle("Reset DXVK tuning overrides?")
            .setMessage("Removes custom global values for the tuning controls. Comments, per-application sections and settings on the Performance page are kept. Per-application sections can still override the defaults.")
            .setPositiveButton("Reset overrides",(d,w)->{try{String source=XhynV02Config.read(XhynV02Runtime.saved(cfg));for(XhynV02DxvkOptions.Option option:XhynV02DxvkOptions.OPTIONS)source=XhynV02Config.setGlobal(source,option.key,null);saveConfig(source);}catch(Exception failure){error(failure);}}).setNegativeButton("Cancel",null).show());
        section("dxvk.conf");
        toggle("Enable saved dxvk.conf overrides", "xhyn", "dxvk_conf_enabled", true, null);
        button("Edit dxvk.conf", this::editor);
        button("Import dxvk.conf", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT_CONFIG));
        button("View effective configuration", () -> { try { showText("Effective dxvk.conf", XhynV02Runtime.preview(activity, RuntimeConfig.load(cfg.root))); } catch (Exception e) { error(e); } });
        text("Edit and View show the same active configuration for the next launch. Saving edits enables them. Changing a managed control removes its matching global override. Executable sections can override global controls. Turning the file off keeps your saved text.", 13);
        button("Clear XHYN shader caches", () -> new MenuDialogBuilder(activity).setTitle("Clear shader caches?").setMessage("The game must be closed. Shaders will compile again on your next launch; this can temporarily increase stutter.").setPositiveButton("Clear", (d,w) -> { try { XhynV02Runtime.clearCaches(activity); toast("Shader caches cleared"); } catch (Exception e) { error(e); } }).setNegativeButton("Cancel", null).show());
    }
    private void dxvkOption(XhynV02DxvkOptions.Option option){
        String current=dxvkValues.get(option.key);
        String suffix=current!=null&&!dxvkExplicit.containsKey(option.key)?" · default":"";
        LinearLayout row=MenuStyle.detailRow(body,option.title,option.display(current)+suffix);row.setTag("xhyn-dxvk-"+option.key);
        Runnable change=()->{
            MenuDialogBuilder builder=new MenuDialogBuilder(activity);
            String[] labels=new String[option.labels.length+1];labels[0]="Default";System.arraycopy(option.labels,0,labels,1,option.labels.length);
            String selectedValue=dxvkValues.get(option.key);int index=option.index(selectedValue);int selected=!dxvkExplicit.containsKey(option.key)?0:index<0?-1:index+1;
            builder.setTitle(option.title).setSingleChoiceItems(labels,selected,(dialog,item)->{
                try{writeDxvkOption(option,item==0?null:option.values[item-1]);dialog.dismiss();}catch(Exception failure){error(failure);}
            }).setNegativeButton("Cancel",null);
            if(option.memory)builder.setPositiveButton("Custom value",(dialog,which)->memoryValue(option));
            builder.show();
        };
        row.addView(MenuStyle.icon(activity,android.R.drawable.ic_menu_info_details,"About "+option.title,()->info(option.title,option.information())));
        row.addView(MenuStyle.icon(activity,android.R.drawable.ic_menu_edit,"Change "+option.title,change));row.setOnClickListener(v->change.run());
    }
    private void memoryValue(XhynV02DxvkOptions.Option option){
        LinearLayout panel=MenuStyle.column(activity);EditText input=number(panel,"MiB (0 = automatic; maximum 65536)",dxvkValues.getOrDefault(option.key,"0"));input.setTag("xhyn-dxvk-number");
        panel.addView(MenuStyle.text(activity,option.help,13,0xffb3b5b9));
        AlertDialog dialog=new MenuDialogBuilder(activity).setTitle(option.title).setView(panel).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{int value=Integer.parseInt(input.getText().toString());if(value<0||value>65536)throw new IOException("Choose a value from 0 to 65536 MiB");writeDxvkOption(option,Integer.toString(value));dialog.dismiss();}catch(Exception failure){input.setError(failure.getMessage()==null?"Enter a whole number from 0 to 65536":failure.getMessage());}}));dialog.show();
    }
    private void writeDxvkOption(XhynV02DxvkOptions.Option option,String value)throws Exception{
        cfg=RuntimeConfig.load(cfg.root);activity.config=cfg;
        if(value!=null&&!XhynV02Runtime.supported(activity,cfg).contains(option.key))throw new IOException("This option is not advertised by the selected renderer");
        String source=XhynV02Config.read(XhynV02Runtime.saved(cfg));
        saveConfig(XhynV02Config.setGlobal(source,option.key,value));
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
        section("Help with a crash or slow game");
        text("1. Turn on Record next game.\n2. Start the game and reproduce the problem.\n3. Return here and save the report to share with support.",14);
        toggle("Record next game", "performance", "capture", false, ()->put("xhyn","capture_seconds","1200"));
        text("Recording stops automatically after 20 minutes or when you close the game. It is off during normal play. Reports stay on your device until you choose to share them.",13);
        button("What happened last time?", () -> showText("Last game attempt", XhynV02Runtime.userSummary(activity)));
        button("Save report to share", this::export);
        button("View technical details", () -> showText("Technical details", XhynV02Runtime.lastError(activity)+"\n\n"+XhynV02Runtime.startupError(activity)));
        button(showAdvanced?"Hide advanced diagnostics":"Show advanced diagnostics",()->{showAdvanced=!showAdvanced;for(View view:advanced)if(view.getParent()==activity.content)view.setVisibility(showAdvanced?View.VISIBLE:View.GONE);draw();});
    }
    private boolean restoreGraphics(java.util.Collection<String> keys){
        if(GTAVActivity.isGameRunning()){info("Close the game first","Close the game before restoring graphics defaults.");return false;}
        try{
            int restored=XhynV02Graphics.restore(new File(cfg.root,"save"),keys);
            RuntimeConfig live=RuntimeConfig.load(cfg.root);
            for(String key:keys){live.put("xhyn_graphics",key.toLowerCase(Locale.ROOT),"");}
            live.save();cfg=live;activity.config=live;draw();
            if(restored>0)toast("Saved graphics defaults restored");
            else info("Quality overrides cleared","No original values were recorded for these fields. Your current game values have been kept. New changes will record the original values before applying them.");
            return true;
        }catch(Exception failure){error(failure);return false;}
    }
    private void resolution() {
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        EditText width = number(panel, "Base width (320..7680)", cfg.get("xhyn","render_width",option("xhyn_graphics", "ScreenWidth", "1280")));
        EditText height = number(panel, "Base height (180..4320)", cfg.get("xhyn","render_height",option("xhyn_graphics", "ScreenHeight", "720")));
        SeekBar scale=new SeekBar(activity);scale.setTag("xhyn-render-scale");scale.setMax(100);scale.setProgress(cfg.getInt("xhyn","render_percent",0,0,100));panel.addView(scale);
        TextView value=MenuStyle.text(activity,"",14,0xfff2f2f0);value.setTag("xhyn-render-scale-label");panel.addView(value);
        Runnable update=()->{int percent=scale.getProgress();if(percent==0)value.setText("0% · Use game resolution");else try{int[] size=XhynV02Graphics.resolution(Integer.parseInt(width.getText().toString()),Integer.parseInt(height.getText().toString()),percent);value.setText(percent+"% · "+size[0]+" × "+size[1]+" (minimum 320 × 180)");}catch(Exception ignored){value.setText(percent+"% · Enter a valid base size");}};
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int progress,boolean user){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});update.run();
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}public void afterTextChanged(android.text.Editable e){}};width.addTextChangedListener(watcher);height.addTextChangedListener(watcher);
        AlertDialog dialog = new MenuDialogBuilder(activity).setTitle("Render resolution").setView(panel).setPositiveButton("Save", null).setNegativeButton("Cancel", null).setNeutralButton("Use game resolution", (d,w) -> {if(restoreGraphics(Arrays.asList(XhynV02Graphics.SIZE)))put("xhyn","render_percent","0");}).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try {int percent=scale.getProgress();if(percent==0){if(restoreGraphics(Arrays.asList(XhynV02Graphics.SIZE))){put("xhyn","render_percent","0");dialog.dismiss();}return;}
                int w = Integer.parseInt(width.getText().toString()), h = Integer.parseInt(height.getText().toString()); if (w < 320 || w > 7680 || h < 180 || h > 4320) throw new IOException("Use a width of 320..7680 and a height of 180..4320");
                int[] dimensions = XhynV02Graphics.resolution(w, h, percent);RuntimeConfig live=RuntimeConfig.load(cfg.root);
                live.put("xhyn","render_width",Integer.toString(w));live.put("xhyn","render_height",Integer.toString(h));live.put("xhyn","render_percent",Integer.toString(percent));
                live.put("xhyn_graphics","screenwidth",Integer.toString(dimensions[0]));live.put("xhyn_graphics","screenheight",Integer.toString(dimensions[1]));live.save();cfg=live;activity.config=live;
                toast(dimensions[0] + " × " + dimensions[1] + " saved"); dialog.dismiss(); } catch (Exception e) { error(e); }
        })); dialog.show();
    }
    private EditText number(LinearLayout parent, String hint, String value) { EditText field = MenuStyle.input(activity); field.setHint(hint); field.setText(value); field.setInputType(InputType.TYPE_CLASS_NUMBER); parent.addView(field); return field; }
    private void fps() {
        int active=XhynV02Runtime.frameLimit(cfg);try{active=XhynV02Runtime.effectiveFrameLimit(activity,cfg);}catch(Exception ignored){}
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); EditText value = number(panel, "0 = unlimited; otherwise 10..240", Integer.toString(Math.max(0,active)));
        AlertDialog dialog = new MenuDialogBuilder(activity).setTitle("Rendered FPS cap").setView(panel).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> { try { int cap = Integer.parseInt(value.getText().toString()); if (cap != 0 && (cap < 10 || cap > 240)) throw new IOException("Choose 0, or 10..240 FPS"); removeOverride("dxgi.maxFrameRate");removeOverride("dxvk.maxFrameRate"); put("performance", "frame_limit", Integer.toString(cap)); dialog.dismiss(); draw(); } catch (Exception e) { error(e); } })); dialog.show();
    }
    private void editor() {
        try {
            EditText field = MenuStyle.input(activity); field.setTag("xhyn-dxvk-editor");field.setText(XhynV02Runtime.preview(activity,RuntimeConfig.load(cfg.root))); field.setTextSize(13); field.setTypeface(android.graphics.Typeface.MONOSPACE); field.setMinLines(8); field.setGravity(Gravity.TOP); field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            ScrollView scroll = new ScrollView(activity); scroll.addView(field);
            final String active=field.getText().toString();
            AlertDialog dialog = new MenuDialogBuilder(activity).setTitle("dxvk.conf · active configuration").setView(scroll).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
            dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> { try {
                String previous;try{previous=XhynV02Config.read(XhynV02Runtime.saved(cfg));}catch(IOException invalidOld){previous="";}
                saveConfig(XhynV02Config.editedOverrides(active,field.getText().toString(),previous,XhynV02Runtime.managedKeys(activity,cfg)));dialog.dismiss();
            } catch (Exception e) { error(e); } })); dialog.show();
        } catch (Exception e) { error(e); }
    }
    private void saveConfig(String text) throws Exception {
        XhynV02Config.validate(text);
        File file=XhynV02Runtime.saved(cfg);boolean existed=file.exists();String previous=null;
        // Explicit Save can repair an old oversized/invalid file. Such a file is
        // never read without bounds just to create an in-memory rollback copy.
        try{previous=XhynV02Config.read(file);}catch(IOException unreadableOld){}
        XhynV02Config.write(file,text);
        try{put("xhyn","dxvk_conf_enabled","true");}
        catch(Exception failedEnable){
            try{if(XhynV02Config.read(file).equals(XhynV02Config.normalize(text))){if(previous!=null)XhynV02Config.write(file,previous);else if(!existed&&!file.delete())throw new IOException("Could not remove incomplete configuration");}}
            catch(Exception rollback){failedEnable.addSuppressed(rollback);}throw failedEnable;
        }
        draw();
        List<String> unknown = XhynV02Config.unsupported(text, XhynV02Runtime.supported(activity, cfg));
        if("custom".equals(cfg.get("dxvk","source","builtin")))toast("Saved. Imported renderer option support is unknown; check its documentation.");
        else if (!unknown.isEmpty()) info("Saved with unsupported options", "The selected renderer does not advertise these options. They may be ignored:\n" + String.join("\n", unknown)); else toast("dxvk.conf saved for the next game launch");
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
        if ("PASS".equals(result.optString("status"))) details += "\n"+XhynV02Compatibility.advice(result,"custom".equals(cfg.get("dxvk","source","builtin"))?"custom":cfg.get("dxvk","builtin_id","gplall-async"));
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
                    XhynV02Config.validate(text); runOnUiThread(() -> { try { saveConfig(text); } catch (Exception e) { error(e); } });
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
