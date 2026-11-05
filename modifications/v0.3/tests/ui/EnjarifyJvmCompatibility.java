import java.io.*;
import java.util.jar.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Host-only correction for Enjarify's Java 8 static-interface encoding.
 * Nothing written here is ever packaged in the Android APK.
 */
public final class EnjarifyJvmCompatibility {
    private static final Map<String,ClassReader> hierarchy=new HashMap<>();
    private static ClassReader info(String name) {
        return hierarchy.computeIfAbsent(name,key->{try{return new ClassReader(key);}catch(IOException e){throw new IllegalStateException("Missing host hierarchy: "+key,e);}});
    }
    private static String component(String array) {
        String value=array.substring(1);
        return value.startsWith("L")?value.substring(1,value.length()-1):value;
    }
    private static boolean primitive(String name){return name.length()==1;}
    private static boolean assignable(String target,String value) {
        if(target.equals(value)||target.equals("java/lang/Object"))return true;
        if(value.startsWith("[")) {
            if(target.equals("java/lang/Cloneable")||target.equals("java/io/Serializable"))return true;
            if(!target.startsWith("["))return false;
            String a=component(target),b=component(value);
            return primitive(a)||primitive(b)?a.equals(b):assignable(a,b);
        }
        if(target.startsWith("["))return false;
        ClassReader type=info(value);
        if(type.getSuperName()!=null&&assignable(target,type.getSuperName()))return true;
        for(String iface:type.getInterfaces())if(assignable(target,iface))return true;
        return false;
    }
    private static String common(String first,String second) {
        if(assignable(first,second))return first;
        if(assignable(second,first))return second;
        if(first.startsWith("[")&&second.startsWith("[")) {
            String a=component(first),b=component(second);
            if(!primitive(a)&&!primitive(b)) {
                String value=common(a,b);
                return value.startsWith("[")?"["+value:"[L"+value+";";
            }
            return "java/lang/Object";
        }
        if(first.startsWith("[")||second.startsWith("[")||
                (info(first).getAccess()&Opcodes.ACC_INTERFACE)!=0||
                (info(second).getAccess()&Opcodes.ACC_INTERFACE)!=0)return "java/lang/Object";
        for(String parent=info(first).getSuperName();parent!=null;parent=info(parent).getSuperName())
            if(assignable(parent,second))return parent;
        return "java/lang/Object";
    }
    public static void main(String[] args) throws Exception {
        int classes=0,calls=0;
        try(JarFile source=new JarFile(args[0]);JarOutputStream output=new JarOutputStream(new FileOutputStream(args[1]))) {
            for(java.util.Enumeration<JarEntry> entries=source.entries();entries.hasMoreElements();) {
                JarEntry entry=entries.nextElement();
                byte[] data=source.getInputStream(entry).readAllBytes();
                if(entry.getName().endsWith(".class")) {
                    ClassNode node=new ClassNode();new ClassReader(data).accept(node,0);
                    boolean changed=false;
                    for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions) {
                        if(insn instanceof MethodInsnNode) {
                            MethodInsnNode call=(MethodInsnNode)insn;
                            if(call.getOpcode()==Opcodes.INVOKESTATIC&&!call.itf&&call.owner.startsWith("java/")) {
                                if(Class.forName(call.owner.replace('/','.'),false,EnjarifyJvmCompatibility.class.getClassLoader()).isInterface()) {
                                    call.itf=true;changed=true;calls++;
                                }
                            }
                        }
                    }
                    if(changed) {
                        node.version=Math.max(node.version,Opcodes.V1_8);
                        // Read hierarchy metadata without defining translated
                        // classes; their original default interfaces may still
                        // have Enjarify's old class-file version.
                        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS) {
                            @Override protected String getCommonSuperClass(String a,String b){return common(a,b);}
                        };
                        node.accept(writer);data=writer.toByteArray();classes++;
                    }
                }
                output.putNextEntry(new JarEntry(entry.getName()));output.write(data);output.closeEntry();
            }
        }
        System.out.println("Host JVM compatibility: "+calls+" static-interface calls in "+classes+" classes; APK DEX unchanged");
    }
}
