package relic.client.module.setting;

public class NumberSetting extends Setting<Float> {
    private final float min;
    private final float max;
    private final boolean integer;

    public NumberSetting(String name, float defaultValue, float min, float max) {
        this(name, defaultValue, min, max, false);
    }

    public NumberSetting(String name, float defaultValue, float min, float max, boolean integer) {
        super(name, integer ? (float) Math.round(defaultValue) : defaultValue);
        this.min = min;
        this.max = max;
        this.integer = integer;
    }

    @Override
    public void setValue(Float value) {
        float v = Math.max(min, Math.min(max, value));
        super.setValue(integer ? (float) Math.round(v) : v);
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    public boolean isInteger() {
        return integer;
    }

    public int getInt() {
        return Math.round(getValue());
    }
}
