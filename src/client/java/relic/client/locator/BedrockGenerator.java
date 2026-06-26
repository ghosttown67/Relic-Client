package relic.client.locator;

import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.RandomSplitter;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;

public final class BedrockGenerator {

    private static volatile RegistryWrapper.WrapperLookup builtin;

    private final long seed;
    private final RandomSplitter bedrockSplitter;

    private final int below;
    private final int above;

    public BedrockGenerator(long seed) {
        this.seed = seed;
        RegistryWrapper.WrapperLookup lookup = builtin();

        var noiseParams = lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS);
        var settingsEntry = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
                .getOrThrow(ChunkGeneratorSettings.OVERWORLD);
        ChunkGeneratorSettings settings = settingsEntry.value();

        NoiseConfig noiseConfig = NoiseConfig.create(settings, noiseParams, seed);
        this.bedrockSplitter = noiseConfig.getOrCreateRandomDeriver(Identifier.of("bedrock_floor"));

        int bottomY = settings.generationShapeConfig().minimumY();
        this.below = bottomY;
        this.above = bottomY + 5;
    }

    private static RegistryWrapper.WrapperLookup builtin() {
        RegistryWrapper.WrapperLookup l = builtin;
        if (l == null) {
            synchronized (BedrockGenerator.class) {
                l = builtin;
                if (l == null) {
                    l = BuiltinRegistries.createWrapperLookup();
                    builtin = l;
                }
            }
        }
        return l;
    }

    public long getSeed() {
        return seed;
    }

    public int getMinPatternY() {
        return below + 1;
    }

    public int getMaxPatternY() {
        return above - 1;
    }

    public float chance(int y) {
        if (y <= below) return 1.0f;
        if (y >= above) return 0.0f;
        return (float) MathHelper.map(y, below, above, 1.0, 0.0);
    }

    public boolean isBedrock(int x, int y, int z) {
        if (y <= below) return true;
        if (y >= above) return false;
        double chance = MathHelper.map(y, below, above, 1.0, 0.0);
        return bedrockSplitter.split(x, y, z).nextFloat() < chance;
    }
}
