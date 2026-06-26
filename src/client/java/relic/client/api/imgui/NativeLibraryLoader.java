package relic.client.api.imgui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import relic.client.RelicClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class NativeLibraryLoader {

    public static void setupLibraryPath() {
        try {

            File minecraftDir = MinecraftClient.getInstance().runDirectory;
            Path nativesDir = minecraftDir.toPath().resolve("natives");

            if (!Files.exists(nativesDir)) {
                Files.createDirectories(nativesDir);
            }

            String libraryPath = System.getProperty("java.library.path");
            String nativePath = nativesDir.toAbsolutePath().toString();

            if (libraryPath == null || !libraryPath.contains(nativePath)) {
                String newPath = nativePath + File.pathSeparator + (libraryPath != null ? libraryPath : "");
                System.setProperty("java.library.path", newPath);

                try {
                    java.lang.reflect.Field field = ClassLoader.class.getDeclaredField("usr_paths");
                    field.setAccessible(true);
                    String[] paths = (String[]) field.get(null);
                    String[] newPaths = new String[paths.length + 1];
                    newPaths[0] = nativePath;
                    System.arraycopy(paths, 0, newPaths, 1, paths.length);
                    field.set(null, newPaths);
                } catch (Exception e) {
                    RelicClient.LOGGER.warn("Could not update ClassLoader library path", e);
                }
            }
        } catch (Exception e) {
            RelicClient.LOGGER.warn("Could not setup native library path", e);
        }
    }
}
