package relic.client.client.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.api.packet.PacketLog;
import relic.client.module.impl.exploit.PacketCancellerModule;

@Mixin(ClientConnection.class)
public class PacketCancelIncomingMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void relic$cancelIncoming(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketLog.record(true, packet);
        if (PacketCancellerModule.shouldCancelIncoming(packet)) {
            ci.cancel();
        }
    }
}
