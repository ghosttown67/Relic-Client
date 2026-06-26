package relic.client.gui.screen;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import relic.client.account.Account;
import relic.client.account.AccountManager;
import relic.client.account.auth.MicrosoftAuth;
import relic.client.api.sound.SoundManager;
import relic.client.config.ClientSettings;
import relic.client.gui.ImGuiManager;
import relic.client.gui.theme.ColorTheme;
import relic.client.gui.theme.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class AccountsScreen extends Screen implements ImGuiScreen {

    private static final float WINDOW_W = 520f, WINDOW_H = 560f;

    private final Screen parent;
    private final ImGuiManager imGuiManager;
    private final ImString offlineNameBuf = new ImString(16);

    private final ImString sessionTokenBuf = newResizableString(256);

    private final ImString refreshTokenBuf = newResizableString(256);

    private final ImInt refreshLauncher = new ImInt(0);

    private static final MicrosoftAuth.Launcher[] LAUNCHERS = MicrosoftAuth.Launcher.values();
    private static final String[] LAUNCHER_NAMES = launcherNames();

    private static String[] launcherNames() {
        String[] names = new String[LAUNCHERS.length];
        for (int i = 0; i < LAUNCHERS.length; i++) names[i] = LAUNCHERS[i].displayName();
        return names;
    }

    public AccountsScreen(Screen parent) {
        super(Text.literal("Accounts"));
        this.parent = parent;
        this.imGuiManager = ImGuiManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        if (!imGuiManager.isInitialized()) imGuiManager.init();
        imGuiManager.flushInputs();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

    }

    @Override
    public void renderImGui() {
        applyStyle();
        boolean baseFont = pushFont(ImGuiManager.getTextFont());
        try {
            drawWindow();
        } finally {
            if (baseFont) ImGui.popFont();
            cleanupStyle();
        }
    }

    private void drawWindow() {
        float dx = ImGui.getIO().getDisplaySizeX();
        float dy = ImGui.getIO().getDisplaySizeY();
        ImGui.setNextWindowPos((dx - WINDOW_W) / 2f, (dy - WINDOW_H) / 2f, ImGuiCond.Always);
        ImGui.setNextWindowSize(WINDOW_W, WINDOW_H, ImGuiCond.Always);
        ImGui.begin("##relicAccountsWindow", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse);
        boolean body = pushFont(ImGuiManager.getTextFont());
        try {
            header();

            AccountManager mgr = AccountManager.getInstance();
            boolean busy = mgr.isBusy();

            ImGui.text("Add account");
            ImGui.dummy(0f, 2f);
            ImGui.setNextItemWidth(200);
            ImGui.inputTextWithHint("##offlineName", "Username", offlineNameBuf);
            ImGui.sameLine();
            boolean canAddOffline = !offlineNameBuf.get().trim().isEmpty();
            if (disabledButton("Add Cracked##addOffline", 110, canAddOffline)) {
                mgr.addOffline(offlineNameBuf.get());
                offlineNameBuf.set("");
                uiSound("/sounds/confirm.wav", 0.5f);
            }

            ImGui.dummy(0f, 2f);
            if (disabledButton("Add Microsoft Account##addMs", 200, !busy)) {
                mgr.beginAddMicrosoft();
                uiSound("/sounds/click.wav", 0.5f);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Opens your browser to sign in with Microsoft, then adds the account.");
            }

            ImGui.dummy(0f, 4f);
            ImGui.setNextItemWidth(200);
            ImGui.inputTextWithHint("##sessionToken", "Session token", sessionTokenBuf,
                    ImGuiInputTextFlags.Password);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("A Minecraft services access token from a logged-in account.\n"
                        + "It's validated against your profile, then saved as a premium account.");
            }
            ImGui.sameLine();
            boolean canAddSession = !busy && !sessionTokenBuf.get().trim().isEmpty();
            if (disabledButton("Add Session##addSession", 110, canAddSession)) {
                mgr.beginAddSession(sessionTokenBuf.get());
                sessionTokenBuf.set("");
                uiSound("/sounds/click.wav", 0.5f);
            }

            ImGui.dummy(0f, 4f);
            ImGui.setNextItemWidth(120);
            ImGui.combo("##refreshLauncher", refreshLauncher, LAUNCHER_NAMES);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Which launcher this refresh token came from. Its token is bound\n"
                        + "to that launcher's Microsoft client, so pick the right one.");
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(160);
            ImGui.inputTextWithHint("##refreshToken", "Refresh token", refreshTokenBuf,
                    ImGuiInputTextFlags.Password);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("A Microsoft refresh token stored by the selected launcher.\n"
                        + "It's redeemed for a session and saved as a premium account that\n"
                        + "re-authenticates itself on each login.");
            }
            ImGui.sameLine();
            boolean canAddRefresh = !busy && !refreshTokenBuf.get().trim().isEmpty();
            if (disabledButton("Add Refresh##addRefresh", 110, canAddRefresh)) {
                mgr.beginAddRefresh(refreshTokenBuf.get(), LAUNCHERS[refreshLauncher.get()]);
                refreshTokenBuf.set("");
                uiSound("/sounds/click.wav", 0.5f);
            }

            status(mgr);

            ImGui.dummy(0f, 6f);
            ImGui.separator();
            ImGui.dummy(0f, 4f);

            ImGui.text("Saved accounts");
            ImGui.dummy(0f, 2f);
            list(mgr, busy);
        } finally {
            if (body) ImGui.popFont();
            ImGui.end();
        }
    }

    private void header() {
        ImGui.dummy(0f, 2f);
        boolean tf = pushFont(ImGuiManager.getTitleFont());
        centeredText("Accounts");
        if (tf) ImGui.popFont();
        ImGui.dummy(0f, 6f);
        ImGui.separator();
        ImGui.dummy(0f, 8f);
    }

    private void status(AccountManager mgr) {
        String s = mgr.getStatus();
        if (s != null && !s.isEmpty()) {
            ImGui.dummy(0f, 2f);
            ImGui.textColored(0.55f, 0.62f, 1.0f, 1.0f, s);
        }
    }

    private void list(AccountManager mgr, boolean busy) {
        List<Account> accounts = mgr.getAccounts();
        ImGui.beginChild("##accountList", 0f, 0f, true);
        if (accounts.isEmpty()) {
            ImGui.textDisabled("No accounts yet — add one above.");
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        String current = mc.getSession() != null ? mc.getSession().getUsername() : null;

        Account toRemove = null;
        for (Account account : new ArrayList<>(accounts)) {
            int id = System.identityHashCode(account);
            boolean active = current != null && account.getUsername().equalsIgnoreCase(current);

            ImGui.text(account.getUsername());
            ImGui.sameLine();
            ImGui.textDisabled("(" + account.getType().getDisplayName() + ")");
            if (active) {
                ImGui.sameLine();
                ImGui.textColored(0.45f, 0.85f, 0.5f, 1.0f, "active");
            }

            ImGui.sameLine();
            float rightW = 70f + 70f + 8f;
            float avail = ImGui.getContentRegionAvailX();
            if (avail > rightW) ImGui.setCursorPosX(ImGui.getCursorPosX() + (avail - rightW));
            if (disabledButton("Login##login" + id, 70, !busy)) {
                mgr.beginLogin(account);
                uiSound("/sounds/click.wav", 0.5f);
            }
            ImGui.sameLine();
            if (disabledButton("Delete##del" + id, 70, !busy)) {
                toRemove = account;
                uiSound("/sounds/click.wav", 0.4f);
            }
            ImGui.separator();
        }
        if (toRemove != null) mgr.remove(toRemove);
        ImGui.endChild();
    }

    private static ImString newResizableString(int initialCapacity) {
        ImString s = new ImString(initialCapacity);
        s.inputData.isResizable = true;
        return s;
    }

    private boolean disabledButton(String label, float width, boolean enabled) {
        if (!enabled) ImGui.beginDisabled();
        boolean clicked = ImGui.button(label, width, 0);
        if (!enabled) ImGui.endDisabled();
        return clicked && enabled;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void centeredText(String s) {
        float avail = ImGui.getContentRegionAvailX();
        float textW = ImGui.calcTextSize(s).x;
        float offset = (avail - textW) * 0.5f;
        if (offset > 0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        ImGui.text(s);
    }

    private void uiSound(String path, float volume) {
        if (ClientSettings.uiSoundsEnabled()) SoundManager.play(path, volume);
    }

    private boolean pushFont(ImFont font) {
        if (font != null) {
            ImGui.pushFont(font);
            return true;
        }
        return false;
    }

    private void applyStyle() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding,    8f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding,     6f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding,     4f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding,      4f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding,     6f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 5f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,     14f, 14f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding,      8f, 5f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,       8f, 7f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize,  0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize,   0f);

        ColorTheme theme = ThemeManager.get();
        int accent    = argbToAbgr(theme.accent());
        int frame     = argbToAbgr(theme.frame());
        int buttonOff = argbToAbgr(theme.buttonOff());
        int text      = argbToAbgr(theme.text());

        ImGui.pushStyleColor(ImGuiCol.Text,             text);
        ImGui.pushStyleColor(ImGuiCol.TextDisabled,     withAlpha(text, 0.55f));
        ImGui.pushStyleColor(ImGuiCol.WindowBg,         argbToAbgr(theme.bg()));
        ImGui.pushStyleColor(ImGuiCol.Border,           argbToAbgr(theme.border()));
        ImGui.pushStyleColor(ImGuiCol.ChildBg,          0x00000000);
        ImGui.pushStyleColor(ImGuiCol.FrameBg,          frame);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered,   lighten(frame, 12));
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive,    lighten(frame, 20));
        ImGui.pushStyleColor(ImGuiCol.CheckMark,        accent);
        ImGui.pushStyleColor(ImGuiCol.PopupBg,          argbToAbgr(theme.popup()));
        ImGui.pushStyleColor(ImGuiCol.ScrollbarBg,      0x00000000);
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab,    buttonOff);
        ImGui.pushStyleColor(ImGuiCol.Separator,        withAlpha(text, 0.10f));
        ImGui.pushStyleColor(ImGuiCol.Button,           buttonOff);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,    lighten(buttonOff, 14));
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,     accent);
    }

    private void cleanupStyle() {
        ImGui.popStyleColor(16);
        ImGui.popStyleVar(11);
    }

    private int lighten(int abgr, int amount) {
        int a = (abgr >> 24) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >>  8) & 0xFF;
        int r =  abgr        & 0xFF;
        r = Math.min(255, r + amount);
        g = Math.min(255, g + amount);
        b = Math.min(255, b + amount);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private int withAlpha(int abgr, float factor) {
        factor = Math.max(0f, Math.min(1f, factor));
        int a = (int) (((abgr >> 24) & 0xFF) * factor);
        return (a << 24) | (abgr & 0x00FFFFFF);
    }

    private int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >>  8) & 0xFF;
        int b =  argb        & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
