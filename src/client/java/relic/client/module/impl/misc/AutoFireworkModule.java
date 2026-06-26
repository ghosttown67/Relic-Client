package relic.client.module.impl.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

public class AutoFireworkModule extends Module {

    private final NumberSetting minDelay  = new NumberSetting("Min Delay", 10, 0, 60, false);
    private final BooleanSetting autoSwap = new BooleanSetting("Auto Swap", true);
    private final BooleanSetting swapBack = new BooleanSetting("Swap Back", true);

    private int ticksSinceUse = Integer.MAX_VALUE;

    public AutoFireworkModule() {
        super("AutoFirework", "Re-uses a firework when the last boost fades while gliding", Category.MISC);
        addSettings(minDelay, autoSwap, swapBack);
    }

    @Override
    protected void onEnable() {
        ticksSinceUse = Integer.MAX_VALUE;
    }

    @Override
    public void onPreTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (player.isSpectator()) return;

        if (ticksSinceUse != Integer.MAX_VALUE) ticksSinceUse++;

        if (!player.isGliding()) return;

        if (ticksSinceUse < minDelay.getValue()) return;

        if (hasActiveBoost(mc, player)) return;

        Hand hand = findFireworkHand(player);
        int previousSlot = -1;

        if (hand == null) {
            if (!autoSwap.isOn()) return;
            int slot = findFireworkHotbarSlot(player);
            if (slot < 0) return;
            previousSlot = player.getInventory().getSelectedSlot();
            selectSlot(mc, player, slot);
            hand = Hand.MAIN_HAND;
        }

        mc.interactionManager.interactItem(player, hand);
        player.swingHand(hand);
        ticksSinceUse = 0;

        if (previousSlot >= 0 && swapBack.isOn()) {
            selectSlot(mc, player, previousSlot);
        }
    }

    private boolean hasActiveBoost(MinecraftClient mc, ClientPlayerEntity player) {
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof FireworkRocketEntity firework
                    && firework.isAlive()
                    && firework.getOwner() == player) {
                return true;
            }
        }
        return false;
    }

    private Hand findFireworkHand(ClientPlayerEntity player) {
        if (player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) return Hand.MAIN_HAND;
        if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET))  return Hand.OFF_HAND;
        return null;
    }

    private int findFireworkHotbarSlot(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.HOTBAR_SIZE; slot++) {
            ItemStack stack = inv.getStack(slot);
            if (stack.isOf(Items.FIREWORK_ROCKET)) return slot;
        }
        return -1;
    }

    private void selectSlot(MinecraftClient mc, ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }
}
