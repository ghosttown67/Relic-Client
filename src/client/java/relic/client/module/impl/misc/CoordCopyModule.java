package relic.client.module.impl.misc;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ButtonSetting;
import relic.client.notification.NotificationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class CoordCopyModule extends Module {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("h:mm:ss a M/d/yyyy", Locale.US);

    private final BooleanSetting notify =
            new BooleanSetting("Notify", true);
    private final ButtonSetting copyNow =
            new ButtonSetting("Copy now", this::capture);

    public CoordCopyModule() {
        super("CoordCopy", "Silently copies your coords to the clipboard and a file", Category.MISC);
        addSettings(notify, copyNow);
    }

    @Override
    protected void onEnable() {
        capture();

        setEnabled(false);
    }

    private void capture() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());

        String coords = x + " " + y + " " + z;
        String server = currentServer(mc);
        String dimension = mc.world != null
                ? mc.world.getRegistryKey().getValue().toString() : "unknown";
        String time = LocalDateTime.now().format(TIME_FMT);

        mc.keyboard.setClipboard(coords);

        String entry = "time " + time + "\n"
                + "server " + server + "\n"
                + "coordinates " + coords + "\n"
                + "dimension " + dimension + "\n\n";
        boolean saved = appendEntry(entry);

        if (notify.getValue()) {
            NotificationManager.getInstance().push(
                    "Coords copied",
                    saved ? coords : coords + " (file write failed)");
        }
    }

    private static String currentServer(MinecraftClient mc) {
        ServerInfo entry = mc.getCurrentServerEntry();
        if (entry != null && entry.address != null && !entry.address.isBlank()) {
            return entry.address;
        }
        if (mc.isInSingleplayer()) return "singleplayer";
        return "unknown";
    }

    private static boolean appendEntry(String entry) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir()
                    .resolve("relic").resolve("saved-coords");
            Files.createDirectories(dir);
            Path file = dir.resolve("coords.txt");
            Files.writeString(file, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            System.err.println("[Relic Client] CoordCopy: failed to write saved-coords:");
            e.printStackTrace();
            return false;
        }
    }
}
