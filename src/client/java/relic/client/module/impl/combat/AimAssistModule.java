package relic.client.module.impl.combat;

import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ColorSetting;
import relic.client.module.setting.ModeSetting;
import relic.client.module.setting.NumberSetting;

public class AimAssistModule extends Module {

    private final ModeSetting    mode           = new ModeSetting("Mode", "Silent", "Silent", "Direct");
    private final ModeSetting    target         = new ModeSetting("Target", "Closest", "Closest", "Lowest Health", "Least Armor");
    private final ModeSetting    targetPoint    = new ModeSetting("Target Point", "Chest", "Chest", "Nearest Part", "Surface");
    private final NumberSetting  range          = new NumberSetting("Range", 4.0f, 1.0f, 10.0f);
    private final NumberSetting  silentFov      = new NumberSetting("Silent FOV", 40, 1, 180, true);
    private final NumberSetting  speed          = new NumberSetting("Speed", 60, 1, 100, true);
    private final BooleanSetting ignoreTeammates = new BooleanSetting("Ignore Teammates", true);
    private final BooleanSetting fovCircle      = new BooleanSetting("FOV Circle", true);
    private final ColorSetting   fovColor       = new ColorSetting("FOV Color", 0x806B7CFF);

    private static final double[] PART_FRACTIONS = {0.9, 0.6, 0.3, 0.08};

    public AimAssistModule() {
        super("AimAssist", "Aims at players for sword combat; pairs with TriggerBot", Category.COMBAT);
        addSettings(mode, target, targetPoint, range, silentFov, speed, ignoreTeammates, fovCircle, fovColor);
    }

    @Override
    public void onPreTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (player.isSpectator()) return;

        Vec3d eye = player.getCameraPosVec(1.0f);
        PlayerEntity victim = findTarget(mc, player, eye);
        if (victim == null) return;

        Vec3d point = getTargetPoint(player, eye, victim);
        float[] rot = rotationsTo(eye, point);

        if (mode.is("Silent")) {

            if (isLookingAt(mc, player, victim)) return;

            if (angleDiff(player, eye, point) > silentFov.getValue()) return;
        }

        applyRotation(player, rot);
    }

    @Override
    public boolean wantsImGuiOverlay() {
        if (!fovCircle.isOn()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.world != null && !mc.options.hudHidden;
    }

    @Override
    public void onImGuiRender(ImDrawList drawList) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        float w = ImGui.getIO().getDisplaySizeX();
        float h = ImGui.getIO().getDisplaySizeY();
        if (w <= 0 || h <= 0) return;

        double angle = Math.toRadians(Math.min(silentFov.getValue(), 89.0));
        double halfFovTan = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        float radius = (float) (Math.tan(angle) / halfFovTan * (h / 2.0));
        if (radius < 1.0f) return;

        float thickness = 2.0f * (float) mc.getWindow().getScaleFactor();
        drawList.addCircle(w / 2.0f, h / 2.0f, radius, argbToAbgr(fovColor.getValue()), 0, thickness);
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private PlayerEntity findTarget(MinecraftClient mc, ClientPlayerEntity player, Vec3d eye) {
        boolean silent = mode.is("Silent");
        double maxRange = range.getValue();
        double fov = silentFov.getValue();

        PlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof PlayerEntity p)) continue;
            if (!isValidTarget(player, p)) continue;

            Vec3d point = getTargetPoint(player, eye, p);
            double dist = eye.distanceTo(point);
            if (dist > maxRange) continue;
            if (silent && angleDiff(player, eye, point) > fov) continue;

            double score = score(p, dist);
            if (best == null || score < bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean isValidTarget(ClientPlayerEntity self, PlayerEntity p) {
        return p != self
                && p.isAlive()
                && !p.isSpectator()
                && !p.isInvisible()
                && p.isAttackable()
                && (!ignoreTeammates.isOn() || !self.isTeammate(p));
    }

    private double score(PlayerEntity p, double dist) {
        if (target.is("Lowest Health")) return p.getHealth() + p.getAbsorptionAmount();
        if (target.is("Least Armor"))   return p.getArmor();
        return dist;
    }

    private Vec3d getTargetPoint(ClientPlayerEntity player, Vec3d eye, PlayerEntity victim) {
        Box box = victim.getBoundingBox();
        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double height = box.maxY - box.minY;

        if (targetPoint.is("Surface")) {

            return new Vec3d(
                    MathHelper.clamp(eye.x, box.minX, box.maxX),
                    MathHelper.clamp(eye.y, box.minY, box.maxY),
                    MathHelper.clamp(eye.z, box.minZ, box.maxZ));
        }

        if (targetPoint.is("Nearest Part")) {
            Vec3d best = null;
            double bestDelta = Double.MAX_VALUE;
            for (double f : PART_FRACTIONS) {
                Vec3d candidate = new Vec3d(cx, box.minY + height * f, cz);
                if (!canSee(eye, candidate, victim)) continue;
                double delta = angleDiff(player, eye, candidate);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    best = candidate;
                }
            }
            if (best != null) return best;

        }

        return new Vec3d(cx, box.minY + height * 0.6, cz);
    }

    private float[] rotationsTo(Vec3d eye, Vec3d point) {
        Vec3d diff = point.subtract(eye);
        double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, horiz));
        return new float[]{yaw, pitch};
    }

    private double angleDiff(ClientPlayerEntity player, Vec3d eye, Vec3d point) {
        float[] r = rotationsTo(eye, point);
        float dy = MathHelper.wrapDegrees(r[0] - player.getYaw());
        float dp = r[1] - player.getPitch();
        return Math.sqrt(dy * dy + dp * dp);
    }

    private void applyRotation(ClientPlayerEntity player, float[] rot) {
        float step = Math.max(speed.getValue() / 100.0f, 0.05f);

        float curYaw = player.getYaw();
        float curPitch = player.getPitch();
        float dy = MathHelper.wrapDegrees(rot[0] - curYaw);
        float dp = rot[1] - curPitch;

        player.setYaw(curYaw + dy * step);
        player.setPitch(MathHelper.clamp(curPitch + dp * step, -90.0f, 90.0f));
    }

    private boolean canSee(Vec3d eye, Vec3d point, PlayerEntity victim) {
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, victim));
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    private boolean isLookingAt(MinecraftClient mc, ClientPlayerEntity player, PlayerEntity victim) {
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
                e -> e == victim, maxDist * maxDist);
        return hit != null && hit.getEntity() == victim;
    }
}
