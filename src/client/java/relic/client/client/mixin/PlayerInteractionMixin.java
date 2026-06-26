package relic.client.client.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.misc.KillMessageModule;
import relic.client.module.impl.visual.DamageTagModule;

@Mixin(ClientPlayerInteractionManager.class)
public class PlayerInteractionMixin {

    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void relic$onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        KillMessageModule killMessage = KillMessageModule.getInstance();
        if (killMessage != null) {
            killMessage.onAttack(target);
        }
        DamageTagModule damageTag = DamageTagModule.getInstance();
        if (damageTag != null) {
            damageTag.onAttack(target);
        }
    }
}
