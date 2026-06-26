package relic.client.module.impl.visual;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import relic.client.api.render.BoxRenderer;
import relic.client.module.Module;
import relic.client.module.setting.BlockListSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockESPModule extends Module {

    private static BlockESPModule instance;

    private final BlockListSetting blocks = new BlockListSetting("Blocks",
            "diamond_ore", "deepslate_diamond_ore", "ancient_debris");

    private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<BoxRenderer.ColoredBox>> chunks =
            new Long2ObjectOpenHashMap<>();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Relic-BlockESP-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Map<Block, float[]> targets = Map.of();

    private RegistryKey<World> lastDimension;

    public BlockESPModule() {
        super("BlockESP", "Highlights selected blocks in the world", Category.VISUAL);
        addSettings(blocks);
        instance = this;

        blocks.onChanged(this::fullRescan);

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled()) scanChunkAsync(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            synchronized (chunks) {
                chunks.remove(chunk.getPos().toLong());
            }
        });
    }

    public static BlockESPModule getInstance() {
        return instance;
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
    }

    @Override
    public void onTick() {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (lastDimension != null && lastDimension != dimension) {
            fullRescan();
        }
        lastDimension = dimension;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        List<BoxRenderer.ColoredBox> list = new ArrayList<>();
        synchronized (chunks) {
            for (Long2ObjectOpenHashMap<BoxRenderer.ColoredBox> chunk : chunks.values()) {
                list.addAll(chunk.values());
            }
        }
        BoxRenderer.draw(list, BoxRenderer.Mode.BOTH);
    }

    public void onBlockUpdate(BlockPos pos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        float[] color = targets.get(mc.world.getBlockState(pos).getBlock());
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        long posKey = pos.asLong();

        synchronized (chunks) {
            Long2ObjectOpenHashMap<BoxRenderer.ColoredBox> chunk = chunks.get(chunkKey);
            if (color != null) {
                if (chunk == null) {
                    chunk = new Long2ObjectOpenHashMap<>();
                    chunks.put(chunkKey, chunk);
                }
                chunk.put(posKey, new BoxRenderer.ColoredBox(new Box(pos), color[0], color[1], color[2], 1.0f));
            } else if (chunk != null) {
                chunk.remove(posKey);
            }
        }
    }

    private void fullRescan() {
        if (!isEnabled()) return;

        resolveTargets();
        synchronized (chunks) {
            chunks.clear();
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        int viewDistance = mc.options.getViewDistance().getValue();
        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                Chunk chunk = mc.world.getChunkManager()
                        .getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk instanceof WorldChunk worldChunk) {
                    scanChunkAsync(worldChunk);
                }
            }
        }
    }

    private void resolveTargets() {
        Map<Block, float[]> resolved = new HashMap<>();
        for (Block block : Registries.BLOCK) {
            String path = Registries.BLOCK.getId(block).getPath();
            if (blocks.isSelected(path)) {
                resolved.put(block, colorFor(path));
            }
        }
        targets = resolved;
    }

    private void scanChunkAsync(WorldChunk chunk) {
        WORKER.submit(() -> {
            if (!isEnabled()) return;
            Map<Block, float[]> targets = this.targets;
            if (targets.isEmpty()) return;

            ChunkPos chunkPos = chunk.getPos();
            int startX = chunkPos.getStartX();
            int startZ = chunkPos.getStartZ();

            Long2ObjectOpenHashMap<BoxRenderer.ColoredBox> found = new Long2ObjectOpenHashMap<>();
            ChunkSection[] sections = chunk.getSectionArray();

            for (int si = 0; si < sections.length; si++) {
                ChunkSection section = sections[si];

                if (section == null || section.isEmpty()) continue;
                int baseY = chunk.getBottomY() + (si << 4);

                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            float[] color = targets.get(section.getBlockState(x, y, z).getBlock());
                            if (color == null) continue;
                            BlockPos pos = new BlockPos(startX + x, baseY + y, startZ + z);
                            found.put(pos.asLong(), new BoxRenderer.ColoredBox(
                                    new Box(pos), color[0], color[1], color[2], 1.0f));
                        }
                    }
                }
            }

            synchronized (chunks) {
                if (found.isEmpty()) {
                    chunks.remove(chunkPos.toLong());
                } else {
                    chunks.put(chunkPos.toLong(), found);
                }
            }
        });
    }

    private float[] colorFor(String path) {
        float hue = (path.hashCode() & 0xFFFF) / (float) 0xFFFF;
        return hsvToRgb(hue, 0.75f, 1.0f);
    }

    private float[] hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6);
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        return switch (i) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
    }
}
