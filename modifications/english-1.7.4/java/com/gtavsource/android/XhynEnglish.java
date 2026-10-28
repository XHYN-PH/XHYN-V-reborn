package com.gtavsource.android;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Translates presentation values. Protocol keys, commands and model IDs stay intact. */
public final class XhynEnglish {
 private static volatile Map<String,String> words=Collections.emptyMap();
 private static volatile List<String> tokens=Collections.emptyList();
 private static volatile List<Format> formats=Collections.emptyList();
 private static final Map<String,String> cache=Collections.synchronizedMap(new LinkedHashMap<String,String>(128,0.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>512;}});
 private static final Pattern PATH=Pattern.compile("(?:/(?:storage|sdcard|data|mnt|proc|system|vendor|apex)/|(?:content|file)://)[^\\r\\n\\t]*");
 private static final Pattern PRINTF=Pattern.compile("%[-+ #0]*[0-9]*(?:\\.[0-9]+)?[sdiufxX]");
 private static final class Format {
  final Pattern pattern;final String[] parts;
  Format(String key,String value){Matcher m=PRINTF.matcher(key);StringBuilder p=new StringBuilder("\\A");int start=0,n=0;while(m.find()){p.append(Pattern.quote(key.substring(start,m.start()))).append("(.*?)");start=m.end();n++;}p.append(Pattern.quote(key.substring(start))).append("\\z");pattern=Pattern.compile(p.toString(),Pattern.DOTALL);parts=PRINTF.split(value,-1);if(parts.length!=n+1)throw new IllegalArgumentException("Translation format mismatch");}
  String apply(String s){Matcher m=pattern.matcher(s);if(!m.matches())return null;StringBuilder b=new StringBuilder(parts[0]);for(int i=1;i<parts.length;i++)b.append(m.group(i)).append(parts[i]);return b.toString();}
 }
 public static synchronized void load(String json)throws JSONException {
  JSONObject o=new JSONObject(json);Map<String,String> next=new HashMap<>();List<Format> fs=new ArrayList<>();
  for(Iterator<String> i=o.keys();i.hasNext();){String k=i.next(),v=o.getString(k);if(chinese(v))throw new IllegalArgumentException("Non-English translation");next.put(k,v);if(chinese(k)&&PRINTF.matcher(k).find())fs.add(new Format(k,v));}
  List<String> ts=new ArrayList<>();for(String k:next.keySet())if(chinese(k))ts.add(k);Collections.sort(ts,(a,b)->Integer.compare(b.length(),a.length()));
  words=Collections.unmodifiableMap(next);tokens=Collections.unmodifiableList(ts);formats=Collections.unmodifiableList(fs);cache.clear();
 }
 public static boolean chinese(String s){if(s==null)return false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>=0x3400&&c<=0x9fff||c>=0xf900&&c<=0xfaff)return true;}return false;}
 public static String exact(String s){String t=words.get(s);return t==null?s:t;}
 public static String t(String s){
  if(s==null)return null;String direct=words.get(s);if(direct!=null)return direct;if(!chinese(s))return s;
  String cached=cache.get(s);if(cached!=null)return cached;
  for(Format f:formats){String v=f.apply(s);if(v!=null){cache.put(s,v);return v;}}
  List<String> paths=new ArrayList<>();Matcher path=PATH.matcher(s);StringBuffer protectedText=new StringBuffer();while(path.find()){String token="\uE000"+paths.size()+"\uE001";paths.add(path.group());path.appendReplacement(protectedText,Matcher.quoteReplacement(token));}path.appendTail(protectedText);String v=protectedText.toString();for(String k:tokens)if(v.contains(k))v=v.replace(k,words.get(k));
  v=v.replace("：",": ").replace("；","; ").replace("（"," (").replace("）",")").replace("，",", ").replace("。",".").replace("、",", ");for(int i=0;i<paths.size();i++)v=v.replace("\uE000"+i+"\uE001",paths.get(i));cache.put(s,v);return v;
 }
 public static String readable(String model){
  if(model==null||model.isEmpty())return "Unnamed model";if(model.startsWith("0x"))return "Model "+model;
  String s=model.replaceFirst("^(?:a|s|g)_[mf]_[moy]_","").replaceFirst("^(?:ig|cs|csb|u_[mf]_[moy]|mp_[mf]|prop|p|weapon)_","").replace('_',' ').replaceAll("([a-z])([0-9])","$1 $2");
  StringBuilder b=new StringBuilder();for(String x:s.split(" +")){if(x.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));}return b.toString();
 }
 public static void catalog(JSONObject item)throws JSONException {
  String model=item.optString("model","");String display=exact(item.optString("display","")),label=exact(item.optString("label",""));
  if(chinese(display))display=readable(model);if(chinese(label))label=display.isEmpty()?readable(model):display;
  item.put("display",display);item.put("label",label);
  String make=exact(item.optString("make",""));if(chinese(make))make="";item.put("make",make);
  String category=exact(item.optString("categoryLabel",""));if(chinese(category))category=readable(item.optString("category","other"));item.put("categoryLabel",category);
  item.put("search",item.optString("search","")+" "+display+" "+label+" "+make+" "+category+" "+model);
 }
 private XhynEnglish(){}
}
