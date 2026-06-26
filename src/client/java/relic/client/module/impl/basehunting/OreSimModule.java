package relic.client.module.impl.basehunting;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import relic.client.api.render.BoxRenderer;
import relic.client.locator.BedrockLocator;
import relic.client.module.Module;
import relic.client.module.setting.ModeSetting;
import relic.client.module.setting.NumberSetting;
import relic.client.module.setting.StringSetting;
import relic.client.notification.NotificationManager;
import relic.client.oresim.Ore;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OreSimModule extends Module {

    private static OreSimModule instance;

    private enum AirCheck { RECHECK, ON_LOAD, OFF }

    private final StringSetting seed = new StringSetting("Seed", "");
    private final NumberSetting chunkRange = new NumberSetting("Chunk Range", 5, 1, 16, true);
    private final ModeSetting airCheck = new ModeSetting("Air Check", "Recheck", "Recheck", "On Load", "Off");
    private final NumberSetting lineWidth = new NumberSetting("Line Width", 1.5f, 0.5f, 5f);

    private final Map<Long, Map<Ore, Set<Vec3d>>> chunkRenderers = new ConcurrentHashMap<>();

    private long worldSeed;
    private boolean hasSeed;
    private Map<RegistryKey<Biome>, List<Ore>> oreConfig;
    private RegistryKey<World> lastDimension;

    private volatile boolean needsReload;

    public OreSimModule() {
        super("OreSim", "Simulates ore generation from a known seed (X-ray on crack)", Category.BASE_HUNTING);
        instance = this;

        addSettings(seed, chunkRange, airCheck);
        addSettings(Ore.oreSettings.toArray(new relic.client.module.setting.Setting[0]));
        addSettings(lineWidth);

        seed.onChanged(() -> needsReload = true);
        airCheck.onChanged(() -> needsReload = true);

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled() && oreConfig != null) doMathOnChunk(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
                chunkRenderers.remove(chunk.getPos().toLong()));
    }

    public static OreSimModule getInstance() {
        return instance;
    }

    private AirCheck airCheckMode() {
        return switch (airCheck.getValue()) {
            case "On Load" -> AirCheck.ON_LOAD;
            case "Off"     -> AirCheck.OFF;
            default        -> AirCheck.RECHECK;
        };
    }

    @Override
    protected void onEnable() {
        if (BedrockLocator.parseSeed(seed.getValue()) == null) {
            NotificationManager.getInstance().push("OreSim",
                    "Set a world seed in the Seed setting.", 0xFFE0533D,
                    NotificationManager.DEFAULT_HOLD_MS, true);

            return;
        }

        needsReload = true;
    }

    @Override
    protected void onDisable() {
        chunkRenderers.clear();
        oreConfig = null;
    }

    @Override
    public void onTick() {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (!needsReload && dimension == lastDimension) return;

        needsReload = false;
        lastDimension = dimension;
        try {
            reload();
        } catch (Exception e) {

            oreConfig = null;
            chunkRenderers.clear();
            NotificationManager.getInstance().push("OreSim",
                    "Couldn't simulate ores for this seed/world.", 0xFFE0533D,
                    NotificationManager.DEFAULT_HOLD_MS, true);
            System.err.println("[Relic Client] OreSim reload failed:");
            e.printStackTrace();
        }
    }

    private void reload() {
        if (!isEnabled()) return;

        chunkRenderers.clear();
        oreConfig = null;

        Long parsed = BedrockLocator.parseSeed(seed.getValue());
        MinecraftClient mc = MinecraftClient.getInstance();
        if (parsed == null || mc.world == null) {
            hasSeed = parsed != null;
            return;
        }
        worldSeed = parsed;
        hasSeed = true;
        lastDimension = mc.world.getRegistryKey();

        oreConfig = Ore.getRegistry(mc.world.getRegistryKey());

        for (Chunk chunk : loadedChunks(mc)) {
            doMathOnChunk(chunk);
        }
    }

    private List<Chunk> loadedChunks(MinecraftClient mc) {
        List<Chunk> chunks = new ArrayList<>();
        if (mc.player == null || mc.world == null) return chunks;
        int viewDistance = mc.options.getViewDistance().getValue();
        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                Chunk chunk = mc.world.getChunkManager()
                        .getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk instanceof WorldChunk) chunks.add(chunk);
            }
        }
        return chunks;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (!hasSeed || oreConfig == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        ChunkPos playerChunk = mc.player.getChunkPos();
        int range = chunkRange.getInt();

        List<BoxRenderer.ColoredBox> boxes = new ArrayList<>();
        for (var entry : chunkRenderers.entrySet()) {
            ChunkPos cp = new ChunkPos(entry.getKey());
            if (Math.abs(cp.x - playerChunk.x) > range || Math.abs(cp.z - playerChunk.z) > range) continue;

            for (Map.Entry<Ore, Set<Vec3d>> oreRenders : entry.getValue().entrySet()) {
                Ore ore = oreRenders.getKey();
                if (!ore.active.isOn()) continue;
                for (Vec3d pos : oreRenders.getValue()) {
                    boxes.add(new BoxRenderer.ColoredBox(
                            new Box(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1),
                            ore.red, ore.green, ore.blue, 1.0f));
                }
            }
        }
        BoxRenderer.draw(boxes, BoxRenderer.Mode.OUTLINED, lineWidth.getValue(), 0f);
    }

    public void onBlockUpdate(BlockPos pos) {
        if (!isEnabled() || airCheckMode() != AirCheck.RECHECK) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.world.getBlockState(pos).isOpaque()) return;

        Map<Ore, Set<Vec3d>> chunk = chunkRenderers.get(ChunkPos.toLong(pos));
        if (chunk == null) return;
        Vec3d v = Vec3d.of(pos);
        for (Set<Vec3d> positions : chunk.values()) positions.remove(v);
    }

    private void doMathOnChunk(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (oreConfig == null || world == null || chunkRenderers.containsKey(chunkKey)) return;

        Set<RegistryKey<Biome>> biomes = new HashSet<>();
        ChunkPos.stream(chunkPos, 1).forEach(cp -> {
            Chunk c = world.getChunk(cp.x, cp.z, ChunkStatus.BIOMES, false);
            if (c == null) return;
            for (ChunkSection section : c.getSectionArray()) {
                section.getBiomeContainer().forEachValue(entry -> entry.getKey().ifPresent(biomes::add));
            }
        });

        Set<Ore> oreSet = new HashSet<>();
        for (RegistryKey<Biome> biome : biomes) oreSet.addAll(getDefaultOres(biome));

        int chunkX = chunkPos.x << 4;
        int chunkZ = chunkPos.z << 4;
        ChunkRandom random = new ChunkRandom(ChunkRandom.RandomProvider.XOROSHIRO.create(0));
        long populationSeed = random.setPopulationSeed(worldSeed, chunkX, chunkZ);

        Map<Ore, Set<Vec3d>> result = new HashMap<>();
        AirCheck mode = airCheckMode();

        for (Ore ore : oreSet) {
            Set<Vec3d> ores = new HashSet<>();
            random.setDecoratorSeed(populationSeed, ore.index, ore.step);

            int repeat = ore.count.get(random);
            for (int i = 0; i < repeat; i++) {
                if (ore.rarity != 1F && random.nextFloat() >= 1 / ore.rarity) continue;

                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                int y = ore.heightProvider.get(random, ore.heightContext);
                BlockPos origin = new BlockPos(x, y, z);

                RegistryKey<Biome> biome = chunk.getBiomeForNoiseGen(x, y, z).getKey().orElse(null);
                if (biome == null || !getDefaultOres(biome).contains(ore)) continue;

                if (ore.scattered) {
                    ores.addAll(generateHidden(world, random, origin, ore.size, mode));
                } else {
                    ores.addAll(generateNormal(world, random, origin, ore.size, ore.discardOnAirChance, mode));
                }
            }
            if (!ores.isEmpty()) result.put(ore, ores);
        }
        chunkRenderers.put(chunkKey, result);
    }

    private List<Ore> getDefaultOres(RegistryKey<Biome> biome) {
        List<Ore> ores = oreConfig.get(biome);
        if (ores != null) return ores;

        for (List<Ore> any : oreConfig.values()) return any;
        return List.of();
    }

    private List<Vec3d> generateNormal(ClientWorld world, ChunkRandom random, BlockPos blockPos,
                                        int veinSize, float discardOnAir, AirCheck mode) {
        float f = random.nextFloat() * (float) Math.PI;
        float g = (float) veinSize / 8.0F;
        int i = MathHelper.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = blockPos.getX() + Math.sin(f) * g;
        double e = blockPos.getX() - Math.sin(f) * g;
        double h = blockPos.getZ() + Math.cos(f) * g;
        double j = blockPos.getZ() - Math.cos(f) * g;
        double l = blockPos.getY() + random.nextInt(3) - 2;
        double m = blockPos.getY() + random.nextInt(3) - 2;
        int n = blockPos.getX() - MathHelper.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - MathHelper.ceil(g) - i;
        int q = 2 * (MathHelper.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= world.getTopY(Heightmap.Type.MOTION_BLOCKING, s, t)) {
                    return generateVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r, discardOnAir, mode);
                }
            }
        }
        return new ArrayList<>();
    }

    private List<Vec3d> generateVeinPart(ClientWorld world, ChunkRandom random, int veinSize,
                                         double startX, double endX, double startZ, double endZ,
                                         double startY, double endY, int x, int y, int z, int size, int i,
                                         float discardOnAir, AirCheck mode) {
        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double[] ds = new double[veinSize * 4];
        List<Vec3d> poses = new ArrayList<>();

        int n;
        double p, q, r, s;
        for (n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            p = MathHelper.lerp(f, startX, endX);
            q = MathHelper.lerp(f, startY, endY);
            r = MathHelper.lerp(f, startZ, endZ);
            s = random.nextDouble() * veinSize / 16.0D;
            double m = ((MathHelper.sin((float) Math.PI * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p;
            ds[n * 4 + 1] = q;
            ds[n * 4 + 2] = r;
            ds[n * 4 + 3] = m;
        }

        for (n = 0; n < veinSize - 1; ++n) {
            if (ds[n * 4 + 3] <= 0.0D) continue;
            for (int o = n + 1; o < veinSize; ++o) {
                if (ds[o * 4 + 3] <= 0.0D) continue;
                p = ds[n * 4] - ds[o * 4];
                q = ds[n * 4 + 1] - ds[o * 4 + 1];
                r = ds[n * 4 + 2] - ds[o * 4 + 2];
                s = ds[n * 4 + 3] - ds[o * 4 + 3];
                if (s * s > p * p + q * q + r * r) {
                    if (s > 0.0D) ds[o * 4 + 3] = -1.0D;
                    else ds[n * 4 + 3] = -1.0D;
                }
            }
        }

        for (n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (u < 0.0D) continue;
            double v = ds[n * 4];
            double w = ds[n * 4 + 1];
            double aa = ds[n * 4 + 2];
            int ab = Math.max(MathHelper.floor(v - u), x);
            int ac = Math.max(MathHelper.floor(w - u), y);
            int ad = Math.max(MathHelper.floor(aa - u), z);
            int ae = Math.max(MathHelper.floor(v + u), ab);
            int af = Math.max(MathHelper.floor(w + u), ac);
            int ag = Math.max(MathHelper.floor(aa + u), ad);

            for (int ah = ab; ah <= ae; ++ah) {
                double ai = (ah + 0.5D - v) / u;
                if (ai * ai >= 1.0D) continue;
                for (int aj = ac; aj <= af; ++aj) {
                    double ak = (aj + 0.5D - w) / u;
                    if (ai * ai + ak * ak >= 1.0D) continue;
                    for (int al = ad; al <= ag; ++al) {
                        double am = (al + 0.5D - aa) / u;
                        if (ai * ai + ak * ak + am * am >= 1.0D) continue;
                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                        if (bitSet.get(an)) continue;
                        bitSet.set(an);
                        mutable.set(ah, aj, al);
                        if (aj >= -64 && aj < 320
                                && (mode == AirCheck.OFF || world.getBlockState(mutable).isOpaque())
                                && shouldPlace(world, mutable, discardOnAir, random)) {
                            poses.add(new Vec3d(ah, aj, al));
                        }
                    }
                }
            }
        }
        return poses;
    }

    private boolean shouldPlace(ClientWorld world, BlockPos orePos, float discardOnAir, ChunkRandom random) {
        if (discardOnAir == 0F || (discardOnAir != 1F && random.nextFloat() >= discardOnAir)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (!world.getBlockState(orePos.add(direction.getVector())).isOpaque() && discardOnAir != 1F) {
                return false;
            }
        }
        return true;
    }

    private List<Vec3d> generateHidden(ClientWorld world, ChunkRandom random, BlockPos blockPos, int size, AirCheck mode) {
        List<Vec3d> poses = new ArrayList<>();
        int i = random.nextInt(size + 1);
        for (int j = 0; j < i; ++j) {
            size = Math.min(j, 7);
            int x = randomCoord(random, size) + blockPos.getX();
            int y = randomCoord(random, size) + blockPos.getY();
            int z = randomCoord(random, size) + blockPos.getZ();
            BlockPos pos = new BlockPos(x, y, z);
            if ((mode == AirCheck.OFF || world.getBlockState(pos).isOpaque())
                    && shouldPlace(world, pos, 1F, random)) {
                poses.add(new Vec3d(x, y, z));
            }
        }
        return poses;
    }

    private int randomCoord(ChunkRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) size);
    }
}
