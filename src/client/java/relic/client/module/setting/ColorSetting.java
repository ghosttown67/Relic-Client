package relic.client.module.setting;

public class ColorSetting extends Setting<Integer> {

    public ColorSetting(String name, int argb) {
        super(name, argb);
    }

    public ColorSetting(String name, float r, float g, float b, float a) {
        super(name, pack(r, g, b, a));
    }

    public float red()   { return ((getValue() >> 16) & 0xFF) / 255f; }
    public float green() { return ((getValue() >>  8) & 0xFF) / 255f; }
    public float blue()  { return ( getValue()        & 0xFF) / 255f; }
    public float alpha() { return ((getValue() >>> 24) & 0xFF) / 255f; }

    public float[] toFloats() {
        return new float[]{red(), green(), blue(), alpha()};
    }

    public void fromFloats(float[] c) {
        setValue(pack(c[0], c[1], c[2], c[3]));
    }

    public int opaque() {
        return 0xFF000000 | (getValue() & 0x00FFFFFF);
    }

    private static int pack(float r, float g, float b, float a) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }
}
