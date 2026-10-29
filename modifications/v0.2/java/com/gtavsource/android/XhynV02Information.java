package com.gtavsource.android;
import android.app.Activity;
import android.widget.*;
import android.content.Intent;
import android.net.Uri;

/** Local English information replaces remote download notices. */
public final class XhynV02Information {
 public static String notice() { return "XHYN V 0.2\n\nUse your own compatible GTA V Legacy files. Game data is not bundled in this APK.\n\nStart with System Vulkan and lower graphics settings. Imported Turnip, PanVK and RADV drivers require matching Android hardware and a separate driver check.\n\nThe engine runs ARM64 Android code. DXVK translates its Direct3D graphics calls to Vulkan. Compatibility depends on your device, driver, and game data.\n\nProject and credits: https://github.com/XHYN-PH/XHYN-V-reborn\nBuy GTA V: https://store.rockstargames.com/game/buy-gta-v"; }
 private XhynV02Information() { }
}
