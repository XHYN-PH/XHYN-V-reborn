package com.gtavsource.android;

import android.app.AlertDialog;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.OpenableColumns;
import android.system.Os;
import android.system.OsConstants;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;

/** Stage, validate and install without changing the selected driver. */
final class XhynV02DriverImport {
    private final GameMenuActivity activity;
    private final File directory;
    private final Uri source;
    private final CancellationSignal signal = new CancellationSignal();
    private final Object commit = new Object();
    private final ProgressBar progress;
    private final TextView text;
    private final AlertDialog dialog;
    private volatile AssetFileDescriptor descriptor;
    private volatile boolean cancelled;
    private volatile boolean done;

    XhynV02DriverImport(GameMenuActivity activity, RuntimeConfig config, Uri source) {
        this.activity = activity;
        this.directory = config.drivers;
        this.source = source;
        LinearLayout panel = MenuStyle.column(activity);
        int padding = MenuStyle.dp(activity, 20);
        panel.setPadding(padding, padding, padding, padding);
        text = MenuStyle.text(activity, "Opening driver ZIP…", 14, 0xfff2f2f0);
        panel.addView(text);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        panel.addView(progress);
        dialog = new MenuDialogBuilder(activity).setTitle("Import Vulkan driver")
                .setView(panel).setNegativeButton("Cancel", (d, w) -> cancel()).create();
        dialog.setOnCancelListener(d -> cancel());
        dialog.setCanceledOnTouchOutside(false);
    }

    boolean running() { return !done; }

    void start() {
        dialog.show();
        Thread worker = new Thread(this::run, "xhyn-driver-import");
        worker.setDaemon(true);
        worker.start();
    }

    void cancel() {
        synchronized (commit) {
            if (done) return;
            cancelled = true;
        }
        Thread closer = new Thread(() -> {
            signal.cancel();
            AssetFileDescriptor open = descriptor;
            if (open != null) try { open.close(); } catch (IOException ignored) { }
        }, "xhyn-driver-import-cancel");
        closer.setDaemon(true);
        closer.start();
    }

    private void check() {
        if (cancelled) throw new OperationCanceledException();
    }

    private void status(String message, long bytes, long total) {
        activity.runOnUiThread(() -> {
            if (!dialog.isShowing() || cancelled) return;
            text.setText(message);
            progress.setIndeterminate(total <= 0);
            if (total > 0) progress.setProgress((int)Math.min(100, bytes * 100 / total));
        });
    }

    private void run() {
        File staged = null;
        File installed = null;
        Exception failure = null;
        try {
            String name = name();
            check();
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot open the Drivers folder");
            staged = File.createTempFile(".xhyn-driver-", ".tmp", directory);
            copy(staged);
            status("Checking Android API, ARM64 libraries and Vulkan metadata…", 0, 0);
            XhynV02DriverPackage.validate(staged, Build.VERSION.SDK_INT, Os.sysconf(OsConstants._SC_PAGESIZE), this::check);
            synchronized (commit) {
                check();
                File destination = new File(directory, name);
                for (int n = 2; destination.exists() && n < 1000; n++) {
                    destination = new File(directory, name.substring(0, name.length() - 4) + " (" + n + ").zip");
                }
                Files.move(staged.toPath(), destination.toPath());
                installed = destination;
                done = true;
            }
        } catch (Exception error) {
            failure = error;
        } finally {
            if (staged != null) staged.delete();
            done = true;
        }
        File result = installed;
        Exception error = failure;
        activity.runOnUiThread(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) return;
            dialog.dismiss();
            if (cancelled) return;
            if (result != null) {
                activity.showPage(XhynV02Settings.DRIVERS);
                Toast.makeText(activity, "Added " + result.getName() + ". Select it to use it on the next launch.", Toast.LENGTH_LONG).show();
            } else {
                String details = error instanceof java.util.zip.ZipException
                        ? "The ZIP is damaged or incomplete. Choose a complete Android Vulkan driver ZIP."
                        : error == null || error.getMessage() == null ? "The package could not be read." : error.getMessage();
                new MenuDialogBuilder(activity).setTitle("Driver import failed").setMessage(details)
                        .setPositiveButton("Close", null).show();
            }
        });
    }

    private String name() {
        String value = "driver.zip";
        try (Cursor metadata = activity.getContentResolver().query(source,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null, signal)) {
            if (metadata != null && metadata.moveToFirst() && metadata.getString(0) != null) value = metadata.getString(0);
        } catch (RuntimeException unavailable) { check(); }
        value = value.replaceAll("[^\\p{L}\\p{N} ._+-]", "_").trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".zip")) value = value.substring(0, value.length() - 4);
        if (value.startsWith(".")) value = "driver-" + value;
        if (value.isEmpty()) value = "driver";
        return value.substring(0, Math.min(value.length(), 64)) + ".zip";
    }

    private void copy(File staged) throws Exception {
        descriptor = activity.getContentResolver().openAssetFileDescriptor(source, "r", signal);
        if (descriptor == null) throw new IOException("Could not open the selected ZIP");
        try (AssetFileDescriptor opened = descriptor;
             InputStream input = opened.createInputStream();
             FileOutputStream output = new FileOutputStream(staged)) {
            long bytes = 0, lastUpdate = -1, total = opened.getLength();
            if (total > XhynV02DriverPackage.MAX) throw new IOException("Driver ZIP exceeds 128 MiB");
            byte[] block = new byte[65536];
            for (int count; (count = input.read(block)) != -1; ) {
                check();
                bytes += count;
                if (bytes > XhynV02DriverPackage.MAX) throw new IOException("Driver ZIP exceeds 128 MiB");
                output.write(block, 0, count);
                if (lastUpdate < 0 || bytes - lastUpdate >= 512 * 1024) {
                    status("Copying the selected ZIP… " + (bytes / 1024) + " KiB", bytes, total);
                    lastUpdate = bytes;
                }
            }
            check();
            output.getFD().sync();
        } finally {
            descriptor = null;
        }
    }
}
