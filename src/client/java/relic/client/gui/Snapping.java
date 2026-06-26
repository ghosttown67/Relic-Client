package relic.client.gui;

public final class Snapping {

    private Snapping() {}

    public static final float THRESHOLD = 6f;

    public static final float GRID = 3f;

    public static final class Result {
        public float x, y;
        public float guideX = Float.NaN;
        public float guideY = Float.NaN;
    }

    public static Result snap(float x, float y, float w, float h, float[] vlines, float[] hlines) {
        Result r = new Result();

        float best = THRESHOLD;
        r.x = x;
        for (float v : vlines) {
            best = tryAxisX(r, x, w, v, best, 0f);
            best = tryAxisX(r, x, w, v, best, w);
            best = tryAxisX(r, x, w, v, best, w * 0.5f);
        }
        if (Float.isNaN(r.guideX)) r.x = Math.round(x / GRID) * GRID;

        best = THRESHOLD;
        r.y = y;
        for (float hl : hlines) {
            best = tryAxisY(r, y, h, hl, best, 0f);
            best = tryAxisY(r, y, h, hl, best, h);
            best = tryAxisY(r, y, h, hl, best, h * 0.5f);
        }
        if (Float.isNaN(r.guideY)) r.y = Math.round(y / GRID) * GRID;

        return r;
    }

    private static float tryAxisX(Result r, float x, float w, float v, float best, float offset) {
        float d = Math.abs((x + offset) - v);
        if (d < best) {
            r.x = v - offset;
            r.guideX = v;
            return d;
        }
        return best;
    }

    private static float tryAxisY(Result r, float y, float h, float hl, float best, float offset) {
        float d = Math.abs((y + offset) - hl);
        if (d < best) {
            r.y = hl - offset;
            r.guideY = hl;
            return d;
        }
        return best;
    }
}
