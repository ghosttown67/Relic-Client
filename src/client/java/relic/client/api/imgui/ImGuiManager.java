package relic.client.api.imgui;

import imgui.ImGui;
import imgui.flag.ImGuiConfigFlags;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import relic.client.RelicClient;

@Environment(EnvType.CLIENT)
public class ImGuiManager {
    private static ImGuiManager instance;
    private boolean initialized = false;
    private boolean initializationAttempted = false;

    public static ImGuiManager getInstance() {
        if (instance == null) {
            instance = new ImGuiManager();
        }
        return instance;
    }

    public void initialize() {

    }

    private void tryInitialize() {
        if (initialized || initializationAttempted) return;

        initializationAttempted = true;

        try {

            NativeLibraryLoader.setupLibraryPath();

            ImGui.createContext();
            ImGui.getIO().addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
            ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable);
            ImGui.getStyle().setWindowRounding(5.0f);

            ImGui.styleColorsDark();

            initialized = true;
            RelicClient.LOGGER.info("ImGui initialized successfully!");
        } catch (UnsatisfiedLinkError e) {
            RelicClient.LOGGER.error("Failed to load ImGui native library. Make sure imgui-java natives are in the classpath.", e);
        } catch (Exception e) {
            RelicClient.LOGGER.error("Failed to initialize ImGui: ", e);
        }
    }

    public void render() {
        if (!initialized) {
            tryInitialize();
            if (!initialized) return;
        }

        try {
            ImGui.render();
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } catch (Exception e) {
            RelicClient.LOGGER.error("Error during ImGui render: ", e);
        }
    }

    public void shutdown() {
        if (!initialized) return;
        try {
            ImGui.destroyContext();
            initialized = false;
            initializationAttempted = false;
        } catch (Exception e) {
            RelicClient.LOGGER.error("Error during ImGui shutdown: ", e);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
