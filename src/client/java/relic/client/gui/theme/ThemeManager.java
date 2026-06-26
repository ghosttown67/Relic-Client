package relic.client.gui.theme;

import java.util.ArrayList;
import java.util.List;

public final class ThemeManager {

    private static final Theme DEFAULT = Theme.RELIC_BLUE;

    private static final List<CustomTheme> custom = new ArrayList<>();
    private static ColorTheme current = DEFAULT;

    private ThemeManager() {}

    public static ColorTheme get() {
        return current;
    }

    public static void set(ColorTheme theme) {
        if (theme != null) current = theme;
    }

    public static void setByName(String name) {
        ColorTheme t = byId(name);
        if (t != null) current = t;
    }

    public static List<ColorTheme> all() {
        List<ColorTheme> list = new ArrayList<>(Theme.values().length + custom.size());
        for (Theme t : Theme.values()) list.add(t);
        list.addAll(custom);
        return list;
    }

    public static List<CustomTheme> customThemes() {
        return custom;
    }

    public static ColorTheme byId(String id) {
        if (id == null) return null;
        for (Theme t : Theme.values()) {
            if (t.id().equalsIgnoreCase(id) || t.getDisplayName().equalsIgnoreCase(id)) return t;
        }
        for (CustomTheme c : custom) {
            if (c.id().equalsIgnoreCase(id) || c.getDisplayName().equalsIgnoreCase(id)) return c;
        }
        return null;
    }

    public static void addCustom(CustomTheme theme) {
        if (theme != null) custom.add(theme);
    }

    public static void removeCustom(CustomTheme theme) {
        custom.remove(theme);
        if (current == theme) current = DEFAULT;
    }

    public static void clearCustom() {
        custom.clear();
        if (!current.isBuiltin()) current = DEFAULT;
    }

    public static String uniqueName(String base) {
        String name = base;
        int n = 2;
        while (nameTaken(name)) name = base + " " + n++;
        return name;
    }

    public static boolean nameTaken(String name) {
        for (ColorTheme t : all()) {
            if (t.getDisplayName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
