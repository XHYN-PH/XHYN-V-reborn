package com.gtavsource.android;

import android.app.Application;
import android.app.Dialog;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libsdl.app.SDLActivity;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk={30,35}, application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SdlRetryChecks {
    public static class FailingGame extends SDLActivity {
        static int loadAttempts;

        @Override public void loadLibraries() {
            loadAttempts++;
            throw new UnsatisfiedLinkError("Simulated library load failure");
        }
    }

    @Test public void firstFailureAndRetryBothReachOriginalLoader() {
        FailingGame.loadAttempts = 0;
        ReflectionHelpers.setStaticField(SDLActivity.class, "mActivityCreated", false);
        ReflectionHelpers.setStaticField(SDLActivity.class, "mSDLMainFinished", false);
        String threadName = Thread.currentThread().getName();
        try {
            for (int attempt = 1; attempt <= 2; attempt++) {
                ActivityController<FailingGame> controller = Robolectric.buildActivity(FailingGame.class);
                controller.get().setTheme(android.R.style.Theme_Material_NoActionBar);
                controller.create();
                try {
                    assertEquals("Original library load reached", attempt, FailingGame.loadAttempts);
                    assertFalse("Failed load leaves recreation JNI disabled",
                            ReflectionHelpers.<Boolean>getStaticField(SDLActivity.class, "mActivityCreated"));
                    Dialog dialog = ShadowDialog.getLatestDialog();
                    assertNotNull("SDL displays its load failure", dialog);
                    dialog.dismiss();
                } finally {
                    controller.destroy();
                }
            }
        } finally {
            Thread.currentThread().setName(threadName);
        }
    }
}
