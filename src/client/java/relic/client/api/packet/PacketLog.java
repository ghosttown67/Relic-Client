package relic.client.api.packet;

import net.minecraft.network.packet.Packet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class PacketLog {

    public record Entry(long time, boolean incoming, String name) {}

    private static final int CAPACITY = 500;
    private static final ArrayDeque<Entry> entries = new ArrayDeque<>();

    private static volatile boolean capturing;
    private static volatile boolean logIncoming = true;
    private static volatile boolean logOutgoing = true;

    private PacketLog() {}

    public static void record(boolean incoming, Packet<?> packet) {
        if (!capturing) return;
        if (incoming ? !logIncoming : !logOutgoing) return;

        String name = packet.getClass().getSimpleName();
        if (name.isEmpty()) name = packet.getClass().getName();

        Entry entry = new Entry(System.currentTimeMillis(), incoming, name);
        synchronized (entries) {
            entries.addLast(entry);
            while (entries.size() > CAPACITY) entries.removeFirst();
        }
    }

    public static List<Entry> snapshot() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    public static void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    public static boolean isCapturing()      { return capturing; }
    public static void setCapturing(boolean v){ capturing = v; }
    public static boolean logIncoming()       { return logIncoming; }
    public static void setLogIncoming(boolean v){ logIncoming = v; }
    public static boolean logOutgoing()       { return logOutgoing; }
    public static void setLogOutgoing(boolean v){ logOutgoing = v; }
    public static int capacity()              { return CAPACITY; }
}
