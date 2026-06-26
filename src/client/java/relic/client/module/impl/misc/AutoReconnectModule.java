package relic.client.module.impl.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

public class AutoReconnectModule extends Module {

    private static AutoReconnectModule instance;

    private static final Text IDLE_LABEL = Text.literal("Auto Reconnect");

    private final NumberSetting delay = new NumberSetting("Delay", 3, 0, 60, true);

    private final BooleanSetting hideButtons = new BooleanSetting("Hide Buttons", false);

    private ServerInfo lastServer;

    private boolean counting;

    private long reconnectAt;

    private ButtonWidget autoButton;

    public AutoReconnectModule() {
        super("AutoReconnect", "Reconnects to the last server after a disconnect", Category.MISC);
        addSettings(delay, hideButtons);
        instance = this;
    }

    public static AutoReconnectModule getInstance() {
        return instance;
    }

    public boolean hideButtons() {
        return hideButtons.isOn();
    }

    public boolean canReconnect() {
        return lastServer != null;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    public void onDisconnectScreen(ButtonWidget autoButton) {
        this.autoButton = autoButton;
        this.counting = true;
        this.reconnectAt = System.currentTimeMillis() + delay.getInt() * 1000L;
    }

    public void toggleCountdown() {
        if (counting) {
            counting = false;
            if (autoButton != null) autoButton.setMessage(IDLE_LABEL);
        } else {
            onDisconnectScreen(autoButton);
        }
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null) {
            ServerInfo current = mc.getCurrentServerEntry();
            if (current != null) lastServer = current;
            reset();
            return;
        }

        if (!(mc.currentScreen instanceof DisconnectedScreen) || !counting || !canReconnect()) {
            return;
        }

        long remaining = reconnectAt - System.currentTimeMillis();
        if (remaining <= 0) {
            counting = false;
            reconnect();
            return;
        }

        if (autoButton != null) {
            int secs = (int) Math.ceil(remaining / 1000.0);
            autoButton.setMessage(Text.literal("Reconnect in " + secs + "s"));
        }
    }

    public void reconnect() {
        if (lastServer == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo info = lastServer;
        ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc,
                ServerAddress.parse(info.address), info, false, null);
    }

    private void reset() {
        counting = false;
        autoButton = null;
    }
}
