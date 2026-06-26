package relic.client.module.impl.visual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.notification.NotificationManager;

public class GhostModeModule extends Module {

    private static GhostModeModule instance;

    private final BooleanSetting fullFood = new BooleanSetting("Full Food", true);

    private boolean active;

    public GhostModeModule() {
        super("Ghost Mode",
                "Keep playing after you die: hides the death screen and spoofs your health "
                        + "client-side so movement and binds keep working. Disable to respawn.",
                Category.VISUAL);
        addSettings(fullFood);
        instance = this;
    }

    public static GhostModeModule getInstance() {
        return instance;
    }

    public void onDeath() {
        if (!active) {
            active = true;
            NotificationManager.getInstance().push("Ghost Mode",
                    "You are now a ghost. Disable to respawn.");
        }
    }

    @Override
    protected void onEnable() {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof DeathScreen) {
            mc.setScreen(null);
            onDeath();
        }
    }

    @Override
    protected void onDisable() {
        active = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.getNetworkHandler() != null) {
            mc.player.requestRespawn();
            NotificationManager.getInstance().push("Ghost Mode", "Respawn request sent.");
        }
    }

    @Override
    public void onPreTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            active = false;
            return;
        }
        if (!active) return;

        if (mc.player.getHealth() < 1f) mc.player.setHealth(20f);
        if (fullFood.isOn() && mc.player.getHungerManager().getFoodLevel() < 20) {
            mc.player.getHungerManager().setFoodLevel(20);
        }
    }
}
