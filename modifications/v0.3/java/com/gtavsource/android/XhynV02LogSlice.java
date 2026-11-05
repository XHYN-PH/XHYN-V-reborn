package com.gtavsource.android;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Rotate small app logs before launch; fingerprint unrotated files conservatively. */
public final class XhynV02LogSlice {
 private static final long HASH_LIMIT=8L*1024*1024;
 public static String prepare(File file,File previous)throws Exception{
  if(file.isFile()&&!Files.isSymbolicLink(file.toPath())&&file.length()<=HASH_LIMIT){
   File parent=previous.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Could not preserve previous log");
   if(previous.exists())throw new IOException("Previous log destination already exists");
   Files.move(file.toPath(),previous.toPath());
  }
  return snapshot(file);
 }
 public static String snapshot(File file)throws Exception{
  if(!file.isFile()||Files.isSymbolicLink(file.toPath()))return "v2:0:0::";
  long length=file.length(),hashed=Math.min(length,HASH_LIMIT);
  return "v2:"+length+":"+hashed+":"+identity(file)+":"+prefixHash(file,hashed);
 }
 private static String identity(File file)throws Exception{
  java.nio.file.attribute.BasicFileAttributes attrs=Files.readAttributes(file.toPath(),java.nio.file.attribute.BasicFileAttributes.class);
  Object key=attrs.fileKey();String value=(key==null?"unknown":key.toString())+";created="+attrs.creationTime();
  return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes("UTF-8"));
 }
 private static String prefixHash(File file,long length)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new FileInputStream(file)){
   byte[] bytes=new byte[8192];long left=length;
   while(left>0){int n=in.read(bytes,0,(int)Math.min(left,bytes.length));if(n<0)throw new EOFException("Log changed while fingerprinting");digest.update(bytes,0,n);left-=n;}
  }
  StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
 }
 public static byte[] current(File file,String snapshot,long started,int limit)throws Exception{
  if(limit<=0)throw new IOException("Invalid log capture limit");
  if(!file.isFile()||Files.isSymbolicLink(file.toPath())||file.lastModified()<started)return new byte[0];
  String[] parts=snapshot.split(":",-1);long offset=0;
  if(parts.length==5&&parts[0].equals("v2")){
   long old=Long.parseLong(parts[1]),hashed=Long.parseLong(parts[2]);
   if(old<0||hashed<0||hashed>HASH_LIMIT||hashed>old)throw new IOException("Invalid log snapshot bounds");
   if(old>0&&hashed==old&&file.length()>=old&&identity(file).equals(parts[3])&&prefixHash(file,old).equals(parts[4]))offset=old;
  }else if(parts.length!=3)throw new IOException("Invalid log snapshot");
  // A legacy 4 KiB snapshot cannot distinguish a rewritten identical header.
  // Include bounded text from zero, labeled separately by the report exporter.
  long length=file.length();if(length-offset>limit)offset=length-limit;
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){in.seek(offset);byte[] data=new byte[(int)Math.min(limit,length-offset)];in.readFully(data);return data;}
 }
 public static String compatibility(String text){
  LinkedHashSet<String> missing=new LinkedHashSet<>();
  for(String line:text.split("\n"))if(line.contains("[script-native]")&&line.contains("is not registered")){
   java.util.regex.Matcher match=java.util.regex.Pattern.compile("hash=(0x[0-9a-fA-F]+)").matcher(line);if(match.find())missing.add(match.group(1));
  }
  if(missing.isEmpty())return "No unregistered script-native entry was identified in the current captured text. This is not a compatibility certification.\n";
  return "SCRIPT / ENGINE API MISMATCH\n"+missing.size()+" distinct unregistered script-native entries were reported during this attempt.\nUse game and script resources compatible with this engine build. A graphics-driver change cannot register missing game APIs. No dummy APIs were substituted.\n"+String.join("\n",missing)+"\n";
 }
 private XhynV02LogSlice(){}
}
