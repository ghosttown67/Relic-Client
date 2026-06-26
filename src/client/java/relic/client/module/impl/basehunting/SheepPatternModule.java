package relic.client.module.impl.basehunting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import relic.client.api.render.BoxRenderer;
import relic.client.api.render.WorldToScreen;
import relic.client.gui.theme.ThemeManager;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ModeSetting;
import relic.client.module.setting.NumberSetting;
import relic.client.notification.NotificationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SheepPatternModule extends Module {

    private static final float DR = 0.55f, DG = 0.38f, DB = 0.22f;

    private static final float SR = 0.95f, SG = 0.95f, SB = 0.92f;

    private static final int  DIRT_TEXT  = argb(DR, DG, DB);
    private static final int  SHEEP_TEXT = 0xFFEFEFEF;
    private static final int  NOTIFY_ACCENT = 0xFF8FC04A;
    private static final long NOTIFY_COOLDOWN_MS = 4000L;

    private static final int  MAX_FOLIAGE = 3;

    private final BooleanSetting dirtBoxes  = new BooleanSetting("Eaten Dirt", true);
    private final BooleanSetting sheepBoxes = new BooleanSetting("Sheep Boxes", true);

    private final BooleanSetting foliageCompat = new BooleanSetting("Through Foliage", true);

    private final BooleanSetting adjacentGrass = new BooleanSetting("Adjacent Grass", true);

    private final NumberSetting  minSheep = new NumberSetting("Min Sheep", 1, 1, 32, true);

    private final NumberSetting  minEaten = new NumberSetting("Min Eaten Dirt", 3, 1, 128, true);

    private final BooleanSetting requireBoth = new BooleanSetting("Require Both", true);

    private final NumberSetting  chunkRadius = new NumberSetting("Chunk Radius", 8, 1, 32, true);
    private final ModeSetting    renderMode = new ModeSetting("Render", "Both", "Both", "Filled", "Outline");
    private final BooleanSetting chunkHighlight = new BooleanSetting("Chunk Highlight", true);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);
    private final BooleanSetting notify = new BooleanSetting("Notify", true);

    private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<BoxRenderer.ColoredBox>> chunks =
            new Long2ObjectOpenHashMap<>();

    private final Set<Long> notified = ConcurrentHashMap.newKeySet();
    private final Object notifyLock = new Object();
    private volatile long lastNotifyTime;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Relic-SheepPattern-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private RegistryKey<World> lastDimension;

    public SheepPatternModule() {
        super("SheepPatternESP", "Flags chunks where sheep have grazed grass into dirt", Category.BASE_HUNTING);
        addSettings(dirtBoxes, sheepBoxes, foliageCompat, adjacentGrass, minSheep, minEaten, requireBoth,
                chunkRadius, renderMode, chunkHighlight, labels, notify);

        foliageCompat.onChanged(this::fullRescan);
        adjacentGrass.onChanged(this::fullRescan);
        chunkRadius.onChanged(this::fullRescan);

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled()) scanChunkAsync(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            synchronized (chunks) {
                chunks.remove(key);
            }
            notified.remove(key);
        });
    }

    @Override
    protected void onEnable() {
        fullRescan();
    }

    @Override
    protected void onDisable() {
        synchronized (chunks) {
            chunks.clear();
        }
        notified.clear();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (lastDimension != null && lastDimension != dimension) {
            synchronized (chunks) {
                chunks.clear();
            }
            notified.clear();
        }
        lastDimension = dimension;

        if (!notify.isOn()) return;

        Long2IntOpenHashMap sheepCounts = new Long2IntOpenHashMap();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof SheepEntity) {
                sheepCounts.addTo(ChunkPos.toLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4), 1);
            }
        }

        LongOpenHashSet keys = new LongOpenHashSet();
        synchronized (chunks) {
            keys.addAll(chunks.keySet());
        }
        keys.addAll(sheepCounts.keySet());
        for (long key : keys) {
            int eaten;
            synchronized (chunks) {
                var found = chunks.get(key);
                eaten = found == null ? 0 : found.size();
            }
            if (flagged(sheepCounts.get(key), eaten)) {
                maybeNotify(key);
            }
        }
    }

    private boolean flagged(int sheep, int eaten) {
        boolean sheepOk = sheep >= minSheep.getInt();
        boolean dirtOk = eaten >= minEaten.getInt();
        return requireBoth.isOn() ? (sheepOk && dirtOk) : (sheepOk || dirtOk);
    }

    private record FlaggedChunk(long key, List<BoxRenderer.ColoredBox> dirt, List<Box> sheep,
                                int sheepCount, int eatenCount, int loY, int hiY) {}

    private List<FlaggedChunk> snapshotFlagged(MinecraftClient mc, float tickDelta) {

        Long2ObjectOpenHashMap<List<Box>> sheepMap = new Long2ObjectOpenHashMap<>();
        Long2IntOpenHashMap sheepCounts = new Long2IntOpenHashMap();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof SheepEntity)) continue;
            long key = ChunkPos.toLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
            sheepCounts.addTo(key, 1);
            Vec3d lerped = entity.getLerpedPos(tickDelta);
            Box box = entity.getBoundingBox().offset(lerped.subtract(entity.getEntityPos()));
            List<Box> list = sheepMap.get(key);
            if (list == null) { list = new ArrayList<>(); sheepMap.put(key, list); }
            list.add(box);
        }

        Long2ObjectOpenHashMap<List<BoxRenderer.ColoredBox>> dirtMap = new Long2ObjectOpenHashMap<>();
        synchronized (chunks) {
            for (var entry : chunks.long2ObjectEntrySet()) {
                dirtMap.put(entry.getLongKey(), new ArrayList<>(entry.getValue().values()));
            }
        }

        LongOpenHashSet keys = new LongOpenHashSet();
        keys.addAll(dirtMap.keySet());
        keys.addAll(sheepMap.keySet());

        List<FlaggedChunk> out = new ArrayList<>();
        for (long key : keys) {
            List<BoxRenderer.ColoredBox> dirt = dirtMap.get(key);
            List<Box> sheep = sheepMap.get(key);
            int eaten = dirt == null ? 0 : dirt.size();
            int sheepCount = sheepCounts.get(key);
            if (!flagged(sheepCount, eaten)) continue;

            int loY = Integer.MAX_VALUE, hiY = Integer.MIN_VALUE;
            if (dirt != null) for (BoxRenderer.ColoredBox b : dirt) {
                loY = Math.min(loY, (int) Math.floor(b.box().minY));
                hiY = Math.max(hiY, (int) Math.ceil(b.box().maxY));
            }
            if (sheep != null) for (Box b : sheep) {
                loY = Math.min(loY, (int) Math.floor(b.minY));
                hiY = Math.max(hiY, (int) Math.ceil(b.maxY));
            }
            out.add(new FlaggedChunk(key, dirt == null ? List.of() : dirt,
                    sheep == null ? List.of() : sheep, sheepCount, eaten, loY, hiY));
        }
        return out;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        boolean wantDirt = dirtBoxes.isOn();
        boolean wantSheep = sheepBoxes.isOn();
        boolean highlight = chunkHighlight.isOn();
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);

        int accent = ThemeManager.get().accent();
        float cr = ((accent >> 16) & 0xFF) / 255f;
        float cg = ((accent >> 8) & 0xFF) / 255f;
        float cb = (accent & 0xFF) / 255f;

        List<BoxRenderer.ColoredBox> list = new ArrayList<>();
        for (FlaggedChunk f : snapshotFlagged(mc, tickDelta)) {
            if (wantDirt) list.addAll(f.dirt());
            if (wantSheep) for (Box b : f.sheep()) list.add(new BoxRenderer.ColoredBox(b, SR, SG, SB, 1.0f));
            if (highlight && f.loY() <= f.hiY()) {
                ChunkPos cp = new ChunkPos(f.key());
                Box column = new Box(cp.getStartX(), f.loY(), cp.getStartZ(),
                        cp.getStartX() + 16, f.hiY(), cp.getStartZ() + 16);
                list.add(new BoxRenderer.ColoredBox(column, cr, cg, cb, 0.5f));
            }
        }
        BoxRenderer.draw(list, resolveMode());
    }

    private BoxRenderer.Mode resolveMode() {
        return switch (renderMode.getValue()) {
            case "Filled"  -> BoxRenderer.Mode.FILLED;
            case "Outline" -> BoxRenderer.Mode.OUTLINED;
            default        -> BoxRenderer.Mode.BOTH;
        };
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (!labels.isOn()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        List<FlaggedChunk> flagged = snapshotFlagged(mc, mc.getRenderTickCounter().getTickProgress(false));
        if (flagged.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int line = tr.fontHeight + 1;
        float scale = 0.75f;
        final double maxLabelDistSq = 128.0 * 128.0;
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        for (FlaggedChunk f : flagged) {
            ChunkPos cp = new ChunkPos(f.key());
            double centerX = cp.getStartX() + 8.0;
            double centerZ = cp.getStartZ() + 8.0;
            double dx = centerX - px;
            double dz = centerZ - pz;
            if (dx * dx + dz * dz > maxLabelDistSq) continue;

            double anchorY = (f.hiY() >= f.loY() ? f.hiY() : 0) + 1.5;
            float[] screen = WorldToScreen.project(new Vec3d(centerX, anchorY, centerZ));
            if (screen == null) continue;

            List<String> texts = new ArrayList<>(2);
            List<Integer> colors = new ArrayList<>(2);
            if (f.sheepCount() > 0) { texts.add("Sheep x" + f.sheepCount()); colors.add(SHEEP_TEXT); }
            if (f.eatenCount() > 0) { texts.add("Eaten x" + f.eatenCount()); colors.add(DIRT_TEXT); }
            if (texts.isEmpty()) continue;

            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.scale(scale, scale);
            float cx = screen[0] / scale;
            float y = screen[1] / scale - texts.size() * line;
            for (int i = 0; i < texts.size(); i++) {
                drawCentered(context, tr, texts.get(i), cx, y, colors.get(i));
                y += line;
            }
            matrices.popMatrix();
        }
    }

    private void drawCentered(DrawContext context, TextRenderer tr, String text, float centerX, float y, int color) {
        int w = tr.getWidth(text);
        int x = (int) (centerX - w / 2.0f);
        int iy = (int) y;
        context.fill(x - 2, iy - 1, x + w + 2, iy + tr.fontHeight, 0x90000000);
        context.drawText(tr, text, x, iy, color, true);
    }

    private void fullRescan() {
        if (!isEnabled()) return;
        synchronized (chunks) {
            chunks.clear();
        }
        notified.clear();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        int radius = chunkRadius.getInt();
        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = mc.world.getChunkManager()
                        .getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk instanceof WorldChunk worldChunk) {
                    scanChunkAsync(worldChunk);
                }
            }
        }
    }

    private void scanChunkAsync(WorldChunk chunk) {
        boolean compat = foliageCompat.isOn();
        boolean needGrass = adjacentGrass.isOn();

        WORKER.submit(() -> {
            if (!isEnabled()) return;

            World world = needGrass ? MinecraftClient.getInstance().world : null;

            ChunkPos pos = chunk.getPos();
            int startX = pos.getStartX();
            int startZ = pos.getStartZ();
            int bottom = chunk.getBottomY();

            Heightmap heightmap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE);
            Long2ObjectOpenHashMap<BoxRenderer.ColoredBox> found = new Long2ObjectOpenHashMap<>();
            BlockPos.Mutable m = new BlockPos.Mutable();
            BlockPos.Mutable probe = new BlockPos.Mutable();

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int wx = startX + lx;
                    int wz = startZ + lz;
                    int y = heightmap.get(lx, lz) - 1;
                    if (y < bottom) continue;

                    if (compat) {
                        int guard = 0;
                        while (guard++ < MAX_FOLIAGE && y > bottom
                                && isFoliageCover(chunk.getBlockState(m.set(wx, y, wz)))) {
                            y--;
                        }
                    }

                    if (!chunk.getBlockState(m.set(wx, y, wz)).isOf(Blocks.DIRT)) continue;

                    if (needGrass && world != null && !hasAdjacentGrass(world, probe, wx, y, wz)) continue;

                    found.put(BlockPos.asLong(wx, y, wz), new BoxRenderer.ColoredBox(
                            new Box(new BlockPos(wx, y, wz)), DR, DG, DB, 1.0f));
                }
            }

            long key = pos.toLong();
            synchronized (chunks) {
                if (found.isEmpty()) chunks.remove(key);
                else chunks.put(key, found);
            }
        });
    }

    private static boolean hasAdjacentGrass(World world, BlockPos.Mutable p, int x, int y, int z) {
        int[][] horiz = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : horiz) {
            for (int dy = -1; dy <= 1; dy++) {
                if (world.getBlockState(p.set(x + d[0], y + dy, z + d[1])).isOf(Blocks.GRASS_BLOCK)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFoliageCover(BlockState s) {
        return s.isOf(Blocks.SHORT_GRASS) || s.isOf(Blocks.TALL_GRASS)
                || s.isOf(Blocks.FERN) || s.isOf(Blocks.LARGE_FERN)
                || s.isOf(Blocks.SNOW);
    }

    private void maybeNotify(long chunkKey) {
        synchronized (notifyLock) {
            if (!notified.add(chunkKey)) return;
            long now = System.currentTimeMillis();
            if (now - lastNotifyTime < NOTIFY_COOLDOWN_MS) {
                notified.remove(chunkKey);
                return;
            }
            lastNotifyTime = now;
        }
        ChunkPos pos = new ChunkPos(chunkKey);
        String where = (pos.getStartX() + 8) + ", " + (pos.getStartZ() + 8);
        NotificationManager.getInstance().push("SheepPatternESP",
                "Grazing pattern near " + where, NOTIFY_ACCENT,
                NotificationManager.DEFAULT_HOLD_MS, true);
    }

    private static int argb(float r, float g, float b) {
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
