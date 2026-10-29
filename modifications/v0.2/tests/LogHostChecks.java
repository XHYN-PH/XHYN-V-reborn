import com.gtavsource.android.XhynV02LogSlice;
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
  Files.delete(file);check(XhynV02LogSlice.snapshot(file.toFile()).equals("0:0:"),"Missing log snapshot accepted");
  System.out.println("PASS "+checks+" session-log and script-compatibility host checks");
 }
}
