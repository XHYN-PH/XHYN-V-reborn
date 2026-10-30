import com.gtavsource.android.XhynV02Advice;
import com.gtavsource.android.XhynV02Compatibility;
import org.json.JSONObject;
public class AdviceHostChecks {
 static int checks;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 public static void main(String[] args)throws Exception{
  check(XhynV02Advice.explain("error_code=0xe79d84ff").contains("could not use this GPU"),"Device-creation error has plain advice");
  check(XhynV02Advice.explain("error_code=0xe79d84ff\nfeature_limit=10_1").contains("Default or DX11"),"Feature-limit device failure explains recovery without guessing its cause");
  check(XhynV02Advice.explain("VK_ERROR_DEVICE_LOST").contains("stopped responding"),"Device loss has distinct advice");
  check(XhynV02Advice.explain("allocation failed").contains("textures, resolution"),"Allocation failure has concrete lower-demand suggestions");
  check(XhynV02Advice.explain("hash=0x1234 is not registered").contains("scripts"),"Script mismatch is not blamed on a graphics driver");
  check(XhynV02Advice.explain("nothing recorded").contains("no saved explanation"),"Missing evidence is not a diagnosis");
  JSONObject gpu=new JSONObject().put("status","PASS").put("vendorId",0x144d).put("apiVersion",(1<<22)|(3<<12)).put("dualSrcBlend",true).put("loadStoreOpNone",false).put("reportedBcCompression",true);
  check(XhynV02Compatibility.advice(gpu,"upstream").contains("Xclipse startup failure"),"Observed missing load/store feature is advisory for upstream");
  check(!XhynV02Compatibility.advice(gpu,"sarek").contains("Xclipse startup failure"),"Upstream requirement is not imposed on Sarek");
  check(XhynV02Compatibility.advice(gpu,"custom").contains("requirements are unknown"),"Imported renderer is not falsely qualified");
  gpu.put("apiVersion",(1<<22)|(4<<12));check(!XhynV02Compatibility.advice(gpu,"upstream").contains("Xclipse startup failure"),"Core promotion avoids a false missing-extension warning");
  check(XhynV02Compatibility.advice(null,"upstream").contains("optional"),"No mandatory new probe requirement");
  System.out.println("PASS "+checks+" plain-language diagnostic and compatibility checks");
 }
}
