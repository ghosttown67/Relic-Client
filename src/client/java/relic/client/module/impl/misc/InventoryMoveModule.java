package relic.client.module.impl.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import relic.client.client.mixin.KeyBindingAccessor;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;

public class InventoryMoveModule extends Module {

    private static InventoryMoveModule instance;

    private final BooleanSetting allScreens =
            new BooleanSetting("All Screens", false);

    public InventoryMoveModule() {
        super("InventoryMove", "Move around while a GUI (inventory/container) is open", Category.MISC);
        addSettings(allScreens);
        instance = this;
    }

    public static InventoryMoveModule getInstance() {
        return instance;
    }

    public boolean shouldApply() {
        if (!isEnabled()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen == null) return false;
        return allScreens.getValue() || mc.currentScreen instanceof HandledScreen;
    }

    public boolean isBoundKeyHeld(KeyBinding binding) {
        InputUtil.Key key = ((KeyBindingAccessor) (Object) binding).relic$getBoundKey();

        if (key.getCategory() != InputUtil.Type.KEYSYM) return false;
        return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), key.getCode());
    }
}
