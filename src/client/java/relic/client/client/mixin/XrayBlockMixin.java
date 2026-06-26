package relic.client.client.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.module.impl.visual.XrayModule;

@Mixin(Block.class)
public class XrayBlockMixin {

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void relic$xrayFace(BlockState state, BlockState neighborState, Direction side,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (XrayModule.isActive() && XrayModule.isExempt(state.getBlock())) {
            cir.setReturnValue(true);
        }
    }
}
