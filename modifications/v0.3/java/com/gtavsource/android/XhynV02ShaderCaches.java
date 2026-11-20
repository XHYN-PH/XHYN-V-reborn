package com.gtavsource.android;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONObject;

/** The app owns this cache directory. Never traverse links or remove the active cache. */
final class XhynV02ShaderCaches {
    private static final long BUDGET = 512L * 1024 * 1024;
    private static File active;

    static final class Entry {
        final File directory;
        final String label;
        final long bytes;
        final long lastUsed;

        Entry(File directory, String label, long bytes, long lastUsed) {
            this.directory = directory;
            this.label = label;
            this.bytes = bytes;
            this.lastUsed = lastUsed;
        }
    }

    static synchronized File open(Context context, String key) throws IOException {
        File directory = child(context, key);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare shader cache");
        active = directory;
        return directory;
    }

    static synchronized void release() { active = null; }

    static void maintain(Context context, File directory, String label) throws Exception {
        synchronized (XhynV02ShaderCaches.class) {
            if (!directory.equals(active)) return;
            JSONObject metadata = new JSONObject().put("label", label).put("lastUsed", System.currentTimeMillis());
            XhynV02Config.write(new File(directory, "xhyn-cache.json"), metadata.toString());
        }
        List<Entry> entries = list(context);
        long total = 0;
        for (Entry entry : entries) total += entry.bytes;
        int kept = entries.size();
        for (int index = entries.size() - 1; index >= 0 && (total > BUDGET || kept > 9); index--) {
            Entry entry = entries.get(index);
            synchronized (XhynV02ShaderCaches.class) {
                if (!directory.equals(active)) return;
                if (entry.directory.equals(active)) continue;
                XhynV02Runtime.removeTree(child(context, entry.directory.getName()), root(context));
            }
            total -= entry.bytes;
            kept--;
        }
    }

    static List<Entry> list(Context context) throws IOException {
        List<Entry> entries = new ArrayList<>();
        File root = root(context);
        File[] directories = root.listFiles();
        if (directories == null) return entries;
        for (File directory : directories) {
            if (!directory.getName().matches("[a-f0-9]{64}") || !directory.isDirectory()
                    || Files.isSymbolicLink(directory.toPath())) continue;
            String label = "Previous shader cache " + directory.getName().substring(0, 8);
            long lastUsed = directory.lastModified();
            try {
                JSONObject metadata = new JSONObject(XhynV02Config.read(new File(directory, "xhyn-cache.json")));
                label = metadata.optString("label", label);
                lastUsed = metadata.optLong("lastUsed", lastUsed);
            } catch (Exception unavailable) { /* Existing directories may predate metadata. */ }
            entries.add(new Entry(directory, label, size(directory), lastUsed));
        }
        entries.sort(Comparator.comparingLong((Entry item) -> item.lastUsed).reversed());
        return entries;
    }

    static synchronized void clear(Context context, String key) throws Exception {
        File directory = child(context, key);
        if (directory.equals(active) || GTAVActivity.isGameRunning()) {
            throw new IOException("Close the game before clearing this shader cache");
        }
        if (directory.exists()) XhynV02Runtime.removeTree(directory, root(context));
    }

    private static File child(Context context, String key) throws IOException {
        if (!key.matches("[a-f0-9]{64}")) throw new IOException("Invalid shader cache name");
        File directory = new File(root(context), key);
        if (Files.isSymbolicLink(directory.toPath())) throw new IOException("Invalid shader cache directory");
        return directory;
    }

    private static File root(Context context) throws IOException {
        File root = new File(context.getCacheDir(), "xhyn-shaders");
        if (Files.isSymbolicLink(root.toPath())) throw new IOException("Invalid shader cache root");
        return root;
    }

    private static long size(File file) {
        if (Files.isSymbolicLink(file.toPath())) return 0;
        if (file.isFile()) return file.length();
        File[] children = file.listFiles();
        long bytes = 0;
        if (children != null) for (File child : children) bytes += size(child);
        return bytes;
    }

    private XhynV02ShaderCaches() { }
}
