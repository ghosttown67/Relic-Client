package relic.client.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import relic.client.module.impl.privacy.HotbarObfuscatorModule;

@Mixin(InGameHud.class)
public abstract class HotbarObfuscatorHudMixin {

    @Shadow private ItemStack currentStack;

    @ModifyVariable(method = "renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V",
            at = @At("HEAD"), argsOnly = true)
    private ItemStack relic$obfuscateHotbar(ItemStack stack) {
        return HotbarObfuscatorModule.obfuscateHotbar(stack);
    }

    @Redirect(method = "renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;currentStack:Lnet/minecraft/item/ItemStack;"))
    private ItemStack relic$obfuscateHeldName(InGameHud self) {
        return HotbarObfuscatorModule.obfuscateHotbar(this.currentStack);
    }
}
