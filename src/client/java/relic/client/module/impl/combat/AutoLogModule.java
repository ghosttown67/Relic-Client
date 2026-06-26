package relic.client.module.impl.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;
import relic.client.notification.NotificationManager;

public class AutoLogModule extends Module {

    private static final int NOTIFY_ACCENT = 0xFFE0563B;

    private final NumberSetting health = new NumberSetting("Health", 4, 1, 19, true);

    private final BooleanSetting disableOnLog = new BooleanSetting("Disable On Log", false);

    private boolean fired;

    public AutoLogModule() {
        super("AutoLog", "Disconnects when your health drops too low", Category.COMBAT);
        addSettings(health, disableOnLog);
    }

    @Override
    protected void onEnable() {
        fired = false;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (mc.player == null || net == null) {
            fired = false;
            return;
        }

        float hp = mc.player.getHealth();
        if (hp <= 0 || hp > health.getInt()) {

            if (hp > health.getInt()) fired = false;
            return;
        }
        if (fired) return;
        fired = true;

        String reason = "[AutoLog] Health <= " + health.getInt();
        NotificationManager.getInstance().push("AutoLog", reason, NOTIFY_ACCENT,
                NotificationManager.DEFAULT_HOLD_MS, true);
        net.getConnection().disconnect(Text.literal(reason));

        if (disableOnLog.isOn()) setEnabled(false);
    }
}
