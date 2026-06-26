package relic.client.module.setting;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class EntityListSetting extends Setting<Set<String>> {

    public EntityListSetting(String name, String... defaults) {
        super(name, new LinkedHashSet<>(Arrays.asList(defaults)));
    }

    public boolean isSelected(String entityPath) {
        return getValue().contains(entityPath);
    }

    public void add(String entityPath) {
        getValue().add(entityPath);
        notifyChanged();
    }

    public void remove(String entityPath) {
        getValue().remove(entityPath);
        notifyChanged();
    }
}
