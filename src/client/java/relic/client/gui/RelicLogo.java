package relic.client.gui;

import imgui.ImDrawList;

import java.util.ArrayList;
import java.util.List;

public final class RelicLogo {

    private RelicLogo() {}

    private static final String[] PATHS = {
            "M 175,305 L 385,305 A 75 75 0 0 1 460 380 A 75 75 0 0 1 385 455 L 370,455 L 490,620",
            "M 175,315 L 385,315 A 65 65 0 0 1 450 380 A 65 65 0 0 1 385 445 L 360,445 L 475,620",
            "M 220,345 L 380,345 A 35 35 0 0 1 415 380 A 35 35 0 0 1 380 415 L 280,415 L 280,455 L 260,475 L 260,600",
            "M 230,355 L 380,355 A 25 25 0 0 1 405 380 A 25 25 0 0 1 380 405 L 290,405 L 290,455 L 275,470 L 275,600",
            "M 365,465 L 435,565",
            "M 350,475 L 420,575",
            "M 220,430 L 220,500 L 245,525",
            "M 170,350 L 205,385 L 205,420",
    };

    private static final float[][] NODES = {
            {175, 305}, {175, 315}, {220, 345}, {230, 355}, {280, 415},
            {280, 455}, {260, 600}, {275, 600}, {490, 620}, {475, 620},
    };

    private static final float MIN_X  = 170f;
    private static final float MIN_Y  = 305f;
    private static final float SPAN_X = 320f;
    private static final float SPAN_Y = 315f;

    public static void draw(ImDrawList dl, float ox, float oy, float size, int accent, int white) {
        float scale = size / SPAN_X;
        float offY = oy + (size - SPAN_Y * scale) * 0.5f;
        int glow = withAlpha(accent, 0.22f);
        int core = lerp(accent, white, 0.55f);

        for (String d : PATHS) {
            List<float[]> pts = parse(d);
            stroke(dl, pts, ox, offY, scale, glow,   3.2f);
            stroke(dl, pts, ox, offY, scale, accent, 1.8f);
            stroke(dl, pts, ox, offY, scale, core,   0.9f);
        }

        float nodeR = Math.max(1.4f, size * 0.05f);
        float coreR = Math.max(0.7f, size * 0.022f);
        for (float[] n : NODES) {
            float sx = ox + (n[0] - MIN_X) * scale;
            float sy = offY + (n[1] - MIN_Y) * scale;
            dl.addCircleFilled(sx, sy, nodeR, accent);
            dl.addCircleFilled(sx, sy, coreR, white);
        }
    }

    private static void stroke(ImDrawList dl, List<float[]> pts, float ox, float offY, float scale, int col, float w) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            float[] a = pts.get(i);
            float[] b = pts.get(i + 1);
            dl.addLine(ox + (a[0] - MIN_X) * scale, offY + (a[1] - MIN_Y) * scale,
                       ox + (b[0] - MIN_X) * scale, offY + (b[1] - MIN_Y) * scale, col, w);
        }
    }

    private static List<float[]> parse(String d) {
        List<float[]> pts = new ArrayList<>();
        String[] t = d.trim().split("[ ,]+");
        int i = 0;
        char cmd = 0;
        float cx = 0, cy = 0;
        while (i < t.length) {
            char c0 = t[i].charAt(0);
            if (Character.isLetter(c0)) {
                cmd = c0;
                i++;
            }
            switch (cmd) {
                case 'M', 'L' -> {
                    cx = Float.parseFloat(t[i++]);
                    cy = Float.parseFloat(t[i++]);
                    pts.add(new float[]{cx, cy});
                }
                case 'A' -> {
                    float r = Float.parseFloat(t[i++]);
                    i += 2;
                    int large = (int) Float.parseFloat(t[i++]);
                    int sweep = (int) Float.parseFloat(t[i++]);
                    float x = Float.parseFloat(t[i++]);
                    float y = Float.parseFloat(t[i++]);
                    arc(pts, cx, cy, r, large, sweep, x, y);
                    cx = x;
                    cy = y;
                }
                default -> i++;
            }
        }
        return pts;
    }

    private static void arc(List<float[]> pts, float x1, float y1, float r, int large, int sweep, float x2, float y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 1e-4) {
            pts.add(new float[]{x2, y2});
            return;
        }
        double rr = r;
        if (dist > 2 * rr) rr = dist / 2;
        double mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
        double h = Math.sqrt(Math.max(0, rr * rr - dist * dist / 4));
        double ux = -dy / dist, uy = dx / dist;
        double sign = (large != sweep) ? 1 : -1;
        double ccx = mx + sign * h * ux;
        double ccy = my + sign * h * uy;
        double a1 = Math.atan2(y1 - ccy, x1 - ccx);
        double a2 = Math.atan2(y2 - ccy, x2 - ccx);
        if (sweep == 1) {
            if (a2 < a1) a2 += 2 * Math.PI;
        } else {
            if (a2 > a1) a2 -= 2 * Math.PI;
        }
        int segs = Math.max(2, (int) Math.ceil(Math.abs(a2 - a1) / (Math.PI / 24)));
        for (int k = 1; k <= segs; k++) {
            double tt = a1 + (a2 - a1) * k / segs;
            pts.add(new float[]{(float) (ccx + rr * Math.cos(tt)), (float) (ccy + rr * Math.sin(tt))});
        }
    }

    private static int lerp(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int fa = (from >> 24) & 0xFF, fb = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fr = from & 0xFF;
        int ta = (to   >> 24) & 0xFF, tb = (to   >> 16) & 0xFF, tg = (to   >> 8) & 0xFF, tr = to   & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int b = (int) (fb + (tb - fb) * t);
        int g = (int) (fg + (tg - fg) * t);
        int r = (int) (fr + (tr - fr) * t);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int withAlpha(int abgr, float f) {
        int a = (int) (((abgr >> 24) & 0xFF) * Math.max(0, Math.min(1, f)));
        return (a << 24) | (abgr & 0x00FFFFFF);
    }
}
