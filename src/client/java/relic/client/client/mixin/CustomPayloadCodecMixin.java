package relic.client.client.mixin;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.api.packet.RawCustomPayload;

@Mixin(targets = "net.minecraft.network.packet.CustomPayload$1")
public class CustomPayloadCodecMixin {

    @Inject(
            method = "encode(Lnet/minecraft/network/PacketByteBuf;Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void relic$encodeRawPayload(PacketByteBuf buf, CustomPayload payload, CallbackInfo ci) {
        if (payload instanceof RawCustomPayload raw) {
            buf.writeIdentifier(raw.channel());
            buf.writeBytes(raw.data());
            ci.cancel();
        }
    }
}
