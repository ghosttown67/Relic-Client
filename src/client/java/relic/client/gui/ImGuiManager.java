package relic.client.gui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

public class ImGuiManager {
    private static ImGuiManager instance;
    private ImGuiImplGlfw imGuiGlfw;
    private ImGuiImplGl3 imGuiGl3;
    private boolean initialized = false;

    private boolean frameStarted = false;

    private static ImFont textFont;

    private static ImFont titleFont;

    private static ImFont overlayFont;

    private ImGuiManager() {}

    public static ImGuiManager getInstance() {
        if (instance == null) {
            instance = new ImGuiManager();
        }
        return instance;
    }

    public void init() {
        if (initialized) return;

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            long glfwWindow = client.getWindow().getHandle();

            ImGui.createContext();
            ImGui.getIO().addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);

            ImGui.getIO().setConfigInputTrickleEventQueue(false);
            ImGui.styleColorsDark();

            loadFonts();

            imGuiGlfw = new ImGuiImplGlfw();

            imGuiGlfw.init(glfwWindow, true);

            ImGui.getIO().setSetClipboardTextFn(new ImStrConsumer() {
                @Override
                public void accept(final String text) {
                    GLFW.glfwSetClipboardString(glfwWindow, text);
                }
            });
            ImGui.getIO().setGetClipboardTextFn(new ImStrSupplier() {
                @Override
                public String get() {
                    final String text = GLFW.glfwGetClipboardString(glfwWindow);
                    return text != null ? text : "";
                }
            });

            imGuiGl3 = new ImGuiImplGl3();
            imGuiGl3.init("#version 330 core");

            initialized = true;
            System.out.println("[Relic Client] ImGui initialized successfully!");
        } catch (Exception e) {
            System.err.println("[Relic Client] Failed to initialize ImGui:");
            e.printStackTrace();
        }
    }

    private void loadFonts() {
        try {
            byte[] vendSans = readResource("/fonts/VendSans-Regular.ttf");
            var fonts = ImGui.getIO().getFonts();

            textFont    = fonts.addFontFromMemoryTTF(vendSans, 18f, crispConfig());
            titleFont   = fonts.addFontFromMemoryTTF(vendSans, 22f, crispConfig());
            overlayFont = fonts.addFontFromMemoryTTF(vendSans, 32f, crispConfig());
            fonts.build();
        } catch (Exception e) {
            textFont = null;
            titleFont = null;
            overlayFont = null;
            System.err.println("[Relic Client] Failed to load fonts, using ImGui default:");
            e.printStackTrace();
        }
    }

    private static ImFontConfig crispConfig() {
        ImFontConfig cfg = new ImFontConfig();
        cfg.setOversampleH(3);
        cfg.setOversampleV(3);
        cfg.setRasterizerDensity(1.5f);
        return cfg;
    }

    private byte[] readResource(String path) throws java.io.IOException {
        try (java.io.InputStream in = ImGuiManager.class.getResourceAsStream(path)) {
            if (in == null) throw new java.io.IOException("Resource not found: " + path);
            return in.readAllBytes();
        }
    }

    public static ImFont getTextFont() {
        return textFont;
    }

    public static ImFont getTitleFont() {
        return titleFont;
    }

    public static ImFont getOverlayFont() {
        return overlayFont;
    }

    public boolean newFrame() {
        if (!initialized) init();
        if (!initialized) return false;

        if (frameStarted) {
            try {
                ImGui.endFrame();
            } catch (Exception ignored) {

            }
            frameStarted = false;
        }

        try {

            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
            int fboId = ((GlTexture) framebuffer.getColorAttachment()).getOrCreateFramebuffer(
                    ((GlBackend) RenderSystem.getDevice()).getBufferManager(), null);

            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fboId);
            GL11C.glViewport(0, 0, framebuffer.textureWidth, framebuffer.textureHeight);

            imGuiGl3.newFrame();
            imGuiGlfw.newFrame();
            ImGui.newFrame();
            frameStarted = true;
            return true;
        } catch (Exception e) {
            System.err.println("[Relic Client] Error in ImGui newFrame:");
            e.printStackTrace();
            return false;
        }
    }

    public void flushInputs() {
        if (!initialized) return;

        if (frameStarted) {
            try { ImGui.endFrame(); } catch (Exception ignored) {}
            frameStarted = false;
        }
        try {
            imGuiGlfw.newFrame();
            ImGui.newFrame();
            ImGui.endFrame();
            ImGui.getIO().clearInputKeys();
            ImGui.getIO().clearInputMouse();
        } catch (Exception e) {

            System.err.println("[Relic Client] Error flushing ImGui inputs:");
            e.printStackTrace();
        }
    }

    public void render() {

        if (!initialized || !frameStarted) return;
        try {
            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
        } catch (Exception e) {
            System.err.println("[Relic Client] Error rendering ImGui draw data:");
            e.printStackTrace();
        } finally {
            frameStarted = false;
        }
    }

    public void shutdown() {
        if (!initialized) return;
        if (imGuiGl3 != null) imGuiGl3.shutdown();
        if (imGuiGlfw != null) imGuiGlfw.shutdown();
        ImGui.destroyContext();
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
