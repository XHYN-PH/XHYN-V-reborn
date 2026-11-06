package com.gtavsource.android;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Hardware and driver identity are separate from phone brand and ZIP filename. */
public final class XhynV02DriverPolicy {
    public static String family(int vendor, String device, int driverId) {
        String gpu = device == null ? "" : device.toLowerCase(Locale.ROOT);
        if (gpu.contains("xclipse") || vendor == 0x144d || driverId == 21) return "xclipse";
        if (vendor == 0x13b5 || gpu.contains("mali") || gpu.contains("immortalis")) return "mali";
        if (vendor == 0x5143 || gpu.contains("adreno")) return "adreno";
        return "unknown";
    }

    public static boolean isTurnip(int vendor, int driverId, String driverName) {
        String name = driverName == null ? "" : driverName.toLowerCase(Locale.ROOT);
        return vendor == 0x5143 && (driverId == 18 || name.contains("turnip"));
    }

    public static String memoryFlags(String existing, int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Unknown Turnip memory mode");
        Set<String> flags = new LinkedHashSet<>();
        if (existing != null) for (String item : existing.split(",")) {
            String value = item.trim();
            if (!value.isEmpty() && !value.equals("gmem") && !value.equals("sysmem")) flags.add(value);
        }
        if (mode == 1) flags.add("gmem");
        if (mode == 2) flags.add("sysmem");
        return String.join(",", flags);
    }

    public static String oneUiFeatures(String existing) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        if (existing != null && !existing.trim().isEmpty()) for (String item : existing.split(":")) {
            int equals = item.indexOf('=');
            if (equals <= 0 || !item.substring(0, equals).trim().matches("[A-Za-z0-9_]+")
                    || item.substring(equals + 1).trim().isEmpty() || item.substring(equals + 1).contains(",") || item.indexOf('=', equals + 1) >= 0)
                throw new IllegalArgumentException("FD_DEV_FEATURES needs feature=value pairs separated by colons");
            values.put(item.substring(0, equals).trim(), item.substring(equals + 1).trim());
        }
        values.put("enable_tp_ubwc_flag_hint", "1");
        java.util.List<String> output = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) output.add(entry.getKey() + "=" + entry.getValue());
        return String.join(":", output);
    }
    private XhynV02DriverPolicy() {}
}
