package relic.client.module.impl.misc;

import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import relic.client.api.discord.DiscordRPCService;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.StringSetting;

public class DiscordRPCModule extends Module {

    private static final String APP_ID = "1517650667680825504";

    private final StringSetting largeImage =
            new StringSetting("Large Image", "relic_logo");
    private final StringSetting largeText =
            new StringSetting("Large Text", "Relic Client");
    private final BooleanSetting showServerIp =
            new BooleanSetting("Show Server IP", false);

    private long startEpochSeconds;

    private String lastState;

    public DiscordRPCModule() {
        super("DiscordRPC", "Shows Relic Client on your Discord profile (Rich Presence)", Category.MISC);
        addSettings(largeImage, largeText, showServerIp);
    }

    @Override
    protected void onEnable() {
        startEpochSeconds = System.currentTimeMillis() / 1000L;
        lastState = null;
        DiscordRPCService.getInstance().start(APP_ID);
    }

    @Override
    protected void onDisable() {
        DiscordRPCService.getInstance().stop();
    }

    @Override
    public void onTick() {
        DiscordRPCService svc = DiscordRPCService.getInstance();

        String state = computeState();
        if (state.equals(lastState)) return;
        lastState = state;

        JsonObject activity = new JsonObject();
        activity.addProperty("details", SharedConstants.getGameVersion().name());
        activity.addProperty("state", state);

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", startEpochSeconds);
        activity.add("timestamps", timestamps);

        if (!largeImage.getValue().isBlank()) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", largeImage.getValue());
            if (!largeText.getValue().isBlank()) {
                assets.addProperty("large_text", largeText.getValue());
            }
            activity.add("assets", assets);
        }

        svc.setActivity(activity);
    }

    private String computeState() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "In Main Menu";
        if (mc.isInSingleplayer()) return "Playing Singleplayer";

        ServerInfo entry = mc.getCurrentServerEntry();
        if (showServerIp.isOn() && entry != null
                && entry.address != null && !entry.address.isBlank()) {
            return "Connected to " + entry.address;
        }
        return "Playing on a server";
    }
}
