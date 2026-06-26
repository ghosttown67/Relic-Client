package relic.client.module.setting;

public class ModeSetting extends Setting<String> {
    private final String[] modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name, defaultValue);
        this.modes = modes;
    }

    public String[] getModes() {
        return modes;
    }

    public int getIndex() {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(getValue())) return i;
        }
        return 0;
    }

    public void setIndex(int index) {
        if (index >= 0 && index < modes.length) setValue(modes[index]);
    }

    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }
}
