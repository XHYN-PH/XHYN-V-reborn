package com.gtavsource.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.LinearLayout;
import com.gtavsource.android.lan.LanSession;
import com.gtavsource.android.lan.LanTransport;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;

/** Displays existing LAN state and measures its transport without changing the wire format. */
public final class XhynV02Lan {
    private static final int PROTOCOL = 14;
    private static final int HOST_SLOT = 1;
    private static final byte[] EMPTY_PACKET = new byte[0];
    private static final ThreadLocal<DatagramPacket> OUTBOUND = ThreadLocal.withInitial(
            () -> new DatagramPacket(EMPTY_PACKET, 0));
    private static final ThreadLocal<Long> PACKING_START = new ThreadLocal<>();
    private static volatile Meter meter;

    public static void addToPage(Activity activity, LinearLayout page) {
        LinearLayout row = MenuStyle.detailRow(page, "LAN room details", "Host, build and traffic");
        row.setTag("xhyn-lan-details");
        Runnable open = () -> show(activity);
        row.setOnClickListener(view -> open.run());
        row.addView(MenuStyle.icon(activity, android.R.drawable.ic_menu_info_details, "Open LAN room details", open));
    }

    private static void show(Activity activity) {
        LanSession.State room = LanSession.state();
        String app = "XHYN V";
        try { app += " " + activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        StringBuilder details = new StringBuilder(app).append("\nRuntime 1.7.57 · LAN protocol ").append(PROTOCOL)
                .append("\nUse matching builds and gameplay mods on every device.");
        if (room.active) {
            String host = room.host ? "You" : "Waiting for the host name";
            if (!room.host && room.members != null) for (LanTransport.Member member : room.members) {
                if (member != null && member.slot == HOST_SLOT) {
                    host = member.name;
                    break;
                }
            }
            details.append("\n\nHost · ").append(host).append("\n")
                    .append(room.virtualNetwork ? "Virtual LAN · Bridge or a compatible VPN" : "Local network · Wi-Fi or hotspot")
                    .append("\n").append(room.players).append(" / ").append(room.playerLimit).append(" players")
                    .append("\n").append(room.ready ? "Room connected" : "Connecting to room");
        } else {
            details.append(room.cleanup || room.busy ? "\n\nClosing the previous connection" : "\n\nNo active room");
        }
        AlertDialog.Builder dialog = new MenuDialogBuilder(activity).setTitle("LAN room details")
                .setMessage(details).setPositiveButton("Close", null)
                .setNeutralButton("Local gameplay mods", (d, w) -> showMods(activity));
        if (room.active && room.ready) dialog.setNegativeButton("Measure traffic", (d, w) -> measure(activity, room.generation));
        dialog.show();
    }

    private static void showMods(Activity activity) {
        RuntimeConfig config = activity instanceof GameMenuActivity && ((GameMenuActivity)activity).config != null
                ? ((GameMenuActivity)activity).config : XhynV02Runtime.config();
        Thread reader = new Thread(() -> {
            StringBuilder text = new StringBuilder();
            if (!config.getBoolean("mods", "enabled", false)) text.append("Resource mods are disabled.");
            else try {
                List<ModCatalog.Item> items = ModCatalog.list(config.root);
                int enabled = 0;
                for (ModCatalog.Item item : items) if (item.enabled) {
                    if (++enabled <= 24) text.append(item.name).append('\n');
                }
                if (enabled == 0) text.append("No enabled resource mods.");
                else if (enabled > 24) text.append("And ").append(enabled - 24).append(" more enabled mods.");
            } catch (Exception unavailable) { text.append("The local mod list could not be read."); }
            String summary = text.toString().trim() + "\n\nThis is the local catalog. Compare it with the other players.";
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) new MenuDialogBuilder(activity)
                        .setTitle("Local gameplay mods").setMessage(summary).setPositiveButton("Close", null).show();
            });
        }, "xhyn-lan-mods");
        reader.setDaemon(true);
        reader.start();
    }

    private static void measure(Activity activity, int generation) {
        Handler handler = new Handler(Looper.getMainLooper());
        Meter measurement = new Meter();
        meter = measurement;
        AlertDialog progress = new MenuDialogBuilder(activity).setTitle("Measuring LAN traffic")
                .setMessage("Keep this room connected for 10 seconds. Only outgoing packet counts, sizes and timing are measured.")
                .setNegativeButton("Cancel", null).create();
        Runnable finish = () -> {
            if (meter != measurement || activity.isFinishing() || activity.isDestroyed()) return;
            meter = null;
            progress.dismiss();
            LanSession.State current = LanSession.state();
            String result = (current.active && current.generation == generation)
                    ? measurement.result() : "The room changed during the measurement. Try again in the current room.";
            new MenuDialogBuilder(activity).setTitle("LAN traffic").setMessage(result).setPositiveButton("Close", null).show();
        };
        progress.setOnDismissListener(d -> {
            if (meter == measurement) meter = null;
            handler.removeCallbacks(finish);
        });
        progress.show();
        handler.postDelayed(finish, 10000);
    }

    public static void send(DatagramSocket socket, byte[] bytes, InetSocketAddress address) throws IOException {
        DatagramPacket packet = OUTBOUND.get();
        packet.setData(bytes);
        packet.setSocketAddress(address);
        try {
            socket.send(packet);
            Meter active = meter;
            if (active != null) active.sent(bytes.length);
        } finally {
            packet.setData(EMPTY_PACKET);
        }
    }

    public static void packing() {
        if (meter != null) PACKING_START.set(System.nanoTime());
    }

    public static void packed(byte[][] packets) {
        Meter active = meter;
        if (active != null) {
            Long start = PACKING_START.get();
            if (start != null) active.packed(packets.length, System.nanoTime() - start);
            PACKING_START.remove();
        }
    }

    private static final class Meter {
        final long began = SystemClock.elapsedRealtime();
        long datagrams, bytes, frames, fragmented, fragmentPackets, packingNanos;

        synchronized void sent(int size) {
            datagrams++;
            bytes += size;
        }

        synchronized void packed(int packets, long nanos) {
            frames++;
            packingNanos += nanos;
            if (packets > 1) {
                fragmented++;
                fragmentPackets += packets;
            }
        }

        synchronized String result() {
            double seconds = Math.max(1, SystemClock.elapsedRealtime() - began) / 1000.0;
            return String.format(Locale.ROOT, "Outgoing datagrams · %d\nTraffic · %.1f KiB/s"
                            + "\nState sends and relays · %d\nFragmented state sends · %d (%d packets)"
                            + "\nAverage Java packing time · %s\n\nCounts include sends and relays to individual peers.",
                    datagrams, bytes / 1024.0 / seconds, frames, fragmented, fragmentPackets,
                    frames == 0 ? "Unavailable" : String.format(Locale.ROOT, "%.2f ms", packingNanos / frames / 1e6));
        }
    }

    private XhynV02Lan() { }
}
