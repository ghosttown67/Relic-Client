package relic.client.gui.panel;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class BasePanel {
    protected String title;
    protected float posX;
    protected float posY;
    protected float sizeX;
    protected float sizeY;
    protected boolean open = true;

    public BasePanel(String title, float posX, float posY, float sizeX, float sizeY) {
        this.title = title;
        this.posX = posX;
        this.posY = posY;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
    }

    public void render() {
        if (!open) return;

        try {
            ImGui.setNextWindowPos(posX, posY, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowSize(sizeX, sizeY, ImGuiCond.FirstUseEver);

            boolean visible = ImGui.begin(title);
            if (visible) {
                renderContent();

                posX = ImGui.getWindowPosX();
                posY = ImGui.getWindowPosY();
                sizeX = ImGui.getWindowWidth();
                sizeY = ImGui.getWindowHeight();
            }
            ImGui.end();
        } catch (Exception e) {

        }
    }

    protected abstract void renderContent();

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public String getTitle() {
        return title;
    }

    public float getPosX() {
        return posX;
    }

    public float getPosY() {
        return posY;
    }

    public float getSizeX() {
        return sizeX;
    }

    public float getSizeY() {
        return sizeY;
    }
}
