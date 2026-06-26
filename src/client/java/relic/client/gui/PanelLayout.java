package relic.client.gui;

import relic.client.module.Module;

import java.util.EnumMap;
import java.util.Map;

public final class PanelLayout {

    private PanelLayout() {}

    public static final float PANEL_W = 190f;

    private static final float MARGIN_X = 24f;
    private static final float TOP      = 70f;
    private static final float GAP      = 12f;

    private static final Map<Module.Category, float[]> POS = new EnumMap<>(Module.Category.class);

    public static float[] get(Module.Category category) {
        return POS.computeIfAbsent(category, PanelLayout::defaultFor);
    }

    public static void set(Module.Category category, float x, float y) {
        float[] p = get(category);
        p[0] = x;
        p[1] = y;
    }

    private static float[] defaultFor(Module.Category category) {
        int i = category.ordinal();
        return new float[]{MARGIN_X + i * (PANEL_W + GAP), TOP};
    }

    public static void reset() {
        POS.clear();
    }

    public static void tidy(float screenW) {
        float x = MARGIN_X;
        float y = TOP;
        for (Module.Category c : Module.Category.values()) {
            if (x + PANEL_W > screenW - MARGIN_X && x > MARGIN_X) {
                x = MARGIN_X;
                y += 220f;
            }
            set(c, x, y);
            x += PANEL_W + GAP;
        }
    }
}
