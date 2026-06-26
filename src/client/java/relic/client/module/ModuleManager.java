package relic.client.module;

import relic.client.module.impl.combat.AimAssistModule;
import relic.client.module.impl.combat.AutoLogModule;
import relic.client.module.impl.combat.TriggerBotModule;
import relic.client.module.impl.basehunting.AmethystESPModule;
import relic.client.module.impl.basehunting.BlockAlertModule;
import relic.client.module.impl.basehunting.ChickenPatternModule;
import relic.client.module.impl.basehunting.ChunkFinderModule;
import relic.client.module.impl.basehunting.EndermanBlockESPModule;
import relic.client.module.impl.basehunting.OreSimModule;
import relic.client.module.impl.basehunting.SheepPatternModule;
import relic.client.module.impl.exploit.BlinkModule;
import relic.client.module.impl.exploit.PacketCancellerModule;
import relic.client.module.impl.exploit.XCarryModule;
import relic.client.module.impl.misc.AutoFireworkModule;
import relic.client.module.impl.misc.AutoReconnectModule;
import relic.client.module.impl.misc.CoordCopyModule;
import relic.client.module.impl.misc.ProximityAlertModule;
import relic.client.module.impl.misc.DiscordRPCModule;
import relic.client.module.impl.misc.InventoryMoveModule;
import relic.client.module.impl.misc.KillMessageModule;
import relic.client.module.impl.misc.RainNotifierModule;
import relic.client.module.impl.misc.SetHomeModule;
import relic.client.module.impl.privacy.BlockObfuscatorModule;
import relic.client.module.impl.privacy.CoordObfuscatorModule;
import relic.client.module.impl.privacy.HotbarObfuscatorModule;
import relic.client.module.impl.privacy.PrivacyModule;
import relic.client.module.impl.troll.FakeScoreboardModule;
import relic.client.module.impl.visual.BlockESPModule;
import relic.client.module.impl.visual.DamageTagModule;
import relic.client.module.impl.visual.ESPModule;
import relic.client.module.impl.visual.FreecamModule;
import relic.client.module.impl.visual.FullbrightModule;
import relic.client.module.impl.visual.GhostModeModule;
import relic.client.module.impl.visual.HoleESPModule;
import relic.client.module.impl.visual.HudModule;
import relic.client.module.impl.visual.InventoryHUDModule;
import relic.client.module.impl.visual.MediaControllerModule;
import relic.client.module.impl.visual.NametagsModule;
import relic.client.module.impl.visual.PaperDollModule;
import relic.client.module.impl.visual.RegionMapModule;
import relic.client.module.impl.visual.StorageESPModule;
import relic.client.module.impl.visual.TracersModule;
import relic.client.module.impl.visual.TrajectoriesModule;
import relic.client.module.impl.visual.XrayModule;
import relic.client.module.impl.visual.ZoomModule;

import java.util.*;

public class ModuleManager {
    private static ModuleManager instance;
    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        registerDefaultModules();
    }

    public static ModuleManager getInstance() {
        if (instance == null) {
            instance = new ModuleManager();
        }
        return instance;
    }

    private void registerDefaultModules() {

        registerModule(new ESPModule());
        registerModule(new BlockESPModule());
        registerModule(new StorageESPModule());
        registerModule(new HoleESPModule());
        registerModule(new FreecamModule());
        registerModule(new GhostModeModule());
        registerModule(new TracersModule());
        registerModule(new TrajectoriesModule());
        registerModule(new NametagsModule());
        registerModule(new DamageTagModule());
        registerModule(new FullbrightModule());
        registerModule(new XrayModule());
        registerModule(new HudModule());
        registerModule(new InventoryHUDModule());
        registerModule(new PaperDollModule());
        registerModule(new MediaControllerModule());
        registerModule(new RegionMapModule());
        registerModule(new ZoomModule());

        registerModule(new AimAssistModule());
        registerModule(new TriggerBotModule());
        registerModule(new AutoLogModule());

        registerModule(new BlockAlertModule());
        registerModule(new ChunkFinderModule());
        registerModule(new AmethystESPModule());
        registerModule(new SheepPatternModule());
        registerModule(new ChickenPatternModule());
        registerModule(new EndermanBlockESPModule());
        registerModule(new OreSimModule());

        registerModule(new PrivacyModule());
        registerModule(new HotbarObfuscatorModule());
        registerModule(new BlockObfuscatorModule());
        registerModule(new CoordObfuscatorModule());

        registerModule(new KillMessageModule());
        registerModule(new CoordCopyModule());
        registerModule(new SetHomeModule());
        registerModule(new DiscordRPCModule());
        registerModule(new AutoFireworkModule());
        registerModule(new RainNotifierModule());
        registerModule(new InventoryMoveModule());
        registerModule(new AutoReconnectModule());
        registerModule(new ProximityAlertModule());

        registerModule(new PacketCancellerModule());
        registerModule(new BlinkModule());
        registerModule(new XCarryModule());

        registerModule(new FakeScoreboardModule());
    }

    public void registerModule(Module module) {
        modules.add(module);
    }

    public List<Module> getAllModules() {
        return new ArrayList<>(modules);
    }

    public List<Module> getModulesByCategory(Module.Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public Module getModuleByName(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }
}
