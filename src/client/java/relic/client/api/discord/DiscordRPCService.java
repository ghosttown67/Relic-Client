package relic.client.api.discord;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class DiscordRPCService {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private static final long RECONNECT_BACKOFF_MS = 5000;

    private static DiscordRPCService instance;

    private final long pid = ProcessHandle.current().pid();

    private volatile boolean running;
    private volatile String appId = "";

    private volatile JsonObject activity;

    private volatile int revision;

    private int sentRevision = -1;
    private RandomAccessFile pipe;
    private volatile boolean handshaked;
    private long lastConnectAttempt;
    private boolean warnedUnavailable;
    private Thread worker;

    private DiscordRPCService() {}

    public static synchronized DiscordRPCService getInstance() {
        if (instance == null) instance = new DiscordRPCService();
        return instance;
    }

    public synchronized void start(String appId) {
        setAppId(appId);
        if (running) return;
        running = true;
        worker = new Thread(this::loop, "Relic-DiscordRPC");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    public void setAppId(String appId) {
        String a = appId == null ? "" : appId.trim();
        if (!a.equals(this.appId)) {
            this.appId = a;
            disconnect();
        }
    }

    public void setActivity(JsonObject activity) {
        this.activity = activity;
        this.revision++;
    }

    public boolean isConnected() {
        return handshaked;
    }

    private void loop() {
        while (running) {
            try {
                if (!handshaked) connect();
                if (handshaked && revision != sentRevision) {
                    sentRevision = revision;
                    sendActivity(activity);
                }
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {

                disconnect();
            }
        }

        try {
            if (handshaked) sendActivity(null);
        } catch (Exception ignored) {
        }
        disconnect();
    }

    private void connect() {
        if (appId.isEmpty() || !WINDOWS) return;
        long now = System.currentTimeMillis();
        if (now - lastConnectAttempt < RECONNECT_BACKOFF_MS) return;
        lastConnectAttempt = now;

        for (int i = 0; i < 10; i++) {
            try {
                pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                JsonObject hs = new JsonObject();
                hs.addProperty("v", 1);
                hs.addProperty("client_id", appId);
                write(0, hs.toString());
                read();
                handshaked = true;
                warnedUnavailable = false;
                sentRevision = -1;
                System.out.println("[Relic Client] Discord RPC: connected (discord-ipc-" + i + ").");
                return;
            } catch (IOException e) {
                closePipe();
            }
        }
        if (!warnedUnavailable) {
            warnedUnavailable = true;
            System.out.println("[Relic Client] Discord RPC: Discord not running / no IPC pipe; will keep retrying.");
        }
    }

    private void sendActivity(JsonObject act) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        args.add("activity", act);

        JsonObject frame = new JsonObject();
        frame.addProperty("cmd", "SET_ACTIVITY");
        frame.add("args", args);
        frame.addProperty("nonce", UUID.randomUUID().toString());

        write(1, frame.toString());
        read();
    }

    private void write(int op, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op);
        buf.putInt(data.length);
        buf.put(data);
        pipe.write(buf.array());
    }

    private void read() throws IOException {
        byte[] header = new byte[8];
        pipe.readFully(header);
        int len = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
        if (len > 0) {
            pipe.readFully(new byte[len]);
        }
    }

    private void disconnect() {
        handshaked = false;
        closePipe();
    }

    private void closePipe() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (IOException ignored) {
            }
            pipe = null;
        }
    }
}
