package relic.client.gui.theme;

public enum Theme implements ColorTheme {

    RELIC_BLUE("Relic",
            0xFFF3F4F7,
            0xF0121212,
            0xFF1A1A1A,
            0xFF6B7CFF,
            0xFF2A2A30,
            0xFF1E1E22,
            0xFF222228,
            0xFF1A1A1E
    ),

    RELIC_RED("Relic Red",
            0xFFF2E7E7,
            0xF50B0708,
            0xFF150709,
            0xFFE11D2A,
            0xFF3D161B,
            0xFF200E11,
            0xFF271315,
            0xFF150A0C
    ),

    BLIZZARD("Blizzard",
            0xFFEAF6FF,
            0xF50E1620,
            0xFF0A0F18,
            0xFF5CC6FF,
            0xFF26384A,
            0xFF16222E,
            0xFF1C2A38,
            0xFF101A24
    ),

    JUNGLE("Jungle",
            0xFFEAF5E4,
            0xF50C140C,
            0xFF081008,
            0xFF53C24C,
            0xFF274020,
            0xFF132013,
            0xFF192A1A,
            0xFF0E160E
    ),

    MIDNIGHT("Midnight",
            0xFFE3E8F5,
            0xF50A0E1A,
            0xFF070A14,
            0xFF536DF0,
            0xFF202C46,
            0xFF111A2E,
            0xFF17213A,
            0xFF0C1222
    ),

    VIOLENT_VIOLET("Violent Violet",
            0xFFF3E8FF,
            0xF5120A1A,
            0xFF0E0716,
            0xFFB23AFF,
            0xFF3A2450,
            0xFF1F1230,
            0xFF281739,
            0xFF160B22
    ),

    DEEP_DARK_RED("Deep Dark Red",
            0xFFEFD9D9,
            0xF5080303,
            0xFF0D0303,
            0xFF9E1B1B,
            0xFF3A1414,
            0xFF1B0909,
            0xFF230D0D,
            0xFF110505
    );

    private final String displayName;
    private final int text;
    private final int bg;
    private final int title;
    private final int accent;
    private final int border;
    private final int frame;
    private final int buttonOff;
    private final int popup;

    Theme(String displayName, int text, int bg, int title, int accent,
          int border, int frame, int buttonOff, int popup) {
        this.displayName = displayName;
        this.text = text;
        this.bg = bg;
        this.title = title;
        this.accent = accent;
        this.border = border;
        this.frame = frame;
        this.buttonOff = buttonOff;
        this.popup = popup;
    }

    @Override public String getDisplayName() { return displayName; }
    @Override public String id() { return name(); }
    @Override public boolean isBuiltin() { return true; }
    @Override public int text()      { return text; }
    @Override public int bg()        { return bg; }
    @Override public int title()     { return title; }
    @Override public int accent()    { return accent; }
    @Override public int border()    { return border; }
    @Override public int frame()     { return frame; }
    @Override public int buttonOff() { return buttonOff; }
    @Override public int popup()     { return popup; }

    public static String[] displayNames() {
        Theme[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }

    public static Theme byName(String name) {
        if (name != null) {
            for (Theme theme : values()) {
                if (theme.displayName.equalsIgnoreCase(name) || theme.name().equalsIgnoreCase(name)) {
                    return theme;
                }
            }
        }
        return RELIC_BLUE;
    }
}
