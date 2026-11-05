package com.gtavsource.android;

import android.app.Application;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.UserHandle;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowEnvironment;
import org.robolectric.shadows.StorageVolumeBuilder;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Execute the release's real Application.onCreate using only release resources. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk={30,35}, application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ApkStartupChecks {
    private ArtworkGuardApplication startApplication() throws Exception {
        final AssetManager assets=RuntimeEnvironment.getApplication().getAssets();
        int cookie=ReflectionHelpers.callInstanceMethod(assets,"addAssetPath",
                ClassParameter.from(String.class,System.getProperty("xhyn.test.apk")));
        assertTrue("Release APK assets loaded",cookie!=0);
        final ArtworkGuardApplication app=new ArtworkGuardApplication();
        Context context=new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public AssetManager getAssets(){return assets;}
            @Override public Context getApplicationContext(){return app;}
        };
        ReflectionHelpers.callInstanceMethod(app,"attach",ClassParameter.from(Context.class,context));
        app.onCreate();
        return app;
    }
    @Test public void releaseApplicationAuthenticatesArtworkAndLoadsDictionary() throws Exception {
        ArtworkGuardApplication app=startApplication();
        assertEquals("System",XhynEnglish.t("系统"));
        assertNotNull("Touch artwork decrypts and renders",ProtectedArtwork.bitmap(app,"glyph/fire"));
    }
    private File mountedStorage() throws Exception {
        // Model a mounted Android volume; Robolectric starts with no volumes.
        File storage=Files.createTempDirectory("xhyn-startup-storage").toFile();
        ShadowEnvironment.addExternalDir(storage.getPath());
        ShadowEnvironment.setExternalStorageDirectory(storage.toPath());
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
        StorageManager manager=(StorageManager)RuntimeEnvironment.getApplication().getSystemService(Context.STORAGE_SERVICE);
        shadowOf(manager).addStorageVolume(new StorageVolumeBuilder("primary",storage,"Test storage",
                ReflectionHelpers.callConstructor(UserHandle.class,ClassParameter.from(int.class,0)),
                Environment.MEDIA_MOUNTED).setIsPrimary(true).setIsEmulated(true).build());
        Application app=RuntimeEnvironment.getApplication();
        AppOpsManager ops=(AppOpsManager)app.getSystemService(Context.APP_OPS_SERVICE);
        shadowOf(ops).setMode("android:manage_external_storage",android.os.Process.myUid(),
                app.getPackageName(),AppOpsManager.MODE_ERRORED);
        assertFalse("Fixture has no all-files permission",Environment.isExternalStorageManager());
        return storage;
    }
    private ActivityController<StoragePermissionActivity> launcher(File folder) {
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),StoragePermissionActivity.class);
        intent.putExtra("game_root",folder.getPath());
        ActivityController<StoragePermissionActivity> controller=Robolectric.buildActivity(StoragePermissionActivity.class,intent);
        // Config.NONE has no manifest theme; match GameMenuTheme's parent.
        controller.get().setTheme(android.R.style.Theme_Material_NoActionBar);
        ReflectionHelpers.callInstanceMethod(controller.get().getAssets(),"addAssetPath",
                ClassParameter.from(String.class,System.getProperty("xhyn.test.apk")));
        return controller;
    }
    @Test public void launcherRunsOriginalCreateAndResumeWithReleaseResources() throws Exception {
        startApplication();
        File storage=mountedStorage();
        ActivityController<StoragePermissionActivity> controller=launcher(storage);
        try {
            controller.create().start().resume().visible();
            StoragePermissionActivity activity=controller.get();
            ExecutorService io=ReflectionHelpers.getField(activity,"io");
            io.submit(()->{}).get(30,TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
            assertFalse("Launcher remains open",activity.isFinishing());
            String visible=texts(activity.getWindow().getDecorView());
            assertEquals("Launcher initialized its real config: "+visible,Boolean.TRUE,ReflectionHelpers.getField(activity,"ready"));
            assertTrue("Launcher renders menu navigation: "+visible,visible.contains("System"));
            assertFalse("Startup interface is translated: "+visible,XhynEnglish.chinese(visible));
        } finally {
            controller.pause().stop().destroy();
        }
    }
    @Test public void launcherWithoutFileAccessShowsPermissionAction() throws Exception {
        startApplication();
        mountedStorage();
        // A read-only fixture path models inaccessible game storage on the host.
        ActivityController<StoragePermissionActivity> controller=launcher(new File("/proc/self/xhyn-startup-fixture"));
        try {
            controller.create().start().resume().visible();
            String visible=texts(controller.get().getWindow().getDecorView());
            assertFalse(controller.get().isFinishing());
            assertTrue("Permission action is reachable: "+visible,visible.contains("Grant file access"));
            assertFalse("Permission page is translated: "+visible,XhynEnglish.chinese(visible));
        } finally {
            controller.pause().stop().destroy();
        }
    }
    private static String texts(View root) {
        StringBuilder text=new StringBuilder();
        if(root instanceof TextView)text.append(((TextView)root).getText()).append('\n');
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)text.append(texts(group.getChildAt(i)));}
        return text.toString();
    }
}
