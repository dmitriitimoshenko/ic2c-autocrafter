package dev.dtim.ic2cautocrafter.content;

import dev.dtim.ic2cautocrafter.AutoCrafterMod;
import ic2.core.inventory.container.ContainerComponent;
import ic2.core.inventory.filter.SimpleFilter;
import ic2.core.inventory.gui.IC2Screen;
import ic2.core.inventory.gui.components.simple.ChargeBarComponent;
import ic2.core.inventory.gui.components.simple.ProgressComponent;
import ic2.core.inventory.slot.FilterSlot;
import ic2.core.inventory.slot.MemorySlot;
import ic2.core.inventory.slot.SlotBase;
import ic2.core.inventory.slot.UpgradeSlot;
import ic2.core.platform.registries.IC2Items;
import ic2.core.utils.math.geometry.Box2i;
import ic2.core.utils.math.geometry.Vec2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class AutoCrafterContainer extends ContainerComponent<AutoCrafterTileEntity> {
    public static final ResourceLocation TEXTURE = new ResourceLocation(AutoCrafterMod.MOD_ID, "textures/gui/auto_crafter.png");
    public static final int GUI_HEIGHT = 196;

    /** Recipe ghosts, upgrades, outputs, stick, battery and the buffer row. */
    public static final Box2i RECIPES = new Box2i(7, 26, 54, 54);
    public static final Box2i PROGRESS = new Box2i(92, 45, 24, 16);
    public static final Box2i CHARGE = new Box2i(69, 46, 14, 14);
    /** Memory stick above the charge bar, battery below it. */
    private static final int SLOT_MIDDLE_X = 68;
    private static final int SLOT_OUTPUT_X = 124;
    /** Upgrades keep the column every IC2 machine puts them in. */
    private static final int SLOT_UPGRADE_X = 152;
    private static final int BUFFER_Y = 86;
    private static final int ROW_TOP = 27;

    private static final int MACHINE_SLOTS = 1 + AutoCrafterTileEntity.INPUT_SLOTS + AutoCrafterTileEntity.OUTPUT_SLOTS
            + 1 + AutoCrafterTileEntity.UPGRADE_SLOTS + AutoCrafterTileEntity.RECIPE_SLOTS;

    public AutoCrafterContainer(AutoCrafterTileEntity tile, Player player, int id) {
        super(tile, player, id);
        addSlot(FilterSlot.createDischargeSlot(tile, tile.tier, AutoCrafterTileEntity.SLOT_BATTERY, SLOT_MIDDLE_X, ROW_TOP + 36));
        for (int i = 0; i < AutoCrafterTileEntity.INPUT_SLOTS; i++) {
            addSlot(new SlotBase(tile, AutoCrafterTileEntity.SLOT_INPUT + i, 8 + i * 18, BUFFER_Y));
        }
        for (int i = 0; i < AutoCrafterTileEntity.OUTPUT_SLOTS; i++) {
            addSlot(new SlotBase(tile, AutoCrafterTileEntity.SLOT_OUTPUT + i, SLOT_OUTPUT_X, ROW_TOP + i * 18));
        }
        addSlot(new FilterSlot(tile, AutoCrafterTileEntity.SLOT_STICK, SLOT_MIDDLE_X, ROW_TOP, new SimpleFilter(IC2Items.MEMORY_STICK)));
        for (int i = 0; i < AutoCrafterTileEntity.UPGRADE_SLOTS; i++) {
            addSlot(new UpgradeSlot(tile, AutoCrafterTileEntity.UPGRADE_START + i, SLOT_UPGRADE_X, ROW_TOP + i * 18));
        }
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new MemorySlot(tile, x + y * 3, 8 + x * 18, ROW_TOP + y * 18));
            }
        }
        addPlayerInventoryWithOffset(player.getInventory(), 0, 30);
        addComponent(new ChargeBarComponent(CHARGE, tile, new Vec2i(176, 0), true));
        addComponent(new ProgressComponent(PROGRESS, tile, new Vec2i(176, 14), false));
        addComponent(new RecipePanelComponent(tile, RECIPES));
        addComponent(new StatusComponent(tile, PROGRESS));
    }

    @Override
    public int getInventorySize() {
        return MACHINE_SLOTS;
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void onGuiLoaded(IC2Screen screen) {
        screen.setYSize(GUI_HEIGHT);
    }
}
