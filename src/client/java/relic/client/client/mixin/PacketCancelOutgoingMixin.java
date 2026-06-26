package relic.client.client.mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.api.packet.PacketLog;
import relic.client.module.impl.exploit.BlinkModule;
import relic.client.module.impl.exploit.PacketCancellerModule;
import relic.client.module.impl.exploit.XCarryModule;

@Mixin(ClientCommonNetworkHandler.class)
public class PacketCancelOutgoingMixin {

    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void relic$onSendPacket(Packet<?> packet, CallbackInfo ci) {
        PacketLog.record(false, packet);

        if (XCarryModule.shouldBlockClose(packet)) {
            ci.cancel();
            return;
        }
        if (BlinkModule.shouldHold(packet)) {
            BlinkModule.hold(packet);
            ci.cancel();
            return;
        }
        if (PacketCancellerModule.shouldCancelOutgoing(packet)) {
            ci.cancel();
        }
    }
}
