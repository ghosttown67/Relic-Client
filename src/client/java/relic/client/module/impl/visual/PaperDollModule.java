package relic.client.module.impl.visual;

import imgui.ImDrawList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import relic.client.gui.hud.HudElement;
import relic.client.gui.hud.HudProvider;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

import java.util.List;

public class PaperDollModule extends Module implements HudProvider {

    private static PaperDollModule instance;

    private final BooleanSetting background = new BooleanSetting("Background", true);
    private final NumberSetting  scale      = new NumberSetting("Scale", 1f, 0.5f, 2.5f, false);

    private final BooleanSetting showHearts = new BooleanSetting("Show Hearts", true);
    private final BooleanSetting showArmor  = new BooleanSetting("Show Armor", true);
    private final BooleanSetting showHunger = new BooleanSetting("Show Hunger", true);
    private final BooleanSetting showXp     = new BooleanSetting("Show XP", true);

    private final BooleanSetting hideHearts = new BooleanSetting("Hide Vanilla Hearts", false);
    private final BooleanSetting hideHunger = new BooleanSetting("Hide Vanilla Hunger", false);
    private final BooleanSetting hideXp     = new BooleanSetting("Hide Vanilla XP", false);

    private final NumberSetting posX = new NumberSetting("X %", 1, 0, 100, false);
    private final NumberSetting posY = new NumberSetting("Y %", 40, 0, 100, false);

    private static final int DOLL_W = 50;
    private static final int DOLL_H = 75;
    private static final int ENTITY_SIZE = 30;
    private static final int ICON = 9;
    private static final int ROW_GAP = 2;
    private static final int DOLL_GAP = 2;
    private static final int PAD = 3;

    private static final Identifier HEART_NORMAL   = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier HEART_POISONED = Identifier.ofVanilla("hud/heart/poisoned_full");
    private static final Identifier HEART_WITHERED = Identifier.ofVanilla("hud/heart/withered_full");
    private static final Identifier HEART_FROZEN   = Identifier.ofVanilla("hud/heart/frozen_full");
    private static final Identifier FOOD_NORMAL    = Identifier.ofVanilla("hud/food_full");
    private static final Identifier FOOD_HUNGER    = Identifier.ofVanilla("hud/food_full_hunger");
    private static final Identifier ARMOR_FULL     = Identifier.ofVanilla("hud/armor_full");
    private static final Identifier XP_ORB         = Identifier.ofVanilla("textures/entity/experience_orb.png");

    public PaperDollModule() {
        super("PaperDoll", "Shows your player model with armour, plus vanilla stat icons", Category.VISUAL);
        addSettings(background, scale, showHearts, showArmor, showHunger, showXp,
                hideHearts, hideHunger, hideXp, posX, posY);
        instance = this;
    }

    private static boolean active() { return instance != null && instance.isEnabled(); }

    public static boolean hideVanillaHearts() { return active() && instance.hideHearts.isOn(); }
    public static boolean hideVanillaHunger() { return active() && instance.hideHunger.isOn(); }
    public static boolean hideVanillaXp()     { return active() && instance.hideXp.isOn(); }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        float s = scale.getValue();
        int contentW = Math.round(DOLL_W * s);
        int contentH = totalHeight(s);

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int x = Math.round((sw - contentW) * posX.getValue() / 100f);
        int y = Math.round((sh - contentH) * posY.getValue() / 100f);

        draw(context, mc, x, y, s);
    }

    private void draw(DrawContext context, MinecraftClient mc, int x, int y, float s) {
        ClientPlayerEntity player = mc.player;
        int dollW = Math.round(DOLL_W * s);
        int dollH = Math.round(DOLL_H * s);
        int contentH = totalHeight(s);

        if (background.isOn()) {
            context.fill(x - PAD, y - PAD, x + dollW + PAD, y + contentH + PAD, 0xA0101019);
        }

        float cx = x + dollW / 2f;
        float cy = y + dollH / 2f;
        InventoryScreen.drawEntity(context, x, y, x + dollW, y + dollH,
                Math.round(ENTITY_SIZE * s), 0.0625F, cx, cy, player);

        int rows = enabledStats();
        if (rows == 0) return;

        Matrix3x2fStack m = context.getMatrices();
        m.pushMatrix();
        m.translate(x, y + dollH + Math.round(DOLL_GAP * s));
        m.scale(s, s);

        TextRenderer tr = mc.textRenderer;
        int ly = 0;
        if (showHearts.isOn()) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, heartSprite(player), 0, ly, ICON, ICON);
            statText(context, tr, Integer.toString(MathHelper.ceil(player.getHealth())), ly);
            ly += ICON + ROW_GAP;
        }
        if (showArmor.isOn()) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ARMOR_FULL, 0, ly, ICON, ICON);
            statText(context, tr, Integer.toString(player.getArmor()), ly);
            ly += ICON + ROW_GAP;
        }
        if (showHunger.isOn()) {
            Identifier food = player.hasStatusEffect(StatusEffects.HUNGER) ? FOOD_HUNGER : FOOD_NORMAL;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, food, 0, ly, ICON, ICON);
            statText(context, tr, Integer.toString(player.getHungerManager().getFoodLevel()), ly);
            ly += ICON + ROW_GAP;
        }
        if (showXp.isOn()) {

            context.drawTexture(RenderPipelines.GUI_TEXTURED, XP_ORB, 0, ly, 0f, 0f, ICON, ICON, 16, 16, 64, 64, 0xFF80FF20);
            statText(context, tr, Integer.toString(player.experienceLevel), ly);
        }

        m.popMatrix();
    }

    private void statText(DrawContext context, TextRenderer tr, String value, int rowY) {
        int ty = rowY + (ICON - tr.fontHeight) / 2 + 1;
        context.drawText(tr, value, ICON + 3, ty, 0xFFFFFFFF, true);
    }

    private Identifier heartSprite(ClientPlayerEntity p) {
        if (p.hasStatusEffect(StatusEffects.POISON)) return HEART_POISONED;
        if (p.hasStatusEffect(StatusEffects.WITHER)) return HEART_WITHERED;
        if (p.isFrozen()) return HEART_FROZEN;
        return HEART_NORMAL;
    }

    private int enabledStats() {
        return (showHearts.isOn() ? 1 : 0) + (showArmor.isOn() ? 1 : 0)
                + (showHunger.isOn() ? 1 : 0) + (showXp.isOn() ? 1 : 0);
    }

    private int totalHeight(float s) {
        int h = Math.round(DOLL_H * s);
        int rows = enabledStats();
        if (rows > 0) h += Math.round((DOLL_GAP + rows * (ICON + ROW_GAP)) * s);
        return h;
    }

    private HudElement element;

    @Override
    public List<HudElement> hudElements() {
        if (element == null) {
            element = new HudElement() {
                @Override public String name() { return "Paper Doll"; }
                @Override public float getXPercent() { return posX.getValue(); }
                @Override public float getYPercent() { return posY.getValue(); }
                @Override public void setPercent(float x, float y) { posX.setValue(x); posY.setValue(y); }
                @Override public float width(float dispW, float dispH, float guiScale) {
                    return DOLL_W * scale.getValue() * guiScale;
                }
                @Override public float height(float dispW, float dispH, float guiScale) {
                    return totalHeight(scale.getValue()) * guiScale;
                }
                @Override public void renderPreview(ImDrawList dl, float x, float y, float dispW, float dispH, float guiScale) {

                    float w = DOLL_W * scale.getValue() * guiScale;
                    float h = totalHeight(scale.getValue()) * guiScale;
                    dl.addRectFilled(x, y, x + w, y + h, 0xA0191010);
                }
            };
        }
        return List.of(element);
    }
}
