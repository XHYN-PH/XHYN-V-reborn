package com.gtavsource.android;

import android.view.Surface;

/** Native operations are invoked explicitly at setup or in the isolated probe. */
public final class XhynV02Native {
    static { System.loadLibrary("xhyn_v02"); }
    public static native boolean install(String privateDirectory, String session);
    public static native String identity();
    public static native String probe(Surface surface);
    private XhynV02Native() {}
}
