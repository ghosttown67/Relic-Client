package relic.client.module.impl.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.StringSetting;

import java.util.Locale;

public class SetHomeModule extends Module {

    private static SetHomeModule instance;

    private static final long HIDE_WINDOW_MS = 3000L;

    private final StringSetting command   = new StringSetting("Command", "sethome");
    private final StringSetting hideText   = new StringSetting("Hide Text", "Home Set");
    private final BooleanSetting hideMessage = new BooleanSetting("Hide Message", true);

    private long hideUntil = 0L;

    public SetHomeModule() {
        super("SetHome", "Silently sets a home and hides the confirmation message", Category.MISC);
        addSettings(command, hideText, hideMessage);
        instance = this;
    }

    public static SetHomeModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net == null) return;
        String cmd = command.getValue();
        if (cmd == null || cmd.isBlank()) return;

        net.sendChatCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);

        hideUntil = System.currentTimeMillis() + HIDE_WINDOW_MS;
    }

    @Override
    public void onTick() {

        if (isEnabled()) setEnabled(false);
    }

    public static boolean shouldHide(Text message) {
        if (instance == null) return false;
        if (!instance.hideMessage.isOn()) return false;
        if (System.currentTimeMillis() > instance.hideUntil) return false;
        String filter = instance.hideText.getValue();
        if (message == null || filter == null || filter.isBlank()) return false;
        return message.getString().toLowerCase(Locale.ROOT)
                .contains(filter.toLowerCase(Locale.ROOT));
    }
}
