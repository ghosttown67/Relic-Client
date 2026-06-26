package relic.client.gui.hud;

import imgui.ImDrawList;

public interface HudElement {

    String name();

    float getXPercent();

    float getYPercent();

    void setPercent(float xPercent, float yPercent);

    float width(float dispW, float dispH, float scale);

    float height(float dispW, float dispH, float scale);

    default void renderPreview(ImDrawList dl, float x, float y, float dispW, float dispH, float scale) {}
}
