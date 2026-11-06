package com.gtavsource.android;

import org.json.JSONObject;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Admission checks for Android Vulkan packages; filenames never establish GPU support. */
public final class XhynV02DriverPackage {
    private static final int MAX = 128 * 1024 * 1024;
    public static void validate(File file, int api, long page) throws Exception {
        if (!file.isFile() || file.length() > MAX || Files.isSymbolicLink(file.toPath())) throw new IOException("Invalid or oversized driver ZIP");
        try (ZipFile zip = new ZipFile(file)) {
            Set<String> names = new HashSet<>(); long total = 0; int count = 0; JSONObject meta = null;
            ZipEntry metadata = zip.getEntry("meta.json");
            if (metadata == null) throw new IOException("Driver needs an AdrenoTools-format meta.json. Desktop drivers and OpenGL packages cannot be used.");
            meta = new JSONObject(new String(read(zip, metadata, 65536), StandardCharsets.UTF_8));
            if (meta.optInt("schemaVersion", -1) != 1 || meta.optInt("minApi", -1) < 0 || meta.getInt("minApi") > api) throw new IOException("Driver metadata or Android API requirement is incompatible");
            String library = meta.optString("libraryName");
            if (!library.matches("[A-Za-z0-9_.+-]+\\.so") || zip.getEntry(library) == null) throw new IOException("Driver entry library is missing");
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement(); String name = entry.getName();
                if (++count > 512 || !names.add(name) || name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/", -1)).contains("..") || name.indexOf('\0') >= 0) throw new IOException("Unsafe or duplicate driver ZIP path");
                if (entry.isDirectory()) continue;
                if (name.toLowerCase(Locale.ROOT).endsWith(".dll")) throw new IOException("Windows drivers cannot run on Android");
                byte[] data = read(zip, entry, MAX);
                total += data.length; if (total > MAX) throw new IOException("Expanded driver exceeds 128 MiB");
                if (name.endsWith(".so") || name.contains(".so.")) validateElf(data, page);
                if (name.equals(library) && !contains(data, "vkGetInstanceProcAddr")) throw new IOException("Entry library does not expose a Vulkan loader interface");
            }
        }
    }
    public static void beforePrepare(File directory, String name, int api) throws IOException {
        try {
            File file = new File(directory, name);
            if (!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())) throw new IOException("Driver path is outside Drivers");
            validate(file, api, android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE));
        } catch (Exception failure) { throw new IOException("Vulkan driver package rejected: " + failure.getMessage(), failure); }
    }
    public static void checkPrepare(File directory, String name, File ignored, int api) throws IOException { beforePrepare(directory, name, api); }
    private static byte[] read(ZipFile zip, ZipEntry entry, int limit) throws Exception {
        if (entry.getSize() > limit) throw new IOException("Driver ZIP entry is too large");
        try (InputStream in = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[65536]; int count; while ((count = in.read(bytes)) != -1) { if (out.size() + count > limit) throw new IOException("Driver ZIP entry is too large"); out.write(bytes,0,count); } return out.toByteArray();
        }
    }
    static boolean contains(byte[] bytes, String text) {
        byte[] key = text.getBytes(StandardCharsets.US_ASCII);
        outer: for (int i = 0; i <= bytes.length - key.length; i++) { for (int j = 0; j < key.length; j++) if (bytes[i+j] != key[j]) continue outer; return true; } return false;
    }
    public static void validateElf(byte[] bytes, long page) throws Exception {
        if (bytes.length < 64 || bytes[0]!=0x7f || bytes[1]!='E' || bytes[2]!='L' || bytes[3]!='F' || bytes[4]!=2 || bytes[5]!=1) throw new IOException("Driver must contain little-endian 64-bit ELF libraries");
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (data.getShort(16)!=3 || data.getShort(18)!=183) throw new IOException("Driver must be an ARM64 Android shared library");
        if (contains(bytes, "GLIBC_") || contains(bytes, "libc.so.6") || contains(bytes, "libpthread.so.0")) throw new IOException("Desktop glibc drivers cannot run on Android");
        long table=data.getLong(32); int width=data.getShort(54)&65535, count=data.getShort(56)&65535;
        if (width!=56 || count==0 || count>256 || table<64 || table+(long)width*count>bytes.length) throw new IOException("Invalid driver program headers");
        boolean load=false;
        for(int i=0;i<count;i++) { int p=(int)table+width*i; if(data.getInt(p)!=1) continue; load=true;
            long offset=data.getLong(p+8), address=data.getLong(p+16), size=data.getLong(p+32), memory=data.getLong(p+40), alignment=data.getLong(p+48);
            if(offset<0 || size<0 || size>bytes.length || offset>bytes.length-size || memory<size || alignment<0 || (alignment>1 && (alignment&(alignment-1))!=0)) throw new IOException("Invalid driver load segment");
            if(page>=16384 && (alignment<page || ((address-offset)&(page-1))!=0)) throw new IOException("Driver is not compatible with this device's 16 KiB pages");
        }
        if(!load) throw new IOException("Driver has no loadable code");
    }
    private XhynV02DriverPackage() { }
}
