package relic.client.module.impl.basehunting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import relic.client.api.discord.DiscordWebhook;
import relic.client.module.Module;
import relic.client.module.setting.BlockListSetting;
import relic.client.module.setting.BooleanSetting;
import relic.client.notification.NotificationManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockAlertModule extends Module {

    private static BlockAlertModule instance;

    private static final int ALERT_ACCENT = 0xFFFFB020;
    private static final long ALERT_COOLDOWN_MS = 4000L;

    private final BlockListSetting blocks = new BlockListSetting("Blocks",
            "netherite_block", "gilded_blackstone", "ancient_debris", "wet_sponge", "sponge");
    private final BooleanSetting sound = new BooleanSetting("Sound", true);
    private final BooleanSetting webhook = new BooleanSetting("Discord Webhook", false);

    private final Long2ObjectOpenHashMap<LongOpenHashSet> alerted = new Long2ObjectOpenHashMap<>();

    private volatile Set<Block> targets = Set.of();

    private final Map<Block, Long> lastAlert = new ConcurrentHashMap<>();

    private RegistryKey<World> lastDimension;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Relic-BlockAlert-Worker");
        thread.setDaemon(true);
        return thread;
    });

    public BlockAlertModule() {
        super("BlockAlert", "Notifies when selected blocks are detected nearby", Category.BASE_HUNTING);
        addSettings(blocks, sound, webhook);
        instance = this;

        blocks.onChanged(this::fullRescan);

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled()) scanChunkAsync(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            synchronized (alerted) {
                alerted.remove(chunk.getPos().toLong());
            }
        });
    }

    public static BlockAlertModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        fullRescan();
    }

    @Override
    protected void onDisable() {
        synchronized (alerted) {
            alerted.clear();
        }
        lastAlert.clear();
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

    public void onBlockUpdate(BlockPos pos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        Block block = mc.world.getBlockState(pos).getBlock();
        boolean isTarget = targets.contains(block);
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        long posKey = pos.asLong();

        boolean newlyFound = false;
        synchronized (alerted) {
            LongOpenHashSet set = alerted.get(chunkKey);
            if (isTarget) {
                if (set == null) {
                    set = new LongOpenHashSet();
                    alerted.put(chunkKey, set);
                }
                newlyFound = set.add(posKey);
            } else if (set != null) {
                set.remove(posKey);
            }
        }

        if (newlyFound) alert(block, 1, pos);
    }

    private void fullRescan() {
        if (!isEnabled()) return;

        resolveTargets();
        synchronized (alerted) {
            alerted.clear();
        }
        lastAlert.clear();

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
        Set<Block> resolved = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Block block : Registries.BLOCK) {
            if (blocks.isSelected(Registries.BLOCK.getId(block).getPath())) {
                resolved.add(block);
            }
        }
        targets = resolved;
    }

    private void scanChunkAsync(WorldChunk chunk) {
        WORKER.submit(() -> {
            if (!isEnabled()) return;
            Set<Block> targets = this.targets;
            if (targets.isEmpty()) return;

            ChunkPos chunkPos = chunk.getPos();
            long chunkKey = chunkPos.toLong();
            int startX = chunkPos.getStartX();
            int startZ = chunkPos.getStartZ();

            LongOpenHashSet found = new LongOpenHashSet();

            Map<Block, Integer> newCounts = new HashMap<>();

            Map<Block, Long> newPositions = new HashMap<>();

            LongOpenHashSet previous;
            synchronized (alerted) {
                previous = alerted.get(chunkKey);
            }

            ChunkSection[] sections = chunk.getSectionArray();
            for (int si = 0; si < sections.length; si++) {
                ChunkSection section = sections[si];
                if (section == null || section.isEmpty()) continue;
                int baseY = chunk.getBottomY() + (si << 4);

                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            Block block = section.getBlockState(x, y, z).getBlock();
                            if (!targets.contains(block)) continue;
                            long posKey = BlockPos.asLong(startX + x, baseY + y, startZ + z);
                            found.add(posKey);
                            if (previous == null || !previous.contains(posKey)) {
                                newCounts.merge(block, 1, Integer::sum);
                                newPositions.putIfAbsent(block, posKey);
                            }
                        }
                    }
                }
            }

            synchronized (alerted) {
                if (found.isEmpty()) {
                    alerted.remove(chunkKey);
                } else {
                    alerted.put(chunkKey, found);
                }
            }

            newCounts.forEach((block, count) ->
                    alert(block, count, BlockPos.fromLong(newPositions.get(block))));
        });
    }

    private void alert(Block block, int count, BlockPos pos) {
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(block);
        if (last != null && now - last < ALERT_COOLDOWN_MS) return;
        lastAlert.put(block, now);

        String name = displayName(block);
        String message = count > 1 ? name + " x" + count : name;
        NotificationManager.getInstance().push("Block Alert", message, ALERT_ACCENT,
                NotificationManager.DEFAULT_HOLD_MS, sound.isOn());

        if (webhook.isOn()) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("Coordinates", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            fields.put("Dimension", dimensionName());
            fields.put("Server", serverAddress());
            DiscordWebhook.send("Block Alert", message, ALERT_ACCENT, fields);
        }
    }

    private String dimensionName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "Unknown";
        Identifier id = mc.world.getRegistryKey().getValue();
        String path = id.getPath();
        return switch (path) {
            case "overworld"  -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end"    -> "End";
            default -> prettify(path);
        };
    }

    private String serverAddress() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo entry = mc.getCurrentServerEntry();
        if (entry != null && entry.address != null && !entry.address.isBlank()) {
            return entry.address;
        }
        if (mc.isInSingleplayer()) return "Singleplayer";
        return "Unknown";
    }

    private String displayName(Block block) {
        try {
            return block.getName().getString();
        } catch (Exception e) {

            return prettify(Registries.BLOCK.getId(block).getPath());
        }
    }

    private static String prettify(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
