package relic.client.gui.theme;

public interface ColorTheme {

    String getDisplayName();

    String id();

    boolean isBuiltin();

    int text();
    int bg();
    int title();
    int accent();
    int border();
    int frame();
    int buttonOff();
    int popup();
}
