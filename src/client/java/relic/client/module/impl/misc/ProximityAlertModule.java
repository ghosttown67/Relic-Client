package relic.client.module.impl.misc;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;
import relic.client.notification.NotificationManager;

public class ProximityAlertModule extends Module {

    private static final int NOTIFY_ACCENT = 0xFFE0A53B;

    private static final int JOIN_GRACE_TICKS = 40;

    private final NumberSetting range = new NumberSetting("Range", 0, 0, 256, true);
    private final BooleanSetting playSound = new BooleanSetting("Sound", true);

    private final IntSet notified = new IntOpenHashSet();

    private int joinTicks;

    public ProximityAlertModule() {
        super("ProximityAlert", "Alerts when a new player enters render distance", Category.MISC);
        addSettings(range, playSound);
    }

    @Override
    protected void onDisable() {
        notified.clear();
        joinTicks = 0;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {

            notified.clear();
            joinTicks = 0;
            return;
        }

        boolean grace = joinTicks < JOIN_GRACE_TICKS;
        if (grace) joinTicks++;

        double maxSq = range.getInt() <= 0 ? Double.MAX_VALUE
                : (double) range.getInt() * range.getInt();

        IntSet present = new IntOpenHashSet();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            present.add(player.getId());

            if (grace) {
                notified.add(player.getId());
                continue;
            }

            if (notified.add(player.getId()) && mc.player.squaredDistanceTo(player) <= maxSq) {
                NotificationManager.getInstance().push("ProximityAlert",
                        player.getName().getString() + " nearby", NOTIFY_ACCENT,
                        NotificationManager.DEFAULT_HOLD_MS, playSound.isOn());
            }
        }

        notified.retainAll(present);
    }
}
