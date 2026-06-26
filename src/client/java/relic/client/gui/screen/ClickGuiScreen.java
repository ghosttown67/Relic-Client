package relic.client.gui.screen;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiButtonFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import relic.client.api.item.HoveredItemTracker;
import relic.client.api.packet.PacketLog;
import relic.client.api.packet.RawCustomPayload;
import relic.client.api.discord.DiscordWebhook;
import relic.client.api.sound.SoundManager;
import relic.client.config.ClientSettings;
import relic.client.config.ConfigManager;
import relic.client.event.ClientTickEvent;
import relic.client.gui.Animations;
import relic.client.gui.ImGuiManager;
import relic.client.gui.PanelLayout;
import relic.client.gui.RelicLogo;
import relic.client.gui.Snapping;
import relic.client.gui.hud.HudEditor;
import relic.client.gui.theme.ColorTheme;
import relic.client.gui.theme.CustomTheme;
import relic.client.gui.theme.ThemeManager;
import relic.client.locator.BedrockLocator;
import relic.client.map.BiomeMapGenerator;
import relic.client.map.BiomePalette;
import relic.client.map.SeedMap;
import relic.client.map.StructureFinder;
import relic.client.module.Module;
import relic.client.module.ModuleManager;
import relic.client.module.impl.visual.GhostModeModule;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClickGuiScreen extends Screen implements ImGuiScreen {

    private enum Tab {
        CLICKGUI("Modules"),
        HUD_EDITOR("HUD Editor"),
        SETTINGS("Settings"),
        LOCATOR("Locator"),
        MAP("Seed Map"),
        COMMAND("Command Input"),
        PAYLOAD("Custom Payload Sender"),
        NBT("NBT Viewer"),
        PACKET_LOG("Packet Log");

        final String title;
        Tab(String title) { this.title = title; }
    }

    private static final Tab[] MAIN_TABS = { Tab.CLICKGUI, Tab.HUD_EDITOR, Tab.SETTINGS, Tab.LOCATOR, Tab.MAP };

    private static final Tab[] TOOL_TABS = { Tab.COMMAND, Tab.PAYLOAD, Tab.NBT, Tab.PACKET_LOG };

    private static boolean isToolTab(Tab t) {
        return t == Tab.COMMAND || t == Tab.PAYLOAD || t == Tab.NBT || t == Tab.PACKET_LOG;
    }

    private static final float TAB_BAR_H  = 38f;
    private static final float PANEL_W    = PanelLayout.PANEL_W;
    private static final float HEADER_H   = 24f;
    private static final float ROW_H      = 22f;
    private static final float PAD        = 8f;
    private static final float WIDGET_WIDTH = 150f;

    private static final float SETTINGS_W = 560f, SETTINGS_H = 430f;
    private static final float LOCATOR_W  = 620f, LOCATOR_H  = 540f;
    private static final float PAYLOAD_W  = 580f, PAYLOAD_H  = 470f;
    private static final float NBT_W      = 600f, NBT_H      = 520f;
    private static final float PACKETLOG_W = 640f, PACKETLOG_H = 540f;
    private static final float COMMAND_W   = 560f, COMMAND_H   = 420f;
    private static final String[] PAYLOAD_FORMATS = { "Hex bytes", "UTF-8 text" };
    private static final String[] NBT_SOURCES     = { "Main hand", "Off hand", "Last hovered" };

    private static final float CELL_SIZE = 17f;
    private static final float CELL_GAP  = 2f;
    private static final float PICKER_PANE_W = 180f;
    private static final float PICKER_PANE_H = 190f;
    private static final int[] LAYER_YS = {-63, -62, -61, -60};
    private static final String[] LAYER_LABELS = {
            "-63  (80% bedrock)", "-62  (60%)", "-61  (40%)", "-60  (20%)"};

    private static final Module.Category[] CATEGORIES = Module.Category.values();

    private final MinecraftClient client;
    private final ImGuiManager imGuiManager;
    private final ModuleManager moduleManager;

    private static Tab activeTab = Tab.CLICKGUI;

    private static final Set<Module> expanded = new LinkedHashSet<>();

    private Module.Category draggingPanel;
    private float grabX, grabY;

    private float guideX = Float.NaN, guideY = Float.NaN;

    private final Map<Module.Category, Float> panelHeights = new HashMap<>();

    private final Map<String, Boolean> prevHover = new HashMap<>();
    private final Map<BlockListSetting, ImString> searchBuffers = new HashMap<>();
    private final Map<EntityListSetting, ImString> entitySearchBuffers = new HashMap<>();
    private final Map<StringSetting, ImString> stringBuffers = new HashMap<>();

    private NumberSetting editingNumber;
    private final Map<NumberSetting, ImString> numberEditBuffers = new HashMap<>();

    private boolean numberEditFocus;

    private Module bindingModule;

    private boolean bindingGuiKey;
    private ImString bedrockSeedBuf;
    private String lastBedrockValidation = "";

    private ImString mapSeedBuf;
    private final ImString mapFilterSearch = new ImString(48);
    private final ImString mapWpName = new ImString(48);
    private final ImInt mapGoX = new ImInt(0);
    private final ImInt mapGoZ = new ImInt(0);
    private final ImInt mapWpX = new ImInt(0);
    private final ImInt mapWpZ = new ImInt(0);
    private boolean mapDragging;
    private float mapLastX, mapLastY;

    private float mapPressX, mapPressY;
    private boolean mapMoved;

    private final Map<String, Integer> mapIconTex = new HashMap<>();

    private ImString webhookUrlBuf;
    private String webhookTestResult = "";

    private final ImString moduleSearch = new ImString(64);
    private boolean searchOpen;
    private boolean searchFocus;

    private final ImString themeNameBuf = new ImString(32);
    private CustomTheme themeNameBufFor;

    private static boolean packetGroupOpen;
    private float packetChipX, packetChipW, packetChipBottomY;

    private final ImString payloadChannel = new ImString(128);
    private boolean payloadChannelInit;
    private int payloadFormat;
    private final ImString payloadBody = new ImString(4096);
    private String payloadStatus = "";

    private int nbtSource;

    private final ImString commandInput = new ImString(256);
    private boolean commandFocus;
    private String commandStatus = "";
    private final List<String> commandHistory = new ArrayList<>();
    private static final int COMMAND_HISTORY_MAX = 12;

    private final ImString packetLogFilter = new ImString(64);

    private final Screen parent;

    public ClickGuiScreen() {
        this(null);
    }

    public ClickGuiScreen(Screen parent) {
        super(Text.literal("Relic Client GUI"));
        this.parent = parent;
        this.client = MinecraftClient.getInstance();
        this.imGuiManager = ImGuiManager.getInstance();
        this.moduleManager = ModuleManager.getInstance();
    }

    public Screen getParent() {

        if (parent instanceof net.minecraft.client.gui.screen.DeathScreen
                && GhostModeModule.getInstance() != null
                && GhostModeModule.getInstance().isEnabled()) {
            return null;
        }
        return parent;
    }

    @Override
    protected void init() {
        super.init();
        if (!imGuiManager.isInitialized()) {
            imGuiManager.init();
        }
        imGuiManager.flushInputs();
        HudEditor.clearDrag();
        Animations.set("gui_open", 0f);
        uiSound("/sounds/scroll-slide.wav", 0.5f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

    }

    @Override
    public void renderImGui() {
        applyStyle();
        float open = Animations.to("gui_open", 1f, 11f);
        float yOff = (1f - open) * 22f;
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, Math.max(0.05f, open));
        boolean baseFont = pushFont(ImGuiManager.getTextFont());
        try {
            handleBindCapture();
            handleGuiBindCapture();

            renderTopBar(yOff);
            switch (activeTab) {
                case CLICKGUI   -> renderPanels(yOff);
                case HUD_EDITOR -> HudEditor.render(ImGui.getForegroundDrawList());
                case SETTINGS   -> renderSettingsScreen(yOff);
                case LOCATOR    -> renderLocatorScreen(yOff);
                case MAP        -> renderMapScreen(yOff);
                case COMMAND    -> renderCommandScreen(yOff);
                case PAYLOAD    -> renderPayloadScreen(yOff);
                case NBT        -> renderNbtScreen(yOff);
                case PACKET_LOG -> renderPacketLogScreen(yOff);
            }

            renderPacketGroupDropdown();
            renderSearch(yOff);
        } finally {
            if (baseFont) ImGui.popFont();
            ImGui.popStyleVar();
            cleanupStyle();
        }
    }

    private void renderTopBar(float yOff) {
        ColorTheme theme = ThemeManager.get();
        float dx = ImGui.getIO().getDisplaySizeX();

        float logoSize = 22f, logoGap = 7f, tabPad = 16f, brandGap = 22f;

        boolean tf = pushFont(ImGuiManager.getTitleFont());
        float brandTextW = ImGui.calcTextSize("RELIC").x;
        if (tf) ImGui.popFont();
        float brandW = logoSize + logoGap + brandTextW;

        float[] tabW = new float[MAIN_TABS.length];
        float tabsW = 0;
        for (int i = 0; i < MAIN_TABS.length; i++) {
            tabW[i] = ImGui.calcTextSize(MAIN_TABS[i].title).x + tabPad * 2;
            tabsW += tabW[i];
        }

        float groupGap = 14f, caretW = 12f;
        String groupLabel = "Packets";
        float groupW = ImGui.calcTextSize(groupLabel).x + caretW + tabPad * 2;

        float totalW = PAD * 2 + brandW + brandGap + tabsW + groupGap + groupW;
        float barX = Math.max(8f, (dx - totalW) / 2f);

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PAD, 0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, argbToAbgr(theme.title()));
        ImGui.setNextWindowPos(barX, 12f + yOff, ImGuiCond.Always);
        ImGui.setNextWindowSize(totalW, TAB_BAR_H, ImGuiCond.Always);
        ImGui.begin("##relicTopBar", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
                | ImGuiWindowFlags.NoBringToFrontOnFocus);

        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 wp = ImGui.getWindowPos();

        float brandY = wp.y + (TAB_BAR_H - logoSize) / 2f;
        RelicLogo.draw(dl, wp.x + PAD, brandY, logoSize, argbToAbgr(theme.accent()), 0xFFFFFFFF);
        boolean tf2 = pushFont(ImGuiManager.getTitleFont());
        float wordY = wp.y + (TAB_BAR_H - ImGui.getTextLineHeight()) / 2f;
        dl.addText(ImGuiManager.getTitleFont() != null ? ImGuiManager.getTitleFont() : ImGui.getFont(),
                ImGui.getFontSize(), wp.x + PAD + logoSize + logoGap, wordY,
                argbToAbgr(theme.accent()), "RELIC");
        if (tf2) ImGui.popFont();

        float tabsStart = wp.x + PAD + brandW + brandGap;
        float tx = tabsStart;
        int accent = argbToAbgr(theme.accent());
        boolean toolActive = isToolTab(activeTab);
        for (int i = 0; i < MAIN_TABS.length; i++) {
            Tab t = MAIN_TABS[i];
            float w = tabW[i];
            ImGui.setCursorScreenPos(tx, wp.y);
            if (ImGui.invisibleButton("##tab" + t.name(), w, TAB_BAR_H)) {
                if (activeTab != t) {
                    activeTab = t;
                    packetGroupOpen = false;
                    HudEditor.clearDrag();
                    uiSound("/sounds/click.wav", 0.5f);
                }
            }
            boolean hovered = ImGui.isItemHovered();
            hoverSound("tab" + t.name(), hovered);
            boolean active = activeTab == t;

            float hv = Animations.to("tabhov" + t.name(), (hovered && !active) ? 1f : 0f, 18f);
            if (hv > 0.002f) {
                dl.addRectFilled(tx, wp.y + 5, tx + w, wp.y + TAB_BAR_H - 5, withAlpha(accent, hv * 0.16f), 4f);
            }
            ImVec2 ts = ImGui.calcTextSize(t.title);
            dl.addText(tx + (w - ts.x) / 2f, wp.y + (TAB_BAR_H - ts.y) / 2f,
                    active ? accent : argbToAbgr(theme.text()), t.title);
            tx += w;
        }

        if (!toolActive) {
            float targetX = tabsStart;
            int activeIdx = 0;
            for (int i = 0; i < MAIN_TABS.length; i++) {
                if (MAIN_TABS[i] == activeTab) { activeIdx = i; break; }
                targetX += tabW[i];
            }
            float ux = Animations.to("tab_underline_x", targetX, 18f);
            float uw = Animations.to("tab_underline_w", tabW[activeIdx], 18f);
            dl.addRectFilled(ux + 6, wp.y + TAB_BAR_H - 3, ux + uw - 6, wp.y + TAB_BAR_H - 1, accent, 1f);
        }

        renderPacketGroupChip(dl, theme, tx + groupGap, groupW, caretW, wp.y, toolActive, accent);

        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar();
    }

    private void renderPacketGroupChip(ImDrawList dl, ColorTheme theme, float gx, float groupW,
                                       float caretW, float barY, boolean toolActive, int accent) {
        ImGui.setCursorScreenPos(gx, barY);
        if (ImGui.invisibleButton("##packetGroup", groupW, TAB_BAR_H)) {
            packetGroupOpen = !packetGroupOpen;
            uiSound("/sounds/click.wav", 0.5f);
        }
        boolean hovered = ImGui.isItemHovered();
        hoverSound("packetGroup", hovered);

        float hv = Animations.to("packetGroupHov", (hovered && !toolActive) ? 1f : 0f, 18f);
        if (hv > 0.002f) {
            dl.addRectFilled(gx, barY + 5, gx + groupW, barY + TAB_BAR_H - 5, withAlpha(accent, hv * 0.16f), 4f);
        }

        float tabPad = 16f;
        int labelCol = toolActive ? accent : argbToAbgr(theme.text());
        ImVec2 ls = ImGui.calcTextSize("Packets");
        float lx = gx + tabPad;
        dl.addText(lx, barY + (TAB_BAR_H - ls.y) / 2f, labelCol, "Packets");

        float cx = lx + ls.x + 6f;
        float cy = barY + TAB_BAR_H / 2f;
        if (packetGroupOpen) {
            dl.addTriangleFilled(cx, cy - 2.5f, cx + 8f, cy - 2.5f, cx + 4f, cy + 3f, labelCol);
        } else {
            dl.addTriangleFilled(cx, cy - 4f, cx, cy + 4f, cx + 4.5f, cy, labelCol);
        }

        if (toolActive) {
            dl.addRectFilled(gx + 6, barY + TAB_BAR_H - 3, gx + groupW - 6, barY + TAB_BAR_H - 1, accent, 1f);
        }

        packetChipX = gx;
        packetChipW = groupW;
        packetChipBottomY = barY + TAB_BAR_H;
    }

    private void renderPacketGroupDropdown() {
        float t = Animations.to("packet_group_drop", packetGroupOpen ? 1f : 0f, 14f);
        if (t < 0.02f) return;

        ColorTheme theme = ThemeManager.get();
        float rowH = ROW_H + 6f;
        float dropW = Math.max(packetChipW, 200f);
        float fullH = TOOL_TABS.length * rowH + 8f;
        float dropH = fullH * t;

        float dx = ImGui.getIO().getDisplaySizeX();
        float dropX = Math.min(packetChipX, dx - 8f - dropW);

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 4f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, argbToAbgr(theme.bg()));
        ImGui.setNextWindowPos(dropX, packetChipBottomY, ImGuiCond.Always);
        ImGui.setNextWindowSize(dropW, dropH, ImGuiCond.Always);
        ImGui.begin("##relicPacketGroup", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollWithMouse);
        boolean body = pushFont(ImGuiManager.getTextFont());

        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 wp = ImGui.getWindowPos();
        ImVec2 ws = ImGui.getWindowSize();

        for (Tab tool : TOOL_TABS) packetGroupRow(tool, dropW, rowH);

        dl.addRect(wp.x, wp.y, wp.x + ws.x, wp.y + ws.y, argbToAbgr(theme.border()), 0f, 0, 1f);
        dl.addRectFilled(wp.x, wp.y, wp.x + ws.x, wp.y + 1f, withAlpha(argbToAbgr(theme.accent()), t));

        if (body) ImGui.popFont();
        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
    }

    private void packetGroupRow(Tab tool, float rowW, float rowH) {
        ColorTheme theme = ThemeManager.get();
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 p = ImGui.getCursorScreenPos();
        boolean active = activeTab == tool;

        ImGui.invisibleButton("##pg" + tool.name(), rowW, rowH);
        boolean hovered = ImGui.isItemHovered();
        hoverSound("pg" + tool.name(), hovered);
        if (ImGui.isItemClicked(0)) {
            activeTab = tool;
            packetGroupOpen = false;
            if (tool == Tab.COMMAND) commandFocus = true;
            HudEditor.clearDrag();
            uiSound("/sounds/click.wav", 0.5f);
        }

        float en = Animations.to("pgen" + tool.name(), active ? 1f : 0f, 16f);
        float hv = Animations.to("pghov" + tool.name(), hovered ? 1f : 0f, 18f);
        int rowCol = lerpColor(withAlpha(argbToAbgr(theme.buttonOff()), hv * 0.6f),
                argbToAbgr(theme.accent()), en);
        if (en > 0.002f || hv > 0.002f) {
            dl.addRectFilled(p.x, p.y, p.x + rowW, p.y + rowH, rowCol);
        }
        int nameCol = lerpColor(argbToAbgr(theme.text()), 0xFFFFFFFF, en);
        dl.addText(p.x + PAD, p.y + (rowH - ImGui.getTextLineHeight()) / 2f, nameCol, tool.title);
    }

    private void renderPayloadScreen(float yOff) {
        boolean body = beginCenteredWindow("##relicPayload", PAYLOAD_W, PAYLOAD_H, yOff, true);
        try {
            contentHeader("Custom Payload Sender", "Send a CustomPayloadC2SPacket on any channel");
            boolean connected = client.getNetworkHandler() != null;
            if (!connected) {
                ImGui.textColored(1.0f, 0.63f, 0.25f, 1.0f, "Not connected to a server.");
                ImGui.dummy(0f, 4f);
            }

            if (!payloadChannelInit) {
                payloadChannel.set("minecraft:brand");
                payloadChannelInit = true;
            }
            ImGui.text("Channel");
            ImGui.sameLine(120);
            ImGui.setNextItemWidth(320);
            ImGui.inputTextWithHint("##plchannel", "namespace:path", payloadChannel);
            String channelText = payloadChannel.get().trim();
            Identifier channel = channelText.isEmpty() ? null : Identifier.tryParse(channelText);

            ImGui.text("Format");
            ImGui.sameLine(120);
            ImGui.setNextItemWidth(160);
            ImInt fmt = new ImInt(payloadFormat);
            if (ImGui.combo("##plfmt", fmt, PAYLOAD_FORMATS)) payloadFormat = fmt.get();

            ImGui.dummy(0f, 2f);
            ImGui.text("Payload");
            ImGui.dummy(0f, 2f);
            ImGui.inputTextMultiline("##plbody", payloadBody, PAYLOAD_W - 30f, 150f);

            byte[] data = parsePayloadBytes();
            if (data == null) {
                ImGui.textColored(1.0f, 0.40f, 0.36f, 1.0f, "Invalid hex — use byte pairs like  00 ff 1a.");
            } else {
                ImGui.textDisabled(data.length + " byte" + (data.length == 1 ? "" : "s"));
            }

            ImGui.dummy(0f, 6f);
            boolean canSend = connected && channel != null && data != null;
            if (ImGui.button("Send", 120, 0)) {
                if (canSend) {
                    client.getNetworkHandler().sendPacket(
                            new CustomPayloadC2SPacket(new RawCustomPayload(channel, data)));
                    payloadStatus = "Sent " + data.length + " bytes to " + channel;
                    uiSound("/sounds/confirm.wav", 0.5f);
                } else if (!connected) {
                    payloadStatus = "Can't send — not connected.";
                } else if (channel == null) {
                    payloadStatus = "Can't send — invalid channel id.";
                } else {
                    payloadStatus = "Can't send — fix the payload.";
                }
            }
            if (channel == null && !channelText.isEmpty()) {
                ImGui.sameLine();
                ImGui.textColored(1.0f, 0.40f, 0.36f, 1.0f, "Invalid channel id");
            }
            if (!payloadStatus.isEmpty()) {
                ImGui.dummy(0f, 4f);
                ImGui.textDisabled(payloadStatus);
            }
        } finally {
            if (body) ImGui.popFont();
            ImGui.end();
        }
    }

    private byte[] parsePayloadBytes() {
        String raw = payloadBody.get();
        if (payloadFormat == 1) {
            return raw.getBytes(StandardCharsets.UTF_8);
        }

        String cleaned = raw.replaceAll("(?i)0x", "").replaceAll("[\\s,]", "");
        if (!cleaned.matches("[0-9a-fA-F]*") || cleaned.length() % 2 != 0) return null;
        byte[] out = new byte[cleaned.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private void renderNbtScreen(float yOff) {
        boolean body = beginCenteredWindow("##relicNbt", NBT_W, NBT_H, yOff, true);
        try {
            contentHeader("NBT Viewer", "Inspect an item's components as NBT");

            ImGui.text("Source");
            ImGui.sameLine(120);
            ImGui.setNextItemWidth(200);
            ImInt src = new ImInt(nbtSource);
            if (ImGui.combo("##nbtsrc", src, NBT_SOURCES)) nbtSource = src.get();

            ItemStack stack = resolveNbtStack();
            if (stack == null || stack.isEmpty()) {
                ImGui.dummy(0f, 8f);
                ImGui.textDisabled(nbtSource == 2
                        ? "No item hovered yet — hover one in a container, then reopen this."
                        : "No item in that slot.");
                return;
            }
            if (client.world == null) {
                ImGui.dummy(0f, 8f);
                ImGui.textDisabled("Join a world to read item NBT.");
                return;
            }

            RegistryOps<NbtElement> ops = client.world.getRegistryManager().getOps(NbtOps.INSTANCE);
            NbtElement nbt = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
            if (nbt == null) {
                ImGui.dummy(0f, 8f);
                ImGui.textDisabled("Couldn't serialize that item.");
                return;
            }

            String summary = Registries.ITEM.getId(stack.getItem()) + "  x" + stack.getCount();
            String pretty = NbtHelper.toPrettyPrintedText(nbt).getString();

            if (ImGui.button("Copy SNBT", 120, 0)) {
                ImGui.setClipboardText(pretty);
                uiSound("/sounds/confirm.wav", 0.5f);
            }
            ImGui.sameLine();
            ImGui.textDisabled(summary);

            ImGui.dummy(0f, 4f);
            ImGui.beginChild("##nbtbody", NBT_W - 30f, NBT_H - 150f, true, ImGuiWindowFlags.HorizontalScrollbar);
            for (String line : pretty.split("\n", -1)) {
                ImGui.text(line);
            }
            ImGui.endChild();
        } finally {
            if (body) ImGui.popFont();
            ImGui.end();
        }
    }

    private ItemStack resolveNbtStack() {
        if (nbtSource == 2) return HoveredItemTracker.get();
        if (client.player == null) return ItemStack.EMPTY;
        return nbtSource == 1 ? client.player.getOffHandStack() : client.player.getMainHandStack();
    }

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private void renderPacketLogScreen(float yOff) {
        boolean body = beginCenteredWindow("##relicPacketLog", PACKETLOG_W, PACKETLOG_H, yOff, false);
        try {
            contentHeader("Packet Log", "Live capture of incoming/outgoing packets");

            boolean capturing = PacketLog.isCapturing();
            if (ImGui.button(capturing ? "Pause" : "Capture", 90, 0)) {
                PacketLog.setCapturing(!capturing);
                uiSound("/sounds/click.wav", 0.5f);
            }
            ImGui.sameLine();
            if (ImGui.button("Clear", 70, 0)) {
                PacketLog.clear();
                uiSound("/sounds/click.wav", 0.4f);
            }
            ImGui.sameLine();
            ImBoolean inc = new ImBoolean(PacketLog.logIncoming());
            if (ImGui.checkbox("S2C##loginc", inc)) PacketLog.setLogIncoming(inc.get());
            ImGui.sameLine();
            ImBoolean out = new ImBoolean(PacketLog.logOutgoing());
            if (ImGui.checkbox("C2S##logout", out)) PacketLog.setLogOutgoing(out.get());
            ImGui.sameLine();
            ImGui.setNextItemWidth(180);
            ImGui.inputTextWithHint("##logfilter", "Filter by name...", packetLogFilter);

            ImGui.dummy(0f, 4f);
            String query = packetLogFilter.get().toLowerCase().trim();
            List<PacketLog.Entry> entries = PacketLog.snapshot();

            ImGui.beginChild("##logbody", PACKETLOG_W - 30f, PACKETLOG_H - 150f, true,
                    ImGuiWindowFlags.HorizontalScrollbar);
            int shown = 0;
            for (int i = entries.size() - 1; i >= 0; i--) {
                PacketLog.Entry e = entries.get(i);
                if (!query.isEmpty() && !e.name().toLowerCase().contains(query)) continue;
                String line = formatLogTime(e.time()) + (e.incoming() ? "  <-  " : "  ->  ") + e.name();
                if (e.incoming()) ImGui.textColored(0.56f, 0.72f, 1.0f, 1.0f, line);
                else              ImGui.textColored(1.0f, 0.77f, 0.56f, 1.0f, line);
                shown++;
            }
            if (shown == 0) {
                ImGui.textDisabled(capturing ? "Waiting for packets..." : "Capture is paused.");
            }
            ImGui.endChild();

            ImGui.textDisabled(entries.size() + " / " + PacketLog.capacity() + " buffered  ("
                    + "<- S2C, -> C2S)");
        } finally {
            if (body) ImGui.popFont();
            ImGui.end();
        }
    }

    private String formatLogTime(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(LOG_TIME);
    }

    private void renderCommandScreen(float yOff) {
        boolean body = beginCenteredWindow("##relicCommand", COMMAND_W, COMMAND_H, yOff, false);
        try {
            contentHeader("Command Input", "Send chat or commands without opening chat");

            boolean connected = client.getNetworkHandler() != null;
            if (!connected) {
                ImGui.textColored(1.0f, 0.63f, 0.25f, 1.0f, "Not connected to a server.");
                ImGui.dummy(0f, 4f);
            }

            ImGui.setNextItemWidth(COMMAND_W - 30f);
            if (commandFocus) {
                ImGui.setKeyboardFocusHere();
                commandFocus = false;
            }
            boolean entered = ImGui.inputTextWithHint("##cmdinput", "/command or chat message…",
                    commandInput, ImGuiInputTextFlags.EnterReturnsTrue);

            ImGui.dummy(0f, 4f);
            boolean clicked = ImGui.button("Send", 100, 0);
            ImGui.sameLine();
            ImGui.textDisabled("or press Enter");

            if (entered || clicked) {
                sendCommandInput();
                commandFocus = true;
            }

            if (!commandStatus.isEmpty()) {
                ImGui.dummy(0f, 4f);
                ImGui.textDisabled(commandStatus);
            }

            ImGui.dummy(0f, 6f);
            ImGui.separator();
            ImGui.dummy(0f, 6f);
            ImGui.text("Recent");
            if (!commandHistory.isEmpty()) {
                ImGui.sameLine();
                if (ImGui.smallButton("Clear##cmdhist")) {
                    commandHistory.clear();
                    uiSound("/sounds/click.wav", 0.4f);
                }
            }
            ImGui.dummy(0f, 2f);

            ImGui.beginChild("##cmdhistbody", COMMAND_W - 30f, COMMAND_H - 200f, true);
            if (commandHistory.isEmpty()) {
                ImGui.textDisabled("Nothing sent yet.");
            } else {
                for (int i = 0; i < commandHistory.size(); i++) {
                    String h = commandHistory.get(i);
                    if (ImGui.selectable(h + "##h" + i)) {
                        commandInput.set(h);
                        commandFocus = true;
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Click to refill, Enter to resend");
                }
            }
            ImGui.endChild();
        } finally {
            if (body) ImGui.popFont();
            ImGui.end();
        }
    }

    private void sendCommandInput() {
        String text = commandInput.get().trim();
        if (text.isEmpty()) return;

        var net = client.getNetworkHandler();
        if (net == null) {
            commandStatus = "Can't send — not connected.";
            return;
        }

        if (text.startsWith("/")) {
            net.sendChatCommand(text.substring(1));
            commandStatus = "Sent command: " + text;
        } else {
            net.sendChatMessage(text);
            commandStatus = "Sent: " + text;
        }

        commandHistory.remove(text);
        commandHistory.add(0, text);
        while (commandHistory.size() > COMMAND_HISTORY_MAX) {
            commandHistory.remove(commandHistory.size() - 1);
        }

        commandInput.set("");
        uiSound("/sounds/confirm.wav", 0.5f);
    }

    private void renderSearch(float yOff) {
        ColorTheme theme = ThemeManager.get();
        float dx = ImGui.getIO().getDisplaySizeX();

        float iconW = 30f, openW = 220f, margin = 12f;
        float t = Animations.to("search_open", searchOpen ? 1f : 0f, 14f);
        float boxW = iconW + (openW - iconW) * t;
        float boxH = TAB_BAR_H;
        float barX = dx - boxW - margin;
        float barY = 12f + yOff;

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, argbToAbgr(theme.title()));
        ImGui.setNextWindowPos(barX, barY, ImGuiCond.Always);
        ImGui.setNextWindowSize(boxW, boxH, ImGuiCond.Always);
        ImGui.begin("##relicSearch", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
                | ImGuiWindowFlags.NoBringToFrontOnFocus);
        boolean body = pushFont(ImGuiManager.getTextFont());

        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 wp = ImGui.getWindowPos();
        int accent = argbToAbgr(theme.accent());
        int textCol = argbToAbgr(theme.text());

        drawMagnifier(dl, wp.x + iconW / 2f, wp.y + boxH / 2f,
                withAlpha(textCol, searchOpen ? 1f : 0.75f));
        ImGui.setCursorScreenPos(wp.x, wp.y);
        if (ImGui.invisibleButton("##searchIcon", iconW, boxH)) {
            searchOpen = !searchOpen;
            if (searchOpen) searchFocus = true;
            else moduleSearch.set("");
            uiSound("/sounds/click.wav", 0.5f);
        }
        hoverSound("searchIcon", ImGui.isItemHovered());

        if (t > 0.05f) {
            float fieldX = iconW + 2f;
            float fieldW = boxW - fieldX - 6f;
            ImGui.setCursorScreenPos(wp.x + fieldX, wp.y + (boxH - ImGui.getFrameHeight()) / 2f);
            ImGui.setNextItemWidth(fieldW);
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0x00000000);
            if (searchFocus) {
                ImGui.setKeyboardFocusHere();
                searchFocus = false;
            }
            ImGui.inputTextWithHint("##searchInput", "Search modules...", moduleSearch);
            ImGui.popStyleColor();
        }

        dl.addRectFilled(wp.x, wp.y + boxH - 1, wp.x + boxW, wp.y + boxH, withAlpha(accent, t));

        if (body) ImGui.popFont();
        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar();

        renderSearchResults(barX, barY + boxH, boxW);
    }

    private void renderSearchResults(float anchorX, float anchorY, float anchorW) {
        String query = moduleSearch.get().toLowerCase().trim();
        boolean show = searchOpen && !query.isEmpty();
        float t = Animations.to("search_drop", show ? 1f : 0f, 14f);
        if (t < 0.02f) return;

        ColorTheme theme = ThemeManager.get();

        List<Module> matches = new ArrayList<>();
        for (Module m : moduleManager.getAllModules()) {
            if (m.getName().toLowerCase().contains(query)
                    || m.getCategory().getDisplayName().toLowerCase().contains(query)) {
                matches.add(m);
            }
        }

        float dropW = Math.max(anchorW, 240f);
        float dropX = anchorX + anchorW - dropW;
        float fullH = (matches.isEmpty() ? ROW_H : matches.size() * ROW_H) + 8f;
        fullH = Math.min(fullH, 8 * ROW_H + 8f);
        float dropH = fullH * t;

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 4f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, argbToAbgr(theme.bg()));
        ImGui.setNextWindowPos(dropX, anchorY, ImGuiCond.Always);
        ImGui.setNextWindowSize(dropW, dropH, ImGuiCond.Always);
        ImGui.begin("##relicSearchResults", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollWithMouse);
        boolean body = pushFont(ImGuiManager.getTextFont());

        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 wp = ImGui.getWindowPos();
        ImVec2 ws = ImGui.getWindowSize();

        if (matches.isEmpty()) {
            dl.addText(wp.x + PAD, wp.y + 6f, withAlpha(argbToAbgr(theme.text()), 0.5f),
                    "No modules found");
        } else {
            for (Module m : matches) searchResultRow(m, dropW);
        }

        dl.addRect(wp.x, wp.y, wp.x + ws.x, wp.y + ws.y, argbToAbgr(theme.border()), 0f, 0, 1f);
        dl.addRectFilled(wp.x, wp.y, wp.x + ws.x, wp.y + 1f, withAlpha(argbToAbgr(theme.accent()), t));

        if (body) ImGui.popFont();
        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
    }

    private void searchResultRow(Module module, float rowW) {
        ColorTheme theme = ThemeManager.get();
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 p = ImGui.getCursorScreenPos();
        int id = module.hashCode();

        ImGui.invisibleButton("##sr" + id, rowW, ROW_H);
        boolean hovered = ImGui.isItemHovered();
        hoverSound("sr" + id, hovered);
        if (ImGui.isItemClicked(0)) {
            module.toggle();
            uiSound(module.isEnabled() ? "/sounds/confirm.wav" : "/sounds/click.wav", 0.5f);
        }
        if (hovered && module.getDescription() != null && !module.getDescription().isEmpty()) {
            ImGui.setTooltip(module.getDescription());
        }

        float en = Animations.to("sren" + id, module.isEnabled() ? 1f : 0f, 16f);
        float hv = Animations.to("srhov" + id, hovered ? 1f : 0f, 18f);
        int rowCol = lerpColor(withAlpha(argbToAbgr(theme.buttonOff()), hv * 0.6f),
                argbToAbgr(theme.accent()), en);
        if (en > 0.002f || hv > 0.002f) {
            dl.addRectFilled(p.x, p.y, p.x + rowW, p.y + ROW_H, rowCol);
        }

        int nameCol = lerpColor(argbToAbgr(theme.text()), 0xFFFFFFFF, en);
        float textY = p.y + (ROW_H - ImGui.getTextLineHeight()) / 2f;
        dl.addText(p.x + PAD, textY, nameCol, module.getName());

        String cat = module.getCategory().getDisplayName();
        float cw = ImGui.calcTextSize(cat).x;
        dl.addText(p.x + rowW - PAD - cw, textY, withAlpha(nameCol, 0.55f), cat);
    }

    private void drawMagnifier(ImDrawList dl, float cx, float cy, int col) {
        float r = 4.2f;
        float ox = cx - 1.5f, oy = cy - 1.5f;
        dl.addCircle(ox, oy, r, col, 12, 1.6f);
        float d = r * 0.7f;
        dl.addLine(ox + d, oy + d, cx + 4.8f, cy + 4.8f, col, 1.8f);
    }

    private void renderPanels(float yOff) {
        guideX = Float.NaN;
        guideY = Float.NaN;
        for (Module.Category category : CATEGORIES) {
            renderPanel(category, yOff);
        }

        if (!Float.isNaN(guideX) || !Float.isNaN(guideY)) {
            ImDrawList fg = ImGui.getForegroundDrawList();
            int accent = argbToAbgr(ThemeManager.get().accent());
            float dh = ImGui.getIO().getDisplaySizeY();
            float dw = ImGui.getIO().getDisplaySizeX();
            if (!Float.isNaN(guideX)) fg.addLine(guideX, 0, guideX, dh, accent, 1f);
            if (!Float.isNaN(guideY)) fg.addLine(0, guideY, dw, guideY, accent, 1f);
        }
    }

    private void renderPanel(Module.Category category, float yOff) {
        ColorTheme theme = ThemeManager.get();
        float[] pos = PanelLayout.get(category);
        float dispW = ImGui.getIO().getDisplaySizeX();
        float dispH = ImGui.getIO().getDisplaySizeY();

        float px = clamp(pos[0], 0f, Math.max(0f, dispW - PANEL_W));
        float py = clamp(pos[1], TAB_BAR_H + 16f, Math.max(TAB_BAR_H + 16f, dispH - 40f));
        pos[0] = px;
        pos[1] = py;

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.setNextWindowPos(px, py + yOff, ImGuiCond.Always);
        ImGui.setNextWindowSizeConstraints(PANEL_W, 0f, PANEL_W, Float.MAX_VALUE);
        ImGui.begin("##panel_" + category.name(), ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.AlwaysAutoResize);

        boolean body = pushFont(ImGuiManager.getTextFont());

        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 wp = ImGui.getWindowPos();

        ImGui.invisibleButton("##drag_" + category.name(), PANEL_W, HEADER_H);
        handlePanelDrag(category, wp);
        dl.addRectFilled(wp.x, wp.y, wp.x + PANEL_W, wp.y + HEADER_H, argbToAbgr(theme.title()));
        dl.addRectFilled(wp.x, wp.y + HEADER_H - 1, wp.x + PANEL_W, wp.y + HEADER_H,
                argbToAbgr(theme.accent()));
        boolean tf = pushFont(ImGuiManager.getTitleFont());
        float nameY = wp.y + (HEADER_H - ImGui.getTextLineHeight()) / 2f;
        dl.addText(wp.x + PAD, nameY, argbToAbgr(theme.text()), category.getDisplayName());
        if (tf) ImGui.popFont();

        List<Module> modules = moduleManager.getModulesByCategory(category);
        for (Module module : modules) {
            panelRow(module);
        }
        if (modules.isEmpty()) {
            ImGui.dummy(PANEL_W, ROW_H);
            dl.addText(wp.x + PAD, wp.y + HEADER_H + 5, withAlpha(argbToAbgr(theme.text()), 0.5f), "Empty");
        }

        ImVec2 ws = ImGui.getWindowSize();
        dl.addRect(wp.x, wp.y, wp.x + ws.x, wp.y + ws.y, argbToAbgr(theme.border()), 0f, 0, 1f);
        panelHeights.put(category, ws.y);

        if (body) ImGui.popFont();
        ImGui.end();
        ImGui.popStyleVar(3);
    }

    private void handlePanelDrag(Module.Category category, ImVec2 wp) {
        float mx = ImGui.getIO().getMousePosX();
        float my = ImGui.getIO().getMousePosY();

        if (ImGui.isItemActivated()) {
            draggingPanel = category;
            grabX = mx - wp.x;
            grabY = my - wp.y;
        }
        if (draggingPanel == category && ImGui.isItemActive()) {
            float dispW = ImGui.getIO().getDisplaySizeX();
            float dispH = ImGui.getIO().getDisplaySizeY();
            float h = panelHeights.getOrDefault(category, 120f);
            float nx = mx - grabX;
            float ny = my - grabY;

            float[][] lines = panelSnapLines(category);
            Snapping.Result r = Snapping.snap(nx, ny, PANEL_W, h, lines[0], lines[1]);
            nx = clamp(r.x, 0f, dispW - PANEL_W);
            ny = clamp(r.y, TAB_BAR_H + 16f, dispH - 40f);
            PanelLayout.set(category, nx, ny);
            guideX = r.guideX;
            guideY = r.guideY;
        }
        if (draggingPanel == category && ImGui.isItemDeactivated()) {
            draggingPanel = null;
            uiSound("/sounds/click.wav", 0.4f);
        }
    }

    private float[][] panelSnapLines(Module.Category exclude) {
        List<Float> v = new ArrayList<>();
        List<Float> h = new ArrayList<>();
        float dispW = ImGui.getIO().getDisplaySizeX();
        float dispH = ImGui.getIO().getDisplaySizeY();
        v.add(0f); v.add(dispW * 0.5f); v.add(dispW);
        h.add(TAB_BAR_H + 16f); h.add(0f); h.add(dispH);
        for (Module.Category c : CATEGORIES) {
            if (c == exclude) continue;
            float[] p = PanelLayout.get(c);
            float ph = panelHeights.getOrDefault(c, 120f);
            v.add(p[0]); v.add(p[0] + PANEL_W); v.add(p[0] + PANEL_W * 0.5f);
            h.add(p[1]); h.add(p[1] + ph); h.add(p[1] + ph * 0.5f);
        }
        return new float[][]{toArr(v), toArr(h)};
    }

    private void panelRow(Module module) {
        ColorTheme theme = ThemeManager.get();
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 p = ImGui.getCursorScreenPos();
        int id = module.hashCode();
        boolean isExpanded = expanded.contains(module);
        boolean hasSettings = !module.getSettings().isEmpty();

        ImGui.invisibleButton("##row" + id, PANEL_W, ROW_H,
                ImGuiButtonFlags.MouseButtonLeft | ImGuiButtonFlags.MouseButtonRight);
        boolean hovered = ImGui.isItemHovered();
        hoverSound("row" + id, hovered);
        if (ImGui.isItemClicked(0)) {
            module.toggle();
            uiSound(module.isEnabled() ? "/sounds/confirm.wav" : "/sounds/click.wav", 0.5f);
        }
        if (ImGui.isItemClicked(1) && hasSettings) {
            if (isExpanded) expanded.remove(module);
            else expanded.add(module);
            isExpanded = !isExpanded;
            uiSound("/sounds/click.wav", 0.4f);
        }
        if (hovered && module.getDescription() != null && !module.getDescription().isEmpty()) {
            ImGui.setTooltip(module.getDescription());
        }

        float en = Animations.to("rowen" + id, module.isEnabled() ? 1f : 0f, 16f);
        float hv = Animations.to("rowhov" + id, hovered ? 1f : 0f, 18f);
        int rowCol = lerpColor(withAlpha(argbToAbgr(theme.buttonOff()), hv * 0.6f),
                argbToAbgr(theme.accent()), en);
        if (en > 0.002f || hv > 0.002f) {
            dl.addRectFilled(p.x, p.y, p.x + PANEL_W, p.y + ROW_H, rowCol);
        }

        int nameCol = lerpColor(argbToAbgr(theme.text()), 0xFFFFFFFF, en);
        float textY = p.y + (ROW_H - ImGui.getTextLineHeight()) / 2f;
        dl.addText(p.x + PAD, textY, nameCol, module.getName());

        float rightEdge = p.x + PANEL_W - PAD;

        if (hasSettings) {
            String arrow = isExpanded ? "-" : "+";
            float aw = ImGui.calcTextSize(arrow).x;
            dl.addText(rightEdge - aw, textY, withAlpha(nameCol, 0.7f), arrow);
            rightEdge -= aw + 6f;
        }

        int key = module.getKeyBind();
        if (key > 0) {
            String kn = keyName(key);
            float kw = ImGui.calcTextSize(kn).x;
            dl.addText(rightEdge - kw, textY, withAlpha(nameCol, 0.65f), kn);
        }

        if (isExpanded) {
            float contentW = PANEL_W - 2 * PAD;
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6f, 6f);
            ImGui.indent(PAD);
            ImGui.dummy(0f, 2f);
            renderSettings(module, contentW);
            renderBindRow(module, contentW);
            renderConfigRow(module, contentW);
            ImGui.dummy(0f, 2f);
            ImGui.unindent(PAD);
            ImGui.popStyleVar();
        }
    }

    private void renderSettingsScreen(float yOff) {
        boolean body = beginCenteredWindow("##relicSettings", SETTINGS_W, SETTINGS_H, yOff, true);
        try {
            contentHeader("Settings", "Client configuration");

            renderThemeControls();

            ImGui.text("UI Sounds");
            ImGui.sameLine(150);
            ImBoolean sounds = new ImBoolean(ClientSettings.uiSoundsEnabled());
            if (ImGui.checkbox("##uisounds", sounds)) {
                ClientSettings.setUiSounds(sounds.get());
                uiSound("/sounds/click.wav", 0.5f);
                ConfigManager.save();
            }

            ImGui.text("Open GUI Bind");
            ImGui.sameLine(150);
            String label = bindingGuiKey ? "Press a key..." : keyName(ClientSettings.getOpenGuiKey());
            if (ImGui.button(label + "##guibind", WIDGET_WIDTH, 0)) {
                bindingGuiKey = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Click, then press a key. Esc cancels, Backspace resets to Right Shift.");
            }

            ImGui.dummy(0f, 6f);
            ImGui.separator();
            ImGui.dummy(0f, 6f);
            ImGui.text("Layout");
            ImGui.dummy(0f, 2f);
            if (ImGui.button("Tidy Panels", 150, 0)) {
                PanelLayout.tidy(ImGui.getIO().getDisplaySizeX());
                uiSound("/sounds/click.wav", 0.5f);
                ConfigManager.save();
            }
            ImGui.sameLine();
            if (ImGui.button("Reset Panel Layout", 170, 0)) {
                PanelLayout.reset();
                uiSound("/sounds/click.wav", 0.5f);
                ConfigManager.save();
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Drop panels back to their default row.");

            ImGui.dummy(0f, 6f);
            ImGui.separator();
            ImGui.dummy(0f, 6f);
            ImGui.text("Configuration");
            ImGui.dummy(0f, 2f);
            if (ImGui.button("Export Config...", 150, 0)) {
                ConfigManager.exportWithDialog();
                uiSound("/sounds/click.wav", 0.5f);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Save every module's config to a file you choose.");
            }
            ImGui.sameLine();
            if (ImGui.button("Import Config...", 150, 0)) {
                ConfigManager.importWithDialog();
                uiSound("/sounds/click.wav", 0.5f);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Load every module's config from a file (replaces current settings).");
            }

            ImGui.dummy(0f, 6f);
            ImGui.separator();
            ImGui.dummy(0f, 6f);
            renderWebhookControls();
        } finally {
            if (body) ImGui.popFont();
            ImGui.end();
        }
    }

    private void renderWebhookControls() {
        ImGui.text("Discord Webhook");
        ImGui.dummy(0f, 2f);

        if (webhookUrlBuf == null) {
            webhookUrlBuf = new ImString(256);
            webhookUrlBuf.set(ClientSettings.getWebhookUrl());
        }
        ImGui.text("Webhook URL");
        ImGui.sameLine(150);
        ImGui.setNextItemWidth(WIDGET_WIDTH);
        if (ImGui.inputTextWithHint("##webhookUrl", "https://discord.com/api/webhooks/...", webhookUrlBuf)) {
            ClientSettings.setWebhookUrl(webhookUrlBuf.get().trim());
            webhookTestResult = "";
            ConfigManager.save();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Shared by every module. Enable per module with its \"Discord Webhook\" toggle.");
        }

        ImGui.dummy(0f, 2f);
        ImGui.sameLine(150);
        if (ImGui.button("Send Test", 100, 0)) {
            uiSound("/sounds/click.wav", 0.5f);
            String url = webhookUrlBuf.get().trim();
            if (!DiscordWebhook.looksLikeWebhook(url)) {
                webhookTestResult = "Not a valid webhook URL";
            } else {
                webhookTestResult = "Sending...";
                DiscordWebhook.sendTest(url, ok -> webhookTestResult =
                        ok ? "Test sent — check Discord" : "Send failed");
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Post a test message to the webhook above.");
        }
        if (!webhookTestResult.isEmpty()) {
            ImGui.sameLine();
            ImGui.text(webhookTestResult);
        }
    }

    private void renderThemeControls() {
        List<ColorTheme> themes = ThemeManager.all();
        String[] names = new String[themes.size()];
        int current = 0;
        for (int i = 0; i < themes.size(); i++) {
            names[i] = themes.get(i).getDisplayName();
            if (themes.get(i) == ThemeManager.get()) current = i;
        }

        ImGui.text("Theme");
        ImGui.sameLine(150);
        ImGui.setNextItemWidth(WIDGET_WIDTH);
        ImInt themeIndex = new ImInt(current);
        if (ImGui.combo("##theme", themeIndex, names)) {
            ThemeManager.set(themes.get(themeIndex.get()));
            uiSound("/sounds/click.wav", 0.5f);
            ConfigManager.save();
        }

        ImGui.dummy(0f, 1f);
        ImGui.sameLine(150);
        if (ImGui.button("New from current", 140, 0)) {
            ColorTheme src = ThemeManager.get();
            CustomTheme created = CustomTheme.copyOf(
                    ThemeManager.uniqueName(src.getDisplayName() + " Copy"), src);
            ThemeManager.addCustom(created);
            ThemeManager.set(created);
            uiSound("/sounds/confirm.wav", 0.5f);
            ConfigManager.save();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Create an editable copy of the selected theme.");
        }
        boolean editable = ThemeManager.get() instanceof CustomTheme;
        if (editable) {
            ImGui.sameLine();
            if (ImGui.button("Delete", 80, 0)) {
                ThemeManager.removeCustom((CustomTheme) ThemeManager.get());
                uiSound("/sounds/click.wav", 0.5f);
                ConfigManager.save();
                editable = false;
            }
        }

        if (editable) {
            renderThemeEditor((CustomTheme) ThemeManager.get());
        }
    }

    private void renderThemeEditor(CustomTheme theme) {
        ImGui.dummy(0f, 4f);
        ImGui.separator();
        ImGui.dummy(0f, 2f);
        ImGui.text("Edit Theme");
        ImGui.dummy(0f, 2f);

        if (themeNameBufFor != theme) {
            themeNameBuf.set(theme.getDisplayName());
            themeNameBufFor = theme;
        }
        ImGui.text("Name");
        ImGui.sameLine(150);
        ImGui.setNextItemWidth(WIDGET_WIDTH);
        if (ImGui.inputText("##themeName", themeNameBuf)) {
            String typed = themeNameBuf.get().trim();

            if (!typed.isEmpty()
                    && (typed.equalsIgnoreCase(theme.getDisplayName()) || !ThemeManager.nameTaken(typed))) {
                theme.setDisplayName(typed);
            }
        }
        if (ImGui.isItemDeactivatedAfterEdit()) ConfigManager.save();

        int flags = ImGuiColorEditFlags.AlphaBar;
        for (int role = 0; role < CustomTheme.ROLE_NAMES.length; role++) {
            ImGui.text(CustomTheme.ROLE_NAMES[role]);
            ImGui.sameLine(150);
            float[] col = argbToFloats(theme.get(role));
            if (ImGui.colorEdit4("##themeRole" + role, col, flags)) {
                theme.set(role, floatsToArgb(col));
            }
            if (ImGui.isItemDeactivatedAfterEdit()) ConfigManager.save();
        }
    }

    private static float[] argbToFloats(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >>  8) & 0xFF) / 255f,
                ( argb        & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f
        };
    }

    private static int floatsToArgb(float[] c) {
        return (clampByte(c[3]) << 24) | (clampByte(c[0]) << 16) | (clampByte(c[1]) << 8) | clampByte(c[2]);
    }

    private static int clampByte(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private void renderLocatorScreen(float yOff) {
        boolean body = beginCenteredWindow("##relicLocator", LOCATOR_W, LOCATOR_H, yOff, true);
        try {
            renderLocatorPage();
        } finally {
            if (body) ImGui.popFont();
            ImGui.end();
        }
    }

    private boolean beginCenteredWindow(String id, float w, float h, float yOff, boolean scroll) {
        float dx = ImGui.getIO().getDisplaySizeX();
        float dy = ImGui.getIO().getDisplaySizeY();
        ImGui.setNextWindowPos((dx - w) / 2f, Math.max(TAB_BAR_H + 24f, (dy - h) / 2f) + yOff, ImGuiCond.Always);
        ImGui.setNextWindowSize(w, h, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoCollapse;
        if (!scroll) flags |= ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        ImGui.begin(id, flags);
        return pushFont(ImGuiManager.getTextFont());
    }

    private void contentHeader(String title, String subtitle) {
        ImGui.dummy(0f, 2f);
        boolean tf = pushFont(ImGuiManager.getTitleFont());
        centeredText(title, false);
        if (tf) ImGui.popFont();
        if (subtitle != null) centeredText(subtitle, true);
        ImGui.dummy(0f, 6f);
        ImGui.separator();
        ImGui.dummy(0f, 8f);
    }

    private void renderLocatorPage() {
        contentHeader("Bedrock Locator", null);
        BedrockLocator locator = BedrockLocator.getInstance();

        if (ImGui.button("?", 26, 0)) ImGui.openPopup("##blhelp");
        if (ImGui.isItemHovered()) ImGui.setTooltip("How to use the Bedrock Locator");
        ImGui.sameLine();
        ImGui.textWrapped("Find where a bedrock pattern occurs in a known seed's overworld.");
        renderBedrockHelpPopup();
        ImGui.separator();

        if (bedrockSeedBuf == null) {
            bedrockSeedBuf = new ImString(64);
            bedrockSeedBuf.set(locator.getSeedText());
        }
        ImGui.text("Seed");
        ImGui.sameLine(90);
        ImGui.setNextItemWidth(WIDGET_WIDTH);
        if (ImGui.inputText("##blseed", bedrockSeedBuf)) {
            locator.setSeedText(bedrockSeedBuf.get());
        }

        ImGui.text("Layer Y");
        ImGui.sameLine(90);
        ImGui.setNextItemWidth(WIDGET_WIDTH);
        int layerIndex = layerIndexOf(locator.getLayerY());
        ImInt li = new ImInt(layerIndex);
        if (ImGui.combo("##bllayer", li, LAYER_LABELS)) {
            locator.setLayerY(LAYER_YS[li.get()]);
        }

        ImGui.text("Grid");
        ImGui.sameLine(90);
        ImInt gw = new ImInt(locator.getGridWidth());
        ImGui.setNextItemWidth(60);
        if (ImGui.inputInt("W##blw", gw)) locator.setGridSize(gw.get(), locator.getGridHeight());
        ImGui.sameLine();
        ImInt gh = new ImInt(locator.getGridHeight());
        ImGui.setNextItemWidth(60);
        if (ImGui.inputInt("H##blh", gh)) locator.setGridSize(locator.getGridWidth(), gh.get());

        ImGui.spacing();
        renderBedrockGrid(locator);
        ImGui.textDisabled("Click a cell: dark = unknown, light = bedrock, red = empty. "
                + "Marked: " + locator.markedCount());

        ImGui.separator();

        ImGui.text("Center");
        ImGui.sameLine(90);
        ImInt cx = new ImInt(locator.getCenterX());
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("X##blcx", cx, 0)) locator.setCenterX(cx.get());
        ImGui.sameLine();
        ImInt cz = new ImInt(locator.getCenterZ());
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Z##blcz", cz, 0)) locator.setCenterZ(cz.get());

        ImGui.text("Radius");
        ImGui.sameLine(90);
        ImInt rad = new ImInt(locator.getRadius());
        ImGui.setNextItemWidth(120);
        if (ImGui.inputInt("##blrad", rad)) locator.setRadius(rad.get());
        ImGui.sameLine();
        long span = (long) locator.getRadius() * 2;
        ImGui.textDisabled(span + " across");
        if (locator.getRadius() > BedrockLocator.SOFT_MAX_RADIUS) {
            ImGui.textColored(1.0f, 0.63f, 0.25f, 1.0f, "Large radius — search may take a long time.");
        }

        ImGui.separator();

        boolean running = locator.isRunning();
        if (running) {
            if (ImGui.button("Cancel", 90, 0)) locator.cancel();
        } else {
            if (ImGui.button("Search", 90, 0)) locator.start();
        }
        ImGui.sameLine();
        if (ImGui.button("Clear Grid")) locator.clearGrid();
        ImGui.sameLine();
        if (ImGui.button("Validate at player")) {
            lastBedrockValidation = locator.validateAtPlayer();
        }

        ImGui.progressBar(locator.getProgress(), 360f, 0f,
                locator.getScanned() + " / " + locator.getTotal());
        if (!locator.getMessage().isEmpty()) ImGui.text(locator.getMessage());
        if (!lastBedrockValidation.isEmpty()) ImGui.textDisabled(lastBedrockValidation);

        var results = locator.getResults();
        if (!results.isEmpty()) {
            ImGui.text("Matches:");
            ImGui.beginChild("##blresults", 360, 120, true);
            for (long[] r : results) {
                String coord = r[0] + ", " + r[1];
                ImGui.text(coord);
                ImGui.sameLine();
                if (ImGui.smallButton("Copy##bl" + coord)) {
                    ImGui.setClipboardText(coord);
                }
            }
            ImGui.endChild();
        }
    }

    private void renderBedrockGrid(BedrockLocator locator) {
        float rowH = CELL_SIZE + CELL_GAP;
        float childH = Math.min(locator.getGridHeight() * rowH + 8f, 360f);
        ImGui.beginChild("##blgrid", 384f, childH, true, ImGuiWindowFlags.HorizontalScrollbar);
        for (int row = 0; row < locator.getGridHeight(); row++) {
            for (int col = 0; col < locator.getGridWidth(); col++) {
                int color = argbToAbgr(cellColorArgb(locator.getCell(col, row)));
                ImGui.pushStyleColor(ImGuiCol.Button, color);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, lighten(color, 18));
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, lighten(color, 8));
                if (ImGui.button("##cell" + row + "_" + col, CELL_SIZE, CELL_SIZE)) {
                    locator.cycleCell(col, row);
                }
                ImGui.popStyleColor(3);
                if (col < locator.getGridWidth() - 1) ImGui.sameLine(0, CELL_GAP);
            }
        }
        ImGui.endChild();
    }

    private void renderBedrockHelpPopup() {
        if (ImGui.beginPopup("##blhelp")) {
            boolean body = pushFont(ImGuiManager.getTextFont());
            ImGui.pushTextWrapPos(390f);

            ImGui.text("How to use the Bedrock Locator");
            ImGui.separator();

            ImGui.textWrapped("1.  Enter the world seed (vanilla overworld only).");
            ImGui.spacing();
            ImGui.textWrapped("2.  ORIENTATION - this is what makes it accurate:");
            ImGui.bullet();
            ImGui.textWrapped("Face NORTH (press F3 - it should read \"Towards negative Z\"), then look straight down at the bedrock.");
            ImGui.bullet();
            ImGui.textWrapped("The TOP of your view/photo is then North (-Z); the RIGHT side is East (+X).");
            ImGui.bullet();
            ImGui.textWrapped("Copy the top-left of your photo into the top-left cell of the grid, going right = East, down = South.");
            ImGui.bullet();
            ImGui.textWrapped("The result is the coordinate of the TOP-LEFT (north-west) cell of your grid.");
            ImGui.spacing();
            ImGui.textWrapped("3.  Pick the Layer Y your photo shows. The % is how often bedrock appears at that layer - expose and read a single Y level.");
            ImGui.spacing();
            ImGui.textWrapped("4.  Click cells to mark them: dark = unknown, light = bedrock, red = no bedrock. Leave a cell UNKNOWN if unsure - one wrong cell can hide the real match.");
            ImGui.spacing();
            ImGui.textWrapped("5.  Mark plenty of cells (about 50+, e.g. a 10x10 area) so the result is unique.");
            ImGui.spacing();
            ImGui.textWrapped("6.  Set Center near where you think the spot is, and a Radius (half the box width). Bigger radius = slower. Then press Search.");
            ImGui.spacing();
            ImGui.textDisabled("If you get no match for a pattern you're sure of, the photo is rotated - re-orient facing North.");

            ImGui.separator();
            if (ImGui.button("Got it")) ImGui.closeCurrentPopup();

            ImGui.popTextWrapPos();
            if (body) ImGui.popFont();
            ImGui.endPopup();
        }
    }

    private static int cellColorArgb(byte state) {
        return switch (state) {
            case BedrockLocator.BEDROCK -> 0xFFC8C8C8;
            case BedrockLocator.NOT_BEDROCK -> 0xFF3A1515;
            default -> 0xFF2A2A2A;
        };
    }

    private static int layerIndexOf(int y) {
        for (int i = 0; i < LAYER_YS.length; i++) if (LAYER_YS[i] == y) return i;
        return 2;
    }

    private static final float MAP_SIDE_W = 250f;

    private static final int MAP_WP_COLOR = 0xFFFFC83D;

    private void renderMapScreen(float yOff) {
        SeedMap map = SeedMap.getInstance();
        float dx = ImGui.getIO().getDisplaySizeX();
        float dy = ImGui.getIO().getDisplaySizeY();
        float margin = 16f;
        float top = TAB_BAR_H + 24f;

        ImGui.setNextWindowPos(margin, top + yOff, ImGuiCond.Always);
        ImGui.setNextWindowSize(dx - margin * 2f, dy - top - margin, ImGuiCond.Always);
        ImGui.begin("##relicMap", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        boolean body = pushFont(ImGuiManager.getTextFont());
        try {
            renderMapControls(map);
            ImGui.separator();
            ImGui.dummy(0f, 2f);

            float availW = ImGui.getContentRegionAvailX();
            float availH = ImGui.getContentRegionAvailY();
            float canvasW = Math.max(120f, availW - MAP_SIDE_W - 10f);

            renderMapCanvas(map, canvasW, availH);
            ImGui.sameLine(0, 10f);
            renderMapSidePanel(map, MAP_SIDE_W, availH);
        } finally {
            if (body) ImGui.popFont();
            ImGui.end();
        }
    }

    private void renderMapControls(SeedMap map) {

        String server = currentMapServer();
        map.syncServerSeed(server);

        if (mapSeedBuf == null) {
            mapSeedBuf = new ImString(64);
            mapSeedBuf.set(map.getSeedText());
        }

        if (!mapSeedBuf.get().equals(map.getSeedText())) mapSeedBuf.set(map.getSeedText());

        ImGui.text("Seed");
        ImGui.sameLine();
        ImGui.setNextItemWidth(150);
        if (ImGui.inputText("##mapseed", mapSeedBuf)) {
            map.setSeedText(mapSeedBuf.get());
            map.rememberSeedForServer(server, mapSeedBuf.get());
        }

        if (ImGui.isItemDeactivatedAfterEdit()) ConfigManager.save();

        ImGui.sameLine(0, 16);
        ImGui.text("Y");
        ImGui.sameLine();
        ImGui.setNextItemWidth(64);
        ImInt y = new ImInt(map.getSampleY());
        if (ImGui.inputInt("##mapy", y, 0)) map.setSampleY(Math.max(-64, Math.min(320, y.get())));

        ImGui.sameLine(0, 16);
        if (ImGui.button("-##mapzo", 26, 0)) map.zoomAt(1.5, map.getCenterX(), map.getCenterZ());
        ImGui.sameLine(0, 4);
        if (ImGui.button("+##mapzi", 26, 0)) map.zoomAt(1.0 / 1.5, map.getCenterX(), map.getCenterZ());
        ImGui.sameLine();
        ImGui.textDisabled(String.format("%.2f blk/px", map.getBlocksPerPixel()));

        ImGui.sameLine(0, 16);
        ImGui.text("Go to");
        ImGui.sameLine();
        ImGui.setNextItemWidth(72);
        ImGui.inputInt("X##mapgox", mapGoX, 0);
        ImGui.sameLine();
        ImGui.setNextItemWidth(72);
        ImGui.inputInt("Z##mapgoz", mapGoZ, 0);
        ImGui.sameLine();
        if (ImGui.button("Go##mapgo")) map.setCenter(mapGoX.get(), mapGoZ.get());

        ImGui.sameLine(0, 16);
        if (ImGui.button("Refresh##map")) map.forceRefresh();

        if (map.isWorking()) {
            ImGui.sameLine(0, 16);
            ImGui.textDisabled("Rendering...");
        } else if (!map.getStatus().isEmpty()) {
            ImGui.sameLine(0, 16);
            ImGui.textColored(1.0f, 0.63f, 0.25f, 1.0f, map.getStatus());
        }
    }

    private String currentMapServer() {
        ServerInfo entry = client.getCurrentServerEntry();
        if (entry != null && entry.address != null && !entry.address.isBlank()) {
            return entry.address;
        }
        return null;
    }

    private void renderMapCanvas(SeedMap map, float w, float h) {
        ImGui.beginChild("##mapcanvas", w, h, true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoMove);
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 wp = ImGui.getWindowPos();
        ImVec2 ws = ImGui.getWindowSize();
        float x0 = wp.x, y0 = wp.y, x1 = wp.x + ws.x, y1 = wp.y + ws.y;
        float cx = x0 + ws.x / 2f, cy = y0 + ws.y / 2f;

        dl.addRectFilled(x0, y0, x1, y1, argbToAbgr(0xFF0E1013));

        map.frame((int) ws.x, (int) ws.y);

        int tex = map.textureGlId();
        double[] win = map.imageWindow();
        if (tex != 0 && win != null) {
            double bppView = map.getBlocksPerPixel();
            double sx0 = cx + (win[0] - map.getCenterX()) / bppView;
            double sy0 = cy + (win[1] - map.getCenterZ()) / bppView;
            double sw = win[3] * win[2] / bppView;
            double sh = win[4] * win[2] / bppView;
            dl.addImage(tex, (float) sx0, (float) sy0, (float) (sx0 + sw), (float) (sy0 + sh));
        } else {
            String msg = map.getStatus().isEmpty() ? "Enter a seed to render the map." : map.getStatus();
            ImVec2 ts = ImGui.calcTextSize(msg);
            dl.addText(cx - ts.x / 2f, cy - ts.y / 2f, argbToAbgr(0xFFB0B0B0), msg);
        }

        ImGui.setCursorScreenPos(x0, y0);
        ImGui.invisibleButton("##mapinput", ws.x, ws.y);
        boolean hovered = ImGui.isItemHovered();
        float mx = ImGui.getIO().getMousePosX();
        float my = ImGui.getIO().getMousePosY();

        if (ImGui.isItemActivated()) {
            mapDragging = true; mapMoved = false;
            mapPressX = mx; mapPressY = my; mapLastX = mx; mapLastY = my;
        }
        if (mapDragging && ImGui.isItemActive()) {
            if (mx != mapLastX || my != mapLastY) map.panPixels(mx - mapLastX, my - mapLastY);
            mapLastX = mx; mapLastY = my;
            float ddx = mx - mapPressX, ddy = my - mapPressY;
            if (ddx * ddx + ddy * ddy > 25f) mapMoved = true;
        }
        if (ImGui.isItemDeactivated()) {
            if (mapDragging && !mapMoved) handleMapClick(map, mx, my, cx, cy);
            mapDragging = false;
        }

        if (hovered) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0f) {
                map.zoomAt(Math.pow(0.82, wheel),
                        map.screenToWorldX(mx, cx), map.screenToWorldZ(my, cy));
            }

            if (ImGui.isMouseClicked(1)) {
                StructureFinder.Found hit = structureAtScreen(map, mx, my, cx, cy);
                if (hit != null) {
                    boolean done = map.toggleStructureComplete(hit);
                    uiSound(done ? "/sounds/click.wav" : "/sounds/hover.wav", 0.45f);
                }
            }
        }

        drawMapOverlays(map, dl, x0, y0, x1, y1, cx, cy, hovered, mx, my);
        dl.addRect(x0, y0, x1, y1, argbToAbgr(ThemeManager.get().border()), 0f, 0, 1f);
        ImGui.endChild();
    }

    private void handleMapClick(SeedMap map, float mx, float my, float cx, float cy) {
        StructureFinder.Found hit = structureAtScreen(map, mx, my, cx, cy);
        if (hit != null) {
            String coord = hit.blockX() + ", " + hit.blockZ();
            ImGui.setClipboardText(coord);
            NotificationManager.getInstance().push("Copied", hit.id() + "  " + coord);
            uiSound("/sounds/confirm.wav", 0.5f);
        } else {
            int wx = (int) Math.round(map.screenToWorldX(mx, cx));
            int wz = (int) Math.round(map.screenToWorldZ(my, cy));
            map.addWaypoint("", wx, wz, MAP_WP_COLOR);
            uiSound("/sounds/confirm.wav", 0.4f);
        }
    }

    private StructureFinder.Found structureAtScreen(SeedMap map, float mx, float my, float cx, float cy) {
        for (StructureFinder.Found s : map.currentStructures()) {
            if (!map.isStructureShown(s.id())) continue;
            float sx = (float) map.worldToScreenX(s.blockX(), cx);
            float sy = (float) map.worldToScreenZ(s.blockZ(), cy);
            if (Math.abs(mx - sx) <= 9 && Math.abs(my - sy) <= 9) return s;
        }
        return null;
    }

    private void drawMapOverlays(SeedMap map, ImDrawList dl, float x0, float y0, float x1, float y1,
                                 float cx, float cy, boolean hovered, float mx, float my) {
        drawMapSlime(map, dl, cx, cy);
        drawMapGrid(map, dl, x0, y0, x1, y1, cx, cy);
        drawMapStructures(map, dl, x0, y0, x1, y1, cx, cy, mx, my, hovered);

        int axis = withAlpha(0xFFFFFFFF, 0.30f);
        dl.addLine(cx - 6, cy, cx + 6, cy, axis, 1f);
        dl.addLine(cx, cy - 6, cx, cy + 6, axis, 1f);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null
                && mc.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD) {
            float psx = (float) map.worldToScreenX(mc.player.getX(), cx);
            float psy = (float) map.worldToScreenZ(mc.player.getZ(), cy);
            if (psx >= x0 && psx <= x1 && psy >= y0 && psy <= y1) {
                int col = argbToAbgr(ThemeManager.get().accent());
                dl.addCircleFilled(psx, psy, 4f, col);
                dl.addCircle(psx, psy, 4.5f, 0xFF000000, 12, 1.5f);
            }
        }

        for (SeedMap.Waypoint wp : map.getWaypoints()) {
            float sx = (float) map.worldToScreenX(wp.x, cx);
            float sy = (float) map.worldToScreenZ(wp.z, cy);
            if (sx < x0 - 30 || sx > x1 + 30 || sy < y0 - 30 || sy > y1 + 30) continue;
            int col = argbToAbgr(wp.argb);
            dl.addQuadFilled(sx, sy - 6, sx + 6, sy, sx, sy + 6, sx - 6, sy, col);
            dl.addQuad(sx, sy - 6, sx + 6, sy, sx, sy + 6, sx - 6, sy, 0xFF000000, 1.3f);
            dl.addText(sx + 8, sy - 7, withAlpha(0xFFFFFFFF, 0.95f), wp.name);
        }

        if (hovered) {
            double wx = map.screenToWorldX(mx, cx);
            double wz = map.screenToWorldZ(my, cy);
            String biome = map.biomeNameAt(wx, wz);
            String label = (long) Math.floor(wx) + ", " + (long) Math.floor(wz)
                    + (biome != null ? "   " + biome : "");
            ImVec2 ts = ImGui.calcTextSize(label);
            float pad = 5f;
            float bx = x0 + 8, by = y1 - ts.y - 10;
            dl.addRectFilled(bx - pad, by - pad, bx + ts.x + pad, by + ts.y + pad,
                    withAlpha(0xFF000000, 0.62f), 3f);
            dl.addText(bx, by, 0xFFFFFFFF, label);
        }
    }

    private void drawMapSlime(SeedMap map, ImDrawList dl, float cx, float cy) {
        if (!map.isShowSlime()) return;
        long[] chunks = map.currentSlimeChunks();
        if (chunks.length == 0) return;
        int fill = withAlpha(argbToAbgr(0xFF54E66A), 0.30f);
        for (long c : chunks) {
            net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(c);
            float sx = (float) map.worldToScreenX(cp.getStartX(), cx);
            float sy = (float) map.worldToScreenZ(cp.getStartZ(), cy);
            float sx2 = (float) map.worldToScreenX(cp.getStartX() + 16, cx);
            float sy2 = (float) map.worldToScreenZ(cp.getStartZ() + 16, cy);
            dl.addRectFilled(sx, sy, sx2, sy2, fill);
        }
    }

    private void drawMapGrid(SeedMap map, ImDrawList dl, float x0, float y0, float x1, float y1,
                             float cx, float cy) {
        if (!map.isShowGrid()) return;
        double bpp = map.getBlocksPerPixel();
        double wMinX = map.screenToWorldX(x0, cx), wMaxX = map.screenToWorldX(x1, cx);
        double wMinZ = map.screenToWorldZ(y0, cy), wMaxZ = map.screenToWorldZ(y1, cy);

        if (16.0 / bpp >= 4.0) {
            int col = withAlpha(0xFFFFFFFF, 0.10f);
            for (int wx = (int) (Math.floor(wMinX / 16) * 16); wx <= wMaxX; wx += 16) {
                float sx = (float) map.worldToScreenX(wx, cx);
                dl.addLine(sx, y0, sx, y1, col, 1f);
            }
            for (int wz = (int) (Math.floor(wMinZ / 16) * 16); wz <= wMaxZ; wz += 16) {
                float sy = (float) map.worldToScreenZ(wz, cy);
                dl.addLine(x0, sy, x1, sy, col, 1f);
            }
        }
        if (512.0 / bpp >= 8.0) {
            int col = withAlpha(argbToAbgr(ThemeManager.get().accent()), 0.45f);
            for (int wx = (int) (Math.floor(wMinX / 512) * 512); wx <= wMaxX; wx += 512) {
                float sx = (float) map.worldToScreenX(wx, cx);
                dl.addLine(sx, y0, sx, y1, col, 1.4f);
            }
            for (int wz = (int) (Math.floor(wMinZ / 512) * 512); wz <= wMaxZ; wz += 512) {
                float sy = (float) map.worldToScreenZ(wz, cy);
                dl.addLine(x0, sy, x1, sy, col, 1.4f);
            }
        }
    }

    private int structureIconGlId(String structureId) {
        String item = StructureFinder.iconItem(structureId);
        if (item == null) return 0;
        Integer cached = mapIconTex.get(item);
        if (cached != null) return cached;
        int glId = 0;
        try {
            AbstractTexture t = client.getTextureManager()
                    .getTexture(Identifier.ofVanilla("textures/item/" + item + ".png"));
            if (t.getGlTexture() instanceof GlTexture gl) glId = gl.getGlId();
        } catch (Exception ignored) {

        }
        mapIconTex.put(item, glId);
        return glId;
    }

    private void drawMapStructures(SeedMap map, ImDrawList dl, float x0, float y0, float x1, float y1,
                                   float cx, float cy, float mx, float my, boolean hovered) {
        if (!map.isShowStructures()) return;
        StructureFinder.Found hoverHit = null;
        for (StructureFinder.Found s : map.currentStructures()) {
            if (!map.isStructureShown(s.id())) continue;
            float sx = (float) map.worldToScreenX(s.blockX(), cx);
            float sy = (float) map.worldToScreenZ(s.blockZ(), cy);
            if (sx < x0 - 12 || sx > x1 + 12 || sy < y0 - 12 || sy > y1 + 12) continue;
            boolean done = map.isStructureComplete(s);
            int icon = structureIconGlId(s.id());
            if (icon != 0) {
                if (done) {

                    dl.addCircleFilled(sx, sy, 9f, withAlpha(0xFF000000, 0.30f));
                    dl.addCircle(sx, sy, 9f, withAlpha(0xFF888888, 0.45f), 16, 1.2f);
                    dl.addImage(icon, sx - 8, sy - 8, sx + 8, sy + 8, 0f, 0f, 1f, 1f, withAlpha(0xFF808080, 0.45f));
                } else {

                    dl.addCircleFilled(sx, sy, 9f, withAlpha(0xFF000000, 0.55f));
                    dl.addCircle(sx, sy, 9f, withAlpha(argbToAbgr(s.color()), 0.9f), 16, 1.4f);
                    dl.addImage(icon, sx - 8, sy - 8, sx + 8, sy + 8);
                }
            } else {
                int col = done ? withAlpha(0xFF808080, 0.45f) : argbToAbgr(s.color());
                dl.addRectFilled(sx - 4, sy - 4, sx + 4, sy + 4, col, 1.5f);
                dl.addRect(sx - 4, sy - 4, sx + 4, sy + 4, withAlpha(0xFF000000, done ? 0.5f : 1f), 1.5f, 0, 1f);
                dl.addText(sx + 6, sy - 6, withAlpha(0xFFFFFFFF, done ? 0.4f : 0.92f), s.label());
            }
            if (hovered && Math.abs(mx - sx) <= 9 && Math.abs(my - sy) <= 9) hoverHit = s;
        }
        if (hoverHit != null) {
            String t = hoverHit.id() + "  " + hoverHit.blockX() + ", " + hoverHit.blockZ()
                    + (map.isStructureComplete(hoverHit) ? "  (done — L: copy, R: undo)" : "  (L: copy, R: mark done)");
            ImVec2 ts = ImGui.calcTextSize(t);
            float bx = mx + 12, by = my + 6;
            dl.addRectFilled(bx - 4, by - 3, bx + ts.x + 4, by + ts.y + 3, withAlpha(0xFF000000, 0.78f), 3f);
            dl.addText(bx, by, 0xFFFFFFFF, t);
        }
    }

    private void renderMapSidePanel(SeedMap map, float w, float h) {
        ImGui.beginChild("##mapside", w, h, true);
        float inner = w - 16f;

        ImGui.text("Overlays");
        ImBoolean st = new ImBoolean(map.isShowStructures());
        if (ImGui.checkbox("Structures##mapst", st)) { map.setShowStructures(st.get()); uiSound("/sounds/click.wav", 0.4f); }
        ImBoolean gr = new ImBoolean(map.isShowGrid());
        if (ImGui.checkbox("Chunk / region grid##mapgr", gr)) { map.setShowGrid(gr.get()); uiSound("/sounds/click.wav", 0.4f); }
        ImBoolean sl = new ImBoolean(map.isShowSlime());
        if (ImGui.checkbox("Slime chunks##mapsl", sl)) { map.setShowSlime(sl.get()); uiSound("/sounds/click.wav", 0.4f); }
        if ((map.isShowGrid() || map.isShowSlime()) && map.getBlocksPerPixel() > SeedMap.GRID_MAX_BPP) {
            ImGui.textDisabled("zoom in for grid / slime");
        } else if (map.isShowStructures() && map.getBlocksPerPixel() > SeedMap.STRUCT_MAX_BPP) {
            ImGui.textDisabled("zoom in for structures");
        }

        if (map.isShowStructures()) {
            ImGui.dummy(0f, 2f);
            ImGui.text("Structure types");
            ImGui.sameLine();
            if (ImGui.smallButton("All##mapstclear")) { map.clearStructureFilter(); uiSound("/sounds/click.wav", 0.4f); }
            StructureFinder f = map.finderForCurrentSeed();
            ImGui.beginChild("##mapsttypes", inner, 92f, true);
            if (f == null) {
                ImGui.textDisabled(map.hasSeed() ? "Loading..." : "Enter a seed.");
            } else {
                ImDrawList dl = ImGui.getWindowDrawList();
                for (String id : f.allStructureIds()) {
                    ImVec2 p = ImGui.getCursorScreenPos();
                    ImGui.invisibleButton("##stsw" + id, 16, 16);
                    boolean cs = ImGui.isItemClicked();
                    int icon = structureIconGlId(id);
                    if (icon != 0) {
                        dl.addImage(icon, p.x, p.y, p.x + 16, p.y + 16);
                    } else {
                        dl.addRectFilled(p.x, p.y + 2, p.x + 14, p.y + 14, argbToAbgr(StructureFinder.color(id)), 2f);
                    }
                    ImGui.sameLine(0, 6);
                    boolean cn = ImGui.selectable(id + "##st" + id, map.getStructureFilter().contains(id));
                    if (cs || cn) { map.toggleStructure(id); uiSound("/sounds/click.wav", 0.35f); }
                }
            }
            ImGui.endChild();
        }

        ImGui.dummy(0f, 4f);
        ImGui.separator();

        ImGui.text("Biome filter");
        ImGui.sameLine();
        if (ImGui.smallButton("All##mapfclear")) { map.clearFilter(); uiSound("/sounds/click.wav", 0.4f); }
        ImGui.textDisabled(map.isFiltered() ? map.getFilter().size() + " selected" : "showing all");

        ImGui.setNextItemWidth(inner);
        ImGui.inputTextWithHint("##mapfsearch", "Search biomes...", mapFilterSearch);
        String q = mapFilterSearch.get().toLowerCase().trim();

        BiomeMapGenerator g = map.generatorForCurrentSeed();
        ImGui.beginChild("##mapflist", inner, h * 0.30f, true);
        if (g == null) {
            ImGui.textDisabled(map.hasSeed() ? "Loading biomes..." : "Enter a seed.");
        } else {
            ImDrawList dl = ImGui.getWindowDrawList();
            List<RegistryEntry<Biome>> biomes = new ArrayList<>(g.possibleBiomes());
            biomes.sort(java.util.Comparator.comparing(BiomePalette::name));
            for (RegistryEntry<Biome> b : biomes) {
                String path = BiomePalette.name(b);
                if (!q.isEmpty() && !path.contains(q)) continue;
                ImVec2 p = ImGui.getCursorScreenPos();
                ImGui.invisibleButton("##sw" + path, 13, 16);
                boolean clickedSwatch = ImGui.isItemClicked();
                dl.addRectFilled(p.x, p.y + 2, p.x + 13, p.y + 14,
                        argbToAbgr(BiomePalette.colorArgb(b)), 2f);
                ImGui.sameLine(0, 6);
                boolean clickedName = ImGui.selectable(path + "##mf" + path, map.getFilter().contains(path));
                if (clickedSwatch || clickedName) {
                    map.toggleFilter(path);
                    uiSound("/sounds/click.wav", 0.35f);
                }
            }
        }
        ImGui.endChild();

        ImGui.dummy(0f, 4f);
        ImGui.separator();
        ImGui.text("Waypoints");

        ImGui.setNextItemWidth(inner);
        ImGui.inputTextWithHint("##mapwpname", "Name (optional)", mapWpName);
        float field = (inner - 8f) / 2f;
        ImGui.setNextItemWidth(field);
        ImGui.inputInt("X##mapwpx", mapWpX, 0);
        ImGui.sameLine();
        ImGui.setNextItemWidth(field);
        ImGui.inputInt("Z##mapwpz", mapWpZ, 0);

        if (ImGui.button("Add##mapwpadd", (inner - 8f) / 2f, 0)) {
            map.addWaypoint(mapWpName.get(), mapWpX.get(), mapWpZ.get(), MAP_WP_COLOR);
            mapWpName.set("");
            uiSound("/sounds/confirm.wav", 0.4f);
        }
        ImGui.sameLine();
        if (ImGui.button("Add @ center##mapwpc", (inner - 8f) / 2f, 0)) {
            map.addWaypoint(mapWpName.get(),
                    (int) Math.round(map.getCenterX()), (int) Math.round(map.getCenterZ()), MAP_WP_COLOR);
            mapWpName.set("");
            uiSound("/sounds/confirm.wav", 0.4f);
        }

        ImGui.beginChild("##mapwplist", inner, ImGui.getContentRegionAvailY(), true);
        List<SeedMap.Waypoint> wps = map.getWaypoints();
        if (wps.isEmpty()) {
            ImGui.textDisabled("No waypoints yet.");
        } else {
            for (SeedMap.Waypoint wp : new ArrayList<>(wps)) {
                int id = System.identityHashCode(wp);
                if (ImGui.selectable(wp.name + "  (" + wp.x + ", " + wp.z + ")##wp" + id)) {
                    map.setCenter(wp.x, wp.z);
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Click to centre the map here");
                ImGui.sameLine();
                float avail = ImGui.getContentRegionAvailX();
                if (avail > 20) ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - 20);
                if (ImGui.smallButton("x##wpdel" + id)) map.removeWaypoint(wp);
            }
        }
        ImGui.endChild();

        ImGui.endChild();
    }

    private void renderSettings(Module module, float contentW) {
        for (Setting<?> setting : module.getSettings()) {
            String label = setting.getName() + "##" + module.hashCode();

            if (setting instanceof ButtonSetting btn) {
                if (ImGui.button(btn.getLabel() + "##" + module.hashCode(), contentW, 0)) {
                    btn.press();
                    uiSound("/sounds/click.wav", 0.4f);
                }
            } else if (setting instanceof BooleanSetting b) {
                ImBoolean buf = new ImBoolean(b.getValue());
                if (ImGui.checkbox(label, buf)) {
                    b.setValue(buf.get());
                    uiSound("/sounds/click.wav", 0.4f);
                }
            } else if (setting instanceof NumberSetting n) {
                ImGui.text(n.getName());
                ImGui.sameLine();
                if (editingNumber == n) {

                    ImString buf = numberEditBuffers.computeIfAbsent(n, k -> {
                        ImString s = new ImString(16);
                        s.set(formatNumber(n));
                        return s;
                    });
                    float fieldW = 60f;
                    float avail = ImGui.getContentRegionAvailX();
                    if (avail > fieldW) ImGui.setCursorPosX(ImGui.getCursorPosX() + (avail - fieldW));
                    boolean starting = numberEditFocus;
                    if (starting) {
                        ImGui.setKeyboardFocusHere();
                        numberEditFocus = false;
                    }
                    ImGui.setNextItemWidth(fieldW);
                    boolean commit = ImGui.inputText("##numedit" + label, buf,
                            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.CharsDecimal
                                    | ImGuiInputTextFlags.AutoSelectAll);
                    if (commit || (!starting && !ImGui.isItemActive())) {
                        applyNumberEdit(n, buf.get());
                        editingNumber = null;
                        numberEditBuffers.remove(n);
                    }
                } else {
                    String val = formatNumber(n);
                    float vw = ImGui.calcTextSize(val).x;
                    float avail = ImGui.getContentRegionAvailX();
                    if (avail > vw + PAD) ImGui.setCursorPosX(ImGui.getCursorPosX() + (avail - vw - PAD));
                    ImGui.textDisabled(val);
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Click to type a value");
                    if (ImGui.isItemClicked()) {
                        editingNumber = n;
                        numberEditFocus = true;
                        numberEditBuffers.remove(n);
                    }
                }
                float[] buf = {n.getValue()};
                if (customSlider("##sl" + label, buf, n.getMin(), n.getMax(), n.isInteger(), contentW)) {
                    n.setValue(buf[0]);

                    if (editingNumber == n) {
                        editingNumber = null;
                        numberEditBuffers.remove(n);
                    }
                }
            } else if (setting instanceof ModeSetting m) {
                ImGui.text(m.getName());
                ImInt index = new ImInt(m.getIndex());
                ImGui.setNextItemWidth(contentW);
                if (ImGui.combo("##" + label, index, m.getModes())) {
                    m.setIndex(index.get());
                }
            } else if (setting instanceof MultiSelectSetting ms) {
                ImGui.text(ms.getName());
                ImGui.setNextItemWidth(contentW);
                if (ImGui.beginCombo("##" + label, ms.getSummary())) {
                    for (String option : ms.getOptions()) {
                        if (ImGui.selectable(option + "##" + module.hashCode(),
                                ms.isSelected(option), ImGuiSelectableFlags.DontClosePopups)) {
                            ms.toggle(option);
                        }
                    }
                    ImGui.endCombo();
                }
            } else if (setting instanceof StringSetting s) {
                ImString buf = stringBuffers.computeIfAbsent(s, k -> {
                    ImString str = new ImString(Math.max(1, s.getMaxLength()));
                    str.set(s.getValue());
                    return str;
                });
                ImGui.text(s.getName());
                ImGui.setNextItemWidth(contentW);
                if (ImGui.inputText("##" + label, buf)) {
                    s.setValue(buf.get());
                }
            } else if (setting instanceof ColorSetting cs) {
                ImGui.text(cs.getName());
                ImGui.sameLine();
                float[] col = cs.toFloats();
                if (ImGui.colorEdit4("##" + label, col,
                        ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.NoInputs)) {
                    cs.fromFloats(col);
                }
            } else if (setting instanceof BlockListSetting bl) {
                renderBlockPicker(bl, module.hashCode(), contentW);
            } else if (setting instanceof EntityListSetting el) {
                renderEntityPicker(el, module.hashCode(), contentW);
            }
        }
    }

    private void renderBindRow(Module module, float contentW) {
        if (!module.getSettings().isEmpty()) {
            ImGui.separator();
        }
        String label = bindingModule == module ? "Press a key..." : "Bind: " + keyName(module.getKeyBind());
        if (ImGui.button(label + "##bind" + module.hashCode(), contentW - 24, 0)) {
            bindingModule = module;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Click, then press a key. Esc cancels, Backspace clears.");
        }
        ImGui.sameLine(0, 4);
        if (ImGui.button("X##clearBind" + module.hashCode(), 20, 0)) {
            module.setKeyBind(-1);
            if (bindingModule == module) bindingModule = null;
        }
    }

    private void renderConfigRow(Module module, float contentW) {
        int id = module.hashCode();
        float btn = 17f, gap = 4f;

        float avail = ImGui.getContentRegionAvailX();
        float offset = avail - (btn * 2f + gap);
        if (offset > 0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);

        if (iconButton("##cfgcopy" + id, btn, true)) {
            ImGui.setClipboardText(ConfigManager.copyModule(module));
            NotificationManager.getInstance().push("Config copied", module.getName());
            uiSound("/sounds/click.wav", 0.4f);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Copy this module's config to the clipboard.");

        ImGui.sameLine(0, gap);
        if (iconButton("##cfgpaste" + id, btn, false)) {
            if (ConfigManager.pasteModule(module, ImGui.getClipboardText())) {
                invalidateSettingBuffers();
                NotificationManager.getInstance().push("Config pasted", module.getName());
                uiSound("/sounds/confirm.wav", 0.4f);
            } else {
                NotificationManager.getInstance().push("Paste failed",
                        "Clipboard isn't a module config", 0xFFE0533D,
                        NotificationManager.DEFAULT_HOLD_MS, true);
                uiSound("/sounds/click.wav", 0.4f);
            }
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Apply a copied module config from the clipboard.");
    }

    private boolean iconButton(String id, float size, boolean copy) {
        ColorTheme theme = ThemeManager.get();
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 p = ImGui.getCursorScreenPos();

        ImGui.invisibleButton(id, size, size);
        boolean hovered = ImGui.isItemHovered();
        hoverSound("ib" + id, hovered);
        boolean clicked = ImGui.isItemClicked(0);

        float hv = Animations.to("ib" + id, hovered ? 1f : 0f, 18f);
        int bg = lerpColor(argbToAbgr(theme.buttonOff()), argbToAbgr(theme.accent()), hv);
        dl.addRectFilled(p.x, p.y, p.x + size, p.y + size, bg, 3f);
        int icon = lerpColor(argbToAbgr(theme.text()), 0xFFFFFFFF, hv);
        if (copy) drawCopyIcon(dl, p.x, p.y, size, icon, bg);
        else      drawPasteIcon(dl, p.x, p.y, size, icon);
        return clicked;
    }

    private void drawCopyIcon(ImDrawList dl, float x, float y, float s, int col, int fillCol) {
        float pad = 4.3f, d = 3f;
        float x0 = x + pad, y0 = y + pad, x1 = x + s - pad, y1 = y + s - pad;
        dl.addRect(x0 + d, y0, x1, y1 - d, col, 2f, 0, 1.3f);
        dl.addRectFilled(x0, y0 + d, x1 - d, y1, fillCol, 2f);
        dl.addRect(x0, y0 + d, x1 - d, y1, col, 2f, 0, 1.3f);
    }

    private void drawPasteIcon(ImDrawList dl, float x, float y, float s, int col) {
        float pad = 4.3f;
        float x0 = x + pad, y0 = y + pad + 1.5f, x1 = x + s - pad, y1 = y + s - pad;
        dl.addRect(x0, y0, x1, y1, col, 2f, 0, 1.3f);
        float cx = (x0 + x1) / 2f;
        dl.addRectFilled(cx - 2.4f, y0 - 2.6f, cx + 2.4f, y0 + 1.4f, col, 1f);
    }

    private void invalidateSettingBuffers() {
        stringBuffers.clear();
        numberEditBuffers.clear();
        editingNumber = null;
    }

    private static String formatNumber(NumberSetting n) {
        return n.isInteger() ? String.valueOf(n.getInt()) : String.format("%.2f", n.getValue());
    }

    private void applyNumberEdit(NumberSetting n, String text) {
        try {
            n.setValue(Float.parseFloat(text.trim()));
        } catch (NumberFormatException ignored) {

        }
    }

    private boolean customSlider(String id, float[] val, float min, float max, boolean integer, float width) {
        ColorTheme theme = ThemeManager.get();
        float h = ImGui.getFrameHeight();
        ImVec2 p = ImGui.getCursorScreenPos();
        ImGui.invisibleButton(id, width, h);
        boolean active = ImGui.isItemActive();
        boolean hovered = ImGui.isItemHovered();

        boolean changed = false;
        if (active && max > min) {
            float t = clamp01((ImGui.getMousePosX() - p.x) / width);
            float nv = min + t * (max - min);
            if (integer) nv = Math.round(nv);
            if (nv != val[0]) {
                val[0] = nv;
                changed = true;
            }
        }

        float t = (max > min) ? clamp01((val[0] - min) / (max - min)) : 0f;
        float ft = Animations.to("sl" + id, t, 18f);

        ImDrawList dl = ImGui.getWindowDrawList();
        float cy = p.y + h * 0.5f;
        float th = 4f;
        dl.addRectFilled(p.x, cy - th / 2f, p.x + width, cy + th / 2f, lighten(argbToAbgr(theme.frame()), 10), th / 2f);
        dl.addRectFilled(p.x, cy - th / 2f, p.x + width * ft, cy + th / 2f, argbToAbgr(theme.accent()), th / 2f);
        dl.addCircleFilled(p.x + width * ft, cy, (hovered || active) ? 7f : 6f, 0xFFFFFFFF);
        return changed;
    }

    private void renderBlockPicker(BlockListSetting setting, int ownerId, float contentW) {
        ImString search = searchBuffers.computeIfAbsent(setting, k -> new ImString(64));
        ImGui.setNextItemWidth(contentW);
        ImGui.inputTextWithHint("##search" + ownerId, "Search blocks...", search);
        String query = search.get().toLowerCase().trim();

        ImGui.textDisabled("Available");
        ImGui.beginChild("##available" + ownerId, contentW, 120, true);
        int shown = 0;
        for (Block block : Registries.BLOCK) {
            String path = Registries.BLOCK.getId(block).getPath();
            if (setting.isSelected(path)) continue;
            if (!query.isEmpty() && !path.contains(query)) continue;
            if (shown++ >= 120) {
                ImGui.textDisabled("... type to narrow down");
                break;
            }
            if (ImGui.selectable(path + "##add" + ownerId)) setting.add(path);
        }
        ImGui.endChild();

        ImGui.textDisabled("Selected");
        ImGui.beginChild("##selected" + ownerId, contentW, 100, true);
        for (String path : new ArrayList<>(setting.getValue())) {
            if (ImGui.selectable(path + "##remove" + ownerId)) setting.remove(path);
        }
        ImGui.endChild();
    }

    private void renderEntityPicker(EntityListSetting setting, int ownerId, float contentW) {
        ImString search = entitySearchBuffers.computeIfAbsent(setting, k -> new ImString(64));
        ImGui.setNextItemWidth(contentW);
        ImGui.inputTextWithHint("##esearch" + ownerId, "Search mobs...", search);
        String query = search.get().toLowerCase().trim();

        ImGui.textDisabled("Available");
        ImGui.beginChild("##eavailable" + ownerId, contentW, 120, true);
        int shown = 0;
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            if (type.getSpawnGroup() == SpawnGroup.MISC) continue;
            String path = Registries.ENTITY_TYPE.getId(type).getPath();
            if (setting.isSelected(path)) continue;
            if (!query.isEmpty() && !path.contains(query)) continue;
            if (shown++ >= 120) {
                ImGui.textDisabled("... type to narrow down");
                break;
            }
            if (ImGui.selectable(path + "##eadd" + ownerId)) setting.add(path);
        }
        ImGui.endChild();

        ImGui.textDisabled("Selected");
        ImGui.beginChild("##eselected" + ownerId, contentW, 100, true);
        for (String path : new ArrayList<>(setting.getValue())) {
            if (ImGui.selectable(path + "##eremove" + ownerId)) setting.remove(path);
        }
        ImGui.endChild();
    }

    private void handleBindCapture() {
        if (bindingModule == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++) {
            if (!InputUtil.isKeyPressed(mc.getWindow(), code)) continue;
            if (code == GLFW.GLFW_KEY_ESCAPE) {

            } else if (code == GLFW.GLFW_KEY_BACKSPACE || code == GLFW.GLFW_KEY_DELETE) {
                bindingModule.setKeyBind(-1);
            } else {
                bindingModule.setKeyBind(code);
            }
            bindingModule = null;
            return;
        }
    }

    private void handleGuiBindCapture() {
        if (!bindingGuiKey) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++) {
            if (!InputUtil.isKeyPressed(mc.getWindow(), code)) continue;
            if (code == GLFW.GLFW_KEY_ESCAPE) {

            } else {
                int newKey = (code == GLFW.GLFW_KEY_BACKSPACE || code == GLFW.GLFW_KEY_DELETE)
                        ? GLFW.GLFW_KEY_RIGHT_SHIFT
                        : code;
                ClientSettings.setOpenGuiKey(newKey);
                ClientTickEvent.primeKey(newKey);
                ConfigManager.save();
            }
            bindingGuiKey = false;
            return;
        }
    }

    private String keyName(int code) {
        if (code <= 0) return "None";
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
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
        ImGui.pushStyleColor(ImGuiCol.TitleBg,          argbToAbgr(theme.title()));
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive,    argbToAbgr(theme.title()));
        ImGui.pushStyleColor(ImGuiCol.TitleBgCollapsed, argbToAbgr(theme.title()));
        ImGui.pushStyleColor(ImGuiCol.FrameBg,          frame);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered,   lighten(frame, 12));
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive,    lighten(frame, 20));
        ImGui.pushStyleColor(ImGuiCol.SliderGrab,       accent);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, lighten(accent, 25));
        ImGui.pushStyleColor(ImGuiCol.CheckMark,        accent);
        ImGui.pushStyleColor(ImGuiCol.Header,           accent);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered,    lighten(accent, 18));
        ImGui.pushStyleColor(ImGuiCol.HeaderActive,     lighten(accent, 8));
        ImGui.pushStyleColor(ImGuiCol.PopupBg,          argbToAbgr(theme.popup()));
        ImGui.pushStyleColor(ImGuiCol.ScrollbarBg,      0x00000000);
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab,    buttonOff);
        ImGui.pushStyleColor(ImGuiCol.Separator,        withAlpha(text, 0.10f));
        ImGui.pushStyleColor(ImGuiCol.Button,           buttonOff);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,    lighten(buttonOff, 14));
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,     accent);
    }

    private void cleanupStyle() {
        ImGui.popStyleColor(23);
        ImGui.popStyleVar(11);
    }

    private void uiSound(String path, float volume) {
        if (ClientSettings.uiSoundsEnabled()) {
            SoundManager.play(path, volume);
        }
    }

    private void hoverSound(String id, boolean hovered) {
        boolean prev = prevHover.getOrDefault(id, false);
        if (hovered && !prev) uiSound("/sounds/hover.wav", 0.35f);
        prevHover.put(id, hovered);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float[] toArr(List<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    private void centeredText(String s, boolean disabled) {
        float avail = ImGui.getContentRegionAvailX();
        float textW = ImGui.calcTextSize(s).x;
        float offset = (avail - textW) * 0.5f;
        if (offset > 0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        if (disabled) ImGui.textDisabled(s);
        else ImGui.text(s);
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

    private int lerpColor(int from, int to, float t) {
        t = clamp01(t);
        int fa = (from >> 24) & 0xFF, fb = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fr = from & 0xFF;
        int ta = (to   >> 24) & 0xFF, tb = (to   >> 16) & 0xFF, tg = (to   >> 8) & 0xFF, tr = to   & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int b = (int) (fb + (tb - fb) * t);
        int g = (int) (fg + (tg - fg) * t);
        int r = (int) (fr + (tr - fr) * t);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private int withAlpha(int abgr, float factor) {
        int a = (int) (((abgr >> 24) & 0xFF) * clamp01(factor));
        return (a << 24) | (abgr & 0x00FFFFFF);
    }

    private int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >>  8) & 0xFF;
        int b =  argb        & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private boolean pushFont(ImFont font) {
        if (font != null) {
            ImGui.pushFont(font);
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        ConfigManager.save();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        client.setScreen(getParent());
    }
}
