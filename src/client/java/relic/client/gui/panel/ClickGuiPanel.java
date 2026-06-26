package relic.client.gui.panel;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ClickGuiPanel extends BasePanel {
    private float primaryColorR = 0.14f;
    private float primaryColorG = 0.20f;
    private float primaryColorB = 0.53f;
    private float bgColorR = 0.12f;
    private float bgColorG = 0.12f;
    private float bgColorB = 0.18f;

    public ClickGuiPanel() {
        super("Relic Client##main", 100, 100, 400, 300);
    }

    @Override
    protected void renderContent() {
        ImGui.text("Welcome to Relic Client!");
        ImGui.separator();

        if (ImGui.collapsingHeader("Appearance##appearance")) {
            ImGui.text("Primary Color:");
            ImGui.sameLine();
            ImGui.colorEdit3("##primaryColor", new float[]{primaryColorR, primaryColorG, primaryColorB});

            ImGui.text("Background Color:");
            ImGui.sameLine();
            ImGui.colorEdit3("##bgColor", new float[]{bgColorR, bgColorG, bgColorB});
        }

        ImGui.spacing();
        ImGui.separator();

        ImGui.text("Status: Ready");
        ImGui.sameLine();
        ImGui.bullet();

        if (ImGui.button("Close GUI", 100, 25)) {
            this.open = false;
        }
    }
}
