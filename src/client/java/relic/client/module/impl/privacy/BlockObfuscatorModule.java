package relic.client.module.impl.privacy;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import relic.client.module.Module;
import relic.client.module.setting.BlockListSetting;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class BlockObfuscatorModule extends Module {

    private static BlockObfuscatorModule instance;

    private final BlockListSetting blocks = new BlockListSetting("Blocks",
            "bedrock",
            "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
            "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
            "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
            "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris",
            "tuff", "gravel");

    private volatile Set<Block> targets = Set.of();

    public BlockObfuscatorModule() {
        super("Block Obfuscator", "Disguises the listed blocks as deepslate", Category.PRIVACY);
        addSettings(blocks);
        instance = this;

        blocks.onChanged(() -> { resolveTargets(); reload(); });
    }

    @Override
    protected void onEnable() {
        resolveTargets();
        reload();
    }

    @Override
    protected void onDisable() {
        reload();
    }

    private void resolveTargets() {
        Set<Block> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Block block : Registries.BLOCK) {
            if (block == Blocks.DEEPSLATE) continue;
            if (blocks.isSelected(Registries.BLOCK.getId(block).getPath())) {
                set.add(block);
            }
        }
        targets = set;
    }

    private void reload() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.worldRenderer != null) mc.worldRenderer.reload();
        });
    }

    public static BlockState getReplacement(BlockState state) {
        if (instance == null || !instance.isEnabled()) return null;
        if (!instance.targets.contains(state.getBlock())) return null;
        return Blocks.DEEPSLATE.getDefaultState();
    }
}
