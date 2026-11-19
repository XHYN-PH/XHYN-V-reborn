package com.gtavsource.android;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Keep the initiating Java/native-loader error distinct from subsequent exits. */
public final class XhynV02Launch {
    private static JSONObject attempt;
    private static File boundSession;

    public static synchronized void begin(Context context) {
        try {
            attempt=new JSONObject().put("attempt",UUID.randomUUID().toString())
                    .put("pid",Process.myPid()).put("start_ms",System.currentTimeMillis())
                    .put("start_elapsed_ms",SystemClock.elapsedRealtime())
                    .put("last_phase","Checking the game folder").put("phase_elapsed_ms",0)
                    .put("events",new JSONArray());
            boundSession=null;
            persist(context);
        } catch(Exception error){Log.w("XHYN V","Could not initialize launch timeline",error);}
    }

    public static synchronized void phase(Context context, String name) {
        if (attempt == null || name.equals(attempt.optString("last_phase"))) return;
        try {
            attempt.put("last_phase",name).put("phase_elapsed_ms",
                    SystemClock.elapsedRealtime()-attempt.getLong("start_elapsed_ms"));
            persist(context);
        } catch (Exception error) {
            Log.w("XHYN V","Could not update the launch stage",error);
        }
    }

    public static synchronized void bind(Context context,String id,File directory) {
        try {
            if(attempt==null)begin(context);
            attempt.put("session",id);
            boundSession=directory;
            persist(context);
        } catch(Exception error){Log.w("XHYN V","Could not bind launch timeline",error);}
    }

    public static synchronized void record(Context context,Throwable failure,String phase) {
        try {
            if(attempt==null)begin(context);
            StringWriter stack=new StringWriter();failure.printStackTrace(new PrintWriter(stack));
            String detail=stack.toString();if(detail.length()>16384)detail=detail.substring(0,16384);
            JSONObject event=new JSONObject().put("time_ms",System.currentTimeMillis())
                    .put("phase",phase).put("type",failure.getClass().getName())
                    .put("message",String.valueOf(failure.getMessage())).put("stack",detail);
            JSONArray events=attempt.getJSONArray("events");
            if(events.length()<16)events.put(event);
            if(!attempt.has("first_error"))attempt.put("first_error",event);
            attempt.put("last_error_ms",event.getLong("time_ms"));
            persist(context);
        } catch(Exception error){Log.w("XHYN V","Could not preserve loader error",error);}
    }

    public static JSONObject read(Context context) {
        try {return new JSONObject(XhynV02Config.read(new File(context.getFilesDir(),"xhyn-java-launch.json")));}
        catch(Exception unavailable){return new JSONObject();}
    }

    public static boolean failedBeforeSession(JSONObject launch) {
        return launch.has("first_error") && launch.optString("session", "").isEmpty();
    }

    public static String current(Context context,String session) {
        JSONObject data=read(context);
        if(!data.has("first_error")||!session.equals(data.optString("session","")))return "";
        return data.toString();
    }

    private static void persist(Context context)throws Exception {
        String text=attempt.toString(2);
        XhynV02Config.write(new File(context.getFilesDir(),"xhyn-java-launch.json"),text);
        if(boundSession!=null)XhynV02Config.write(new File(boundSession,"java-launch.json"),text);
    }
    private XhynV02Launch(){}
}
