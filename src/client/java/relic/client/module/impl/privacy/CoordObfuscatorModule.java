package relic.client.module.impl.privacy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ModeSetting;

public class CoordObfuscatorModule extends Module {

    private static CoordObfuscatorModule instance;

    private final BooleanSetting hideF3 = new BooleanSetting("Hide F3 Coords", true);
    private final BooleanSetting hideServerIp = new BooleanSetting("Hide Server IP", true);

    private final ModeSetting mode = new ModeSetting("Mask", "Censor", "Censor", "Zero");

    private static final String[] COORD_PREFIXES = {
            "XYZ:", "Block:", "Chunk:", "Targeted Block:", "Targeted Fluid:"
    };

    private static final String IP_MASK = "<hidden>";

    public CoordObfuscatorModule() {
        super("CoordObfuscator", "Masks F3 coordinates and the server IP for screenshots", Category.PRIVACY);
        addSettings(hideF3, hideServerIp, mode);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static String redactString(String in) {
        if (in == null || in.isEmpty() || !isActive()) return in;
        String out = in;
        if (instance.hideF3.isOn()) out = maskDebugCoords(out);
        if (instance.hideServerIp.isOn()) out = maskServerIp(out);
        return out;
    }

    private static String maskDebugCoords(String in) {
        for (String prefix : COORD_PREFIXES) {
            if (in.startsWith(prefix)) {
                return prefix + " " + instance.coordMask();
            }
        }
        return in;
    }

    private String coordMask() {
        return mode.is("Zero") ? "0 0 0" : "■ ■ ■";
    }

    private static String maskServerIp(String in) {
        String ip = currentServerAddress();
        if (ip == null || ip.isEmpty() || !in.contains(ip)) return in;
        return in.replace(ip, IP_MASK);
    }

    private static String currentServerAddress() {
        ServerInfo entry = MinecraftClient.getInstance().getCurrentServerEntry();
        return entry != null ? entry.address : null;
    }
}
