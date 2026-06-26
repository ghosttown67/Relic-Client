package relic.client.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.module.impl.privacy.PrivacyModule;

@Mixin(PlayerEntity.class)
public abstract class PrivacyPlayerNameMixin {

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void relic$spoofName(CallbackInfoReturnable<Text> cir) {
        if (!PrivacyModule.shouldSpoofName()) return;
        if ((Object) this != MinecraftClient.getInstance().player) return;
        cir.setReturnValue(Text.literal(PrivacyModule.getSpoofName()));
    }
}
