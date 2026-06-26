package relic.client.oresim;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.ScatteredOreFeature;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;
import relic.client.client.mixin.CountPlacementModifierAccessor;
import relic.client.client.mixin.HeightRangePlacementModifierAccessor;
import relic.client.client.mixin.RarityFilterPlacementModifierAccessor;
import relic.client.module.setting.BooleanSetting;
import relic.client.worldgen.BuiltinLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Ore {

    private static final BooleanSetting coal     = new BooleanSetting("Coal", false);
    private static final BooleanSetting iron     = new BooleanSetting("Iron", true);
    private static final BooleanSetting gold      = new BooleanSetting("Gold", true);
    private static final BooleanSetting redstone  = new BooleanSetting("Redstone", false);
    private static final BooleanSetting diamond   = new BooleanSetting("Diamond", true);
    private static final BooleanSetting lapis     = new BooleanSetting("Lapis", false);
    private static final BooleanSetting copper    = new BooleanSetting("Copper", false);
    private static final BooleanSetting emerald   = new BooleanSetting("Emerald", true);
    private static final BooleanSetting quartz    = new BooleanSetting("Quartz", false);
    private static final BooleanSetting debris    = new BooleanSetting("Ancient Debris", true);

    public static final List<BooleanSetting> oreSettings = new ArrayList<>(List.of(
            coal, iron, gold, redstone, diamond, lapis, copper, emerald, quartz, debris));

    private static RegistryWrapper.WrapperLookup lookup() {
        return BuiltinLookup.get();
    }

    public static Map<RegistryKey<Biome>, List<Ore>> getRegistry(RegistryKey<World> dimension) {
        RegistryWrapper.WrapperLookup registry = lookup();
        RegistryWrapper.Impl<PlacedFeature> features = registry.getOrThrow(RegistryKeys.PLACED_FEATURE);

        Map<RegistryKey<DimensionOptions>, DimensionOptions> dims = registry
                .getOrThrow(RegistryKeys.WORLD_PRESET)
                .getOrThrow(WorldPresets.DEFAULT)
                .value()
                .createDimensionsRegistryHolder()
                .dimensions();

        RegistryKey<DimensionOptions> dimKey;
        if (dimension == World.NETHER)   dimKey = DimensionOptions.NETHER;
        else if (dimension == World.END) dimKey = DimensionOptions.END;
        else                             dimKey = DimensionOptions.OVERWORLD;

        DimensionOptions dim = dims.get(dimKey);

        ChunkGenerator generator = dim.chunkGenerator();
        List<RegistryEntry<Biome>> biomeList = generator.getBiomeSource().getBiomes().stream().toList();

        List<PlacedFeatureIndexer.IndexedFeatures> indexer = PlacedFeatureIndexer.collectIndexedFeatures(
                biomeList, biomeEntry -> biomeEntry.value().getGenerationSettings().getFeatures(), true);

        Map<PlacedFeature, Ore> featureToOre = new HashMap<>();
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_COAL_LOWER, 6, coal, 47, 44, 54);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_COAL_UPPER, 6, coal, 47, 44, 54);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_IRON_MIDDLE, 6, iron, 236, 173, 119);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_IRON_SMALL, 6, iron, 236, 173, 119);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_IRON_UPPER, 6, iron, 236, 173, 119);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_GOLD, 6, gold, 247, 229, 30);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_GOLD_LOWER, 6, gold, 247, 229, 30);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_GOLD_EXTRA, 6, gold, 247, 229, 30);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_GOLD_NETHER, 7, gold, 247, 229, 30);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_GOLD_DELTAS, 7, gold, 247, 229, 30);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_REDSTONE, 6, redstone, 245, 7, 23);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_REDSTONE_LOWER, 6, redstone, 245, 7, 23);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_DIAMOND, 6, diamond, 33, 244, 255);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_DIAMOND_BURIED, 6, diamond, 33, 244, 255);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_DIAMOND_LARGE, 6, diamond, 33, 244, 255);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_DIAMOND_MEDIUM, 6, diamond, 33, 244, 255);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_LAPIS, 6, lapis, 8, 26, 189);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_LAPIS_BURIED, 6, lapis, 8, 26, 189);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_COPPER, 6, copper, 239, 151, 0);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_COPPER_LARGE, 6, copper, 239, 151, 0);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_EMERALD, 6, emerald, 27, 209, 45);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_QUARTZ_NETHER, 7, quartz, 205, 205, 205);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_QUARTZ_DELTAS, 7, quartz, 205, 205, 205);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_DEBRIS_SMALL, 7, debris, 209, 27, 245);
        registerOre(featureToOre, generator, indexer, features, OrePlacedFeatures.ORE_ANCIENT_DEBRIS_LARGE, 7, debris, 209, 27, 245);

        Map<RegistryKey<Biome>, List<Ore>> biomeOreMap = new HashMap<>();
        biomeList.forEach(biome -> {
            RegistryKey<Biome> key = biome.getKey().orElseThrow();
            List<Ore> ores = new ArrayList<>();
            biome.value().getGenerationSettings().getFeatures().stream()
                    .flatMap(RegistryEntryList::stream)
                    .map(RegistryEntry::value)
                    .filter(featureToOre::containsKey)
                    .forEach(feature -> ores.add(featureToOre.get(feature)));
            biomeOreMap.put(key, ores);
        });
        return biomeOreMap;
    }

    private static void registerOre(
            Map<PlacedFeature, Ore> map,
            ChunkGenerator generator,
            List<PlacedFeatureIndexer.IndexedFeatures> indexer,
            RegistryWrapper.Impl<PlacedFeature> oreRegistry,
            RegistryKey<PlacedFeature> oreKey,
            int genStep,
            BooleanSetting active,
            int r, int g, int b) {
        PlacedFeature orePlacement = oreRegistry.getOrThrow(oreKey).value();
        int index = indexer.get(genStep).indexMapping().applyAsInt(orePlacement);
        map.put(orePlacement, new Ore(orePlacement, generator, genStep, index, active, r / 255f, g / 255f, b / 255f));
    }

    public int step;
    public int index;
    public BooleanSetting active;
    public IntProvider count = ConstantIntProvider.create(1);
    public HeightProvider heightProvider;
    public HeightContext heightContext;
    public float rarity = 1;
    public float discardOnAirChance;
    public int size;
    public float red, green, blue;
    public boolean scattered;

    private Ore(PlacedFeature feature, ChunkGenerator generator, int step, int index,
                BooleanSetting active, float red, float green, float blue) {
        this.step = step;
        this.index = index;
        this.active = active;
        this.red = red;
        this.green = green;
        this.blue = blue;

        World world = MinecraftClient.getInstance().world;
        int bottom = world.getBottomY();
        int height = world.getDimension().logicalHeight();

        this.heightContext = new HeightContext(generator, HeightLimitView.create(bottom, height));

        for (PlacementModifier modifier : feature.placementModifiers()) {
            if (modifier instanceof CountPlacementModifier) {
                this.count = ((CountPlacementModifierAccessor) modifier).relic$getCount();
            } else if (modifier instanceof HeightRangePlacementModifier) {
                this.heightProvider = ((HeightRangePlacementModifierAccessor) modifier).relic$getHeight();
            } else if (modifier instanceof RarityFilterPlacementModifier) {
                this.rarity = ((RarityFilterPlacementModifierAccessor) modifier).relic$getChance();
            }
        }

        FeatureConfig featureConfig = feature.feature().value().config();
        if (featureConfig instanceof OreFeatureConfig oreFeatureConfig) {
            this.discardOnAirChance = oreFeatureConfig.discardOnAirChance;
            this.size = oreFeatureConfig.size;
        } else {
            throw new IllegalStateException("config for " + feature + " is not OreFeatureConfig");
        }

        if (feature.feature().value().feature() instanceof ScatteredOreFeature) {
            this.scattered = true;
        }
    }
}
