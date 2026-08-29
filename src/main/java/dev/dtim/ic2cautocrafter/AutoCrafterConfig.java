package dev.dtim.ic2cautocrafter;

import carbonconfiglib.CarbonConfig;
import carbonconfiglib.config.Config;
import carbonconfiglib.config.ConfigEntry;
import carbonconfiglib.config.ConfigHandler;
import carbonconfiglib.config.ConfigSection;
import carbonconfiglib.config.ConfigSettings;

/**
 * Balance knobs, stored next to IC2's own files through CarbonConfig - the config library
 * IC2 Classic already ships with, so the values show up in the same in-game config screen.
 */
public final class AutoCrafterConfig {
    public static final int DEFAULT_ENERGY_PER_TICK = 8;
    public static final int DEFAULT_TICKS_PER_CRAFT = 100;
    public static final int DEFAULT_ENERGY_BUFFER = 4000;

    private static ConfigHandler handler;
    private static ConfigEntry.IntValue energyPerTick;
    private static ConfigEntry.IntValue ticksPerCraft;
    private static ConfigEntry.IntValue energyBuffer;
    private static ConfigEntry.BoolValue machineSound;

    private AutoCrafterConfig() {
    }

    public static void load() {
        if (handler != null) {
            return;
        }
        Config config = new Config(AutoCrafterMod.MOD_ID);
        ConfigSection machine = config.add("auto_crafter");
        energyPerTick = machine.addInt("energyPerTick", DEFAULT_ENERGY_PER_TICK,
                "EU drawn per tick while a craft is running. Upgrades scale this.").setRange(1, 8192);
        ticksPerCraft = machine.addInt("ticksPerCraft", DEFAULT_TICKS_PER_CRAFT,
                "Ticks one craft takes before upgrades. 20 ticks = 1 second.").setRange(1, 12000);
        energyBuffer = machine.addInt("energyBuffer", DEFAULT_ENERGY_BUFFER,
                "Internal EU buffer of the machine.").setRange(100, 10000000);
        machineSound = machine.addBool("machineSound", true,
                "Play the working loop. Turn off if you would rather use IC2's Muffler upgrades.");
        handler = CarbonConfig.CONFIGS.createConfig(config, ConfigSettings.withFolder("ic2c"));
        handler.register();
        AutoCrafterMod.LOGGER.info("Auto Crafter config loaded");
    }

    public static int energyPerTick() {
        return energyPerTick == null ? DEFAULT_ENERGY_PER_TICK : energyPerTick.getValue();
    }

    public static int ticksPerCraft() {
        return ticksPerCraft == null ? DEFAULT_TICKS_PER_CRAFT : ticksPerCraft.getValue();
    }

    public static int energyBuffer() {
        return energyBuffer == null ? DEFAULT_ENERGY_BUFFER : energyBuffer.getValue();
    }

    public static boolean machineSound() {
        return machineSound == null || machineSound.getValue();
    }
}
