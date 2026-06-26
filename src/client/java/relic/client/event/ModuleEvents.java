package relic.client.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import relic.client.module.Module;
import relic.client.module.ModuleManager;
import relic.client.notification.NotificationManager;

@Environment(EnvType.CLIENT)
public final class ModuleEvents {
    private ModuleEvents() {}

    public static void register() {

        WorldRenderEvents.END_MAIN.register(context -> {
            for (Module module : ModuleManager.getInstance().getAllModules()) {
                if (module.isEnabled()) module.onWorldRender(context);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            float tickDelta = tickCounter.getTickProgress(false);

            for (Module module : ModuleManager.getInstance().getAllModules()) {
                if (module.isEnabled()) module.onHudRender(drawContext, tickDelta);
            }

            NotificationManager.getInstance().render(drawContext);
        });

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            for (Module module : ModuleManager.getInstance().getAllModules()) {
                if (module.isEnabled()) module.onPreTick();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            for (Module module : ModuleManager.getInstance().getAllModules()) {
                if (module.isEnabled()) module.onTick();
            }
        });
    }
}
