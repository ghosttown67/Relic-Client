package relic.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import relic.client.gui.PanelLayout;
import relic.client.gui.theme.CustomTheme;
import relic.client.gui.theme.ThemeManager;
import relic.client.map.SeedMap;
import relic.client.module.Module;
import relic.client.module.ModuleManager;
import relic.client.module.setting.BlockListSetting;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ButtonSetting;
import relic.client.module.setting.ColorSetting;
import relic.client.module.setting.EntityListSetting;
import relic.client.module.setting.ModeSetting;
import relic.client.module.setting.MultiSelectSetting;
import relic.client.module.setting.NumberSetting;
import relic.client.module.setting.Setting;
import relic.client.module.setting.StringSetting;
import relic.client.notification.NotificationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("relic-client.json");

    private static final int ERROR_ACCENT = 0xFFE0533D;

    private ConfigManager() {}

    public static void save() {
        writeRoot(FILE, buildRoot());
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            applyRoot(root);
        } catch (Exception e) {
            System.err.println("[Relic Client] Failed to load config (using defaults):");
            e.printStackTrace();
        }
    }

    private static void writeRoot(Path file, JsonObject root) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            System.err.println("[Relic Client] Failed to save config:");
            e.printStackTrace();
        }
    }

    private static JsonObject buildRoot() {
        JsonObject root = new JsonObject();

        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.getInstance().getAllModules()) {
            modules.add(module.getName(), moduleToJson(module));
        }
        root.add("modules", modules);
        root.add("client", buildClient());
        return root;
    }

    private static void applyRoot(JsonObject root) {
        JsonObject client = root.getAsJsonObject("client");
        if (client != null) applyClient(client);

        JsonObject modules = root.getAsJsonObject("modules");
        if (modules == null) return;
        for (Module module : ModuleManager.getInstance().getAllModules()) {
            JsonObject m = modules.getAsJsonObject(module.getName());
            if (m != null) applyModuleJson(module, m);
        }
    }

    public static JsonObject moduleToJson(Module module) {
        JsonObject m = new JsonObject();
        m.addProperty("enabled", module.isEnabled());
        m.addProperty("keybind", module.getKeyBind());

        if (!module.getSettings().isEmpty()) {
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                if (setting instanceof ButtonSetting) continue;
                settings.add(setting.getName(), serialize(setting));
            }
            m.add("settings", settings);
        }
        return m;
    }

    public static void applyModuleJson(Module module, JsonObject m) {
        if (m.has("keybind")) {
            try { module.setKeyBind(m.get("keybind").getAsInt()); } catch (Exception ignored) {}
        }
        JsonObject settings = m.getAsJsonObject("settings");
        if (settings != null) {
            for (Setting<?> setting : module.getSettings()) {
                JsonElement el = settings.get(setting.getName());
                if (el != null) deserialize(setting, el);
            }
        }
        if (m.has("enabled")) {
            try { module.setEnabled(m.get("enabled").getAsBoolean()); } catch (Exception ignored) {}
        }
    }

    public static String copyModule(Module module) {
        return GSON.toJson(moduleToJson(module));
    }

    public static boolean pasteModule(Module module, String json) {
        if (json == null || json.isBlank()) return false;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) return false;
            applyModuleJson(module, el.getAsJsonObject());
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void exportWithDialog() {

        String json = GSON.toJson(buildRoot());
        runDialog(() -> {
            String chosen = saveDialog();
            if (chosen == null) return;
            Path path = withJsonExtension(chosen);
            try {
                Files.writeString(path, json);
                notify("Config exported", path.getFileName().toString(), true);
            } catch (IOException e) {
                e.printStackTrace();
                notify("Export failed", "Could not write the file", false);
            }
        });
    }

    public static void importWithDialog() {
        runDialog(() -> {
            String chosen = openDialog();
            if (chosen == null) return;
            final JsonObject root;
            try {
                String content = Files.readString(Paths.get(chosen));
                root = JsonParser.parseString(content).getAsJsonObject();
            } catch (Exception e) {
                e.printStackTrace();
                notify("Import failed", "Not a valid config file", false);
                return;
            }
            String fileName = Paths.get(chosen).getFileName().toString();

            MinecraftClient.getInstance().execute(() -> {
                applyRoot(root);
                save();
                notify("Config imported", fileName, true);
            });
        });
    }

    private static void runDialog(Runnable task) {
        Thread t = new Thread(task, "Relic-Config-Dialog");
        t.setDaemon(true);
        t.start();
    }

    private static String saveDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json")).flip();
            return TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export Relic Config", defaultExportPath(), filters, "Relic config (*.json)");
        }
    }

    private static String openDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json")).flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Relic Config", defaultExportPath(), filters, "Relic config (*.json)", false);
        }
    }

    private static String defaultExportPath() {
        return FILE.getParent().resolve("relic-config.json").toString();
    }

    private static Path withJsonExtension(String chosen) {
        Path p = Paths.get(chosen);
        if (!chosen.toLowerCase().endsWith(".json")) {
            p = p.resolveSibling(p.getFileName().toString() + ".json");
        }
        return p;
    }

    private static void notify(String title, String message, boolean ok) {
        NotificationManager.getInstance().push(title, message,
                ok ? NotificationManager.DEFAULT_ACCENT : ERROR_ACCENT,
                NotificationManager.DEFAULT_HOLD_MS, true);
    }

    private static JsonObject buildClient() {
        JsonObject client = new JsonObject();
        client.addProperty("theme", ThemeManager.get().id());
        client.addProperty("uiSounds", ClientSettings.uiSoundsEnabled());
        client.addProperty("openGuiKey", ClientSettings.getOpenGuiKey());
        client.addProperty("webhookUrl", ClientSettings.getWebhookUrl());

        JsonArray customThemes = new JsonArray();
        for (CustomTheme theme : ThemeManager.customThemes()) {
            JsonObject t = new JsonObject();
            t.addProperty("name", theme.getDisplayName());
            t.addProperty("text", theme.text());
            t.addProperty("bg", theme.bg());
            t.addProperty("title", theme.title());
            t.addProperty("accent", theme.accent());
            t.addProperty("border", theme.border());
            t.addProperty("frame", theme.frame());
            t.addProperty("buttonOff", theme.buttonOff());
            t.addProperty("popup", theme.popup());
            customThemes.add(t);
        }
        client.add("customThemes", customThemes);

        JsonObject panels = new JsonObject();
        for (Module.Category category : Module.Category.values()) {
            float[] pos = PanelLayout.get(category);
            JsonArray xy = new JsonArray();
            xy.add(pos[0]);
            xy.add(pos[1]);
            panels.add(category.name(), xy);
        }
        client.add("panels", panels);

        JsonObject seedMapServers = new JsonObject();
        SeedMap.getInstance().getServerSeeds().forEach(seedMapServers::addProperty);
        client.add("seedMapServers", seedMapServers);
        return client;
    }

    private static void applyClient(JsonObject client) {

        ThemeManager.clearCustom();
        if (client.has("customThemes") && client.get("customThemes").isJsonArray()) {
            for (JsonElement el : client.getAsJsonArray("customThemes")) {
                if (!el.isJsonObject()) continue;
                CustomTheme theme = readCustomTheme(el.getAsJsonObject());
                if (theme != null) ThemeManager.addCustom(theme);
            }
        }
        if (client.has("theme")) {
            ThemeManager.setByName(client.get("theme").getAsString());
        }
        if (client.has("uiSounds")) {
            ClientSettings.setUiSounds(client.get("uiSounds").getAsBoolean());
        }
        if (client.has("openGuiKey")) {
            ClientSettings.setOpenGuiKey(client.get("openGuiKey").getAsInt());
        }
        if (client.has("webhookUrl")) {
            ClientSettings.setWebhookUrl(client.get("webhookUrl").getAsString());
        }
        JsonObject panels = client.getAsJsonObject("panels");
        if (panels != null) {
            for (Module.Category category : Module.Category.values()) {
                JsonElement el = panels.get(category.name());
                if (el != null && el.isJsonArray() && el.getAsJsonArray().size() == 2) {
                    JsonArray xy = el.getAsJsonArray();
                    PanelLayout.set(category, xy.get(0).getAsFloat(), xy.get(1).getAsFloat());
                }
            }
        }
        JsonObject seedMapServers = client.getAsJsonObject("seedMapServers");
        if (seedMapServers != null) {
            for (var entry : seedMapServers.entrySet()) {
                try {
                    SeedMap.getInstance().putServerSeed(entry.getKey(), entry.getValue().getAsString());
                } catch (Exception ignored) {}
            }
        }
    }

    private static JsonElement serialize(Setting<?> setting) {
        if (setting instanceof BooleanSetting b)     return new JsonPrimitive(b.getValue());
        if (setting instanceof NumberSetting n)      return new JsonPrimitive(n.getValue());
        if (setting instanceof ModeSetting m)        return new JsonPrimitive(m.getValue());
        if (setting instanceof StringSetting s)      return new JsonPrimitive(s.getValue());
        if (setting instanceof ColorSetting c)       return new JsonPrimitive(c.getValue());
        if (setting instanceof MultiSelectSetting ms) return toArray(ms.getValue());
        if (setting instanceof BlockListSetting bl)  return toArray(bl.getValue());
        if (setting instanceof EntityListSetting el) return toArray(el.getValue());
        return JsonNull.INSTANCE;
    }

    private static void deserialize(Setting<?> setting, JsonElement el) {
        try {
            if (setting instanceof BooleanSetting b && el.isJsonPrimitive()) {
                b.setValue(el.getAsBoolean());
            } else if (setting instanceof NumberSetting n && el.isJsonPrimitive()) {
                n.setValue(el.getAsFloat());
            } else if (setting instanceof ModeSetting m && el.isJsonPrimitive()) {

                String stored = el.getAsString();
                for (String mode : m.getModes()) {
                    if (mode.equalsIgnoreCase(stored)) {
                        m.setValue(mode);
                        break;
                    }
                }
            } else if (setting instanceof StringSetting s && el.isJsonPrimitive()) {
                s.setValue(el.getAsString());
            } else if (setting instanceof ColorSetting c && el.isJsonPrimitive()) {
                c.setValue(el.getAsInt());
            } else if (setting instanceof MultiSelectSetting ms && el.isJsonArray()) {
                ms.setValue(toSet(el.getAsJsonArray()));
            } else if (setting instanceof BlockListSetting bl && el.isJsonArray()) {
                bl.setValue(toSet(el.getAsJsonArray()));
            } else if (setting instanceof EntityListSetting en && el.isJsonArray()) {
                en.setValue(toSet(el.getAsJsonArray()));
            }
        } catch (Exception e) {

        }
    }

    private static CustomTheme readCustomTheme(JsonObject t) {
        if (!t.has("name")) return null;
        String name = t.get("name").getAsString();
        if (name == null || name.isBlank()) return null;
        return new CustomTheme(name,
                colorOf(t, "text", 0xFFF3F4F7),
                colorOf(t, "bg", 0xF0121212),
                colorOf(t, "title", 0xFF1A1A1A),
                colorOf(t, "accent", 0xFF6B7CFF),
                colorOf(t, "border", 0xFF2A2A30),
                colorOf(t, "frame", 0xFF1E1E22),
                colorOf(t, "buttonOff", 0xFF222228),
                colorOf(t, "popup", 0xFF1A1A1E));
    }

    private static int colorOf(JsonObject o, String key, int def) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static JsonArray toArray(Set<String> values) {
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        return arr;
    }

    private static Set<String> toSet(JsonArray arr) {
        Set<String> set = new LinkedHashSet<>();
        arr.forEach(e -> set.add(e.getAsString()));
        return set;
    }
}
