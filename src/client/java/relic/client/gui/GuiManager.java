package relic.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import relic.client.api.imgui.ImGuiManager;
import relic.client.gui.panel.BasePanel;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class GuiManager {
    private static GuiManager instance;
    private final List<BasePanel> panels = new ArrayList<>();
    private boolean guiOpen = true;
    private boolean initialized = false;

    public static GuiManager getInstance() {
        if (instance == null) {
            instance = new GuiManager();
        }
        return instance;
    }

    public void initialize() {
        if (initialized) return;
        initialized = true;
    }

    public void registerPanel(BasePanel panel) {
        if (!panels.contains(panel)) {
            panels.add(panel);
        }
    }

    public void unregisterPanel(BasePanel panel) {
        panels.remove(panel);
    }

    public void toggleGui() {
        guiOpen = !guiOpen;
    }

    public void render() {
        if (!initialized) return;

        ImGuiManager imGuiManager = ImGuiManager.getInstance();

        if (!imGuiManager.isInitialized()) {

            return;
        }

        if (guiOpen) {
            try {
                for (BasePanel panel : panels) {
                    if (panel.isOpen()) {
                        panel.render();
                    }
                }
            } catch (Exception e) {

            }
        }
    }

    public boolean isGuiOpen() {
        return guiOpen;
    }

    public List<BasePanel> getPanels() {
        return new ArrayList<>(panels);
    }

    public void shutdown() {
        initialized = false;
    }
}
