package com.gtavsource.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Reuse report and shader-cache identities without changing the loaders' verification. */
final class XhynV02ArchiveIdentity {
    static String hash(Context context, File file, String kind) throws Exception {
        String before = stamp(file);
        if (context == null || before == null) return XhynV02Runtime.hash(file);
        SharedPreferences preferences = context.getSharedPreferences("xhyn-archive-identities", 0);
        String prefix = before + "\n";
        String saved = preferences.getString(kind, "");
        if (saved.startsWith(prefix)) {
            String hash = saved.substring(prefix.length());
            if (hash.matches("[a-f0-9]{64}")) return hash;
        }
        String hash = XhynV02Runtime.hash(file);
        if (!before.equals(stamp(file))) throw new IOException("Package changed while being read");
        preferences.edit().putString(kind, prefix + hash).apply();
        return hash;
    }

    static String stamp(File file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) throw new IOException("Invalid package file");
        if (attributes.fileKey() == null) return null;
        try {
            Object changed = Files.getAttribute(file.toPath(), "unix:ctime", LinkOption.NOFOLLOW_LINKS);
            if (changed == null) return null;
            return file.getCanonicalPath() + "\n" + attributes.fileKey() + "\n" + attributes.size()
                    + "\n" + attributes.lastModifiedTime() + "\n" + changed;
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException unavailable) {
            // A filesystem without a change time supplies no durable identity.
            return null;
        }
    }

    private XhynV02ArchiveIdentity() { }
}
