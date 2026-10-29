package com.gtavsource.android;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Records a prefix and length before launch to exclude old text from append-only logs. */
public final class XhynV02LogSlice {
 public static String snapshot(File file) throws Exception {
  if(!file.isFile()||Files.isSymbolicLink(file.toPath()))return "0:0:";
  long length=file.length();int prefix=(int)Math.min(length,4096);
  return length+":"+prefix+":"+prefixHash(file,prefix);
 }
 private static String prefixHash(File file,int length)throws Exception{
  byte[] bytes=new byte[length];try(RandomAccessFile in=new RandomAccessFile(file,"r")){in.readFully(bytes);}
  StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
 }
 public static byte[] current(File file,String snapshot,long started,int limit)throws Exception{
  if(!file.isFile()||Files.isSymbolicLink(file.toPath())||file.lastModified()<started)return new byte[0];
  String[] parts=snapshot.split(":",-1);if(parts.length!=3)throw new IOException("Invalid log snapshot");
  long old=Long.parseLong(parts[0]);int prefix=Integer.parseInt(parts[1]);long offset=0;
  if(old<0||prefix<0||prefix>4096||prefix>old)throw new IOException("Invalid log snapshot bounds");
  if(file.length()>=old&&prefix>0&&prefixHash(file,prefix).equals(parts[2]))offset=old;
  long size=file.length()-offset;if(size>limit)offset=file.length()-limit;
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){in.seek(offset);byte[] data=new byte[(int)Math.min(limit,file.length()-offset)];in.readFully(data);return data;}
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
