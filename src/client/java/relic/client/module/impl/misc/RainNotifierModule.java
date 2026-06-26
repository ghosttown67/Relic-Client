package relic.client.module.impl.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import relic.client.api.discord.DiscordWebhook;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.notification.NotificationManager;

import java.util.LinkedHashMap;
import java.util.Map;

public class RainNotifierModule extends Module {

    private static final int RAIN_ACCENT = 0xFF4FA3FF;
    private static final int THUNDER_ACCENT = 0xFFB388FF;

    private final BooleanSetting thunder = new BooleanSetting("Thunder", true);
    private final BooleanSetting sound = new BooleanSetting("Sound", true);
    private final BooleanSetting webhook = new BooleanSetting("Discord Webhook", false);

    private Boolean wasRaining;
    private Boolean wasThundering;

    public RainNotifierModule() {
        super("RainNotifier", "Notifies when it starts or stops raining", Category.MISC);
        addSettings(thunder, sound, webhook);
    }

    @Override
    protected void onEnable() {

        wasRaining = null;
        wasThundering = null;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) {

            wasRaining = null;
            wasThundering = null;
            return;
        }

        boolean raining = world.isRaining();
        boolean thundering = world.isThundering();

        if (wasRaining != null && raining != wasRaining) {
            alert(raining ? "Rain started" : "Rain stopped", RAIN_ACCENT);
        }
        if (thunder.isOn() && wasThundering != null && thundering != wasThundering) {
            alert(thundering ? "Thunderstorm started" : "Thunderstorm stopped", THUNDER_ACCENT);
        }

        wasRaining = raining;
        wasThundering = thundering;
    }

    private void alert(String message, int accent) {
        NotificationManager.getInstance().push("Weather", message, accent,
                NotificationManager.DEFAULT_HOLD_MS, sound.isOn());

        if (webhook.isOn()) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("Dimension", dimensionName());
            fields.put("Server", serverAddress());
            DiscordWebhook.send("Weather", message, accent, fields);
        }
    }

    private String dimensionName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "Unknown";
        Identifier id = mc.world.getRegistryKey().getValue();
        String path = id.getPath();
        return switch (path) {
            case "overworld"  -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end"    -> "End";
            default -> prettify(path);
        };
    }

    private String serverAddress() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo entry = mc.getCurrentServerEntry();
        if (entry != null && entry.address != null && !entry.address.isBlank()) {
            return entry.address;
        }
        if (mc.isInSingleplayer()) return "Singleplayer";
        return "Unknown";
    }

    private static String prettify(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
