package relic.client.client.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.basehunting.AmethystESPModule;
import relic.client.module.impl.basehunting.BlockAlertModule;
import relic.client.module.impl.basehunting.OreSimModule;
import relic.client.module.impl.visual.BlockESPModule;
import relic.client.module.impl.visual.HoleESPModule;

@Mixin(ClientPlayNetworkHandler.class)
public class BlockUpdateMixin {

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void relic$onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        BlockESPModule esp = BlockESPModule.getInstance();
        if (esp != null) {
            esp.onBlockUpdate(packet.getPos());
        }
        BlockAlertModule alert = BlockAlertModule.getInstance();
        if (alert != null) {
            alert.onBlockUpdate(packet.getPos());
        }
        HoleESPModule hole = HoleESPModule.getInstance();
        if (hole != null) {
            hole.onBlockUpdate(packet.getPos());
        }
        AmethystESPModule amethyst = AmethystESPModule.getInstance();
        if (amethyst != null) {
            amethyst.onBlockUpdate(packet.getPos());
        }
        OreSimModule oreSim = OreSimModule.getInstance();
        if (oreSim != null) {
            oreSim.onBlockUpdate(packet.getPos());
        }
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void relic$onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        BlockESPModule esp = BlockESPModule.getInstance();
        BlockAlertModule alert = BlockAlertModule.getInstance();
        HoleESPModule hole = HoleESPModule.getInstance();
        AmethystESPModule amethyst = AmethystESPModule.getInstance();
        OreSimModule oreSim = OreSimModule.getInstance();
        packet.visitUpdates((pos, state) -> {
            BlockPos immutable = pos.toImmutable();
            if (esp != null) esp.onBlockUpdate(immutable);
            if (alert != null) alert.onBlockUpdate(immutable);
            if (hole != null) hole.onBlockUpdate(immutable);
            if (amethyst != null) amethyst.onBlockUpdate(immutable);
            if (oreSim != null) oreSim.onBlockUpdate(immutable);
        });
    }
}
