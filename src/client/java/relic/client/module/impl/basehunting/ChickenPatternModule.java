package relic.client.module.impl.basehunting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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

public class ChickenPatternModule extends Module {

    private static final float CR = 0.90f, CG = 0.78f, CB = 0.45f;

    private static final float ER = 0.98f, EG = 0.96f, EB = 0.86f;

    private static final int  CHICKEN_TEXT = argb(CR, CG, CB);
    private static final int  EGG_TEXT = argb(ER, EG, EB);
    private static final int  NOTIFY_ACCENT = 0xFFEFD27A;
    private static final long NOTIFY_COOLDOWN_MS = 4000L;

    private final BooleanSetting chickenBoxes = new BooleanSetting("Chicken Boxes", true);
    private final BooleanSetting eggBoxes = new BooleanSetting("Egg Boxes", true);

    private final NumberSetting  minChickens = new NumberSetting("Min Chickens", 3, 1, 64, true);

    private final NumberSetting  minEggs = new NumberSetting("Min Eggs", 2, 1, 128, true);

    private final BooleanSetting requireBoth = new BooleanSetting("Require Both", true);

    private final NumberSetting  chunkRadius = new NumberSetting("Chunk Radius", 0, 0, 8, true);
    private final ModeSetting    renderMode = new ModeSetting("Render", "Both", "Both", "Filled", "Outline");
    private final BooleanSetting chunkHighlight = new BooleanSetting("Chunk Highlight", true);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);
    private final BooleanSetting notify = new BooleanSetting("Notify", true);

    private final Set<Long> notified = ConcurrentHashMap.newKeySet();
    private final Object notifyLock = new Object();
    private volatile long lastNotifyTime;

    private RegistryKey<World> lastDimension;

    public ChickenPatternModule() {
        super("ChickenPatternESP", "Flags chunks where chickens and dropped eggs cluster", Category.BASE_HUNTING);
        addSettings(chickenBoxes, eggBoxes, minChickens, minEggs, requireBoth,
                chunkRadius, renderMode, chunkHighlight, labels, notify);
    }

    @Override
    protected void onDisable() {
        notified.clear();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (lastDimension != null && lastDimension != dimension) {
            notified.clear();
        }
        lastDimension = dimension;

        if (!notify.isOn()) return;

        LongOpenHashSet active = new LongOpenHashSet();
        for (FlaggedChunk f : snapshotFlagged(mc, 1.0f)) {
            active.add(f.key());
            maybeNotify(f.key());
        }
        notified.retainAll(active);
    }

    private record Counts(Long2IntOpenHashMap chickens, Long2IntOpenHashMap eggs) {}

    private Counts bucketCounts(MinecraftClient mc) {
        Long2IntOpenHashMap chickens = new Long2IntOpenHashMap();
        Long2IntOpenHashMap eggs = new Long2IntOpenHashMap();
        for (Entity entity : mc.world.getEntities()) {
            long key = ChunkPos.toLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
            if (entity instanceof ChickenEntity) {
                chickens.addTo(key, 1);
            } else if (entity instanceof ItemEntity item && item.getStack().isOf(Items.EGG)) {
                eggs.addTo(key, item.getStack().getCount());
            }
        }
        return new Counts(chickens, eggs);
    }

    private static int pooled(Long2IntOpenHashMap counts, long key, int radius) {
        if (radius <= 0) return counts.get(key);
        ChunkPos cp = new ChunkPos(key);
        int sum = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                sum += counts.get(ChunkPos.toLong(cp.x + dx, cp.z + dz));
            }
        }
        return sum;
    }

    private boolean flagged(int chickens, int eggs) {
        boolean chickenOk = chickens >= minChickens.getInt();
        boolean eggOk = eggs >= minEggs.getInt();
        return requireBoth.isOn() ? (chickenOk && eggOk) : (chickenOk || eggOk);
    }

    private record FlaggedChunk(long key, List<Box> chickens, List<Box> eggs,
                                int chickenCount, int eggCount, int loY, int hiY) {}

    private List<FlaggedChunk> snapshotFlagged(MinecraftClient mc, float tickDelta) {
        Counts counts = bucketCounts(mc);
        int radius = chunkRadius.getInt();

        LongOpenHashSet candidates = new LongOpenHashSet();
        candidates.addAll(counts.chickens().keySet());
        candidates.addAll(counts.eggs().keySet());

        Long2ObjectOpenHashMap<int[]> flaggedKeys = new Long2ObjectOpenHashMap<>();
        for (long key : candidates) {
            int chickens = pooled(counts.chickens(), key, radius);
            int eggs = pooled(counts.eggs(), key, radius);
            if (flagged(chickens, eggs)) flaggedKeys.put(key, new int[]{chickens, eggs});
        }
        if (flaggedKeys.isEmpty()) return List.of();

        Long2ObjectOpenHashMap<List<Box>> chickenBoxMap = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<List<Box>> eggBoxMap = new Long2ObjectOpenHashMap<>();
        for (Entity entity : mc.world.getEntities()) {
            long key = ChunkPos.toLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
            if (!flaggedKeys.containsKey(key)) continue;
            Long2ObjectOpenHashMap<List<Box>> target;
            if (entity instanceof ChickenEntity) {
                target = chickenBoxMap;
            } else if (entity instanceof ItemEntity item && item.getStack().isOf(Items.EGG)) {
                target = eggBoxMap;
            } else {
                continue;
            }
            Vec3d lerped = entity.getLerpedPos(tickDelta);
            Box box = entity.getBoundingBox().offset(lerped.subtract(entity.getEntityPos()));
            target.computeIfAbsent(key, k -> new ArrayList<>()).add(box);
        }

        List<FlaggedChunk> out = new ArrayList<>();
        for (var entry : flaggedKeys.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            List<Box> chickens = chickenBoxMap.getOrDefault(key, List.of());
            List<Box> eggs = eggBoxMap.getOrDefault(key, List.of());

            int loY = Integer.MAX_VALUE, hiY = Integer.MIN_VALUE;
            for (Box b : chickens) { loY = Math.min(loY, (int) Math.floor(b.minY)); hiY = Math.max(hiY, (int) Math.ceil(b.maxY)); }
            for (Box b : eggs)     { loY = Math.min(loY, (int) Math.floor(b.minY)); hiY = Math.max(hiY, (int) Math.ceil(b.maxY)); }

            out.add(new FlaggedChunk(key, chickens, eggs, entry.getValue()[0], entry.getValue()[1], loY, hiY));
        }
        return out;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        boolean wantChickens = chickenBoxes.isOn();
        boolean wantEggs = eggBoxes.isOn();
        boolean highlight = chunkHighlight.isOn();
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);

        int accent = ThemeManager.get().accent();
        float ar = ((accent >> 16) & 0xFF) / 255f;
        float ag = ((accent >> 8) & 0xFF) / 255f;
        float ab = (accent & 0xFF) / 255f;

        List<BoxRenderer.ColoredBox> list = new ArrayList<>();
        for (FlaggedChunk f : snapshotFlagged(mc, tickDelta)) {
            if (wantChickens) for (Box b : f.chickens()) list.add(new BoxRenderer.ColoredBox(b, CR, CG, CB, 1.0f));
            if (wantEggs) for (Box b : f.eggs()) list.add(new BoxRenderer.ColoredBox(b, ER, EG, EB, 1.0f));
            if (highlight && f.loY() <= f.hiY()) {
                ChunkPos cp = new ChunkPos(f.key());
                Box column = new Box(cp.getStartX(), f.loY(), cp.getStartZ(),
                        cp.getStartX() + 16, f.hiY(), cp.getStartZ() + 16);
                list.add(new BoxRenderer.ColoredBox(column, ar, ag, ab, 0.5f));
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
            if (f.chickenCount() > 0) { texts.add("Chickens x" + f.chickenCount()); colors.add(CHICKEN_TEXT); }
            if (f.eggCount() > 0) { texts.add("Eggs x" + f.eggCount()); colors.add(EGG_TEXT); }
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
        NotificationManager.getInstance().push("ChickenPatternESP",
                "Chicken cluster near " + where, NOTIFY_ACCENT,
                NotificationManager.DEFAULT_HOLD_MS, true);
    }

    private static int argb(float r, float g, float b) {
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
