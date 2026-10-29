import com.gtavsource.android.XhynV02DriverPackage;
import java.nio.*;
import java.nio.file.*;
import java.util.zip.*;
import java.io.*;

public final class DriverHostChecks {
 static int checks;
 static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);checks++;}
 interface Action{void run()throws Exception;}
 static void reject(Action action,String name)throws Exception{try{action.run();throw new AssertionError(name);}catch(IOException expected){checks++;}}
 static byte[] elf(int machine,int align,String extra){ByteBuffer b=ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);b.put(new byte[]{0x7f,'E','L','F',2,1,1});b.putShort(16,(short)3).putShort(18,(short)machine).putLong(32,64).putShort(54,(short)56).putShort(56,(short)1).putInt(64,1).putLong(72,0).putLong(80,0).putLong(96,512).putLong(104,512).putLong(112,align);b.position(200);b.put(("vkGetInstanceProcAddr\0"+extra).getBytes(java.nio.charset.StandardCharsets.US_ASCII));return b.array();}
 static Path archive(Path root,String name,int api,byte[] data,String path)throws Exception{Path file=root.resolve(name);try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(file))){out.putNextEntry(new ZipEntry("meta.json"));out.write(("{\"schemaVersion\":1,\"minApi\":"+api+",\"libraryName\":\"libvulkan.so\"}").getBytes());out.closeEntry();out.putNextEntry(new ZipEntry(path));out.write(data);out.closeEntry();}return file;}
 public static void main(String[] args)throws Exception{Path root=Path.of(args[0]);Files.createDirectories(root);
  byte[] arm=elf(183,16384,"");XhynV02DriverPackage.validateElf(arm,16384);checks++;
  reject(()->XhynV02DriverPackage.validateElf(elf(62,4096,""),4096),"Desktop architecture rejected");
  reject(()->XhynV02DriverPackage.validateElf(elf(183,4096,"GLIBC_2.28"),4096),"glibc libraries rejected");
  reject(()->XhynV02DriverPackage.validateElf(elf(183,4096,""),16384),"4 KiB driver rejected on a 16 KiB device");
  File valid=archive(root,"valid.zip",30,arm,"libvulkan.so").toFile();XhynV02DriverPackage.validate(valid,35,16384);checks++;
  reject(()->XhynV02DriverPackage.validate(archive(root,"api.zip",36,arm,"libvulkan.so").toFile(),35,4096),"Future API requirement rejected");
  reject(()->XhynV02DriverPackage.validate(archive(root,"traversal.zip",30,arm,"../libvulkan.so").toFile(),35,4096),"Traversal / missing root entry rejected");
  reject(()->XhynV02DriverPackage.validate(archive(root,"nonvk.zip",30,elf(183,16384,"").clone(),"other.so").toFile(),35,4096),"Missing entry library rejected");
  byte[] broken=arm.clone();ByteBuffer.wrap(broken).order(ByteOrder.LITTLE_ENDIAN).putLong(96,Long.MAX_VALUE);
  reject(()->XhynV02DriverPackage.validateElf(broken,4096),"Overflowing load segment rejected");
  check(Files.isRegularFile(root.resolve("valid.zip")),"Validation leaves imported ZIP untouched");
  System.out.println("PASS "+checks+" driver-admission host checks (loader execution is separate)");
 }
}
