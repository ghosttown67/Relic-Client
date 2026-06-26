package relic.client.module.impl.privacy;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class HotbarObfuscatorModule extends Module {

    private static HotbarObfuscatorModule instance;

    private final BooleanSetting hotbar    = new BooleanSetting("Hotbar", true);
    private final BooleanSetting inventory = new BooleanSetting("Inventory", false);

    private static final long SALT = new Random().nextLong();

    private static final Map<Item, ItemStack> disguises = new ConcurrentHashMap<>();

    private static volatile List<Item> itemPool;

    public HotbarObfuscatorModule() {
        super("Hotbar Obfuscator", "Disguises your items in the hotbar and inventory", Category.PRIVACY);
        addSettings(hotbar, inventory);
        instance = this;
    }

    @Override
    protected void onDisable() {
        disguises.clear();
    }

    private static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static ItemStack obfuscateHotbar(ItemStack stack) {
        if (!isActive() || !instance.hotbar.isOn()) return stack;
        return disguise(stack);
    }

    public static ItemStack obfuscateSlot(Slot slot, ItemStack stack) {
        if (!isActive() || !instance.inventory.isOn()) return stack;
        Inventory inv = slot.inventory;
        if (!(inv instanceof PlayerInventory)) return stack;
        return disguise(stack);
    }

    private static ItemStack disguise(ItemStack real) {
        if (real == null || real.isEmpty()) return real;
        return disguises.computeIfAbsent(real.getItem(), HotbarObfuscatorModule::buildDisguise);
    }

    private static ItemStack buildDisguise(Item real) {
        List<Item> pool = pool();
        if (pool.isEmpty()) return new ItemStack(Items.STONE);

        Random rng = new Random(SALT * 31L + Registries.ITEM.getRawId(real));
        Item fake = pool.get(rng.nextInt(pool.size()));

        ItemStack stack = new ItemStack(fake);
        int max = Math.max(1, stack.getMaxCount());
        stack.setCount(1 + rng.nextInt(max));
        return stack;
    }

    private static List<Item> pool() {
        List<Item> p = itemPool;
        if (p == null) {
            p = Registries.ITEM.stream().filter(i -> i != Items.AIR).toList();
            itemPool = p;
        }
        return p;
    }
}
