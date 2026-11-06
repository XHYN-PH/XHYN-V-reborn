import com.gtavsource.android.XhynV02LogSlice;
import com.gtavsource.android.XhynV02Comparison;
import org.json.JSONObject;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class LogHostChecks {
 static int checks;
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);Files.createDirectories(root);Path file=root.resolve("runtime.log");
  Files.writeString(file,"old attempt\n");String snapshot=XhynV02LogSlice.snapshot(file.toFile());
  Files.writeString(file,"current attempt\n",StandardOpenOption.APPEND);
  check(new String(XhynV02LogSlice.current(file.toFile(),snapshot,0,1024),StandardCharsets.UTF_8).equals("current attempt\n"),"Appended old-session content excluded");
  Files.writeString(file,"new log after truncation\n");
  check(new String(XhynV02LogSlice.current(file.toFile(),snapshot,0,1024),StandardCharsets.UTF_8).equals("new log after truncation\n"),"Replaced/truncated log starts at zero");
  check(XhynV02LogSlice.current(file.toFile(),snapshot,System.currentTimeMillis()+1000,1024).length==0,"Older-than-session log excluded");
  check(XhynV02LogSlice.current(file.toFile(),snapshot,0,5).length==5,"Log capture is bounded");
  String log="time=1 [script-native] archive=script:/ entry=startup detail=native_index=0 hash=0x1234 is not registered\n";
  String report=XhynV02LogSlice.compatibility(log+log+log.replace("0x1234","0x5678"));
  check(report.contains("2 distinct")&&report.contains("0x5678")&&report.contains("driver change cannot register"),"Observed unregistered script natives classified and deduplicated");
  check(XhynV02LogSlice.compatibility("normal startup").contains("not a compatibility certification"),"Missing evidence never becomes a compatibility pass");
  String header="H".repeat(4096);
  Files.writeString(file,header+"OLD STARTUP\n"+"a".repeat(4000));
  snapshot=XhynV02LogSlice.snapshot(file.toFile());
  String rewritten=header+"NEW STARTUP IMPORTANT\n"+"b".repeat(6000);
  Files.writeString(file,rewritten);
  check(new String(XhynV02LogSlice.current(file.toFile(),snapshot,0,20000),StandardCharsets.UTF_8).equals(rewritten),"Identical 4 KiB header rewritten beyond old length preserves new startup");
  Path previous=root.resolve("previous/runtime.log");
  snapshot=XhynV02LogSlice.prepare(file.toFile(),previous.toFile());
  check(!Files.exists(file)&&Files.readString(previous).equals(rewritten),"Prelaunch rotation preserves prior log separately");
  Files.writeString(file,rewritten+"fresh attempt\n");
  check(new String(XhynV02LogSlice.current(file.toFile(),snapshot,0,20000),StandardCharsets.UTF_8).startsWith(header),"Rotated log includes startup even with identical content");
  snapshot=XhynV02LogSlice.snapshot(file.toFile());
  String replacement=Files.readString(file)+"replacement inode\n";
  Files.delete(file);Files.writeString(file,replacement);
  check(new String(XhynV02LogSlice.current(file.toFile(),snapshot,0,20000),StandardCharsets.UTF_8).equals(replacement),"Replacement file identity does not drop shared prefix");
  boolean invalid=false;try{XhynV02LogSlice.current(file.toFile(),snapshot,0,0);}catch(java.io.IOException expected){invalid=true;}
  check(invalid,"Invalid capture limit rejected");
  check(new String(XhynV02LogSlice.current(file.toFile(),"8192:4096:untrusted",0,20000),StandardCharsets.UTF_8).equals(replacement),"Legacy 4 KiB fingerprint never drops rewritten startup");
  Path attempts=root.resolve("attempts"),a=attempts.resolve("earlier"),b=attempts.resolve("current");Files.createDirectories(a);Files.createDirectories(b);
  JSONObject one=new JSONObject().put("start_ms",1000).put("renderer_cache_identity","gplall:hash").put("requested_driver","system").put("cache","same-cache").put("frame_generation",false).put("performance_hints",false);
  Files.writeString(a.resolve("session.json"),one.toString());one.put("start_ms",2000);Files.writeString(b.resolve("session.json"),one.toString());
  for(Path attempt:new Path[]{a,b}){
   Files.writeString(attempt.resolve("effective-dxvk.conf"),"dxvk.numCompilerThreads = 0\n");Files.writeString(attempt.resolve("android.ini"),"[performance]\nframe_limit=60\n");Files.writeString(attempt.resolve("applied-settings.xml"),"<Settings><ScreenWidth value=\"1280\"/></Settings>");
   Files.writeString(attempt.resolve("graphics-metrics.json"),new JSONObject().put("elapsed_ms",20000).put("vendorId",0x13b5).put("deviceId",1).put("apiVersion",1).put("driverVersionRaw",42).put("pipelineCreateNs",9000000000L).toString());
  }
  check(XhynV02Comparison.comparable(a.toFile(),b.toFile()),"Matching recorded setup can be compared");
  String comparison=XhynV02Comparison.make(b.toFile());check(comparison.contains("earlier → latest")&&comparison.contains("not frame-stall time")&&comparison.contains("9000.00"),"Comparison reports counters with concurrency limits, without an FPS claim");
  one.put("requested_driver","different-driver");Files.writeString(a.resolve("session.json"),one.toString());check(!XhynV02Comparison.comparable(a.toFile(),b.toFile()),"Different drivers cannot become a warm-cache comparison");
  one.put("requested_driver","system").put("frame_generation",true);Files.writeString(a.resolve("session.json"),one.toString());check(!XhynV02Comparison.comparable(a.toFile(),b.toFile()),"Generated and base-frame runs are not silently mixed");
  one.put("frame_generation",false);Files.writeString(a.resolve("session.json"),one.toString());Files.writeString(a.resolve("android.ini"),"[performance]\nframe_limit=30\n");check(!XhynV02Comparison.comparable(a.toFile(),b.toFile()),"Frame cap differences prevent misleading comparisons");
  Files.delete(file);check(XhynV02LogSlice.snapshot(file.toFile()).equals("v2:0:0::"),"Missing log snapshot accepted");
  System.out.println("PASS "+checks+" session-log and script-compatibility host checks");
 }
}
