package com.gtavsource.android;
import android.app.Activity;
import android.content.Context;
import android.os.PowerManager;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.util.WeakHashMap;

/** One display request uses the existing FPS target. No additional frame limiter. */
public final class XhynV02Display {
    private static final WeakHashMap<Activity,Integer> previous=new WeakHashMap<>();
    private static final WeakHashMap<Surface,Integer> requests=new WeakHashMap<>();

    static boolean supportsSustained(Context context) {
        try {
            PowerManager power = context.getSystemService(PowerManager.class);
            return power != null && power.isSustainedPerformanceModeSupported();
        } catch (RuntimeException unavailable) { return false; }
    }

    static void sustain(Activity activity, RuntimeConfig cfg) {
        try {
            if (supportsSustained(activity)) {
                boolean enabled = cfg != null && cfg.getBoolean("xhyn", "sustained_performance", false);
                activity.getWindow().setSustainedPerformanceMode(enabled);
            }
        } catch (RuntimeException unavailable) {
            android.util.Log.w("XHYN V", "Could not apply sustained performance mode", unavailable);
        }
    }
    public static int choose(float[] rates,int target){
        int selected=-1;double best=Double.POSITIVE_INFINITY;
        for(int i=0;i<rates.length;i++){
            float rate=rates[i];if(!Float.isFinite(rate)||rate<=0)continue;
            double multiple=target>0?Math.round(rate/target):0;
            double score=target<=0?-rate:multiple>=1&&Math.abs(rate-multiple*target)<0.5?multiple-1:rate>=target-0.5?1000+rate-target:2000+target-rate;
            if(score<best){best=score;selected=i;}
        }
        return selected;
    }
    public static void apply(Activity activity){
        try{
            RuntimeConfig cfg=XhynV02Runtime.config();
            if(!cfg.getBoolean("xhyn","match_refresh",true)){release(activity);return;}
            int target=StableFrameProfile.framegen(cfg)?StableFrameProfile.target(cfg):XhynV02Runtime.effectiveFrameLimit(activity,cfg);
            if(target<=0){release(activity);return;}
            Display display=activity.getWindowManager().getDefaultDisplay();if(display==null)return;
            Display.Mode current=display.getMode();Display.Mode[] modes=display.getSupportedModes();
            float[] rates=new float[modes.length];for(int i=0;i<modes.length;i++)if(modes[i].getPhysicalWidth()==current.getPhysicalWidth()&&modes[i].getPhysicalHeight()==current.getPhysicalHeight())rates[i]=modes[i].getRefreshRate();
            int index=choose(rates,target);if(index>=0){WindowManager.LayoutParams attributes=activity.getWindow().getAttributes();
                if(attributes.preferredDisplayModeId!=modes[index].getModeId()){
                    previous.putIfAbsent(activity,attributes.preferredDisplayModeId);
                    attributes.preferredDisplayModeId=modes[index].getModeId();
                    activity.getWindow().setAttributes(attributes);
                }}
            surface(activity.getWindow().getDecorView(),target);
        }catch(Exception|LinkageError ignored){/* Display requests are best effort, never launch requirements. */}
    }
    private static void surface(View view,int target){
        if(view instanceof SurfaceView){Surface value=((SurfaceView)view).getHolder().getSurface();
            if(value!=null&&value.isValid()){
                Integer requested=requests.get(value);
                if(target==0&&requested!=null){
                    value.setFrameRate(0,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                    requests.remove(value);
                }else if(target>0&&!Integer.valueOf(target).equals(requested)){
                    value.setFrameRate(target,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                    requests.put(value,target);
                }
            }}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)surface(group.getChildAt(i),target);}
    }
    public static void release(Activity activity){
        try{surface(activity.getWindow().getDecorView(),0);}catch(RuntimeException ignored){}
        Integer mode=previous.remove(activity);if(mode==null)return;
        try{WindowManager.LayoutParams attributes=activity.getWindow().getAttributes();attributes.preferredDisplayModeId=mode;activity.getWindow().setAttributes(attributes);}catch(RuntimeException ignored){}
    }
    private XhynV02Display(){}
}
