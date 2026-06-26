package relic.client.api.item;

import net.minecraft.item.ItemStack;

public final class HoveredItemTracker {
    private static volatile ItemStack hovered = ItemStack.EMPTY;

    private HoveredItemTracker() {}

    public static void set(ItemStack stack) {
        hovered = stack == null ? ItemStack.EMPTY : stack;
    }

    public static ItemStack get() {
        return hovered;
    }
}
