package relic.client.client.mixin;

import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import relic.client.module.impl.misc.InventoryMoveModule;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z"))
    private boolean relic$inventoryMoveIsPressed(KeyBinding binding) {
        InventoryMoveModule module = InventoryMoveModule.getInstance();
        if (module != null && module.shouldApply()) {
            return module.isBoundKeyHeld(binding);
        }
        return binding.isPressed();
    }
}
