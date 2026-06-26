package relic.client.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.api.item.HoveredItemTracker;

@Mixin(HandledScreen.class)
public class HandledScreenHoverMixin {

    @Shadow
    protected Slot focusedSlot;

    @Inject(method = "drawMouseoverTooltip(Lnet/minecraft/client/gui/DrawContext;II)V", at = @At("HEAD"))
    private void relic$captureHoveredSlot(DrawContext context, int x, int y, CallbackInfo ci) {
        if (focusedSlot != null && focusedSlot.hasStack()) {
            HoveredItemTracker.set(focusedSlot.getStack());
        }
    }
}
