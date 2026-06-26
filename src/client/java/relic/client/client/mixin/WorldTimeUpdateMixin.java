package relic.client.client.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.api.utils.TpsTracker;

@Mixin(ClientPlayNetworkHandler.class)
public class WorldTimeUpdateMixin {

    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"))
    private void relic$onWorldTimeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        TpsTracker.onTimeUpdate();
    }
}
