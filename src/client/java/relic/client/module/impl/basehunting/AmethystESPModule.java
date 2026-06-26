package relic.client.module.impl.basehunting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import relic.client.api.render.BoxRenderer;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ModeSetting;
import relic.client.module.setting.NumberSetting;
import relic.client.notification.NotificationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AmethystESPModule extends Module {

    private static AmethystESPModule instance;

    private static final float PR = 0.62f, PG = 0.20f, PB = 0.91f;
    private static final int  NOTIFY_ACCENT = 0xFF9E33E8;
    private static final long NOTIFY_COOLDOWN_MS = 4000L;

    private final BooleanSetting smallBud  = new BooleanSetting("Small Buds", true);
    private final BooleanSetting mediumBud = new BooleanSetting("Medium Buds", true);
    private final BooleanSetting largeBud  = new BooleanSetting("Large Buds", true);
    private final BooleanSetting cluster   = new BooleanSetting("Clusters", true);

    private final NumberSetting  minCount  = new NumberSetting("Min Count", 4, 1, 64, true);
    private final NumberSetting  minY      = new NumberSetting("Min Y", -64, -64, 320, true);
    private final ModeSetting    renderMode = new ModeSetting("Render", "Both", "Both", "Filled", "Outline");
    private final BooleanSetting chunkHighlight = new BooleanSetting("Chunk Highlight", true);
    private final BooleanSetting notify = new BooleanSetting("Notify", true);

    private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<BoxRenderer.ColoredBox>> chunks =
            new Long2ObjectOpenHashMap<>();

    private final Set<Long> notified = ConcurrentHashMap.newKeySet();
    private volatile long lastNotifyTime;

    private RegistryKey<World> lastDimension;

    public AmethystESPModule() {
        super("AmethystESP", "Flags amethyst buds from block-update packets", Category.BASE_HUNTING);
        addSettings(smallBud, mediumBud, largeBud, cluster, minCount, minY,
                renderMode, chunkHighlight, notify);
        instance = this;

        smallBud.onChanged(this::reevaluate);
        mediumBud.onChanged(this::reevaluate);
        largeBud.onChanged(this::reevaluate);
        cluster.onChanged(this::reevaluate);
        minY.onChanged(this::reevaluate);

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            synchronized (chunks) {
                chunks.remove(key);
            }
            notified.remove(key);
        });
    }

    public static AmethystESPModule getInstance() {
        return instance;
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
    }

    public void onBlockUpdate(BlockPos pos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        long posKey = pos.asLong();
        Block block = mc.world.getBlockState(pos).getBlock();
        boolean wanted = pos.getY() >= minY.getInt() && isEnabledAmethyst(block);

        boolean crossedThreshold = false;
        synchronized (chunks) {
            Long2ObjectOpenHashMap<BoxRenderer.ColoredBox> found = chunks.get(chunkKey);
            if (wanted) {
                if (found == null) {
                    found = new Long2ObjectOpenHashMap<>();
                    chunks.put(chunkKey, found);
                }
                boolean wasFlagged = found.size() >= minCount.getInt();
                found.put(posKey, new BoxRenderer.ColoredBox(
                        new Box(new BlockPos(pos)), PR, PG, PB, 1.0f));
                crossedThreshold = !wasFlagged && found.size() >= minCount.getInt();
            } else if (found != null) {

                found.remove(posKey);
                if (found.isEmpty()) chunks.remove(chunkKey);
            }
        }

        if (crossedThreshold && notify.isOn()) {
            maybeNotify(chunkKey, new ChunkPos(chunkKey));
        }
    }

    private boolean isEnabledAmethyst(Block block) {
        if (block == Blocks.SMALL_AMETHYST_BUD)  return smallBud.isOn();
        if (block == Blocks.MEDIUM_AMETHYST_BUD) return mediumBud.isOn();
        if (block == Blocks.LARGE_AMETHYST_BUD)  return largeBud.isOn();
        if (block == Blocks.AMETHYST_CLUSTER)    return cluster.isOn();
        return false;
    }

    private void reevaluate() {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        int floor = minY.getInt();
        synchronized (chunks) {
            var it = chunks.long2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                Long2ObjectOpenHashMap<BoxRenderer.ColoredBox> found = entry.getValue();
                found.keySet().removeIf(posKey -> {
                    BlockPos pos = BlockPos.fromLong(posKey);
                    Block block = mc.world.getBlockState(pos).getBlock();
                    return pos.getY() < floor || !isEnabledAmethyst(block);
                });
                if (found.isEmpty()) it.remove();
            }
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        boolean highlight = chunkHighlight.isOn();
        int threshold = minCount.getInt();

        List<BoxRenderer.ColoredBox> list = new ArrayList<>();
        synchronized (chunks) {
            for (var entry : chunks.long2ObjectEntrySet()) {
                Long2ObjectOpenHashMap<BoxRenderer.ColoredBox> found = entry.getValue();
                if (found.size() < threshold) continue;

                list.addAll(found.values());
                if (highlight) {

                    int loY = Integer.MAX_VALUE, hiY = Integer.MIN_VALUE;
                    for (BoxRenderer.ColoredBox b : found.values()) {
                        loY = Math.min(loY, (int) Math.floor(b.box().minY));
                        hiY = Math.max(hiY, (int) Math.ceil(b.box().maxY));
                    }
                    ChunkPos cp = new ChunkPos(entry.getLongKey());
                    Box column = new Box(cp.getStartX(), loY, cp.getStartZ(),
                            cp.getStartX() + 16, hiY, cp.getStartZ() + 16);
                    list.add(new BoxRenderer.ColoredBox(column, PR, PG, PB, 0.5f));
                }
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

    private void maybeNotify(long chunkKey, ChunkPos pos) {
        if (!notified.add(chunkKey)) return;
        long now = System.currentTimeMillis();
        if (now - lastNotifyTime < NOTIFY_COOLDOWN_MS) return;
        lastNotifyTime = now;

        String where = (pos.getStartX() + 8) + ", " + (pos.getStartZ() + 8);
        NotificationManager.getInstance().push("AmethystESP",
                "Amethyst buds near " + where, NOTIFY_ACCENT,
                NotificationManager.DEFAULT_HOLD_MS, true);
    }
}
