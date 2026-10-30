package com.gtavsource.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.Arrays;

/** About is an ordinary System child page; existing navigation views are untouched. */
public final class XhynV02Menu {
    public static final int ABOUT_PAGE = 21;
    public static final String TELEGRAM = "https://t.me/xhynv";
    public static final String YOUTUBE = "https://youtube.com/@xhyn_ph";
    public static final String REPOSITORY = "https://github.com/XHYN-PH/XHYN-V-reborn";

    public static String[] titles(String[] original) {
        if (original.length != ABOUT_PAGE) throw new IllegalStateException("Unexpected launcher page table");
        String[] result = Arrays.copyOf(original, original.length + 1);
        result[ABOUT_PAGE] = "About";
        return result;
    }
    public static int[][] children(int[][] original) {
        int[][] result = original.clone();
        int group = -1;
        for (int i=0;i<original.length;i++) for(int page:original[i]) if(page==18) group=i;
        if(group<0) throw new IllegalStateException("Game Resources navigation entry is missing");
        int[] previous=original[group]; int[] pages=new int[previous.length+1]; int next=0;
        for(int page:previous) { pages[next++]=page; if(page==18) pages[next++]=ABOUT_PAGE; }
        result[group]=pages;
        return result;
    }
    public static void about(Activity activity) {
        if(activity instanceof GameMenuActivity) ((GameMenuActivity)activity).showPage(ABOUT_PAGE);
    }
    public static void render(GameMenuActivity activity) {
        LinearLayout content=activity.content;
        content.addView(MenuStyle.heading(activity,"XHYN V 0.2 Hotfix 1"));
        content.addView(MenuStyle.text(activity,"Maintained by XHYN\n\nGTA V for Android, with touch controls, graphics settings and a Story Mode trainer. The engine runs ARM64 Android code; this edition uses DXVK for graphics translation.\n\nUse your own compatible GTA V Legacy files. Performance and compatibility depend on your device, driver and settings.",14,0xffb3b5b9));
        link(activity,content,"Telegram","XHYN V",TELEGRAM);
        link(activity,content,"YouTube","@xhyn_ph",YOUTUBE);
        link(activity,content,"GitHub","XHYN V repository",REPOSITORY);
        link(activity,content,"Buy GTA V","Official Rockstar Store","https://store.rockstargames.com/game/buy-gta-v");
        link(activity,content,"Credits and notices","Original authors and third-party components",REPOSITORY+"/blob/main/docs/CREDITS.md");
        content.addView(MenuStyle.text(activity,"Unofficial project. GTA V and its assets belong to their respective owners. Original engine, port and third-party credits remain applicable.",12,0xffb3b5b9));
    }
    private static void link(GameMenuActivity activity, LinearLayout parent,String title,String detail,String url) {
        LinearLayout row=MenuStyle.detailRow(parent,title,detail);
        row.addView(MenuStyle.icon(activity,android.R.drawable.ic_menu_info_details,"Open "+title,()->{
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
            catch(ActivityNotFoundException missing) { Toast.makeText(activity,"No app can open this link",Toast.LENGTH_LONG).show(); }
        }));
    }
    private XhynV02Menu() { }
}
