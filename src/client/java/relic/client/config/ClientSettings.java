package relic.client.config;

import org.lwjgl.glfw.GLFW;

public final class ClientSettings {

    private static boolean uiSounds = true;
    private static int openGuiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    private static String webhookUrl = "";

    private ClientSettings() {}

    public static boolean uiSoundsEnabled() {
        return uiSounds;
    }

    public static void setUiSounds(boolean enabled) {
        uiSounds = enabled;
    }

    public static int getOpenGuiKey() {
        return openGuiKey;
    }

    public static void setOpenGuiKey(int key) {
        openGuiKey = key;
    }

    public static String getWebhookUrl() {
        return webhookUrl;
    }

    public static void setWebhookUrl(String url) {
        webhookUrl = url == null ? "" : url;
    }
}
