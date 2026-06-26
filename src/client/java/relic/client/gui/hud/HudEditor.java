package relic.client.gui.hud;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import relic.client.gui.ImGuiManager;
import relic.client.gui.Snapping;
import relic.client.gui.theme.ThemeManager;
import relic.client.module.Module;
import relic.client.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

public final class HudEditor {

    private HudEditor() {}

    private static HudElement dragging;
    private static float grabX, grabY;

    public static List<HudElement> collect() {
        List<HudElement> list = new ArrayList<>();
        for (Module m : ModuleManager.getInstance().getAllModules()) {
            if (m.isEnabled() && m instanceof HudProvider provider) {
                list.addAll(provider.hudElements());
            }
        }
        return list;
    }

    public static void render(ImDrawList dl) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float dispW = ImGui.getIO().getDisplaySizeX();
        float dispH = ImGui.getIO().getDisplaySizeY();
        if (dispW <= 0 || dispH <= 0) return;
        float scale = mc.getWindow() != null ? (float) mc.getWindow().getScaleFactor() : 1f;

        ImFont font = ImGuiManager.getTextFont();
        if (font == null) font = ImGui.getFont();
        int accent = col(ThemeManager.get().accent());
        int frameIdle = col(withA(ThemeManager.get().text(), 0x66));
        int label = col(ThemeManager.get().text());

        List<HudElement> elements = collect();

        float mx = ImGui.getIO().getMousePosX();
        float my = ImGui.getIO().getMousePosY();
        boolean down = ImGui.isMouseDown(0);

        String help = elements.isEmpty()
                ? "Enable an overlay module (HUD, Media Controller, Inventory HUD) to position it here."
                : "Drag any element to move it. It snaps to the screen edges, centre and the other elements.";
        float helpW = font.calcTextSizeAX(15f, Float.MAX_VALUE, 0f, help);
        dl.addText(font, 15, (dispW - helpW) / 2f, 58f, label, help);

        float[][] boxes = new float[elements.size()][];
        for (int i = 0; i < elements.size(); i++) {
            boxes[i] = box(elements.get(i), dispW, dispH, scale);
        }

        if (dragging != null) {
            if (!down || elements.indexOf(dragging) < 0) {
                dragging = null;
            } else {
                int di = elements.indexOf(dragging);
                float w = boxes[di][2];
                float h = boxes[di][3];
                float nx = mx - grabX;
                float ny = my - grabY;

                float[][] lines = snapLines(elements, boxes, di, dispW, dispH);
                Snapping.Result r = Snapping.snap(nx, ny, w, h, lines[0], lines[1]);
                nx = clamp(r.x, 0f, dispW - w);
                ny = clamp(r.y, 0f, dispH - h);

                dragging.setPercent(toPercent(nx, dispW - w), toPercent(ny, dispH - h));
                boxes[di] = new float[]{nx, ny, w, h};

                if (!Float.isNaN(r.guideX)) dl.addLine(r.guideX, 0, r.guideX, dispH, accent, 1f);
                if (!Float.isNaN(r.guideY)) dl.addLine(0, r.guideY, dispW, r.guideY, accent, 1f);
            }
        } else if (ImGui.isMouseClicked(0)) {

            for (int i = elements.size() - 1; i >= 0; i--) {
                float[] b = boxes[i];
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                    dragging = elements.get(i);
                    grabX = mx - b[0];
                    grabY = my - b[1];
                    break;
                }
            }
        }

        for (int i = 0; i < elements.size(); i++) {
            HudElement e = elements.get(i);
            float[] b = boxes[i];
            float x = b[0], y = b[1], w = b[2], h = b[3];

            e.renderPreview(dl, x, y, dispW, dispH, scale);

            boolean active = e == dragging;
            boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
            int frameCol = (active || hovered) ? accent : frameIdle;

            dl.addRect(x - 2, y - 2, x + w + 2, y + h + 2, frameCol, 3f, 0, active ? 1.6f : 1f);

            float tagY = y - 16f >= 56f ? y - 15f : y + h + 4f;
            dl.addText(font, 13, x, tagY, frameCol, e.name());
        }
    }

    public static void clearDrag() {
        dragging = null;
    }

    private static float[] box(HudElement e, float dispW, float dispH, float scale) {
        float w = Math.max(8f, e.width(dispW, dispH, scale));
        float h = Math.max(8f, e.height(dispW, dispH, scale));
        float x = (dispW - w) * clamp(e.getXPercent(), 0f, 100f) / 100f;
        float y = (dispH - h) * clamp(e.getYPercent(), 0f, 100f) / 100f;
        return new float[]{x, y, w, h};
    }

    private static float[][] snapLines(List<HudElement> elements, float[][] boxes, int skip,
                                       float dispW, float dispH) {
        List<Float> v = new ArrayList<>();
        List<Float> h = new ArrayList<>();
        v.add(0f); v.add(dispW * 0.5f); v.add(dispW);
        h.add(0f); h.add(dispH * 0.5f); h.add(dispH);
        for (int i = 0; i < boxes.length; i++) {
            if (i == skip) continue;
            float[] b = boxes[i];
            v.add(b[0]); v.add(b[0] + b[2]); v.add(b[0] + b[2] * 0.5f);
            h.add(b[1]); h.add(b[1] + b[3]); h.add(b[1] + b[3] * 0.5f);
        }
        return new float[][]{toArr(v), toArr(h)};
    }

    private static float toPercent(float pos, float free) {
        return free <= 0f ? 0f : clamp(pos / free * 100f, 0f, 100f);
    }

    private static float[] toArr(List<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int withA(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static int col(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
