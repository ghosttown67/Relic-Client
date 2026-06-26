package relic.client.map;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import relic.client.locator.BedrockLocator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SeedMap {

    public static final class Waypoint {
        public String name;
        public int x, z;
        public int argb;
        public Waypoint(String name, int x, int z, int argb) {
            this.name = name; this.x = x; this.z = z; this.argb = argb;
        }
    }

    private static final class Result {
        final int[] baseArgb;
        final int[] pathIdx;
        final String[] paths;
        final double originX, originZ;
        final double bpp;
        final int w, h;
        final List<StructureFinder.Found> structures;
        final long[] slimeChunks;
        Result(int[] baseArgb, int[] pathIdx, String[] paths,
               double originX, double originZ, double bpp, int w, int h,
               List<StructureFinder.Found> structures, long[] slimeChunks) {
            this.baseArgb = baseArgb; this.pathIdx = pathIdx; this.paths = paths;
            this.originX = originX; this.originZ = originZ; this.bpp = bpp; this.w = w; this.h = h;
            this.structures = structures; this.slimeChunks = slimeChunks;
        }
    }

    public static final double MIN_BPP = 0.25;
    public static final double MAX_BPP = 96.0;

    public static final double STRUCT_MAX_BPP = 16.0;

    public static final double GRID_MAX_BPP = 4.0;
    private static final long SLIME_SCRAMBLER = 0x3ad8025fL;
    private static final int MAX_IMG = 1280;
    private static final long DEBOUNCE_MS = 120;

    private static SeedMap instance;

    private String seedText = "";

    private final Map<String, String> serverSeeds = new java.util.LinkedHashMap<>();

    private String autoServer;
    private double centerX = 0, centerZ = 0;
    private double blocksPerPixel = 2.0;
    private int sampleY = 64;
    private final Set<String> filter = new LinkedHashSet<>();
    private final List<Waypoint> waypoints = new ArrayList<>();

    private boolean showStructures = true;
    private final Set<String> structureFilter = new LinkedHashSet<>();

    private final Set<String> completedStructures = new java.util.HashSet<>();
    private boolean showGrid = false;
    private boolean showSlimeChunks = false;

    private final AtomicInteger generation = new AtomicInteger();
    private volatile Result pending;
    private Result current;
    private volatile boolean working;
    private volatile String status = "";
    private boolean dirty = true;
    private long dirtyAt;

    private int genW, genH;

    private final Map<Long, BiomeMapGenerator> generators = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, StructureFinder> finders = new java.util.concurrent.ConcurrentHashMap<>();

    private NativeImageBackedTexture tex;
    private int texW, texH;
    private int filterVersion, uploadedFilterVersion = -1;
    private Result uploadedResult;

    private SeedMap() {}

    public static synchronized SeedMap getInstance() {
        if (instance == null) instance = new SeedMap();
        return instance;
    }

    public String getSeedText()      { return seedText; }
    public void setSeedText(String s) {
        if (!s.equals(seedText)) { seedText = s; markDirty(); }
    }

    public void syncServerSeed(String serverAddress) {
        if (serverAddress == null) { autoServer = null; return; }
        if (serverAddress.equals(autoServer)) return;
        autoServer = serverAddress;
        String cached = serverSeeds.get(serverAddress);
        if (cached != null && !cached.isEmpty()) setSeedText(cached);
    }

    public void rememberSeedForServer(String serverAddress, String seed) {
        if (serverAddress == null || serverAddress.isBlank()) return;
        if (seed == null || seed.isBlank()) serverSeeds.remove(serverAddress);
        else serverSeeds.put(serverAddress, seed);
    }

    public Map<String, String> getServerSeeds() { return serverSeeds; }

    public void putServerSeed(String serverAddress, String seed) {
        if (serverAddress != null && !serverAddress.isBlank() && seed != null && !seed.isBlank()) {
            serverSeeds.put(serverAddress, seed);
        }
    }

    public double getCenterX()       { return centerX; }
    public double getCenterZ()       { return centerZ; }
    public void setCenter(double x, double z) {
        if (x != centerX || z != centerZ) { centerX = x; centerZ = z; markDirty(); }
    }
    public double getBlocksPerPixel() { return blocksPerPixel; }
    public int getSampleY()          { return sampleY; }
    public void setSampleY(int y) {
        if (y != sampleY) { sampleY = y; markDirty(); }
    }

    public void zoomAt(double factor, double anchorWorldX, double anchorWorldZ) {
        double nb = clampBpp(blocksPerPixel * factor);
        if (nb == blocksPerPixel) return;

        double k = 1.0 - nb / blocksPerPixel;
        centerX += (anchorWorldX - centerX) * k;
        centerZ += (anchorWorldZ - centerZ) * k;
        blocksPerPixel = nb;
        markDirty();
    }

    public void panPixels(double dxPixels, double dzPixels) {
        centerX -= dxPixels * blocksPerPixel;
        centerZ -= dzPixels * blocksPerPixel;
        markDirty();
    }

    private static double clampBpp(double b) {
        return b < MIN_BPP ? MIN_BPP : (b > MAX_BPP ? MAX_BPP : b);
    }

    public void forceRefresh() {
        generators.clear();
        finders.clear();
        markDirty();
    }

    public boolean isShowStructures() { return showStructures; }
    public void setShowStructures(boolean v) {
        if (v != showStructures) { showStructures = v; markDirty(); }
    }
    public boolean isShowGrid()   { return showGrid; }
    public void setShowGrid(boolean v)   { showGrid = v; }
    public boolean isShowSlime()  { return showSlimeChunks; }
    public void setShowSlime(boolean v)  {
        if (v != showSlimeChunks) { showSlimeChunks = v; markDirty(); }
    }

    public Set<String> getStructureFilter() { return structureFilter; }
    public boolean isStructureShown(String id) {
        return structureFilter.isEmpty() || structureFilter.contains(id);
    }
    public void toggleStructure(String id) {
        if (!structureFilter.remove(id)) structureFilter.add(id);
    }
    public void clearStructureFilter() { structureFilter.clear(); }

    private static String structKey(StructureFinder.Found s) {
        return s.id() + "@" + s.blockX() + "," + s.blockZ();
    }
    public boolean isStructureComplete(StructureFinder.Found s) {
        return completedStructures.contains(structKey(s));
    }

    public boolean toggleStructureComplete(StructureFinder.Found s) {
        String k = structKey(s);
        if (completedStructures.remove(k)) return false;
        completedStructures.add(k);
        return true;
    }

    public List<StructureFinder.Found> currentStructures() {
        Result r = current;
        return r == null ? List.of() : r.structures;
    }

    public StructureFinder finderForCurrentSeed() {
        Long seed = BedrockLocator.parseSeed(seedText);
        return seed == null ? null : finders.get(seed);
    }

    public long[] currentSlimeChunks() {
        Result r = current;
        return r == null ? new long[0] : r.slimeChunks;
    }

    private void markDirty() {
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    public String getStatus()  { return status; }
    public boolean isWorking() { return working; }
    public boolean hasImage()  { return current != null; }

    public Set<String> getFilter() { return filter; }
    public boolean isFiltered()    { return !filter.isEmpty(); }
    public boolean isShown(String path) { return filter.isEmpty() || filter.contains(path); }
    public void toggleFilter(String path) {
        if (!filter.remove(path)) filter.add(path);
        filterVersion++;
    }
    public void clearFilter() {
        if (!filter.isEmpty()) { filter.clear(); filterVersion++; }
    }

    public List<Waypoint> getWaypoints() { return waypoints; }
    public void addWaypoint(String name, int x, int z, int argb) {
        waypoints.add(new Waypoint(name.isBlank() ? (x + ", " + z) : name, x, z, argb));
    }
    public void removeWaypoint(Waypoint w) { waypoints.remove(w); }

    public BiomeMapGenerator generatorForCurrentSeed() {
        Long seed = BedrockLocator.parseSeed(seedText);
        if (seed == null) return null;
        return generators.get(seed);
    }

    public boolean hasSeed() {
        return BedrockLocator.parseSeed(seedText) != null;
    }

    public double worldToScreenX(double worldX, float canvasCx) {
        return canvasCx + (worldX - centerX) / blocksPerPixel;
    }
    public double worldToScreenZ(double worldZ, float canvasCy) {
        return canvasCy + (worldZ - centerZ) / blocksPerPixel;
    }
    public double screenToWorldX(double sx, float canvasCx) {
        return centerX + (sx - canvasCx) * blocksPerPixel;
    }
    public double screenToWorldZ(double sy, float canvasCy) {
        return centerZ + (sy - canvasCy) * blocksPerPixel;
    }

    public String biomeNameAt(double worldX, double worldZ) {
        Result r = current;
        if (r == null) return null;
        int px = (int) Math.floor((worldX - r.originX) / r.bpp);
        int py = (int) Math.floor((worldZ - r.originZ) / r.bpp);
        if (px < 0 || py < 0 || px >= r.w || py >= r.h) return null;
        return r.paths[r.pathIdx[py * r.w + px]];
    }

    public void frame(int canvasW, int canvasH) {
        canvasW = Math.min(MAX_IMG, Math.max(16, canvasW));
        canvasH = Math.min(MAX_IMG, Math.max(16, canvasH));

        if (canvasW != genW || canvasH != genH) markDirty();

        boolean ready = !working
                && (current == null || System.currentTimeMillis() - dirtyAt >= DEBOUNCE_MS);
        if (dirty && ready) {
            submit(canvasW, canvasH);
        }
        syncTexture();
    }

    private void submit(int w, int h) {
        Long seed = BedrockLocator.parseSeed(seedText);
        if (seed == null) {
            status = "Enter a seed.";
            pending = null;
            current = null;
            dirty = false;
            return;
        }
        dirty = false;
        genW = w; genH = h;

        final int gen = generation.incrementAndGet();
        final double cx = centerX, cz = centerZ, bpp = blocksPerPixel;
        final int yy = sampleY, ww = w, hh = h;
        working = true;
        status = "Rendering...";

        final long seedVal = seed;
        Thread worker = new Thread(() -> {
            try {
                BiomeMapGenerator g = generators.computeIfAbsent(seedVal, BiomeMapGenerator::new);
                Result r = render(g, seedVal, cx, cz, bpp, yy, ww, hh, gen);
                if (r != null && gen == generation.get()) {
                    pending = r;
                    status = "";
                }
            } catch (Throwable t) {
                status = "Render failed: " + t.getClass().getSimpleName();
            } finally {
                if (gen == generation.get()) working = false;
            }
        }, "Relic-SeedMap");
        worker.setDaemon(true);
        worker.start();
    }

    private Result render(BiomeMapGenerator g, long seed, double cx, double cz, double bpp,
                          int y, int w, int h, int gen) {
        double originX = cx - (w / 2.0) * bpp;
        double originZ = cz - (h / 2.0) * bpp;

        int step = Math.max(4, (int) Math.round(bpp));
        step -= step % 4;
        if (step < 4) step = 4;

        int worldX0 = (int) Math.floor(originX);
        int worldZ0 = (int) Math.floor(originZ);
        int worldX1 = (int) Math.floor(originX + w * bpp);
        int worldZ1 = (int) Math.floor(originZ + h * bpp);
        int cellMinX = Math.floorDiv(worldX0, step);
        int cellMinZ = Math.floorDiv(worldZ0, step);
        int cellMaxX = Math.floorDiv(worldX1, step);
        int cellMaxZ = Math.floorDiv(worldZ1, step);
        int gw = cellMaxX - cellMinX + 1;
        int gh = cellMaxZ - cellMinZ + 1;

        final int[] cellColor = new int[gw * gh];
        final String[] cellName = new String[gw * gh];

        final int fStep = step, fgw = gw, fcMinX = cellMinX, fcMinZ = cellMinZ, fy = y;
        java.util.stream.IntStream.range(0, gh).parallel().forEach(gz -> {
            int wz = (fcMinZ + gz) * fStep + fStep / 2;
            int row = gz * fgw;
            for (int gx = 0; gx < fgw; gx++) {
                int wx = (fcMinX + gx) * fStep + fStep / 2;
                RegistryEntry<Biome> biome = g.biomeAt(wx, fy, wz);
                cellColor[row + gx] = BiomePalette.colorArgb(biome);
                cellName[row + gx] = BiomePalette.name(biome);
            }
        });
        if (gen != generation.get()) return null;

        int[] cellPath = new int[gw * gh];
        Map<String, Integer> pathIndex = new HashMap<>();
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < cellColor.length; i++) {
            Integer pi = pathIndex.get(cellName[i]);
            if (pi == null) { pi = paths.size(); pathIndex.put(cellName[i], pi); paths.add(cellName[i]); }
            cellPath[i] = pi;
        }

        int[] baseArgb = new int[w * h];
        int[] pathIdx = new int[w * h];
        for (int py = 0; py < h; py++) {
            int wz = (int) Math.floor(originZ + py * bpp);
            int gz = Math.floorDiv(wz, step) - cellMinZ;
            if (gz < 0) gz = 0; else if (gz >= gh) gz = gh - 1;
            int rowBase = py * w;
            int cellRow = gz * gw;
            for (int px = 0; px < w; px++) {
                int wx = (int) Math.floor(originX + px * bpp);
                int gx = Math.floorDiv(wx, step) - cellMinX;
                if (gx < 0) gx = 0; else if (gx >= gw) gx = gw - 1;
                int ci = cellRow + gx;
                baseArgb[rowBase + px] = cellColor[ci];
                pathIdx[rowBase + px] = cellPath[ci];
            }
        }
        List<StructureFinder.Found> structures = List.of();
        if (showStructures && bpp <= STRUCT_MAX_BPP) {
            StructureFinder f = finders.computeIfAbsent(seed, StructureFinder::new);
            structures = f.findInBlockRange(originX, originZ, originX + w * bpp, originZ + h * bpp, g);
        }

        long[] slime = computeSlime(seed, originX, originZ, bpp, w, h);

        return new Result(baseArgb, pathIdx, paths.toArray(new String[0]),
                originX, originZ, bpp, w, h, structures, slime);
    }

    private long[] computeSlime(long seed, double originX, double originZ, double bpp, int w, int h) {
        if (!showSlimeChunks || bpp > GRID_MAX_BPP) return new long[0];
        int cMinX = (int) Math.floor(originX) >> 4, cMaxX = (int) Math.floor(originX + w * bpp) >> 4;
        int cMinZ = (int) Math.floor(originZ) >> 4, cMaxZ = (int) Math.floor(originZ + h * bpp) >> 4;
        java.util.List<Long> hits = new ArrayList<>();
        for (int cz = cMinZ; cz <= cMaxZ; cz++) {
            for (int cx = cMinX; cx <= cMaxX; cx++) {
                if (net.minecraft.util.math.random.ChunkRandom
                        .getSlimeRandom(cx, cz, seed, SLIME_SCRAMBLER).nextInt(10) == 0) {
                    hits.add(net.minecraft.util.math.ChunkPos.toLong(cx, cz));
                }
            }
        }
        long[] out = new long[hits.size()];
        for (int i = 0; i < out.length; i++) out[i] = hits.get(i);
        return out;
    }

    private void syncTexture() {
        Result r = pending;
        boolean newImage = r != null && r != uploadedResult;
        boolean filterChanged = uploadedResult != null && uploadedFilterVersion != filterVersion;
        if (!newImage && !filterChanged) return;

        if (newImage) {
            current = r;
            ensureTexture(r.w, r.h);
        }
        Result draw = current;
        if (draw == null || tex == null) return;
        writeFiltered(draw);
        try {
            tex.upload();
        } catch (Exception ignored) {

        }
        uploadedResult = draw;
        uploadedFilterVersion = filterVersion;
    }

    private void writeFiltered(Result r) {
        NativeImage img = tex.getImage();
        if (img == null) return;
        boolean filtered = !filter.isEmpty();
        for (int py = 0; py < r.h; py++) {
            int row = py * r.w;
            for (int px = 0; px < r.w; px++) {
                int i = row + px;
                int color = r.baseArgb[i];
                if (filtered && !filter.contains(r.paths[r.pathIdx[i]])) {
                    color = dim(color);
                }
                img.setColorArgb(px, py, color);
            }
        }
    }

    private static int dim(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int gray = (r + g + b) / 3;
        r = (int) (gray * 0.22f + 18);
        g = (int) (gray * 0.22f + 20);
        b = (int) (gray * 0.22f + 24);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void ensureTexture(int w, int h) {
        if (tex != null && texW == w && texH == h) return;
        if (tex != null) {
            try { tex.close(); } catch (Exception ignored) {}
        }
        tex = new NativeImageBackedTexture(() -> "relic_seedmap", w, h, false);
        texW = w; texH = h;
        uploadedResult = null;
        uploadedFilterVersion = -1;
    }

    public int textureGlId() {
        if (tex == null) return 0;
        try {
            return tex.getGlTexture() instanceof GlTexture gl ? gl.getGlId() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public double[] imageWindow() {
        Result r = current;
        if (r == null) return null;
        return new double[]{r.originX, r.originZ, r.bpp, r.w, r.h};
    }
}
