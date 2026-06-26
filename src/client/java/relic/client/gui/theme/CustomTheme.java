package relic.client.gui.theme;

public final class CustomTheme implements ColorTheme {

    public static final String[] ROLE_NAMES = {
            "Text", "Background", "Title", "Accent", "Border", "Frame", "Button Off", "Popup"
    };

    private String displayName;
    private final int[] roles = new int[8];

    public CustomTheme(String displayName, int text, int bg, int title, int accent,
                       int border, int frame, int buttonOff, int popup) {
        this.displayName = displayName;
        roles[0] = text;
        roles[1] = bg;
        roles[2] = title;
        roles[3] = accent;
        roles[4] = border;
        roles[5] = frame;
        roles[6] = buttonOff;
        roles[7] = popup;
    }

    public static CustomTheme copyOf(String displayName, ColorTheme src) {
        return new CustomTheme(displayName, src.text(), src.bg(), src.title(), src.accent(),
                src.border(), src.frame(), src.buttonOff(), src.popup());
    }

    @Override public String getDisplayName() { return displayName; }

    public void setDisplayName(String name) {
        if (name != null && !name.isBlank()) displayName = name.trim();
    }

    @Override public String id() { return "custom:" + displayName; }
    @Override public boolean isBuiltin() { return false; }

    public int get(int role) { return roles[role]; }

    public void set(int role, int argb) { roles[role] = argb; }

    @Override public int text()      { return roles[0]; }
    @Override public int bg()        { return roles[1]; }
    @Override public int title()     { return roles[2]; }
    @Override public int accent()    { return roles[3]; }
    @Override public int border()    { return roles[4]; }
    @Override public int frame()     { return roles[5]; }
    @Override public int buttonOff() { return roles[6]; }
    @Override public int popup()     { return roles[7]; }
}
