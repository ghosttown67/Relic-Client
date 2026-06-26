package relic.client.map;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class BiomePalette {

    private static final Map<String, Integer> COLORS = new HashMap<>();

    private BiomePalette() {}

    private static void put(String path, int rgb) {
        COLORS.put(path, 0xFF000000 | rgb);
    }

    static {

        put("plains", 0x8DB360);
        put("sunflower_plains", 0xB5DB88);
        put("snowy_plains", 0xFFFFFF);
        put("ice_spikes", 0xB4DCDC);
        put("desert", 0xFA9418);
        put("swamp", 0x07F9B2);
        put("mangrove_swamp", 0x67352B);
        put("forest", 0x056621);
        put("flower_forest", 0x2D8E49);
        put("birch_forest", 0x307444);
        put("dark_forest", 0x40511A);
        put("pale_garden", 0x6E7763);
        put("old_growth_birch_forest", 0x589C6C);
        put("old_growth_pine_taiga", 0x596651);
        put("old_growth_spruce_taiga", 0x818E79);
        put("taiga", 0x0B6659);
        put("snowy_taiga", 0x31554A);
        put("savanna", 0xBDB25F);
        put("savanna_plateau", 0xA79D64);
        put("windswept_hills", 0x606060);
        put("windswept_gravelly_hills", 0x818181);
        put("windswept_forest", 0x589C6C);
        put("windswept_savanna", 0xE5DA87);
        put("jungle", 0x537B09);
        put("sparse_jungle", 0x628B17);
        put("bamboo_jungle", 0x768E14);
        put("badlands", 0xD94515);
        put("eroded_badlands", 0xFF6D3D);
        put("wooded_badlands", 0xB09765);
        put("meadow", 0x60A17B);
        put("cherry_grove", 0xEBC5DD);
        put("grove", 0x88AE8E);
        put("snowy_slopes", 0xC4C4C4);
        put("frozen_peaks", 0xA0A0A0);
        put("jagged_peaks", 0xDCDCC8);
        put("stony_peaks", 0x7B8497);
        put("meadow_plateau", 0x60A17B);

        put("river", 0x0000FF);
        put("frozen_river", 0xA0A0FF);
        put("beach", 0xFADE55);
        put("snowy_beach", 0xFAF0C0);
        put("stony_shore", 0xA2A284);

        put("warm_ocean", 0x0058AA);
        put("lukewarm_ocean", 0x0070CC);
        put("deep_lukewarm_ocean", 0x004C8C);
        put("ocean", 0x000070);
        put("deep_ocean", 0x000030);
        put("cold_ocean", 0x202070);
        put("deep_cold_ocean", 0x202038);
        put("frozen_ocean", 0x7070D6);
        put("deep_frozen_ocean", 0x404090);
        put("mushroom_fields", 0xFF00FF);

        put("dripstone_caves", 0x7A5C49);
        put("lush_caves", 0x4C7A2E);
        put("deep_dark", 0x0F1419);

        put("nether_wastes", 0x7A0F0F);
        put("warped_forest", 0x167A6E);
        put("crimson_forest", 0xA01010);
        put("soul_sand_valley", 0x4D3A2E);
        put("basalt_deltas", 0x4A4A4A);
        put("the_end", 0x8080A0);
        put("end_highlands", 0xB0B070);
        put("end_midlands", 0xC0C080);
        put("small_end_islands", 0x6060A0);
        put("end_barrens", 0x9090B0);
        put("the_void", 0x000000);
    }

    public static String name(RegistryEntry<Biome> biome) {
        Identifier id = idOf(biome);
        return id == null ? "unknown" : id.getPath();
    }

    public static int colorArgb(RegistryEntry<Biome> biome) {
        Identifier id = idOf(biome);
        if (id == null) return 0xFF7F7F7F;
        Integer c = COLORS.get(id.getPath());
        return c != null ? c : fallback(id.getPath());
    }

    private static Identifier idOf(RegistryEntry<Biome> biome) {
        if (biome == null) return null;
        Optional<net.minecraft.registry.RegistryKey<Biome>> key = biome.getKey();
        return key.map(net.minecraft.registry.RegistryKey::getValue).orElse(null);
    }

    private static int fallback(String path) {
        int h = path.hashCode();

        int r = 96 + ((h        & 0x7F));
        int g = 96 + ((h >> 7   & 0x7F));
        int b = 96 + ((h >> 14  & 0x7F));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
