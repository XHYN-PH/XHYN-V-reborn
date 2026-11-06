package com.gtavsource.android;

import android.content.Context;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.os.SystemClock;

/** Called on the observed presentation thread. Optional Android 12+ scheduling advice. */
public final class XhynV02FrameHints {
    private static Context context;
    private static boolean paused,failed;
    private static Api31 api;
    private static String status="Off";
    public static synchronized void initialize(Context value){context=value.getApplicationContext();}
    public static synchronized void pause(){paused=true;close();status="Paused";}
    public static synchronized void resume(){paused=false;failed=false;}
    private static void close(){if(api!=null){api.close();api=null;}}
    public static synchronized String status(){return status;}
    public static synchronized void report(long work,long target){
        if(Build.VERSION.SDK_INT<31){status="Android 12 or newer is required";return;}
        if(paused||failed||context==null||work<=0||target<=0)return;
        try{if(api==null)api=new Api31(context);status=api.report(work,target);}
        catch(RuntimeException|LinkageError error){close();failed=true;status="Unavailable on this device; game continues normally";}
    }
    @android.annotation.TargetApi(31)
    private static final class Api31 {
        final PerformanceHintManager manager;
        PerformanceHintManager.Session session;int tid;long target,last;
        Api31(Context context){manager=context.getSystemService(PerformanceHintManager.class);}
        String report(long work,long requested){
            if(manager==null)return "Android did not provide performance hints";
            long next=Math.max(4_166_667L,Math.min(100_000_000L,requested));
            int current=android.os.Process.myTid();
            if(session==null||tid!=current){close();tid=current;target=next;session=manager.createHintSession(new int[]{tid},target);}
            if(session==null)return "Android did not create a hint session";
            if(target!=next){target=next;session.updateTargetWorkDuration(target);}
            long now=SystemClock.elapsedRealtimeNanos();
            if(now-last>=Math.max(0,manager.getPreferredUpdateRateNanos())){session.reportActualWorkDuration(Math.max(1,work));last=now;}
            return "Active for the presentation thread; CPU work only";
        }
        void close(){if(session!=null){try{session.close();}catch(RuntimeException ignored){}finally{session=null;}}}
    }
    private XhynV02FrameHints(){}
}
