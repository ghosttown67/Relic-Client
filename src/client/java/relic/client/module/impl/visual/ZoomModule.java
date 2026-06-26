package relic.client.module.impl.visual;

import net.minecraft.util.math.MathHelper;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

public class ZoomModule extends Module {

    private static ZoomModule instance;

    private static final float SCROLL_STEP = 0.5f;
    private static final float MIN_DIVISOR = 1.0f;
    private static final float MAX_DIVISOR = 50.0f;

    private final NumberSetting defaultZoom =
            new NumberSetting("Default Zoom", 4.0f, MIN_DIVISOR, MAX_DIVISOR);

    private final BooleanSetting holdToZoom = new BooleanSetting("Hold To Zoom", true);

    private float divisor = 4.0f;

    public ZoomModule() {
        super("Zoom", "Magnifies the view; scroll the mouse wheel to adjust", Category.VISUAL);
        addSettings(defaultZoom, holdToZoom);
        instance = this;
    }

    public static ZoomModule getInstance() {
        return instance;
    }

    @Override
    public boolean isHoldToActivate() {
        return holdToZoom.isOn();
    }

    @Override
    protected void onEnable() {
        divisor = defaultZoom.getValue();
    }

    public boolean isZooming() {
        return isEnabled();
    }

    public float getDivisor() {
        return divisor;
    }

    public void onScroll(double amount) {
        divisor = MathHelper.clamp(divisor + (float) amount * SCROLL_STEP, MIN_DIVISOR, MAX_DIVISOR);
    }
}
