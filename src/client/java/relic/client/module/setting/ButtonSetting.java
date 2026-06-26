package relic.client.module.setting;

import java.util.function.Supplier;

public class ButtonSetting extends Setting<Boolean> {

    private final Runnable action;
    private final Supplier<String> labelSupplier;

    public ButtonSetting(String name, Runnable action) {
        this(name, null, action);
    }

    public ButtonSetting(String name, Supplier<String> labelSupplier, Runnable action) {
        super(name, false);
        this.labelSupplier = labelSupplier;
        this.action = action;
    }

    public String getLabel() {
        if (labelSupplier == null) return getName();
        String l = labelSupplier.get();
        return l == null || l.isEmpty() ? getName() : l;
    }

    public void press() {
        if (action != null) action.run();
    }
}
