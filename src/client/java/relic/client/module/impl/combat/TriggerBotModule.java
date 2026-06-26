package relic.client.module.impl.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

public class TriggerBotModule extends Module {

    private final NumberSetting range          = new NumberSetting("Range", 3.0f, 1.0f, 6.0f);
    private final BooleanSetting onlyCrits      = new BooleanSetting("Only Criticals", false);
    private final BooleanSetting waitForCooldown = new BooleanSetting("Wait for Cooldown", true);

    public TriggerBotModule() {
        super("TriggerBot", "Attacks the entity under your crosshair", Category.COMBAT);
        addSettings(range, onlyCrits, waitForCooldown);
    }

    @Override
    public void onPreTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) return;

        if (mc.currentScreen != null) return;
        if (player.isSpectator()) return;

        if (waitForCooldown.isOn() && player.getAttackCooldownProgress(0.5f) < 1.0f) return;

        Entity target = findTarget(mc, player);
        if (target == null) return;

        if (onlyCrits.isOn() && !wouldCrit(player)) return;

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
    }

    private Entity findTarget(MinecraftClient mc, ClientPlayerEntity player) {
        double reach = range.getValue();
        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(reach));

        double maxDist = reach;
        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            maxDist = blockHit.getPos().distanceTo(eye);
            end = blockHit.getPos();
        }

        Box searchBox = player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, searchBox,
                e -> e instanceof PlayerEntity
                        && e != player
                        && e.isAlive()
                        && !e.isSpectator()
                        && !e.isInvisible()
                        && e.isAttackable(),
                maxDist * maxDist);

        return hit == null ? null : hit.getEntity();
    }

    private boolean wouldCrit(ClientPlayerEntity player) {
        return player.getAttackCooldownProgress(0.5f) > 0.9f
                && player.fallDistance > 0.0f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.hasVehicle()
                && !player.isSprinting();
    }
}
