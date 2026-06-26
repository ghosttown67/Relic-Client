package relic.client.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import relic.client.config.ClientSettings;
import relic.client.keybinds.KeyBinds;
import relic.client.gui.screen.ClickGuiScreen;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ClientTickEvent {
    private static final Map<Integer, Boolean> keyStates = new HashMap<>();
    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> onClientTick(mc));
    }

    public static void primeKey(int keyCode) {
        keyStates.put(keyCode, true);
    }

    private static void onClientTick(MinecraftClient client) {

        int guiKeyCode = ClientSettings.getOpenGuiKey();
        boolean isPressed = KeyBinds.isKeyPressed(guiKeyCode);
        boolean wasPressedLastTick = keyStates.getOrDefault(guiKeyCode, false);

        if (isPressed && !wasPressedLastTick) {

            if (client.currentScreen instanceof ClickGuiScreen gui) {
                client.setScreen(gui.getParent());
            } else if (client.currentScreen == null
                    || client.currentScreen instanceof TitleScreen
                    || client.currentScreen instanceof DeathScreen) {
                client.setScreen(new ClickGuiScreen(client.currentScreen));
            }
        }

        keyStates.put(guiKeyCode, isPressed);
    }
}
