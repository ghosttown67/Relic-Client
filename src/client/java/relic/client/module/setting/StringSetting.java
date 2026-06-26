package relic.client.module.setting;

public class StringSetting extends Setting<String> {

    private final int maxLength;

    public StringSetting(String name, String defaultValue) {
        this(name, defaultValue, 256);
    }

    public StringSetting(String name, String defaultValue, int maxLength) {
        super(name, defaultValue);
        this.maxLength = maxLength;
    }

    public int getMaxLength() {
        return maxLength;
    }
}
