package dev.dtim.ic2cautocrafter;

import dev.dtim.ic2cautocrafter.content.AutoCrafterTileEntity;
import ic2.core.block.machines.logic.crafter.CraftRecipe;
import ic2.core.inventory.inv.SimpleInventory;
import ic2.core.item.misc.MemoryStickItem;
import ic2.core.platform.registries.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AutoCrafterMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class AutoCrafterGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    /** One oak log on a stick recipe has to come back out as four planks. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void craftsRecipeFromMemoryStick(GameTestHelper helper) {
        AutoCrafterTileEntity tile = placeMachine(helper);
        tile.addEnergy(tile.getMaxEU());
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_STICK, stickWithPlanksRecipe(helper));
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_INPUT, new ItemStack(Items.OAK_LOG, 8));
        helper.succeedWhen(() -> {
            ItemStack output = tile.getStackInSlot(AutoCrafterTileEntity.SLOT_OUTPUT);
            check(output.is(Items.OAK_PLANKS) && output.getCount() >= 4,
                    "expected at least 4 oak planks in the output slot, found " + output);
            check(tile.getStoredEU() < tile.getMaxEU(), "the craft consumed no energy");
        });
    }

    /** With a programmed stick but an empty buffer the machine must stay idle and keep its charge. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void idlesWithoutIngredients(GameTestHelper helper) {
        AutoCrafterTileEntity tile = placeMachine(helper);
        tile.addEnergy(tile.getMaxEU());
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_STICK, stickWithPlanksRecipe(helper));
        helper.runAfterDelay(100, () -> {
            check(tile.getStackInSlot(AutoCrafterTileEntity.SLOT_OUTPUT).isEmpty(), "crafted without ingredients");
            check(tile.getStoredEU() == tile.getMaxEU(), "burned energy while idle");
            helper.succeed();
        });
    }

    /** A recipe the player switched off in the GUI must not be crafted. */
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void respectsDisabledRecipes(GameTestHelper helper) {
        AutoCrafterTileEntity tile = placeMachine(helper);
        tile.addEnergy(tile.getMaxEU());
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_STICK, stickWithPlanksRecipe(helper));
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_INPUT, new ItemStack(Items.OAK_LOG, 8));
        tile.onClientDataReceived(null, AutoCrafterTileEntity.EVENT_TOGGLE_RECIPE, 0);
        helper.runAfterDelay(200, () -> {
            check(tile.getStackInSlot(AutoCrafterTileEntity.SLOT_OUTPUT).isEmpty(), "crafted a disabled recipe");
            check(tile.status == AutoCrafterTileEntity.STATUS_ALL_DISABLED,
                    "expected the all-disabled status, got " + tile.status);
            helper.succeed();
        });
    }

    /** A blocked first output slot must not stall the machine, the craft goes to the next slot. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void usesSecondOutputSlotWhenFirstIsBlocked(GameTestHelper helper) {
        AutoCrafterTileEntity tile = placeMachine(helper);
        tile.addEnergy(tile.getMaxEU());
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_OUTPUT, new ItemStack(Items.STONE, 64));
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_STICK, stickWithPlanksRecipe(helper));
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_INPUT, new ItemStack(Items.OAK_LOG, 8));
        helper.succeedWhen(() -> {
            ItemStack second = tile.getStackInSlot(AutoCrafterTileEntity.SLOT_OUTPUT + 1);
            check(second.is(Items.OAK_PLANKS) && second.getCount() >= 4,
                    "expected planks in the second output slot, found " + second);
        });
    }

    /** Overclockers are RECIPE_MOD upgrades in IC2C; the craft has to finish well before the base 200 ticks. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overclockersSpeedUpTheCraft(GameTestHelper helper) {
        AutoCrafterTileEntity tile = placeMachine(helper);
        tile.addEnergy(tile.getMaxEU());
        for (int i = 0; i < AutoCrafterTileEntity.UPGRADE_SLOTS; i++) {
            tile.setStackInSlot(AutoCrafterTileEntity.UPGRADE_START + i, new ItemStack(IC2Items.OVERCLOCKER_UPGRADE));
        }
        tile.onUpgradesChanged();
        check(tile.progressPerTick > 1.0F, "overclockers did not raise the crafting speed");
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_STICK, stickWithPlanksRecipe(helper));
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_INPUT, new ItemStack(Items.OAK_LOG, 8));
        helper.runAfterDelay(80, () -> {
            check(tile.getStackInSlot(AutoCrafterTileEntity.SLOT_OUTPUT).is(Items.OAK_PLANKS),
                    "overclocked machine did not finish a craft within 80 ticks");
            helper.succeed();
        });
    }

    /** Pulling the stick out has to drop the recipes; IC2's slot code hands back the same stack object. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void forgetsRecipesWhenStickIsPulled(GameTestHelper helper) {
        AutoCrafterTileEntity tile = placeMachine(helper);
        tile.addEnergy(tile.getMaxEU());
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_STICK, stickWithPlanksRecipe(helper));
        check(tile.enabledSlots != 0, "the stick recipe was never loaded");
        // exactly what SlotBase.remove does when a player grabs the stick
        ItemStack live = tile.getStackInSlot(AutoCrafterTileEntity.SLOT_STICK);
        live.shrink(1);
        tile.setStackInSlot(AutoCrafterTileEntity.SLOT_STICK, live);
        helper.runAfterDelay(10, () -> {
            check(tile.enabledSlots == 0, "recipes survived the stick being pulled");
            check(tile.activeRecipe < 0, "a recipe stayed active without a stick");
            check(tile.status == AutoCrafterTileEntity.STATUS_NO_STICK,
                    "expected the no-stick status, got " + tile.status);
            helper.succeed();
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static AutoCrafterTileEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, AutoCrafterMod.AUTO_CRAFTER.get().defaultBlockState());
        return (AutoCrafterTileEntity) helper.getBlockEntity(MACHINE);
    }

    private static ItemStack stickWithPlanksRecipe(GameTestHelper helper) {
        Recipe<?> found = helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation("minecraft:oak_planks"))
                .orElseThrow(() -> new IllegalStateException("vanilla oak planks recipe is missing"));
        SimpleInventory grid = new SimpleInventory(10);
        grid.setStackInSlot(0, new ItemStack(Items.OAK_LOG));
        CraftRecipe recipe = new CraftRecipe();
        recipe.setRecipe((CraftingRecipe) found, grid);
        ItemStack stick = new ItemStack(IC2Items.MEMORY_STICK);
        MemoryStickItem.saveRecipe(stick, 0, recipe);
        return stick;
    }
}
