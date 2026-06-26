package relic.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import relic.client.event.ClientTickEvent;
import relic.client.event.RenderEvent;
import relic.client.gui.GuiManager;
import relic.client.gui.panel.ClickGuiPanel;

@Environment(EnvType.CLIENT)
public class RelicClientClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {

        GuiManager guiManager = GuiManager.getInstance();
        guiManager.initialize();

        guiManager.registerPanel(new ClickGuiPanel());

        ClientTickEvent.register();
        RenderEvent.register();

        RelicClient.LOGGER.info("Relic Client initialized!");
    }
}
