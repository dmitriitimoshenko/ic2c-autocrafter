package dev.dtim.ic2cautocrafter;

import ic2.api.addons.IC2Plugin;
import ic2.api.addons.IModule;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Registers this mod with IC2 Classic's addon system so it shows up in IC2's plugin
 * list and can be toggled from IC2's config. Content registration itself happens on the
 * mod event bus in {@link AutoCrafterMod}.
 */
@IC2Plugin(name = "IC2C Auto Crafter", id = AutoCrafterMod.MOD_ID, version = "1.0.0", requiredAPIVersion = 0)
public class AutoCrafterPlugin implements IModule {

    @Override
    public boolean canLoad(Dist dist) {
        return true;
    }

    /** IC2's plugin loader calls this before content is built, exactly where a config belongs. */
    @Override
    public void loadConfigs() {
        AutoCrafterConfig.load();
    }

    @Override
    public void preInit(IEventBus bus) {
        AutoCrafterMod.LOGGER.info("Auto Crafter addon hooked into IC2 Classic");
    }
}
