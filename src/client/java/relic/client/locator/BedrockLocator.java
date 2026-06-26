package relic.client.locator;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class BedrockLocator {

    public static final byte UNKNOWN = 0;
    public static final byte BEDROCK = 1;
    public static final byte NOT_BEDROCK = 2;

    private static final int MAX_RESULTS = 256;
    private static final int DEFAULT_GRID = 12;
    private static final int MAX_GRID = 32;

    public static final int SOFT_MAX_RADIUS = 125_000;

    public enum Phase { IDLE, SEARCHING, DONE, CANCELLED }

    private static BedrockLocator instance;

    private String seedText = "";
    private int layerY = -61;
    private int gridWidth = DEFAULT_GRID;
    private int gridHeight = DEFAULT_GRID;
    private byte[] grid = new byte[DEFAULT_GRID * DEFAULT_GRID];
    private int centerX = 0;
    private int centerZ = 0;
    private int radius = 87_500;

    private volatile Phase phase = Phase.IDLE;
    private volatile String message = "";
    private final AtomicLong scanned = new AtomicLong();
    private volatile long total;
    private final AtomicBoolean cancelFlag = new AtomicBoolean();
    private volatile boolean truncated;

    private final CopyOnWriteArrayList<long[]> results = new CopyOnWriteArrayList<>();
    private Thread coordinator;

    private BedrockLocator() {}

    public static synchronized BedrockLocator getInstance() {
        if (instance == null) instance = new BedrockLocator();
        return instance;
    }

    public int getGridWidth()  { return gridWidth; }
    public int getGridHeight() { return gridHeight; }

    public byte getCell(int col, int row) {
        if (col < 0 || row < 0 || col >= gridWidth || row >= gridHeight) return UNKNOWN;
        return grid[row * gridWidth + col];
    }

    public void cycleCell(int col, int row) {
        if (col < 0 || row < 0 || col >= gridWidth || row >= gridHeight) return;
        int i = row * gridWidth + col;
        grid[i] = (byte) ((grid[i] + 1) % 3);
    }

    public void setGridSize(int w, int h) {
        w = Math.max(1, Math.min(MAX_GRID, w));
        h = Math.max(1, Math.min(MAX_GRID, h));
        if (w == gridWidth && h == gridHeight) return;
        byte[] next = new byte[w * h];
        for (int row = 0; row < Math.min(h, gridHeight); row++) {
            for (int col = 0; col < Math.min(w, gridWidth); col++) {
                next[row * w + col] = grid[row * gridWidth + col];
            }
        }
        grid = next;
        gridWidth = w;
        gridHeight = h;
    }

    public void clearGrid() {
        java.util.Arrays.fill(grid, UNKNOWN);
    }

    public int markedCount() {
        int n = 0;
        for (byte b : grid) if (b != UNKNOWN) n++;
        return n;
    }

    public String getSeedText()     { return seedText; }
    public void setSeedText(String s) { this.seedText = s; }
    public int getLayerY()          { return layerY; }
    public void setLayerY(int y)    { this.layerY = y; }
    public int getCenterX()         { return centerX; }
    public void setCenterX(int x)   { this.centerX = x; }
    public int getCenterZ()         { return centerZ; }
    public void setCenterZ(int z)   { this.centerZ = z; }
    public int getRadius()          { return radius; }
    public void setRadius(int r)    { this.radius = Math.max(1, r); }

    public Phase getPhase()       { return phase; }
    public String getMessage()    { return message; }
    public boolean isTruncated()  { return truncated; }
    public long getScanned()      { return scanned.get(); }
    public long getTotal()        { return total; }
    public List<long[]> getResults() { return results; }

    public float getProgress() {
        long t = total;
        return t <= 0 ? 0f : Math.min(1f, (float) ((double) scanned.get() / t));
    }

    public boolean isRunning() {
        return phase == Phase.SEARCHING;
    }

    public synchronized void start() {
        if (phase == Phase.SEARCHING) return;

        Long seed = parseSeed(seedText);
        if (seed == null) { fail("Enter a seed."); return; }
        if (markedCount() == 0) { fail("Mark at least one cell first."); return; }

        final BedrockGenerator gen = new BedrockGenerator(seed);
        final int y = layerY;
        final List<int[]> cells = orderedCells(gen, y);
        final int minX = centerX - radius, maxX = centerX + radius;
        final int minZ = centerZ - radius, maxZ = centerZ + radius;

        results.clear();
        truncated = false;
        scanned.set(0);
        total = (long) (maxX - minX + 1) * (long) (maxZ - minZ + 1);
        cancelFlag.set(false);
        message = "Searching " + total + " positions...";
        phase = Phase.SEARCHING;

        coordinator = new Thread(() -> runSearch(gen, y, cells, minX, maxX, minZ, maxZ),
                "Relic-BedrockLocator");
        coordinator.setDaemon(true);
        coordinator.start();
    }

    public void cancel() {
        if (phase == Phase.SEARCHING) {
            cancelFlag.set(true);
            message = "Cancelling...";
        }
    }

    public synchronized void reset() {
        if (phase == Phase.SEARCHING) return;
        results.clear();
        scanned.set(0);
        total = 0;
        truncated = false;
        phase = Phase.IDLE;
        message = "";
    }

    private void fail(String why) {
        phase = Phase.IDLE;
        message = why;
    }

    private void runSearch(BedrockGenerator gen, int y, List<int[]> cells,
                           int minX, int maxX, int minZ, int maxZ) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        long zSpan = (long) (maxZ - minZ + 1);
        threads = (int) Math.min(threads, zSpan);

        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {

            int from = (int) (minZ + zSpan * t / threads);
            int to   = (int) (minZ + zSpan * (t + 1) / threads);
            Thread worker = new Thread(
                    () -> scanBand(gen, y, cells, minX, maxX, from, to),
                    "Relic-BedrockLocator-" + t);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
        for (Thread w : workers) {
            try { w.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (cancelFlag.get()) {
            phase = Phase.CANCELLED;
            message = "Cancelled after " + scanned.get() + " positions. " + results.size() + " match(es) so far.";
        } else {
            phase = Phase.DONE;
            int n = results.size();
            message = n == 0 ? "No match in the search region."
                    : (truncated ? (MAX_RESULTS + "+ matches (pattern too small — add cells).")
                                 : n + " match" + (n == 1 ? "" : "es") + " found.");
        }
    }

    private void scanBand(BedrockGenerator gen, int y, List<int[]> cells,
                          int minX, int maxX, int zFrom, int zTo) {
        int[][] cellArr = cells.toArray(new int[0][]);
        for (int oz = zFrom; oz < zTo; oz++) {
            if (cancelFlag.get()) return;
            for (int ox = minX; ox <= maxX; ox++) {
                boolean match = true;
                for (int[] c : cellArr) {
                    boolean want = c[2] != 0;
                    if (gen.isBedrock(ox + c[0], y, oz + c[1]) != want) { match = false; break; }
                }
                if (match) {
                    if (results.size() < MAX_RESULTS) {
                        results.add(new long[]{ox, oz});
                    } else {
                        truncated = true;
                    }
                }
            }
            scanned.addAndGet((long) (maxX - minX + 1));
        }
    }

    private List<int[]> orderedCells(BedrockGenerator gen, int y) {
        float chance = gen.chance(y);
        List<int[]> list = new ArrayList<>();
        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                byte b = grid[row * gridWidth + col];
                if (b == UNKNOWN) continue;
                list.add(new int[]{col, row, b == BEDROCK ? 1 : 0});
            }
        }
        list.sort((a, bb) -> {
            float pa = a[2] != 0 ? chance : 1 - chance;
            float pb = bb[2] != 0 ? chance : 1 - chance;
            return Float.compare(pa, pb);
        });
        return list;
    }

    public String validateAtPlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return "No world.";
        if (mc.world.getRegistryKey() != World.OVERWORLD) return "Overworld only.";
        Long seed = parseSeed(seedText);
        if (seed == null) return "Enter a seed.";

        BedrockGenerator gen = new BedrockGenerator(seed);
        ChunkPos cp = mc.player.getChunkPos();
        int matched = 0, compared = 0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int y = gen.getMinPatternY(); y <= gen.getMaxPatternY(); y++) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int wx = cp.getStartX() + lx, wz = cp.getStartZ() + lz;
                    boolean live = mc.world.getBlockState(p.set(wx, y, wz)).isOf(Blocks.BEDROCK);
                    if (live == gen.isBedrock(wx, y, wz)) matched++;
                    compared++;
                }
            }
        }
        double pct = compared == 0 ? 0 : 100.0 * matched / compared;
        return String.format("Validation @chunk %d,%d: %d/%d match (%.2f%%)",
                cp.x, cp.z, matched, compared, pct);
    }

    public static Long parseSeed(String in) {
        if (in == null) return null;
        String t = in.trim();
        if (t.isEmpty()) return null;
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return (long) t.hashCode();
        }
    }
}
