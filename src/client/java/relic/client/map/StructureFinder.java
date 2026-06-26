package relic.client.map;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import relic.client.worldgen.BuiltinLookup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StructureFinder {

    public record Found(String id, int blockX, int blockZ, int color, String label) {}

    private record StructInfo(String id, Set<String> biomes, int sampleY,
                              int color, String label) {}
    private record SetInfo(RandomSpreadStructurePlacement placement, List<StructInfo> structures) {}

    private final long seed;
    private final List<SetInfo> sets = new ArrayList<>();
    private final List<String> allIds = new ArrayList<>();

    public StructureFinder(long seed) {
        this.seed = seed;
        RegistryWrapper.WrapperLookup lookup = BuiltinLookup.get();
        RegistryWrapper.Impl<StructureSet> reg = lookup.getOrThrow(RegistryKeys.STRUCTURE_SET);

        reg.streamEntries().forEach(ref -> {
            StructureSet set = ref.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement rsp)) return;
            List<StructInfo> infos = new ArrayList<>();
            for (StructureSet.WeightedEntry we : set.structures()) {
                String id = we.structure().getKey().map(k -> k.getValue().getPath()).orElse("structure");
                Set<String> biomes = resolveBiomes(we.structure().value().getValidBiomes());
                infos.add(new StructInfo(id, biomes, sampleY(id), color(id), label(id)));
            }
            if (!infos.isEmpty()) sets.add(new SetInfo(rsp, infos));
        });

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (SetInfo s : sets) for (StructInfo si : s.structures()) ids.add(si.id());
        allIds.addAll(ids);
        allIds.sort(null);
    }

    public List<String> allStructureIds() {
        return allIds;
    }

    public List<Found> findInBlockRange(double minX, double minZ, double maxX, double maxZ,
                                        BiomeMapGenerator biomeGen) {
        int cMinX = (int) Math.floor(minX) >> 4, cMaxX = (int) Math.floor(maxX) >> 4;
        int cMinZ = (int) Math.floor(minZ) >> 4, cMaxZ = (int) Math.floor(maxZ) >> 4;
        List<Found> out = new ArrayList<>();

        for (SetInfo set : sets) {
            int spacing = set.placement().getSpacing();
            int regMinX = Math.floorDiv(cMinX, spacing), regMaxX = Math.floorDiv(cMaxX, spacing);
            int regMinZ = Math.floorDiv(cMinZ, spacing), regMaxZ = Math.floorDiv(cMaxZ, spacing);
            for (int rz = regMinZ; rz <= regMaxZ; rz++) {
                for (int rx = regMinX; rx <= regMaxX; rx++) {
                    ChunkPos start = set.placement().getStartChunk(seed, rx * spacing, rz * spacing);
                    if (start.x < cMinX || start.x > cMaxX || start.z < cMinZ || start.z > cMaxZ) continue;
                    if (!set.placement().applyFrequencyReduction(start.x, start.z, seed)) continue;

                    int bx = start.getStartX() + 8, bz = start.getStartZ() + 8;
                    for (StructInfo si : set.structures()) {

                        if (!si.biomes().isEmpty()) {
                            String biomePath = BiomePalette.name(biomeGen.biomeAt(bx, si.sampleY(), bz));
                            if (!si.biomes().contains(biomePath)) continue;
                        }
                        out.add(new Found(si.id(), bx, bz, si.color(), si.label()));
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static Set<String> resolveBiomes(RegistryEntryList<Biome> list) {
        if (list.getTagKey().isPresent()) {
            return BiomeTagResolver.resolveTag(list.getTagKey().get().id().toString());
        }
        Set<String> paths = new HashSet<>();
        for (RegistryEntry<Biome> e : list) {
            e.getKey().ifPresent(k -> paths.add(k.getValue().getPath()));
        }
        return paths;
    }

    public static int sampleY(String id) {
        if (id.equals("ancient_city")) return -51;
        if (id.equals("trial_chambers")) return -20;
        return 64;
    }

    public static String label(String id) {
        if (id.startsWith("village")) return "Vi";
        if (id.startsWith("ruined_portal")) return "RP";
        if (id.startsWith("ocean_ruin")) return "OR";
        if (id.startsWith("shipwreck")) return "Sw";
        return switch (id) {
            case "pillager_outpost" -> "Po";
            case "desert_pyramid" -> "DP";
            case "jungle_pyramid" -> "JP";
            case "igloo" -> "Ig";
            case "swamp_hut" -> "SH";
            case "monument" -> "Mo";
            case "mansion" -> "Ma";
            case "buried_treasure" -> "BT";
            case "ancient_city" -> "AC";
            case "trail_ruins" -> "Tr";
            case "trial_chambers" -> "TC";
            case "mineshaft", "mineshaft_mesa" -> "Ms";
            case "stronghold" -> "St";
            case "fortress" -> "Fo";
            case "bastion_remnant" -> "Ba";
            case "nether_fossil" -> "Nf";
            case "end_city" -> "Ec";
            default -> id.length() >= 2 ? id.substring(0, 2) : id;
        };
    }

    public static String iconItem(String id) {
        if (id.startsWith("village")) return "emerald";
        if (id.startsWith("ruined_portal")) return "flint_and_steel";
        if (id.startsWith("ocean_ruin")) return "prismarine_crystals";
        if (id.startsWith("shipwreck")) return "oak_boat";
        if (id.startsWith("mineshaft")) return "minecart";
        return switch (id) {
            case "pillager_outpost" -> "ominous_bottle";
            case "desert_pyramid" -> "gold_ingot";
            case "jungle_pyramid" -> "arrow";
            case "igloo" -> "snowball";
            case "swamp_hut" -> "spider_eye";
            case "monument" -> "prismarine_shard";
            case "mansion" -> "totem_of_undying";
            case "buried_treasure" -> "heart_of_the_sea";
            case "ancient_city" -> "echo_shard";
            case "trail_ruins" -> "brush";
            case "trial_chambers" -> "trial_key";
            case "fortress" -> "blaze_rod";
            case "bastion_remnant" -> "netherite_scrap";
            case "nether_fossil" -> "bone";
            case "end_city" -> "shulker_shell";
            case "stronghold" -> "ender_eye";
            default -> null;
        };
    }

    public static int color(String id) {
        if (id.startsWith("village")) return 0xFF8DD35F;
        if (id.startsWith("ruined_portal")) return 0xFFB44FE0;
        if (id.startsWith("ocean_ruin")) return 0xFF4FC7E0;
        if (id.startsWith("shipwreck")) return 0xFFC8A06A;
        return switch (id) {
            case "pillager_outpost" -> 0xFFE05D4F;
            case "desert_pyramid" -> 0xFFE6CF6B;
            case "jungle_pyramid" -> 0xFF5FB347;
            case "igloo" -> 0xFFCDE7F0;
            case "swamp_hut" -> 0xFF6E7A45;
            case "monument" -> 0xFF3FB6C9;
            case "mansion" -> 0xFF7A5A3A;
            case "buried_treasure" -> 0xFFF0D23F;
            case "ancient_city" -> 0xFF2A3550;
            case "trail_ruins" -> 0xFFB89C7A;
            case "trial_chambers" -> 0xFFD9803F;
            default -> 0xFFCFCFCF;
        };
    }
}
