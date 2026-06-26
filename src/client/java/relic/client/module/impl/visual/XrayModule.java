package relic.client.module.impl.visual;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import relic.client.module.Module;
import relic.client.module.setting.BlockListSetting;
import relic.client.module.setting.NumberSetting;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class XrayModule extends Module {

    private static XrayModule instance;

    private final NumberSetting opacity = new NumberSetting("Opacity", 25, 0, 255, true);
    private final BlockListSetting ores = new BlockListSetting("Ores",
            "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
            "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
            "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
            "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris");

    private volatile Set<Block> exempt = Set.of();

    private volatile boolean needsReload;
    private long lastReload;

    public XrayModule() {
        super("Xray", "See ores through terrain", Category.VISUAL);
        addSettings(opacity, ores);
        instance = this;

        opacity.onChanged(this::requestReload);
        ores.onChanged(() -> { resolveExempt(); requestReload(); });
    }

    @Override
    protected void onEnable() {
        resolveExempt();

        reloadNow();
    }

    @Override
    protected void onDisable() {
        reloadNow();
    }

    @Override
    public void onTick() {
        if (needsReload && System.currentTimeMillis() - lastReload > 250) {
            reloadNow();
        }
    }

    private void resolveExempt() {
        Set<Block> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Block block : Registries.BLOCK) {
            if (ores.isSelected(Registries.BLOCK.getId(block).getPath())) {
                set.add(block);
            }
        }
        exempt = set;
    }

    private void requestReload() {
        needsReload = true;
    }

    private void reloadNow() {
        needsReload = false;
        lastReload = System.currentTimeMillis();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.worldRenderer != null) mc.worldRenderer.reload();
        });
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean isExempt(Block block) {
        return instance != null && instance.exempt.contains(block);
    }

    public static int getAlpha(BlockState state) {
        if (instance == null || !instance.isEnabled()) return -1;
        if (instance.exempt.contains(state.getBlock())) return -1;
        return instance.opacity.getInt();
    }
}
