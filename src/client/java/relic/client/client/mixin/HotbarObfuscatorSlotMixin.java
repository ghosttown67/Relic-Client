package relic.client.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import relic.client.module.impl.privacy.HotbarObfuscatorModule;

@Mixin(HandledScreen.class)
public abstract class HotbarObfuscatorSlotMixin {

    @Redirect(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack relic$obfuscateSlot(Slot slot) {
        return HotbarObfuscatorModule.obfuscateSlot(slot, slot.getStack());
    }

    @Redirect(method = "drawMouseoverTooltip(Lnet/minecraft/client/gui/DrawContext;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack relic$obfuscateTooltip(Slot slot) {
        return HotbarObfuscatorModule.obfuscateSlot(slot, slot.getStack());
    }
}
