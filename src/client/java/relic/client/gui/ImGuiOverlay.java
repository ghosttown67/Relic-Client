package relic.client.gui;

import imgui.ImDrawList;
import imgui.ImGui;
import relic.client.module.Module;
import relic.client.module.ModuleManager;

public final class ImGuiOverlay {
    private ImGuiOverlay() {}

    public static boolean anyActive() {
        for (Module module : ModuleManager.getInstance().getAllModules()) {
            if (module.isEnabled() && module.wantsImGuiOverlay()) return true;
        }
        return false;
    }

    public static void render() {
        ImDrawList drawList = ImGui.getForegroundDrawList();
        for (Module module : ModuleManager.getInstance().getAllModules()) {
            if (module.isEnabled() && module.wantsImGuiOverlay()) {
                module.onImGuiRender(drawList);
            }
        }
    }
}
