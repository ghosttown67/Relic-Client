package relic.client.module.impl.visual;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Arm;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import relic.client.api.render.BoxRenderer;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ColorSetting;
import relic.client.module.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TrajectoriesModule extends Module {

    private final BooleanSetting heldItem  = new BooleanSetting("Held Item", true);
    private final BooleanSetting previewLines = new BooleanSetting("Preview Lines", true);
    private final BooleanSetting thrown     = new BooleanSetting("Thrown Entities", true);
    private final BooleanSetting hitMarker  = new BooleanSetting("Hit Marker", true);
    private final NumberSetting  steps       = new NumberSetting("Max Steps", 160, 20, 600, true);
    private final NumberSetting  width       = new NumberSetting("Line Width", 1.5f, 0.5f, 5.0f);
    private final ColorSetting   color       = new ColorSetting("Color", 0.30f, 0.85f, 1.0f, 0.9f);

    private record Physics(double speed, double gravity, double airDrag, double waterDrag,
                           double roll, boolean throwable) {}

    private static final Physics ARROW     = new Physics(3.0,  0.05, 0.99, 0.6,    0, false);
    private static final Physics XBOW      = new Physics(3.15, 0.05, 0.99, 0.6,    0, false);
    private static final Physics TRIDENT   = new Physics(2.5,  0.05, 0.99, 0.99,   0, false);
    private static final Physics THROWN    = new Physics(1.5,  0.03, 0.99, 0.8,    0, true);
    private static final Physics POTION    = new Physics(0.5,  0.05, 0.99, 0.8,  -20, true);
    private static final Physics XP_BOTTLE = new Physics(0.7,  0.07, 0.99, 0.8,  -20, true);

    public TrajectoriesModule() {
        super("Trajectories", "Predicts projectile flight paths", Category.VISUAL);
        addSettings(heldItem, previewLines, thrown, hitMarker, steps, width, color);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);

        float cr = color.red(), cg = color.green(), cb = color.blue(), ca = color.alpha();

        List<BoxRenderer.Line> lines = new ArrayList<>();
        List<BoxRenderer.ColoredBox> markers = new ArrayList<>();

        if (heldItem.isOn()) {
            simulateHeld(mc, player, tickDelta, cr, cg, cb, ca, lines, markers);
        }

        if (thrown.isOn()) {
            for (Entity e : mc.world.getEntities()) {
                Physics phys = physicsForEntity(e);
                if (phys == null) continue;

                if (e.getVelocity().lengthSquared() < 1.0e-3) continue;
                Vec3d start = e.getLerpedPos(tickDelta);
                simulate(mc, start, e.getVelocity(), phys, e, e.isTouchingWater(),
                        true, null, null, cr, cg, cb, ca, lines, markers);
            }
        }

        BoxRenderer.drawLines(lines, width.getValue());
        if (hitMarker.isOn() && !markers.isEmpty()) {
            BoxRenderer.draw(markers, BoxRenderer.Mode.BOTH, width.getValue(), 0.25f);
        }
    }

    private void simulateHeld(MinecraftClient mc, ClientPlayerEntity player, float tickDelta,
                              float cr, float cg, float cb, float ca,
                              List<BoxRenderer.Line> lines, List<BoxRenderer.ColoredBox> markers) {

        Physics phys = physicsForHeld(player, player.getMainHandStack());
        boolean offHand = false;
        if (phys == null) {
            phys = physicsForHeld(player, player.getOffHandStack());
            offHand = true;
        }
        if (phys == null) return;

        double yaw = player.getYaw(tickDelta);
        double pitch = player.getPitch();
        double yawR = Math.toRadians(yaw);
        double pitchR = Math.toRadians(pitch);
        double dx = -Math.sin(yawR) * Math.cos(pitchR);
        double dy = -Math.sin(Math.toRadians(pitch + phys.roll()));
        double dz =  Math.cos(yawR) * Math.cos(pitchR);
        Vec3d vel = new Vec3d(dx, dy, dz).normalize().multiply(phys.speed());

        Vec3d pv = player.getVelocity();
        vel = vel.add(pv.x, player.isOnGround() ? 0.0 : pv.y, pv.z);

        Vec3d start = player.getCameraPosVec(tickDelta).subtract(0, 0.1, 0);

        Vec3d handStart = previewLines.isOn() ? handPosition(player, tickDelta, offHand) : null;

        List<Entity> targets = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == player) continue;
            if (e instanceof LivingEntity && e.isAlive() && !e.isSpectator()) targets.add(e);
        }

        simulate(mc, start, vel, phys, player, player.isTouchingWater(),
                previewLines.isOn(), handStart, targets, cr, cg, cb, ca, lines, markers);
    }

    private Vec3d handPosition(ClientPlayerEntity player, float tickDelta, boolean offHand) {
        Vec3d eye = player.getCameraPosVec(tickDelta);
        Vec3d look = player.getRotationVec(tickDelta);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();
        boolean rightArm = (player.getMainArm() == Arm.RIGHT) != offHand;
        double side = rightArm ? 1.0 : -1.0;
        return eye.add(right.multiply(0.35 * side)).add(look.multiply(0.35)).add(0, -0.3, 0);
    }

    private void simulate(MinecraftClient mc, Vec3d pos, Vec3d vel, Physics phys, Entity ignore, boolean inWater,
                          boolean drawLine, Vec3d firstFrom, List<Entity> targets,
                          float cr, float cg, float cb, float ca,
                          List<BoxRenderer.Line> lines, List<BoxRenderer.ColoredBox> markers) {
        double gravity = phys.gravity();
        boolean throwable = phys.throwable();
        int maxSteps = steps.getInt();
        boolean firstLine = true;
        BlockPos.Mutable bp = new BlockPos.Mutable();

        for (int i = 0; i < maxSteps; i++) {

            double drag = inWater ? phys.waterDrag() : phys.airDrag();

            Vec3d next;
            if (throwable) {

                vel = vel.subtract(0, gravity, 0).multiply(drag);
                next = pos.add(vel);
            } else {

                next = pos.add(vel);
                vel = vel.multiply(drag).subtract(0, gravity, 0);
            }
            inWater = inWater(mc, next, bp);

            Vec3d from = (firstLine && firstFrom != null) ? firstFrom : pos;

            HitResult block = mc.world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, ignore));
            Vec3d blockHit = (block != null && block.getType() != HitResult.Type.MISS) ? block.getPos() : null;

            Entity entityHit = firstEntityHit(targets, pos, next);
            if (entityHit != null && (blockHit == null
                    || pos.squaredDistanceTo(entityHit.getEyePos()) < pos.squaredDistanceTo(blockHit))) {
                Vec3d end = entityHit.getEyePos();
                if (drawLine) lines.add(new BoxRenderer.Line(from, end, cr, cg, cb, ca));
                if (hitMarker.isOn()) markers.add(markerBox(end, cr, cg, cb, ca));
                return;
            }
            if (blockHit != null) {
                if (drawLine) lines.add(new BoxRenderer.Line(from, blockHit, cr, cg, cb, ca));
                if (hitMarker.isOn()) markers.add(markerBox(blockHit, cr, cg, cb, ca));
                return;
            }

            if (drawLine) {
                lines.add(new BoxRenderer.Line(from, next, cr, cg, cb, ca));
                firstLine = false;
            }

            pos = next;
        }
    }

    private Entity firstEntityHit(List<Entity> targets, Vec3d from, Vec3d to) {
        if (targets == null) return null;
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity t : targets) {
            Optional<Vec3d> hit = t.getBoundingBox().raycast(from, to);
            if (hit.isEmpty()) continue;
            double d = from.squaredDistanceTo(hit.get());
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    private boolean inWater(MinecraftClient mc, Vec3d p, BlockPos.Mutable bp) {
        bp.set(p.x, p.y, p.z);
        FluidState fluid = mc.world.getFluidState(bp);
        if (!fluid.isIn(FluidTags.WATER)) return false;
        return p.y <= bp.getY() + fluid.getHeight(mc.world, bp);
    }

    private BoxRenderer.ColoredBox markerBox(Vec3d p, float r, float g, float b, float a) {
        double s = 0.15;
        Box box = new Box(p.x - s, p.y - s, p.z - s, p.x + s, p.y + s, p.z + s);
        return new BoxRenderer.ColoredBox(box, r, g, b, a);
    }

    private Physics physicsForHeld(ClientPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return null;

        if (stack.getItem() instanceof BowItem) {

            float pull = 1.0f;
            if (player.isUsingItem() && player.getActiveItem() == stack) {
                pull = BowItem.getPullProgress(player.getItemUseTime());
            }
            if (pull < 0.1f) return null;
            return withSpeed(ARROW, ARROW.speed() * pull);
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return XBOW;
        }
        if (stack.isOf(Items.TRIDENT)) {
            return TRIDENT;
        }
        if (stack.isOf(Items.ENDER_PEARL) || stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG)) {
            return THROWN;
        }
        if (stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) {
            return POTION;
        }
        if (stack.isOf(Items.EXPERIENCE_BOTTLE)) {
            return XP_BOTTLE;
        }
        return null;
    }

    private static Physics withSpeed(Physics base, double speed) {
        return new Physics(speed, base.gravity(), base.airDrag(), base.waterDrag(),
                base.roll(), base.throwable());
    }

    private Physics physicsForEntity(Entity e) {
        if (e instanceof TridentEntity) return TRIDENT;
        if (e instanceof PersistentProjectileEntity) return ARROW;
        if (e instanceof EnderPearlEntity || e instanceof SnowballEntity || e instanceof EggEntity) {
            return THROWN;
        }
        if (e instanceof PotionEntity) return POTION;
        if (e instanceof ExperienceBottleEntity) return XP_BOTTLE;
        return null;
    }
}
