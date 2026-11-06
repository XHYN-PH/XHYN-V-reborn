package com.gtavsource.android;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Process;
import org.json.JSONArray;
import org.json.JSONObject;

/** OS exit records are evidence only when process identity and time agree. */
public final class XhynV02ExitInfo {
    public static boolean matches(ApplicationExitInfo info,int pid,long start,long end,String process){
        return info.getPid()==pid&&info.getTimestamp()>=start&&info.getTimestamp()<=end
                &&process.equals(info.getProcessName());
    }
    public static JSONObject collect(Context context,int pid,long start,long end)throws Exception{
        JSONObject result=new JSONObject().put("session_pid",pid).put("session_start_ms",start)
                .put("query_end_ms",end).put("scope","Only matching app process, PID and time range; absence is not a clean-exit result");
        if(pid==Process.myPid())return result.put("status","Session process is still running; no OS exit record expected yet");
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null)return result.put("status","OS process-exit service unavailable");
        String process=context.getApplicationInfo().processName;
        if(process==null||process.isEmpty())process=context.getPackageName();
        JSONArray matches=new JSONArray();
        for(ApplicationExitInfo info:manager.getHistoricalProcessExitReasons(context.getPackageName(),pid,8)){
            if(!matches(info,pid,start,end,process))continue;
            matches.put(new JSONObject().put("pid",info.getPid()).put("process",info.getProcessName())
                    .put("timestamp_ms",info.getTimestamp()).put("reason",info.getReason())
                    .put("status",info.getStatus()).put("importance",info.getImportance())
                    .put("description",info.getDescription()).put("pss_kib",info.getPss()).put("rss_kib",info.getRss()));
        }
        return result.put("matching_records",matches).put("status",matches.length()==1?"One matching OS exit record":matches.length()>1?"Multiple candidates; OS exit attribution is ambiguous":"No matching OS exit record available");
    }
    private XhynV02ExitInfo(){}
}
