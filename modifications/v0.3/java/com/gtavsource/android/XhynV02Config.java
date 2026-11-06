package com.gtavsource.android;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** DXVK text is data. No text is executed as a command or environment script. */
public final class XhynV02Config {
    public static final int LIMIT = 65536;
    private static final Set<String> BOOLEANS = new HashSet<>(Arrays.asList(
            "dxvk.enableAsync", "dxvk.enableStateCache", "d3d11.disableMsaa",
            "d3d11.relaxedBarriers", "d3d11.relaxedGraphicsBarriers",
            "d3d11.forceVolatileTgsmAccess", "d3d11.forceComputeUavBarriers",
            "d3d11.disableDirectImageMapping", "dxvk.zeroMappedMemory",
            "dxvk.gtaAsyncInterop", "dxvk.gtaDescriptorCache", "dxvk.gtaDescriptorCacheStats",
            "dxvk.gplAsyncCache","dxvk.enableDescriptorUpdateTemplates","dxvk.lowLatencyAllowCpuFramesOverlap",
            "d3d11.clampNegativeLodBias","d3d11.forceSampleRateShading","d3d11.enableContextLock",
            "d3d11.zeroInitWorkgroupMemory","d3d11.invariantPosition","d3d11.floatControls"));
    private static final Set<String> TRISTATES = new HashSet<>(Arrays.asList(
            "dxvk.enableGraphicsPipelineLibrary", "dxvk.enableMemoryDefrag",
            "dxvk.trackPipelineLifetime", "dxvk.tilerMode", "dxvk.useRawSsbo"));
    private static final Map<String, int[]> RANGES;
    static {
        Map<String, int[]> ranges = new LinkedHashMap<>();
        ranges.put("dxvk.numCompilerThreads", new int[]{0, 16});
        ranges.put("dxvk.numShaderCompilerThreads", new int[]{0, 16});
        ranges.put("dxgi.maxFrameRate", new int[]{-1, 1000});
        ranges.put("dxvk.maxFrameRate", new int[]{-1,1000});
        ranges.put("dxgi.maxFrameLatency", new int[]{0, 8});
        ranges.put("dxgi.syncInterval", new int[]{0, 4});
        ranges.put("dxgi.maxDeviceMemory", new int[]{0, 65536});
        ranges.put("dxgi.maxSharedMemory", new int[]{0, 65536});
        ranges.put("dxvk.maxMemoryBudget", new int[]{0, 65536});
        ranges.put("d3d11.samplerAnisotropy",new int[]{-1,16});
        ranges.put("d3d11.maxTessFactor",new int[]{0,64});
        RANGES = Collections.unmodifiableMap(ranges);
    }

    public static String normalize(String source) throws IOException {
        if (source == null) throw new IOException("Configuration text is missing");
        if (source.getBytes(StandardCharsets.UTF_8).length > LIMIT) throw new IOException("Configuration exceeds 64 KiB");
        if (source.indexOf('\0') >= 0) throw new IOException("Configuration contains a null character");
        String text = source.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\ufeff")) text = text.substring(1);
        return text;
    }

    public static String token(String source) throws IOException {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        for (char value : source.trim().toCharArray()) {
            if (value == '"') quoted = !quoted;
            else if (!quoted && Character.isWhitespace(value)) break;
            else result.append(value);
        }
        if (quoted) throw new IOException("Close the quoted value");
        return result.toString();
    }

    public static String key(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || trimmed.startsWith("[")) return null;
        int equals = trimmed.indexOf('=');
        if (equals <= 0) return null;
        String candidate = trimmed.substring(0, equals).trim();
        return candidate.matches("[A-Za-z][A-Za-z0-9_.]*") ? candidate : null;
    }

    public static void validate(String source) throws IOException {
        String[] lines = normalize(source).split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String problem = null;
            try {
                if (line.startsWith("[")) {
                    if (!line.matches("\\[[^\\[\\]\\r\\n]{1,256}\\]\\s*(?:#.*)?")) problem = "Invalid executable section";
                } else {
                    String name = key(line);
                    if (name == null || !name.matches("(?:dxvk|dxgi|d3d11)\\.[A-Za-z][A-Za-z0-9_.]*")) {
                        problem = line.startsWith("d3d9.") ? "D3D9 settings do not apply to this D3D11 renderer" : "Use a dxvk, dxgi, or d3d11 option followed by =";
                    } else {
                        String value = token(line.substring(line.indexOf('=') + 1));
                        if (BOOLEANS.contains(name) && !value.equalsIgnoreCase("True") && !value.equalsIgnoreCase("False")) problem = name + " needs True or False";
                        if (TRISTATES.contains(name) && !value.equalsIgnoreCase("True") && !value.equalsIgnoreCase("False") && !value.equalsIgnoreCase("Auto")) problem = name + " needs Auto, True, or False";
                        if (RANGES.containsKey(name)) {
                            int[] range = RANGES.get(name);
                            int number;
                            try { number = Integer.parseInt(value); }
                            catch (NumberFormatException error) { throw new IOException(name + " needs a whole number"); }
                            if (number < range[0] || number > range[1]) problem = name + " must be " + range[0] + ".." + range[1];
                        }
                        if (name.equals("d3d11.samplerLodBias")) {
                            float number;
                            try { number = Float.parseFloat(value); }
                            catch (NumberFormatException error) { throw new IOException("Texture LOD bias needs a number"); }
                            if (Float.isNaN(number) || Float.isInfinite(number) || number < -2 || number > 1) problem = "Texture LOD bias must be -2..1";
                        }
                        if(name.equals("d3d11.maxTessFactor")){
                            int number=Integer.parseInt(value);if(number>0&&number<8)problem="Tessellation limit must be 0 (game setting), or 8..64";
                        }
                        if(name.equals("d3d11.maxFeatureLevel")&&!Arrays.asList("9_1","9_2","9_3","10_0","10_1","11_0","11_1","12_0","12_1").contains(value))problem="Use a Direct3D feature level such as 11_0 or 10_1";
                        if(name.equals("d3d11.cachedDynamicResources")&&!value.matches("[cvira]*"))problem="Cached dynamic resources accepts only c, v, i, r, a flags or an empty string";
                        if (name.equals("dxvk.hud") && (value.equalsIgnoreCase("True") || value.equalsIgnoreCase("False"))) problem = "HUD needs tokens such as fps,frametimes, or an empty quoted string";
                    }
                }
            } catch (IOException error) { problem = error.getMessage(); }
            if (problem != null) throw new IOException("Line " + (index + 1) + ": " + problem);
        }
    }

    public static boolean hasSections(String source) throws IOException {
        for (String line : normalize(source).split("\n", -1)) if (line.trim().startsWith("[")) return true;
        return false;
    }

    public static Map<String, String> globals(String source) throws IOException {
        validate(source);
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : normalize(source).split("\n", -1)) {
            if (line.trim().startsWith("[")) break;
            String name = key(line);
            if (name != null) values.put(name, token(line.substring(line.indexOf('=') + 1)));
        }
        return values;
    }

    /** Edit a global option while preserving comments and executable sections. */
    public static String setGlobal(String source, String name, String value) throws IOException {
        validate(source);
        if (!name.matches("(?:dxvk|dxgi|d3d11)\\.[A-Za-z][A-Za-z0-9_.]*")) throw new IOException("Invalid DXVK option name");
        if (value != null && (value.indexOf('\n') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\0') >= 0)) throw new IOException("Invalid option value");
        StringBuilder out = new StringBuilder();
        boolean inserted = false;
        for (String line : normalize(source).split("\n", -1)) {
            if (!inserted && line.trim().startsWith("[")) {
                if (value != null) out.append(name).append(" = \"").append(value).append("\"\n");
                inserted = true;
            }
            if (inserted || !name.equals(key(line))) out.append(line).append('\n');
        }
        if (!inserted && value != null) out.append(name).append(" = \"").append(value).append("\"\n");
        String result = out.toString().replaceAll("\\n+\\z","\n");
        validate(result);
        return result;
    }

    /** Saved overrides come after managed defaults, with native last-entry precedence. */
    public static String effective(Map<String, String> defaults, String saved, boolean enabled) throws IOException {
        String header="# XHYN V 0.2 effective DXVK configuration\n";
        String source=enabled?normalize(saved):"";
        // The editor displays this same complete text. Saving/reopening must be
        // idempotent, with comments/sections retained and no repeated defaults.
        if(source.startsWith(header))source=source.substring(header.length());
        source=source.replaceAll("\\A\\n+|\\n+\\z", "");
        Map<String,String> explicit=globals(source);
        StringBuilder out = new StringBuilder(header);
        for (Map.Entry<String, String> entry : defaults.entrySet()) if(!explicit.containsKey(entry.getKey()))
            out.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
        if (!source.isEmpty()) out.append(source).append('\n');
        String result = out.toString();
        validate(result);
        return result;
    }

    public static List<String> unsupported(String source, Set<String> supported) throws IOException {
        List<String> names = new ArrayList<>();
        for (String line : normalize(source).split("\n", -1)) {
            String name = key(line);
            if (name != null && !supported.contains(name) && !names.contains(name)) names.add(name);
        }
        return names;
    }

    /** Keep untouched generated fields managed after editing the complete view.
     * Otherwise saving the editor would freeze FPS/compiler defaults and later
     * defeat the launcher's frame-generation and managed controls. */
    public static String editedOverrides(String active,String edited,String previous,Set<String> managed)throws IOException {
        Map<String,String> before=globals(active),after=globals(edited),explicit;
        try{explicit=globals(previous);}catch(IOException oldInvalid){explicit=Collections.emptyMap();}
        String output=normalize(edited);
        for(String key:managed)if(!explicit.containsKey(key)&&before.containsKey(key)&&before.get(key).equals(after.get(key)))output=setGlobal(output,key,null);
        return output;
    }

    public static String read(File file) throws IOException {
        if (!file.exists()) return "";
        if (Files.isSymbolicLink(file.toPath()) || !file.isFile() || file.length() > LIMIT) throw new IOException("Invalid or oversized configuration file");
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > LIMIT) throw new IOException("Configuration exceeds 64 KiB");
                out.write(buffer, 0, count);
            }
            return normalize(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    public static void write(File file, String value) throws IOException {
        byte[] bytes = normalize(value).getBytes(StandardCharsets.UTF_8);
        File parent = file.getParentFile();
        if ((!parent.isDirectory() && !parent.mkdirs()) || Files.isSymbolicLink(file.toPath())) throw new IOException("Cannot write configuration here");
        File stage = File.createTempFile(".xhyn-v02-", ".tmp", parent);
        try {
            try (FileOutputStream out = new FileOutputStream(stage)) { out.write(bytes); out.getFD().sync(); }
            Files.move(stage.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { stage.delete(); }
    }

    private XhynV02Config() {}
}
