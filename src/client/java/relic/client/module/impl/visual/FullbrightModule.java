package relic.client.module.impl.visual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import relic.client.client.mixin.SimpleOptionAccessor;
import relic.client.module.Module;

public class FullbrightModule extends Module {

    private static final double FULLBRIGHT_GAMMA = 16.0;

    private Double savedGamma;

    public FullbrightModule() {
        super("Fullbright", "Lights the world up as if it were fully lit", Category.VISUAL);
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        GameOptions options = MinecraftClient.getInstance().options;
        if (options == null || savedGamma == null) return;
        setGamma(options.getGamma(), savedGamma);
        savedGamma = null;
    }

    @Override
    public void onTick() {
        apply();
    }

    private void apply() {
        GameOptions options = MinecraftClient.getInstance().options;
        if (options == null) return;
        SimpleOption<Double> gamma = options.getGamma();

        if (savedGamma == null) savedGamma = gamma.getValue();
        if (gamma.getValue() < FULLBRIGHT_GAMMA) {
            setGamma(gamma, FULLBRIGHT_GAMMA);
        }
    }

    private void setGamma(SimpleOption<Double> gamma, double value) {
        ((SimpleOptionAccessor) (Object) gamma).relic$setValue(value);
    }
}
