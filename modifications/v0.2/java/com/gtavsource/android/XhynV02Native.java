package com.gtavsource.android;

import android.view.Surface;

/** Native operations are invoked explicitly at setup or in the isolated probe. */
public final class XhynV02Native {
    static { System.loadLibrary("xhyn_v02"); }
    public static native boolean install(String privateDirectory, String session);
    public static native String identity();
    public static native String probe(Surface surface);
    public static native boolean observe(boolean capture,boolean hints,int presentationFps);
    public static native String metrics();
    public static native void capture(boolean enabled);
    public static native void paused(boolean paused);
    private XhynV02Native() {}
}
