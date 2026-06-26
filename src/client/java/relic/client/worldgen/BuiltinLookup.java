package relic.client.worldgen;

import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryWrapper;

public final class BuiltinLookup {

    private static RegistryWrapper.WrapperLookup cache;

    private BuiltinLookup() {}

    public static synchronized RegistryWrapper.WrapperLookup get() {
        if (cache == null) cache = BuiltinRegistries.createWrapperLookup();
        return cache;
    }
}
