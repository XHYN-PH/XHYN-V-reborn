import com.gtavsource.android.XhynEnglish;import java.nio.file.*;import java.util.*;import org.json.*;
public final class EnglishHostChecks {
 static int checks;static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 public static void main(String[] a)throws Exception {
  String root=a[0];XhynEnglish.load(Files.readString(Path.of(root,"translations.json")));
  JSONObject input=new JSONObject(Files.readString(Path.of(root,"reports/chinese-smali-strings.json")));
  for(Iterator<String> it=input.keys();it.hasNext();){String s=it.next();check(!XhynEnglish.chinese(XhynEnglish.t(s)),"Untranslated launcher string: "+s);}
  JSONObject resource=new JSONObject(Files.readString(Path.of(root,"reports/additional-resource-strings.json")));
  for(Iterator<String> it=resource.keys();it.hasNext();)check(!XhynEnglish.chinese(XhynEnglish.t(it.next())),"Untranslated layout text");
  check(XhynEnglish.t("XHYN V").equals("XHYN V"),"English text changed");check(XhynEnglish.t(null)==null,"Null changed");
  String userPath="/storage/emulated/0/GTAV模组/My 文件.rar";check(XhynEnglish.t("导入失败："+userPath).endsWith(userPath),"File paths must not be translated");
  String protocol="spawn_vehicle|0x12345678|models/a_f_m_eastsa_02|/storage/emulated/0/Games/GTAV";check(XhynEnglish.t(protocol).equals(protocol),"Protocol changed");
  check(XhynEnglish.t("当前现金 123456").equals("Current cash: 123456"),"Dynamic cash translation");
  String dynamic="DXVK 无法加载 /safe/lib.so：missing symbol。请重新启动并选择兼容的完整库包。";
  String result=XhynEnglish.t(dynamic);check(!XhynEnglish.chinese(result)&&result.contains("/safe/lib.so")&&result.contains("missing symbol"),"Formatted native error changed its arguments");
  JSONObject c=new JSONObject("{\"model\":\"a_f_m_eastsa_02\",\"display\":\"东洛圣都中年女2\",\"category\":\"peds\",\"categoryLabel\":\"未知分类\",\"search\":\"original\",\"hash\":12345,\"command\":\"spawn_ped\"}");
  XhynEnglish.catalog(c);check(c.getString("model").equals("a_f_m_eastsa_02")&&c.getInt("hash")==12345&&c.getString("command").equals("spawn_ped"),"Catalog protocol changed");check(!XhynEnglish.chinese(c.getString("display"))&&!XhynEnglish.chinese(c.getString("categoryLabel")),"Catalog label untranslated");check(c.getString("search").contains(c.getString("display")),"English catalog not searchable");
  JSONObject dictionary=new JSONObject(Files.readString(Path.of(root,"translations.json")));for(Iterator<String> it=dictionary.keys();it.hasNext();){String k=it.next();check(!XhynEnglish.chinese(dictionary.getString(k)),"Dictionary contains Chinese output");}
  System.out.println("PASS "+checks+" English host checks; Android widgets and gameplay are not exercised");
 }
}
