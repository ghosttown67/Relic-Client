package relic.client.module.impl.visual;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

public class FreecamModule extends Module {

    private static FreecamModule instance;

    private final NumberSetting speed       = new NumberSetting("Speed", 1.0f, 0.1f, 5.0f);
    private final BooleanSetting keepMining = new BooleanSetting("Keep Mining", true);

    private double x, y, z, prevX, prevY, prevZ;
    private float yaw, pitch;
    private boolean holdAttack;

    public FreecamModule() {
        super("Freecam", "Detaches the camera from your player", Category.VISUAL);
        addSettings(speed, keepMining);
        instance = this;

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (isEnabled() && client.player != null) {
                unpressMovementKeys(client);
            }
        });
    }

    public static FreecamModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        x = prevX = camPos.x;
        y = prevY = camPos.y;
        z = prevZ = camPos.z;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();

        holdAttack = keepMining.isOn() && mc.options.attackKey.isPressed();

        unpressMovementKeys(mc);
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (holdAttack && mc.player != null && !isPhysicallyDown(mc, mc.options.attackKey)) {
            mc.options.attackKey.setPressed(false);
        }
        holdAttack = false;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            setEnabled(false);
            return;
        }

        prevX = x;
        prevY = y;
        prevZ = z;

        if (mc.currentScreen == null) {
            double s = speed.getValue() * 0.5;
            if (isPhysicallyDown(mc, mc.options.sprintKey)) s *= 2;

            Vec3d forward = Vec3d.fromPolar(0, yaw);
            Vec3d right = Vec3d.fromPolar(0, yaw + 90);

            boolean f = isPhysicallyDown(mc, mc.options.forwardKey);
            boolean b = isPhysicallyDown(mc, mc.options.backKey);
            boolean l = isPhysicallyDown(mc, mc.options.leftKey);
            boolean r = isPhysicallyDown(mc, mc.options.rightKey);

            double velX = 0, velY = 0, velZ = 0;
            if (f) { velX += forward.x * s; velZ += forward.z * s; }
            if (b) { velX -= forward.x * s; velZ -= forward.z * s; }
            if (r) { velX += right.x * s;   velZ += right.z * s; }
            if (l) { velX -= right.x * s;   velZ -= right.z * s; }
            if ((f || b) && (l || r)) {
                double diagonal = 1 / Math.sqrt(2);
                velX *= diagonal;
                velZ *= diagonal;
            }
            if (isPhysicallyDown(mc, mc.options.jumpKey))  velY += s;
            if (isPhysicallyDown(mc, mc.options.sneakKey)) velY -= s;

            x += velX;
            y += velY;
            z += velZ;
        }

        unpressMovementKeys(mc);

        if (holdAttack) {
            mc.options.attackKey.setPressed(true);
        }
    }

    public void rotate(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) (cursorDeltaX * 0.15);
        pitch = MathHelper.clamp(pitch + (float) (cursorDeltaY * 0.15), -90.0f, 90.0f);
    }

    public double getX(float tickDelta) { return MathHelper.lerp(tickDelta, prevX, x); }
    public double getY(float tickDelta) { return MathHelper.lerp(tickDelta, prevY, y); }
    public double getZ(float tickDelta) { return MathHelper.lerp(tickDelta, prevZ, z); }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    private void unpressMovementKeys(MinecraftClient mc) {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private boolean isPhysicallyDown(MinecraftClient mc, KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }
}
