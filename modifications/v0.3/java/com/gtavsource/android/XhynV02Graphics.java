package com.gtavsource.android;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Collection;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Selected graphics attributes and their original values for targeted reset. */
public final class XhynV02Graphics {
    public static final String[] QUALITY = {"TextureQuality", "ShadowQuality", "ReflectionQuality", "GrassQuality", "ParticleQuality", "WaterQuality", "PostFX", "Tessellation", "MSAA", "ReflectionMSAA", "SSAO"};
    public static final String[] SHADOWS = {"Shadow_ParticleShadows", "Shadow_LongShadows", "Shadow_SoftShadows"};
    public static final String[] SIZE = {"ScreenWidth", "ScreenHeight"};
    public static java.util.List<String> qualityKeys(){java.util.List<String> keys=new ArrayList<>(Arrays.asList(QUALITY));keys.addAll(Arrays.asList(SHADOWS));return keys;}
    private static boolean allowed(String key) {
        return Arrays.asList(QUALITY).contains(key) || Arrays.asList("ScreenWidth", "ScreenHeight", "VSync", "LodScale", "PedLodBias", "VehicleLodBias", "CityDensity", "PedVarietyMultiplier", "VehicleVarietyMultiplier", "Shadow_ParticleShadows", "Shadow_LongShadows", "Shadow_SoftShadows").contains(key);
    }

    private static Document parse(String xml) throws Exception {
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) throw new IOException("Unsupported XML declaration");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> { throw new org.xml.sax.SAXException("External XML entities are disabled"); });
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static String patch(String xml, Map<String, String> overrides) throws Exception {
        if (xml.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024) throw new IOException("Graphics settings are too large");
        Document document = parse(xml);
        Map<String, String> applied = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String key = entry.getKey(), value = entry.getValue();
            if (!allowed(key) || !value.matches("(?:true|false|[0-9]{1,8}(?:\\.[0-9]{1,8})?)")) throw new IOException("Invalid graphics override: " + key);
            NodeList items = document.getElementsByTagName(key);
            if (items.getLength() == 0) continue;
            if (items.getLength() != 1 || !((Element)items.item(0)).hasAttribute("value")) throw new IOException("Ambiguous graphics field: " + key);
            Pattern field = Pattern.compile("(<" + Pattern.quote(key) + "\\b[^<>]*?\\bvalue\\s*=\\s*)([\"'])([^\"']*)(\\2)");
            Matcher matcher = field.matcher(xml);
            if (!matcher.find()) throw new IOException("Cannot update graphics field: " + key);
            xml = matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + value + matcher.group(4)));
            applied.put(key, value);
        }
        Document check = parse(xml);
        for (Map.Entry<String, String> entry : applied.entrySet()) {
            Element item = (Element)check.getElementsByTagName(entry.getKey()).item(0);
            if (!item.getAttribute("value").equals(entry.getValue())) throw new IOException("Graphics write verification failed: " + entry.getKey());
        }
        return xml;
    }

    private static File settings(File save) {
        File file=new File(save,"settings.xml");
        return !file.isFile()&&new File(save,"Settings.xml").isFile()?new File(save,"Settings.xml"):file;
    }
    private static Properties originals(File save) throws Exception {
        Properties values=new Properties();File record=new File(save,".xhyn-graphics-original.properties");
        if(record.exists()){
            if(Files.isSymbolicLink(record.toPath())||!record.isFile()||record.length()>65536)throw new IOException("Invalid graphics defaults record");
            try(FileInputStream input=new FileInputStream(record)){values.load(input);}
        }
        return values;
    }
    private static void remember(File save,String xml,Collection<String> keys)throws Exception {
        Properties values=originals(save);Document doc=parse(xml);boolean changed=false;
        for(String key:keys)if(allowed(key)&&!values.containsKey(key)){
            NodeList items=doc.getElementsByTagName(key);
            if(items.getLength()==1&&((Element)items.item(0)).hasAttribute("value")){
                String value=((Element)items.item(0)).getAttribute("value");
                if(value.matches("(?:true|false|[0-9]{1,8}(?:\\.[0-9]{1,8})?)")){values.setProperty(key,value);changed=true;}
            }
        }
        if(changed){File stage=File.createTempFile(".xhyn-defaults-",".tmp",save);
            try{try(FileOutputStream output=new FileOutputStream(stage)){values.store(output,"Original graphics attributes before XHYN overrides");output.getFD().sync();}
                Files.move(stage.toPath(),new File(save,".xhyn-graphics-original.properties").toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            }finally{stage.delete();}}
    }
    public static synchronized void rememberQuality(File save)throws Exception {
        File file=settings(save);if(!file.isFile())return;
        validatePath(save,file);remember(save,new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8),qualityKeys());
    }
    private static void validatePath(File save,File file)throws Exception {
        if(Files.isSymbolicLink(save.toPath())||Files.isSymbolicLink(file.toPath())||file.length()>2*1024*1024)throw new IOException("Invalid graphics settings path");
    }
    public static synchronized int restore(File save,Collection<String> keys)throws Exception {
        File file=settings(save);if(!file.isFile())return 0;validatePath(save,file);
        Properties original=originals(save);Map<String,String> values=new LinkedHashMap<>();
        for(String key:keys)if(allowed(key)&&original.containsKey(key))values.put(key,original.getProperty(key));
        if(values.isEmpty())return 0;
        String before=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        write(save,file,before,patch(before,values));return values.size();
    }
    private static void write(File save,File file,String current,String next)throws Exception {
        if(!next.equals(current)){
            File stage=File.createTempFile(".xhyn-graphics-",".tmp",save);
            try{try(FileOutputStream out=new FileOutputStream(stage)){out.write(next.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                if(!new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).equals(current))throw new IOException("Game settings changed while saving; try again with the game closed");
                Files.move(stage.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            }finally{stage.delete();}
        }
    }
    public static synchronized boolean apply(File save, Map<String, String> values) throws Exception {
        if (values.isEmpty()) return true;
        File file = settings(save);
        if (!file.isFile()) return false;
        validatePath(save,file);
        String current = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String next = patch(current, values);
        remember(save,current,values.keySet());
        write(save,file,current,next);
        return true;
    }

    public static int[] resolution(int width, int height, int percent) {
        if (width <= 0 || height <= 0 || width>7680 || height>4320 || percent < 0 || percent > 100) throw new IllegalArgumentException("Invalid resolution scale");
        if(percent==0)return new int[]{width,height};
        double ratio = Math.max(percent / 100.0, Math.max(320.0 / width, 180.0 / height));
        return new int[]{(int)Math.ceil(width * ratio / 2) * 2, (int)Math.ceil(height * ratio / 2) * 2};
    }
    private XhynV02Graphics() {}
}
