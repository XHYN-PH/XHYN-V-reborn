import com.gtavsource.android.XhynV02Graphics;
import java.nio.file.*;
import java.util.*;

public final class GraphicsHostChecks {
 static int checks;
 static void check(boolean yes,String name) {if(!yes)throw new AssertionError(name);checks++;}
 interface Action { void run() throws Exception; }
 static void reject(Action action,String name) throws Exception { try{action.run();throw new AssertionError(name);}catch(java.io.IOException expected){checks++;} }
 public static void main(String[] args) throws Exception {
  String xml="<?xml version=\"1.0\"?>\n<Settings>\n <!-- retained -->\n <graphics><TextureQuality value=\"2\"/><MSAA value='4'/><Other value=\"77\"/></graphics>\n <video><ScreenWidth value=\"1920\"/><ScreenHeight value=\"1080\"/></video>\n</Settings>\n";
  Map<String,String> values=new LinkedHashMap<>();values.put("TextureQuality","0"); values.put("MSAA","0");values.put("SSAO","0");
  String result=XhynV02Graphics.patch(xml,values);
  check(result.equals(xml.replace("TextureQuality value=\"2\"","TextureQuality value=\"0\"").replace("MSAA value='4'","MSAA value='0'")),"Only selected attributes change; comments, unrelated values and formatting retained");
  check(Arrays.equals(XhynV02Graphics.resolution(1920,1080,50),new int[]{960,540}),"50 percent scales both dimensions");
  check(Arrays.equals(XhynV02Graphics.resolution(1280,720,10),new int[]{320,180}),"Minimum render size preserves aspect");
  check(Arrays.equals(XhynV02Graphics.resolution(1280,720,0),new int[]{1280,720}),"Zero selects game/base resolution, not an invalid zero target");
  for(int percent=1;percent<=100;percent++){int[] size=XhynV02Graphics.resolution(1920,1080,percent);check(size[0]>=320&&size[1]>=180&&size[0]%2==0&&size[1]%2==0,"Every percent has a valid aligned render size");}
  reject(()->XhynV02Graphics.patch("<!DOCTYPE Settings [<!ENTITY remote SYSTEM 'file:///etc/passwd'>]><Settings/>",values),"External entities rejected");
  reject(()->XhynV02Graphics.patch("<Settings><MSAA value='1'/><MSAA value='2'/></Settings>",values),"Ambiguous duplicate fields rejected");
  reject(()->XhynV02Graphics.patch(xml,Collections.singletonMap("TextureQuality","0\" evil=\"1")),"Attribute injection rejected");
  reject(()->XhynV02Graphics.patch(xml,Collections.singletonMap("Unrelated","0")),"Only authorized graphics fields accepted");
  Path save=Path.of(args[0],"save");Files.createDirectories(save);
  check(!XhynV02Graphics.apply(save.toFile(),values),"First launch without settings does not invent a game file");
  Files.writeString(save.resolve("settings.xml"),xml);
  check(XhynV02Graphics.apply(save.toFile(),values),"Existing settings updated");
  check(Files.readString(save.resolve("settings.xml")).equals(result),"Saved settings match checked output");
  try(java.util.stream.Stream<Path> files=Files.list(save)){check(files.count()==2,"Only settings and targeted original attributes are stored; no leftover temp files");}
  check(XhynV02Graphics.restore(save.toFile(),XhynV02Graphics.qualityKeys())==2,"Only recorded quality fields are restored");
  check(Files.readString(save.resolve("settings.xml")).equals(xml),"Restore defaults restores original XML attributes and keeps unrelated data");
  XhynV02Graphics.apply(save.toFile(),values);XhynV02Graphics.apply(save.toFile(),Map.of("TextureQuality","1"));XhynV02Graphics.restore(save.toFile(),List.of("TextureQuality"));
  check(Files.readString(save.resolve("settings.xml")).contains("TextureQuality value=\"2\""),"Repeated changes do not overwrite the original quality value");
  Path target=Path.of(args[0],"outside.xml");Files.writeString(target,xml);Files.delete(save.resolve("settings.xml"));Files.createSymbolicLink(save.resolve("settings.xml"),target);
  reject(()->XhynV02Graphics.apply(save.toFile(),values),"Symlink targets rejected");
  System.out.println("PASS "+checks+" graphics host checks");
 }
}
