package com.gtavsource.android;

import android.content.Context;

/** SDL's recreation decision is native, so load its core before that decision. */
public final class XhynV02Sdl {
    public static void beforeCreate(Context context) {
        try {
            // Use SDL's own contextual loader. Its later full library load is
            // retained; no driver preparation or engine startup is moved here.
            org.libsdl.app.SDL.loadLibrary("SDL3",context);
        } catch(UnsatisfiedLinkError|RuntimeException failure) {
            XhynV02Launch.record(context,failure,"SDL core load before recreation check");
            throw failure;
        }
    }
    private XhynV02Sdl(){}
}
