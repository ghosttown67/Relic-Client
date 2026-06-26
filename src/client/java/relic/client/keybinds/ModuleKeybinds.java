package relic.client.keybinds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.InputUtil;
import relic.client.module.Module;
import relic.client.module.ModuleManager;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class ModuleKeybinds {
    private static final Map<Module, Boolean> lastDown = new HashMap<>();

    private ModuleKeybinds() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean inGame = mc.currentScreen == null && mc.player != null;

            for (Module module : ModuleManager.getInstance().getAllModules()) {
                int key = module.getKeyBind();
                if (key <= 0) continue;

                boolean down = InputUtil.isKeyPressed(mc.getWindow(), key);
                boolean was = lastDown.getOrDefault(module, false);

                if (module.isHoldToActivate()) {

                    module.setEnabled(inGame && down);
                } else if (inGame && down && !was) {
                    module.toggle();
                }
                lastDown.put(module, down);
            }
        });
    }
}
