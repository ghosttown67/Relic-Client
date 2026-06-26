package relic.client.module.setting;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class MultiSelectSetting extends Setting<Set<String>> {
    private final String[] options;

    public MultiSelectSetting(String name, String[] options, String... defaultOn) {
        super(name, new LinkedHashSet<>(Arrays.asList(defaultOn)));
        this.options = options;
    }

    public String[] getOptions() {
        return options;
    }

    public boolean isSelected(String option) {
        return getValue().contains(option);
    }

    public void toggle(String option) {
        if (!getValue().remove(option)) {
            getValue().add(option);
        }
        notifyChanged();
    }

    public String getSummary() {
        int n = getValue().size();
        if (n == 0) return "None";
        if (n == options.length) return "All";
        return n + " selected";
    }
}
