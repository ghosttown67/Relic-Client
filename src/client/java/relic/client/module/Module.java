package relic.client.module;

import imgui.ImDrawList;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gui.DrawContext;
import relic.client.module.setting.Setting;

import java.util.*;

public abstract class Module {
    public enum Category {
        COMBAT("Combat"),
        EXPLOIT("Exploit"),
        VISUAL("Visual"),
        BASE_HUNTING("Base Hunting"),
        PRIVACY("Privacy"),
        MISC("Misc"),
        TROLL("Troll");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;

    private int keyBind = -1;

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = false;
    }

    public void toggle() {
        this.enabled = !this.enabled;
        if (this.enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            toggle();
        }
    }

    protected void onEnable() {}
    protected void onDisable() {}

    protected void addSettings(Setting<?>... toAdd) {
        Collections.addAll(settings, toAdd);
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public boolean isHoldToActivate() {
        return false;
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public void onPreTick() {}

    public void onTick() {}

    public void onWorldRender(WorldRenderContext context) {}

    public void onHudRender(DrawContext context, float tickDelta) {}

    public boolean wantsImGuiOverlay() {
        return false;
    }

    public void onImGuiRender(ImDrawList drawList) {}

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
