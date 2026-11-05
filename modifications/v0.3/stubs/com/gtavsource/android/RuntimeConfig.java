package com.gtavsource.android;
import java.io.*;
public class RuntimeConfig {
 public static File GAME_ROOT;
 public File root, file, drivers, dxvk, mods;
 public static RuntimeConfig load(File f) { throw new UnsupportedOperationException(); }
 public static RuntimeConfig readOnly(File f) { throw new UnsupportedOperationException(); }
 public String get(String s,String k,String d) { throw new UnsupportedOperationException(); }
 public boolean getBoolean(String s,String k,boolean d) { throw new UnsupportedOperationException(); }
 public int getInt(String s,String k,int d,int lo,int hi) { throw new UnsupportedOperationException(); }
 public void put(String s,String k,String v) { throw new UnsupportedOperationException(); }
 public void save() { throw new UnsupportedOperationException(); }
}
