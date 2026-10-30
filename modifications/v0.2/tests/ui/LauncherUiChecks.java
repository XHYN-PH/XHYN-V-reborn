package com.gtavsource.android;

import android.app.*;
import android.content.*;
import android.content.res.AssetManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.robolectric.android.controller.ActivityController;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=30,application=Application.class,
        shadows=LauncherUiChecks.OfflineSource.class,
        instrumentedPackages={"com.deepseek.quarkdownload.RemoteSource"})
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherUiChecks {
    static final Path ROOT=Path.of(System.getProperty("xhyn.test.root"));
    ActivityController<Fixture> controller;
    Fixture activity;

    @Implements(className="com.deepseek.quarkdownload.RemoteSource")
    public static class OfflineSource {
        @Implementation public com.deepseek.quarkdownload.DownloadSpec resolve(int count)throws IOException {
            throw new IOException("Offline UI regression test");
        }
    }
    public static class Fixture extends GameMenuActivity {
        AssetManager assets;
        @Override public AssetManager getAssets(){
            if(assets==null){assets=ReflectionHelpers.callConstructor(AssetManager.class);
                ReflectionHelpers.callInstanceMethod(assets,"addAssetPath",ClassParameter.from(String.class,System.getProperty("xhyn.test.apk")));
                ReflectionHelpers.callInstanceMethod(assets,"addAssetPath",ClassParameter.from(String.class,System.getProperty("xhyn.test.assets")));}
            return assets;
        }
        @Override protected boolean isLauncher(){return false;}
        @Override protected void onCreate(Bundle state){
            setTheme(android.R.style.Theme_Material_NoActionBar);
            ReflectionHelpers.setField(this,"mCalled",true);
            try{
                File root=Files.createTempDirectory(ROOT,"game-").toFile();
                config=RuntimeConfig.initialize(root,new FileInputStream(System.getProperty("xhyn.test.ini")));
                profiles=new ProfileStore(root,getAssets());
                ReflectionHelpers.setField(this,"ready",true);
                ReflectionHelpers.setField(this,"selectedRoot",root);
                ReflectionHelpers.setField(this,"backdrop",new MenuBackdrop(this,false,null));
                getSharedPreferences("xhyn-v02",0).edit().putString("gameRoot",root.getPath()).commit();
            }catch(Exception error){throw new RuntimeException(error);}
        }
    }
    @Before public void create()throws Exception{
        Files.createDirectories(ROOT);
        XhynEnglish.load(Files.readString(Path.of(System.getProperty("xhyn.test.translations"))));
        controller=Robolectric.buildActivity(Fixture.class).setup();activity=controller.get();
    }
    @After public void destroy(){if(controller!=null)controller.pause().stop().destroy();}
    static List<TextView> textViews(View root){List<TextView> out=new ArrayList<>();walk(root,out);return out;}
    static void walk(View view,List<TextView> out){if(view instanceof TextView)out.add((TextView)view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)walk(group.getChildAt(i),out);}}
    static TextView find(View root,String text){for(TextView view:textViews(root))if(view.getText().toString().equals(text))return view;throw new AssertionError("Missing text: "+text+" in "+texts(root));}
    static String texts(View root){StringBuilder out=new StringBuilder();for(TextView v:textViews(root))out.append(v.getText()).append('\n');return out.toString();}
    void page(int page){activity.showPage(page);shadowOf(Looper.getMainLooper()).idle();}
    int current(){return ReflectionHelpers.getField(activity,"page");}
    View root(){return activity.getWindow().getDecorView();}
    void clickRow(String title){TextView text=find(activity.content,title);View item=text;while(item!=activity.content&&!item.isClickable())item=(View)item.getParent();assertTrue("Action is clickable: "+title,item.isClickable());item.performClick();shadowOf(Looper.getMainLooper()).idle();}
    void layout(int width){View view=root();view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));view.layout(0,0,width,720);}

    @Test public void aboutIsBelowResourcesWithoutChangingExistingPageIds(){
        assertEquals(22,MenuPages.TITLES.length);assertEquals(5,MenuPages.GROUPS.length);
        assertArrayEquals(new int[]{15,9,8,18,21},MenuPages.CHILDREN[4]);
        assertEquals("About",MenuPages.TITLES[21]);
        for(int page=0;page<22;page++){assertEquals(page==14?2:page,MenuPages.valid(page));int group=MenuPages.group(page);assertTrue(group>=0&&group<5);}
        assertEquals(4,MenuPages.group(9));assertEquals(4,MenuPages.group(18));assertEquals(4,MenuPages.group(21));
    }
    @Test public void systemRemainsClickableAtNarrowAndWideWidths(){
        for(int width:new int[]{640,1280}){
            page(0);layout(width);TextView system=find(root(),"System");
            assertTrue(system.isClickable());assertTrue(system.getWidth()>0);assertTrue(system.getHeight()>0);
            system.performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(4,MenuPages.group(current()));
            TextView resources=find(root(),XhynEnglish.t("GTAV资源下载"));assertTrue(resources.isClickable());
            resources.performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(18,current());
            assertTrue(texts(activity.content).contains("Start download"));
            find(root(),XhynEnglish.t("诊断")).performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(9,current());
            assertTrue(texts(activity.content).contains(XhynEnglish.t("性能读数来源")));
            find(root(),"About").performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(21,current());
            assertTrue(texts(activity.content).contains("Telegram"));assertFalse(texts(activity.content).contains("Export most recent session report"));
        }
    }
    @Test public void settingsAreSplitAcrossExistingLauncherPages(){
        page(0);String performance=texts(activity.content);assertTrue(performance.contains("Rendered FPS cap:"));assertFalse(performance.contains("Turnip memory mode"));assertFalse(performance.contains("Edit dxvk.conf"));assertFalse(performance.contains("Resolution / render scale"));
        page(4);String display=texts(activity.content);assertTrue(display.contains("Resolution / render scale"));assertTrue(display.contains("Texture Quality"));assertFalse(display.contains("Turnip memory mode"));
        page(1);String drivers=texts(activity.content);assertTrue(drivers.contains("Turnip memory mode"));assertTrue(drivers.contains("Check selected driver"));assertFalse(drivers.contains("Choose imported driver"));assertFalse(drivers.contains("Use System Vulkan · including Xclipse"));
        page(10);String dxvk=texts(activity.content);assertTrue(dxvk.contains("Edit dxvk.conf"));assertTrue(dxvk.contains("Shader compiler workers"));assertFalse(dxvk.contains("Turnip memory mode"));
        page(9);String reports=texts(activity.content);assertTrue(reports.contains("Capture duration"));assertTrue(reports.contains("Export most recent session report"));assertTrue(reports.contains(XhynEnglish.t("性能读数来源")));assertTrue(reports.contains(XhynEnglish.t("驱动运行记录")));
    }
    @Test public void existingCustomDriverDoesNotRequireAReadinessResult()throws Exception {
        File driver=new File(activity.config.drivers,"existing-driver.zip");Files.writeString(driver.toPath(),"existing imported package fixture");
        activity.config.put("vulkan","driver","custom");activity.config.put("vulkan","driver_zip",driver.getName());activity.config.save();
        XhynV02Runtime.configure(activity,activity.config);
        assertTrue(XhynV02Runtime.effective(activity).isFile());
    }
    @Test public void invalidLegacyConfigDoesNotBlockTheGameOrEraseUserText()throws Exception {
        File saved=XhynV02Runtime.saved(activity.config);String invalid="dxvk.hud = True\nd3d9.presentInterval = 1\n";XhynV02Config.write(saved,invalid);
        XhynV02Runtime.configure(activity,activity.config);
        assertEquals(invalid,XhynV02Config.read(saved));assertFalse(XhynV02Config.read(XhynV02Runtime.effective(activity)).contains("d3d9."));
        assertTrue(XhynV02Runtime.startupError(activity).contains("Saved dxvk.conf overrides could not be applied"));
    }
    @Test public void missingOldDriverPackageUsesTheExistingFallbackPath()throws Exception {
        activity.config.put("vulkan","driver","custom");activity.config.put("vulkan","driver_zip","removed-driver.zip");activity.config.save();
        XhynV02Runtime.configure(activity,activity.config);
        assertTrue(XhynV02Runtime.driverKey(activity.config).startsWith("unavailable-"));
        String status=DriverManager.prepareDiagnostics(activity,activity.config);
        assertTrue(status,status.contains("system") || status.contains("unavailable"));
        assertTrue(DriverManager.diagnostics().contains("package") || DriverManager.diagnostics().contains("rejected"));
    }
    @Test public void disabledOversizedConfigIsNotReadAndIsKept()throws Exception {
        File saved=XhynV02Runtime.saved(activity.config);byte[] original=new byte[70000];java.util.Arrays.fill(original,(byte)'x');Files.write(saved.toPath(),original);
        activity.config.put("xhyn","dxvk_conf_enabled","false");activity.config.save();
        XhynV02Runtime.configure(activity,activity.config);assertArrayEquals(original,Files.readAllBytes(saved.toPath()));
    }
    @Test public void setupFailureIsNotMisreportedAsAnInvalidFolder() {
        assertEquals("Graphics setup failed",XhynV02Runtime.launchFailureTitle(new XhynV02Runtime.LaunchSetupFailure("bad config",new IOException())));
        assertEquals("Invalid game folder",XhynV02Runtime.launchFailureTitle(new IllegalArgumentException("bad path")));
    }
    @Test public void nativeSwitchStylePersistsAndSynchronizesLauncherConfiguration(){
        page(1);TextView label=find(activity.content,"Turnip One UI workaround");View row=(View)label.getParent();while(!row.isClickable())row=(View)row.getParent();row.performClick();
        assertTrue(RuntimeConfig.load(activity.config.root).getBoolean("xhyn","one_ui_workaround",false));
        assertTrue(activity.config.getBoolean("xhyn","one_ui_workaround",false));
        page(0);page(1);assertTrue(RuntimeConfig.load(activity.config.root).getBoolean("xhyn","one_ui_workaround",false));
    }
    @Test public void dxvkImportIsRoutedThroughLauncherAndCancelDoesNotModifySavedText()throws Exception{
        page(10);File saved=XhynV02Runtime.saved(activity.config);XhynV02Config.write(saved,"dxvk.numCompilerThreads = 3\n");
        clickRow("Import dxvk.conf");org.robolectric.shadows.ShadowActivity.IntentForResult intent=shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(intent);assertEquals(502,intent.requestCode);assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.intent.getAction());
        assertTrue(XhynV02Settings.result(activity,502,Activity.RESULT_CANCELED,null));assertEquals("dxvk.numCompilerThreads = 3\n",XhynV02Config.read(saved));
        assertFalse(XhynV02Settings.result(activity,21,Activity.RESULT_CANCELED,null));assertFalse(XhynV02Settings.result(activity,0xf244,Activity.RESULT_CANCELED,null));
    }
    @Test public void graphicsChoicesUseValidIniKeysAndReachTheActualXml()throws Exception {
        page(4);activity.content.findViewWithTag("xhyn-setting-TextureQuality").performClick();shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dialog.getListView().performItemClick(dialog.getListView().getAdapter().getView(1,null,dialog.getListView()),1,1);shadowOf(Looper.getMainLooper()).idle();
        assertEquals("0",RuntimeConfig.load(activity.config.root).get("xhyn_graphics","texturequality",""));
        File save=new File(activity.config.root,"save");save.mkdirs();Path xml=new File(save,"settings.xml").toPath();
        Files.writeString(xml,"<Settings><graphics><TextureQuality value=\"2\"/></graphics></Settings>");
        XhynV02Runtime.configure(activity,activity.config);
        assertTrue(Files.readString(xml).contains("TextureQuality value=\"0\""));
    }
    @Test public void allLauncherPageHeadersRemainReachableWithoutLoadingGameData(){
        for(int page=0;page<22;page++){
            ReflectionHelpers.callInstanceMethod(activity,"showPage",ClassParameter.from(int.class,page),ClassParameter.from(boolean.class,false));
            assertEquals(page==14?2:page,current());
            assertNotNull(find(root(),XhynEnglish.t(MenuPages.TITLES[current()])));
            assertTrue(find(root(),"System").isClickable());
        }
    }
    @Test public void selectionUsesThemedDialogAndUpdatesItsCurrentValue(){
        page(0);activity.content.findViewWithTag("xhyn-setting-vsync").performClick();shadowOf(Looper.getMainLooper()).idle();AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);
        dialog.getListView().performItemClick(dialog.getListView().getAdapter().getView(1,null,dialog.getListView()),1,1);shadowOf(Looper.getMainLooper()).idle();
        assertEquals("1",RuntimeConfig.load(activity.config.root).get("xhyn","vsync",""));assertEquals("1",activity.config.get("xhyn_graphics","vsync",""));
        assertTrue(texts((View)root().findViewWithTag("xhyn-setting-vsync")).contains("On"));
    }
}
