package dev.dtim.ic2cautocrafter;

import dev.dtim.ic2cautocrafter.content.AutoCrafterBlock;
import dev.dtim.ic2cautocrafter.content.AutoCrafterTileEntity;
import ic2.core.item.base.IC2BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * IC2 Classic addon: adds a low voltage Auto Crafter that executes crafting recipes
 * stored on IC2's Memory Stick.
 */
@Mod(AutoCrafterMod.MOD_ID)
public class AutoCrafterMod {
    public static final String MOD_ID = "ic2c_autocrafter";
    public static final Logger LOGGER = LogManager.getLogger("IC2C-AutoCrafter");

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<Block> AUTO_CRAFTER = BLOCKS.register("auto_crafter", AutoCrafterBlock::new);
    public static final RegistryObject<Item> AUTO_CRAFTER_ITEM = ITEMS.register("auto_crafter", () -> new IC2BlockItem(AUTO_CRAFTER.get()));
    public static final RegistryObject<BlockEntityType<AutoCrafterTileEntity>> AUTO_CRAFTER_TILE = TILES.register("auto_crafter",
            () -> BlockEntityType.Builder.of(AutoCrafterTileEntity::new, AUTO_CRAFTER.get()).build(null));

    public AutoCrafterMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TILES.register(bus);
    }
}
