package relic.client.keybinds;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    private static final int MOUSE_BUTTON_OFFSET = 1000;
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final int OPEN_GUI_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;

    public static String getKeyName(int keyCode) {
        if (keyCode >= MOUSE_BUTTON_OFFSET) {
            return getMouseButtonName(keyCode - MOUSE_BUTTON_OFFSET);
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "Left Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "Left Ctrl";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "Right Alt";
            case GLFW.GLFW_KEY_LEFT_ALT -> "Left Alt";
            default -> {
                if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12) {
                    yield "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
                }
                if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
                    yield String.valueOf((char) keyCode);
                }
                if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                    yield String.valueOf((char) keyCode);
                }
                yield "Unknown (" + keyCode + ")";
            }
        };
    }

    private static String getMouseButtonName(int mouseButton) {
        return switch (mouseButton) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "Left Click";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "Right Click";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "Middle Click";
            default -> "Mouse " + (mouseButton + 1);
        };
    }

    public static boolean isKeyPressed(int keyCode) {
        if (mc.getWindow() == null) return false;

        try {
            return GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMouseButtonPressed(int mouseButton) {
        if (mc.getWindow() == null) return false;

        try {
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), mouseButton) == GLFW.GLFW_PRESS;
        } catch (Exception e) {
            return false;
        }
    }
}
