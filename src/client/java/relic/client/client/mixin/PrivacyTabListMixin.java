package relic.client.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.module.impl.privacy.PrivacyModule;

@Mixin(PlayerListHud.class)
public abstract class PrivacyTabListMixin {

    @Inject(method = "getPlayerName", at = @At("HEAD"), cancellable = true)
    private void relic$spoofTabName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!PrivacyModule.shouldSpoofName()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!entry.getProfile().id().equals(mc.player.getUuid())) return;
        cir.setReturnValue(Text.literal(PrivacyModule.getSpoofName()));
    }
}
