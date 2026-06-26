package relic.client.module.setting;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class BlockListSetting extends Setting<Set<String>> {

    public BlockListSetting(String name, String... defaultBlocks) {
        super(name, new LinkedHashSet<>(Arrays.asList(defaultBlocks)));
    }

    public boolean isSelected(String blockPath) {
        return getValue().contains(blockPath);
    }

    public void add(String blockPath) {
        getValue().add(blockPath);
        notifyChanged();
    }

    public void remove(String blockPath) {
        getValue().remove(blockPath);
        notifyChanged();
    }
}
