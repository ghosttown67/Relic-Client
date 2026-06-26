package relic.client.module.impl.visual;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import relic.client.api.render.BoxRenderer;
import relic.client.module.Module;
import relic.client.module.setting.MultiSelectSetting;
import relic.client.module.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public class StorageESPModule extends Module {

    private static final String[] BLOCK_TYPES = {
            "Chests", "Barrels", "Shulkers", "Ender Chests", "Hoppers",
            "Crafters", "Dispensers", "Blast Furnaces", "Smokers", "Decorated Pots"
    };

    private final MultiSelectSetting blocks = new MultiSelectSetting("Blocks", BLOCK_TYPES,
            "Chests", "Barrels", "Shulkers", "Ender Chests");
    private final NumberSetting radius = new NumberSetting("Chunk Radius", 6, 1, 12, true);

    public StorageESPModule() {
        super("StorageESP", "Highlights storage blocks", Category.VISUAL);
        addSettings(blocks, radius);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        List<BoxRenderer.ColoredBox> boxes = new ArrayList<>();
        ChunkPos center = mc.player.getChunkPos();

        int r = radius.getInt();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Chunk chunk = mc.world.getChunk(center.x + dx, center.z + dz);
                if (!(chunk instanceof WorldChunk worldChunk)) continue;

                for (BlockEntity be : worldChunk.getBlockEntities().values()) {
                    float[] color = colorFor(be);
                    if (color == null) continue;
                    boxes.add(new BoxRenderer.ColoredBox(new Box(be.getPos()),
                            color[0], color[1], color[2], 1.0f));
                }
            }
        }

        BoxRenderer.draw(boxes, BoxRenderer.Mode.BOTH);
    }

    private float[] colorFor(BlockEntity be) {
        if (be instanceof ChestBlockEntity)        return selected("Chests",         1.0f,  0.63f, 0.0f);
        if (be instanceof BarrelBlockEntity)       return selected("Barrels",        0.72f, 0.5f,  0.28f);
        if (be instanceof ShulkerBoxBlockEntity)   return selected("Shulkers",       0.85f, 0.3f,  0.9f);
        if (be instanceof EnderChestBlockEntity)   return selected("Ender Chests",   0.47f, 0.0f,  1.0f);
        if (be instanceof HopperBlockEntity)       return selected("Hoppers",        0.62f, 0.62f, 0.62f);
        if (be instanceof CrafterBlockEntity)      return selected("Crafters",       1.0f,  0.85f, 0.25f);
        if (be instanceof DispenserBlockEntity)    return selected("Dispensers",     0.9f,  0.25f, 0.25f);
        if (be instanceof BlastFurnaceBlockEntity) return selected("Blast Furnaces", 0.4f,  0.7f,  1.0f);
        if (be instanceof SmokerBlockEntity)       return selected("Smokers",        1.0f,  0.45f, 0.1f);
        if (be instanceof DecoratedPotBlockEntity) return selected("Decorated Pots", 0.25f, 0.85f, 0.7f);
        return null;
    }

    private float[] selected(String type, float r, float g, float b) {
        return blocks.isSelected(type) ? new float[]{r, g, b} : null;
    }
}
