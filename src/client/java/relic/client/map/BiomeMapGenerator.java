package relic.client.map;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;
import relic.client.worldgen.BuiltinLookup;

public final class BiomeMapGenerator {

    private final long seed;
    private final BiomeSource biomeSource;
    private final MultiNoiseUtil.MultiNoiseSampler sampler;

    public BiomeMapGenerator(long seed) {
        this.seed = seed;
        RegistryWrapper.WrapperLookup lookup = BuiltinLookup.get();

        ChunkGeneratorSettings settings = lookup
                .getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
                .getOrThrow(ChunkGeneratorSettings.OVERWORLD)
                .value();
        NoiseConfig noiseConfig = NoiseConfig.create(
                settings, lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        this.sampler = noiseConfig.getMultiNoiseSampler();

        DimensionOptions overworld = lookup
                .getOrThrow(RegistryKeys.WORLD_PRESET)
                .getOrThrow(WorldPresets.DEFAULT)
                .value()
                .createDimensionsRegistryHolder()
                .dimensions()
                .get(DimensionOptions.OVERWORLD);
        this.biomeSource = overworld.chunkGenerator().getBiomeSource();
    }

    public long seed() {
        return seed;
    }

    public java.util.Set<RegistryEntry<Biome>> possibleBiomes() {
        return biomeSource.getBiomes();
    }

    public RegistryEntry<Biome> biomeAt(int blockX, int blockY, int blockZ) {
        return biomeSource.getBiome(
                BiomeCoords.fromBlock(blockX),
                BiomeCoords.fromBlock(blockY),
                BiomeCoords.fromBlock(blockZ),
                sampler);
    }

    public RegistryEntry<Biome> biomeAtCell(int cellX, int cellY, int cellZ) {
        return biomeSource.getBiome(cellX, cellY, cellZ, sampler);
    }
}
