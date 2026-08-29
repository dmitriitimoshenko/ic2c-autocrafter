package dev.dtim.ic2cautocrafter.content;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import dev.dtim.ic2cautocrafter.AutoCrafterConfig;
import dev.dtim.ic2cautocrafter.AutoCrafterMod;
import ic2.api.items.IUpgradeItem;
import ic2.api.network.buffer.NetworkInfo;
import ic2.api.util.DirectionList;
import ic2.core.block.base.features.IParticleSpawner;
import ic2.core.block.base.features.ITickListener;
import ic2.core.block.base.misc.comparator.ComparatorNames;
import ic2.core.block.base.misc.comparator.types.base.FlagComparator;
import ic2.core.block.base.misc.comparator.types.base.ProgressComparator;
import ic2.core.block.base.tiles.impls.machine.single.BaseMachineTileEntity;
import ic2.core.block.machines.logic.crafter.CraftRecipe;
import ic2.core.block.machines.logic.crafter.CraftingList;
import ic2.core.block.machines.logic.crafter.IMemorySlotProvider;
import ic2.core.inventory.base.IHasInventory;
import ic2.core.inventory.base.ITileGui;
import ic2.core.inventory.container.IC2Container;
import ic2.core.inventory.filter.IFilter;
import ic2.core.inventory.filter.SimpleFilter;
import ic2.core.inventory.filter.SpecialFilters;
import ic2.core.inventory.filter.special.ElectricItemFilter;
import ic2.core.inventory.handler.AccessRule;
import ic2.core.inventory.handler.InventoryHandler;
import ic2.core.inventory.handler.SlotType;
import ic2.core.inventory.inv.IC2CraftingInventory;
import ic2.core.inventory.inv.RangedInventory;
import ic2.core.inventory.inv.SimpleInventory;
import ic2.core.inventory.transporter.IItemTransporter;
import ic2.core.inventory.transporter.TransporterManager;
import ic2.core.item.misc.MemoryStickItem;
import ic2.core.platform.registries.IC2Items;
import ic2.core.utils.helpers.StackUtil;
import ic2.core.utils.math.MathUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Low voltage crafting machine. Recipes are not configured in the machine itself: it reads
 * up to nine of them from an IC2 Memory Stick that was written on an Industrial Worktable.
 * Every operation crafts exactly one recipe, cycling through the enabled ones whose
 * ingredients are present in the input buffer.
 */
public class AutoCrafterTileEntity extends BaseMachineTileEntity implements ITickListener, ITileGui, IMemorySlotProvider, IParticleSpawner {

    /**
     * Same set every stock IC2 machine accepts. Overclockers are classified as RECIPE_MOD in IC2C,
     * so narrowing this list silently breaks them; upgrades that have nothing to do here simply idle.
     */
    public static final EnumSet<IUpgradeItem.UpgradeType> SUPPORTED_UPGRADES = EnumSet.allOf(IUpgradeItem.UpgradeType.class);

    public static final int SLOT_BATTERY = 0;
    public static final int SLOT_INPUT = 1;
    public static final int INPUT_SLOTS = 9;
    public static final int SLOT_OUTPUT = 10;
    public static final int OUTPUT_SLOTS = 3;
    public static final int SLOT_STICK = 13;
    public static final int UPGRADE_START = 14;
    public static final int UPGRADE_SLOTS = 3;
    public static final int RECIPE_SLOTS = 9;

    /** Medium voltage: 128 EU/t input tier. Everything else comes from the config. */
    public static final int MAX_INPUT = 128;

    /** Client -> server: toggle recipe slot <value>. */
    public static final int EVENT_TOGGLE_RECIPE = 1;

    public static final int STATUS_WORKING = 0;
    public static final int STATUS_NO_STICK = 1;
    public static final int STATUS_NO_RECIPES = 2;
    public static final int STATUS_ALL_DISABLED = 3;
    public static final int STATUS_MISSING_ITEMS = 4;
    public static final int STATUS_OUTPUT_FULL = 5;
    public static final int STATUS_NO_ENERGY = 6;
    public static final int STATUS_REDSTONE = 7;

    private final CraftingContainer crafting = new IC2CraftingInventory(3, 3);
    private final CraftingList recipes = new CraftingList(RECIPE_SLOTS);
    private final List<ItemStack> pending = new ArrayList<>();
    /** Every ingredient filter of every loaded recipe, cached so slot checks stay cheap. */
    private final List<IFilter> ingredientFilters = new ArrayList<>();

    @NetworkInfo
    public float progress = 0F;
    /** Bit mask of the recipe slots the inserted stick actually filled, drives the ghost slots. */
    @NetworkInfo
    public int enabledSlots = 0;
    /** Bit mask of the recipes the player left switched on. */
    @NetworkInfo
    public int selectedSlots = -1;
    /** Recipe currently being crafted, -1 when the machine has nothing to do. */
    @NetworkInfo
    public int activeRecipe = -1;
    /** Why the machine is not running, one of the STATUS_* constants. */
    @NetworkInfo
    public int status = STATUS_NO_STICK;
    /** Recipe and grid slot of the ingredient that ran out, so the GUI can name it. */
    @NetworkInfo
    public int missingRecipe = -1;
    @NetworkInfo
    public int missingSlot = -1;

    private int lastCrafted = -1;
    private int idleTicks = 0;
    private int failedSlot = -1;
    /** Copy of the stick as of the last reload; the live stack is mutated in place by slot code. */
    private ItemStack stickCache = ItemStack.EMPTY;
    private boolean internal = false;
    private boolean recheck = true;

    public AutoCrafterTileEntity(BlockPos pos, BlockState state) {
        super(pos, state, SLOT_STICK + 1, UPGRADE_SLOTS, AutoCrafterConfig.energyPerTick(),
                AutoCrafterConfig.ticksPerCraft(), AutoCrafterConfig.energyBuffer(), MAX_INPUT);
        this.sensitive = false;
        setFuelSlot(SLOT_BATTERY);
        addGuiFields("progress", "enabledSlots", "selectedSlots", "activeRecipe", "status", "missingRecipe", "missingSlot");
        addComparator(new ProgressComparator("progress", ComparatorNames.PROGRESS, this));
        addComparator(FlagComparator.createTile("active", ComparatorNames.ACTIVE, this));
    }

    private static final ResourceLocation SOUND_WORKING =
            new ResourceLocation(AutoCrafterMod.MOD_ID, "sounds/machines/auto_crafter_operating.ogg");
    private static final ResourceLocation SOUND_START =
            new ResourceLocation(AutoCrafterMod.MOD_ID, "sounds/machines/auto_crafter_start.ogg");
    private static final ResourceLocation SOUND_STOP =
            new ResourceLocation(AutoCrafterMod.MOD_ID, "sounds/machines/auto_crafter_interrupt.ogg");

    @Override
    protected ResourceLocation getWorkingSound() {
        return AutoCrafterConfig.machineSound() ? SOUND_WORKING : null;
    }

    @Override
    protected ResourceLocation getStartupSound() {
        return AutoCrafterConfig.machineSound() ? SOUND_START : null;
    }

    @Override
    protected ResourceLocation getInterruptSound() {
        return AutoCrafterConfig.machineSound() ? SOUND_STOP : null;
    }

    @Override
    public BlockEntityType<?> createType() {
        return AutoCrafterMod.AUTO_CRAFTER_TILE.get();
    }

    @Override
    protected void addSlotInfo(InventoryHandler handler) {
        int[] inputs = MathUtils.fromTo(SLOT_INPUT, SLOT_INPUT + INPUT_SLOTS);
        int[] outputs = MathUtils.fromTo(SLOT_OUTPUT, SLOT_OUTPUT + OUTPUT_SLOTS);
        handler.registerBlockSides(DirectionList.ALL);
        handler.registerBlockAccess(DirectionList.ALL, AccessRule.BOTH);
        handler.registerSlotAccess(AccessRule.BOTH, SLOT_BATTERY);
        handler.registerSlotAccess(AccessRule.IMPORT, inputs);
        handler.registerSlotAccess(AccessRule.EXPORT, outputs);
        handler.registerSlotsForSide(DirectionList.DOWN.invert(), inputs);
        handler.registerSlotsForSide(DirectionList.UP.invert(), outputs);
        handler.registerSlotsForSide(DirectionList.ALL, SLOT_BATTERY);
        handler.registerInputFilter(SpecialFilters.createChargeFilter(), SLOT_BATTERY);
        handler.registerOutputFilter(ElectricItemFilter.NOT_DISCHARGE_FILTER, SLOT_BATTERY);
        handler.registerInputFilter(this::isValidIngredient, inputs);
        handler.registerInputFilter(SpecialFilters.ALWAYS_FALSE, outputs);
        handler.registerInputFilter(new SimpleFilter(IC2Items.MEMORY_STICK), SLOT_STICK);
        handler.registerNamedSlot(SlotType.BATTERY, SLOT_BATTERY);
        handler.registerNamedSlot(SlotType.INPUT, inputs);
        handler.registerNamedSlot(SlotType.OUTPUT, outputs);
        handler.registerNamedSlot(SlotType.STORAGE, SLOT_STICK);
    }

    @Override
    protected void createInvCaches() {
        this.inOut = new IHasInventory[2];
        this.inOut[0] = new RangedInventory(this, MathUtils.fromTo(SLOT_INPUT, SLOT_INPUT + INPUT_SLOTS));
        this.inOut[1] = new RangedInventory(this, MathUtils.fromTo(SLOT_OUTPUT, SLOT_OUTPUT + OUTPUT_SLOTS)).setOutputOnly();
    }

    @Override
    protected void handleMods() {
        updateGuiFields("progress");
    }

    @Override
    public EnumSet<IUpgradeItem.UpgradeType> getSupportedUpgradeTypes() {
        return SUPPORTED_UPGRADES;
    }

    @Override
    public float getProgress() {
        return this.progress;
    }

    @Override
    public float getMaxProgress() {
        return this.operationLength;
    }

    @Override
    public IC2Container createContainer(Player player, InteractionHand hand, Direction side, int windowID) {
        return new AutoCrafterContainer(this, player, windowID);
    }

    // ---------------------------------------------------------------- memory stick / recipes

    @Override
    public int getEnabledSlots() {
        return this.enabledSlots;
    }

    @Override
    public boolean isServerSided() {
        return isSimulating();
    }

    @Override
    public CraftingList getRecipes() {
        return this.recipes;
    }

    public boolean isRecipeSelected(int index) {
        return (this.selectedSlots & 1 << index) != 0;
    }

    public boolean isRecipeLoaded(int index) {
        return (this.enabledSlots & 1 << index) != 0;
    }

    /** Left clicking a recipe icon in the GUI switches that recipe off without pulling the stick. */
    @Override
    public void onClientDataReceived(Player player, int key, int value) {
        super.onClientDataReceived(player, key, value);
        if (key == EVENT_TOGGLE_RECIPE && value >= 0 && value < RECIPE_SLOTS) {
            this.selectedSlots ^= 1 << value;
            this.recheck = true;
            updateGuiField("selectedSlots");
            addToTick();
        }
    }

    /** Reads the recipes off the inserted stick. Runs on both sides so the ghost slots have data. */
    private void reloadRecipes(boolean resetSelection) {
        Level level = getLevel();
        ItemStack stick = getStackInSlot(SLOT_STICK);
        int mask = 0;
        if (level != null && stick.getItem() instanceof MemoryStickItem) {
            for (int i = 0; i < RECIPE_SLOTS; i++) {
                CraftRecipe recipe = MemoryStickItem.loadRecipe(stick, i);
                if (recipe != null && recipe.validate(level)) {
                    this.recipes.saveRecipe(i, recipe);
                    mask |= 1 << i;
                } else {
                    this.recipes.removeRecipe(i);
                }
            }
        } else {
            for (int i = 0; i < RECIPE_SLOTS; i++) {
                this.recipes.removeRecipe(i);
            }
        }
        this.stickCache = stick.copy();
        this.enabledSlots = mask;
        this.activeRecipe = -1;
        this.recheck = true;
        if (resetSelection) {
            // A freshly inserted stick starts with every recipe on; a chunk reload keeps the player's choice.
            this.selectedSlots = -1;
        }
        rebuildIngredientCache();
        updateGuiFields("enabledSlots", "activeRecipe", "selectedSlots");
    }

    private void rebuildIngredientCache() {
        this.ingredientFilters.clear();
        for (int i = 0; i < RECIPE_SLOTS; i++) {
            CraftRecipe recipe = this.recipes.getRecipe(i);
            if (recipe != null) {
                this.ingredientFilters.addAll(recipe.getFilters().keySet());
            }
        }
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void onLoaded() {
        super.onLoaded();
        reloadRecipes(false);
        if (isSimulating()) {
            handleUpgrades(false);
        }
    }

    @Override
    public void onTick() {
        handleRedstone();
        syncStick();
        boolean charging = handleChargeSlot((int) (this.maxEnergy * 0.9D));
        boolean flushed = flushPending();
        if (this.activeRecipe < 0 && ++this.idleTicks >= 20) {
            this.idleTicks = 0;
            this.recheck = true;
        }
        if (this.recheck) {
            selectRecipe();
            this.recheck = false;
        }
        boolean hasWork = flushed && this.activeRecipe >= 0;
        boolean powered = hasEnergy(this.energyConsume);
        boolean allowed = canProcess();
        if (hasWork && powered && allowed) {
            setStatus(STATUS_WORKING);
            if (setActive(true)) {
                playSound(false);
            }
            useEnergy(this.energyConsume);
            this.progress += this.progressPerTick;
            if (this.progress >= this.operationLength) {
                this.progress = 0F;
                craft(this.activeRecipe);
                this.recheck = true;
            }
            updateGuiField("progress");
        } else {
            if (setActive(false) && this.progress > 0F) {
                playSound(true);
            }
            if (hasWork && !allowed) {
                setStatus(STATUS_REDSTONE);
            } else if (hasWork) {
                setStatus(STATUS_NO_ENERGY);
            } else if (!flushed) {
                setStatus(STATUS_OUTPUT_FULL);
            }
            if (this.progress > 0F) {
                // Never wipe the bar outright: a hopper briefly stealing an item should not undo 10 seconds.
                this.progress = Math.max(0F, this.progress - 1F);
                updateGuiField("progress");
            }
            if (!charging && this.progress <= 0F && !this.storage.has(IUpgradeItem.Functions.TICK)) {
                removeFromTick();
            }
        }
        this.storage.onTick(this.inventory, this);
        handleComparators();
    }

    private void setStatus(int value) {
        if (this.status != value) {
            this.status = value;
            updateGuiField("status");
        }
    }

    /** Leftovers from a craft (empty buckets and the like) are put away before the next craft. */
    private boolean flushPending() {
        if (this.pending.isEmpty()) {
            return true;
        }
        this.internal = true;
        try {
            IItemTransporter transporter = TransporterManager.getTransporter(getInputInventory());
            Iterator<ItemStack> iter = this.pending.iterator();
            while (iter.hasNext()) {
                ItemStack stack = iter.next();
                if (isIngredient(stack)) {
                    stack.shrink(transporter.addItem(stack, null, false));
                }
                if (!stack.isEmpty()) {
                    stack.shrink(addToOutput(stack, false));
                }
                if (stack.isEmpty()) {
                    iter.remove();
                }
            }
        } finally {
            this.internal = false;
        }
        return this.pending.isEmpty();
    }

    /** Picks the next craftable recipe, round robin so no recipe starves, and reports why none fits. */
    private void selectRecipe() {
        int found = -1;
        int missRecipe = -1;
        int missSlot = -1;
        int reason;
        if (getStackInSlot(SLOT_STICK).isEmpty()) {
            reason = STATUS_NO_STICK;
        } else if (this.enabledSlots == 0) {
            reason = STATUS_NO_RECIPES;
        } else if ((this.enabledSlots & this.selectedSlots) == 0) {
            reason = STATUS_ALL_DISABLED;
        } else {
            reason = STATUS_MISSING_ITEMS;
            boolean blockedByOutput = false;
            for (int i = 1; i <= RECIPE_SLOTS; i++) {
                int index = Math.floorMod(this.lastCrafted + i, RECIPE_SLOTS);
                CraftRecipe recipe = this.recipes.getRecipe(index);
                if (recipe == null || !recipe.isValid() || !isRecipeSelected(index)) {
                    continue;
                }
                Attempt attempt = runRecipe(recipe, true);
                if (attempt == Attempt.OK) {
                    found = index;
                    break;
                }
                if (attempt == Attempt.MISSING && missRecipe < 0) {
                    missRecipe = index;
                    missSlot = this.failedSlot;
                }
                if (attempt == Attempt.NO_ROOM) {
                    blockedByOutput = true;
                }
            }
            if (found < 0 && blockedByOutput) {
                reason = STATUS_OUTPUT_FULL;
            }
        }
        if (found >= 0) {
            missRecipe = -1;
            missSlot = -1;
        }
        if (missRecipe != this.missingRecipe || missSlot != this.missingSlot) {
            this.missingRecipe = missRecipe;
            this.missingSlot = missSlot;
            updateGuiFields("missingRecipe", "missingSlot");
        }
        if (found != this.activeRecipe) {
            this.activeRecipe = found;
            updateGuiField("activeRecipe");
        }
        if (found < 0) {
            setStatus(reason);
        }
    }

    private void craft(int index) {
        CraftRecipe recipe = this.recipes.getRecipe(index);
        if (recipe != null && recipe.isValid() && runRecipe(recipe, false) == Attempt.OK) {
            this.lastCrafted = index;
            notifyListeners();
        }
    }

    private enum Attempt {
        OK,
        MISSING,
        NO_ROOM
    }

    /**
     * Pulls the ingredients out of the buffer and crafts once. When simulating, the work happens on a
     * copy of the buffer so nothing is touched; a failed real run puts everything back where it was.
     */
    private Attempt runRecipe(CraftRecipe recipe, boolean simulate) {
        Level level = getLevel();
        if (level == null) {
            return Attempt.MISSING;
        }
        Object2ObjectMap<IFilter, int[]> filters = recipe.getFilters();
        if (filters.isEmpty()) {
            return Attempt.MISSING;
        }
        IHasInventory target;
        if (simulate) {
            SimpleInventory copy = new SimpleInventory(INPUT_SLOTS);
            for (int i = 0; i < INPUT_SLOTS; i++) {
                copy.setStackInSlot(i, getStackInSlot(SLOT_INPUT + i).copy());
            }
            target = copy;
        } else {
            target = getInputInventory();
        }
        IItemTransporter transporter = TransporterManager.getTransporter(target);
        List<ItemStack> taken = new ArrayList<>();
        Attempt result = Attempt.OK;
        this.failedSlot = -1;
        this.internal = true;
        try {
            for (Object2ObjectMap.Entry<IFilter, int[]> entry : filters.object2ObjectEntrySet()) {
                int[] info = entry.getValue();
                ItemStack provided = transporter.removeItem(entry.getKey(), null, info[1], false);
                if (!provided.isEmpty()) {
                    taken.add(provided);
                }
                if (provided.isEmpty() || provided.getCount() < info[1]) {
                    result = Attempt.MISSING;
                    this.failedSlot = info[0];
                    break;
                }
                this.crafting.setItem(info[0], provided.copy());
            }
            ItemStack output = result == Attempt.OK ? recipe.getOutput(this.crafting, level) : ItemStack.EMPTY;
            if (result == Attempt.OK && (output.isEmpty() || addToOutput(output, true) < output.getCount())) {
                result = Attempt.NO_ROOM;
            }
            if (result != Attempt.OK || simulate) {
                for (ItemStack stack : taken) {
                    transporter.addItem(stack, null, false);
                }
                this.crafting.clearContent();
                return result;
            }
            addToOutput(output, false);
            NonNullList<ItemStack> remaining = recipe.getRemainingItems(this.crafting, level);
            this.crafting.clearContent();
            for (ItemStack rest : remaining) {
                if (rest.isEmpty()) {
                    continue;
                }
                ItemStack leftover = rest.copy();
                if (isIngredient(leftover)) {
                    leftover.shrink(transporter.addItem(leftover, null, false));
                }
                if (!leftover.isEmpty()) {
                    leftover.shrink(addToOutput(leftover, false));
                }
                if (!leftover.isEmpty()) {
                    this.pending.add(leftover);
                }
            }
            return Attempt.OK;
        } finally {
            this.internal = false;
        }
    }

    /** Spreads a stack over the output slots, returns how much fit. */
    private int addToOutput(ItemStack stack, boolean simulate) {
        int left = stack.getCount();
        for (int i = 0; i < OUTPUT_SLOTS && left > 0; i++) {
            int slot = SLOT_OUTPUT + i;
            ItemStack current = getStackInSlot(slot);
            int room;
            if (current.isEmpty()) {
                room = Math.min(stack.getMaxStackSize(), getMaxStackSize(slot));
            } else if (StackUtil.isStackEqual(current, stack)) {
                room = StackUtil.getStackSizeLeft(current);
            } else {
                continue;
            }
            int moved = Math.min(room, left);
            if (moved <= 0) {
                continue;
            }
            if (!simulate) {
                setOrGrow(slot, StackUtil.copyWithSize(stack, moved), false);
            }
            left -= moved;
        }
        return stack.getCount() - left;
    }

    /** Resolves the ingredient that ran out; both sides own the recipes, so two ints are enough to sync. */
    public ItemStack getMissingIngredient() {
        if (this.missingRecipe < 0 || this.missingRecipe >= RECIPE_SLOTS || this.missingSlot < 0) {
            return ItemStack.EMPTY;
        }
        CraftRecipe recipe = this.recipes.getRecipe(this.missingRecipe);
        if (recipe == null) {
            return ItemStack.EMPTY;
        }
        for (Object2ObjectMap.Entry<IFilter, int[]> entry : recipe.getFilters().object2ObjectEntrySet()) {
            if (entry.getValue()[0] == this.missingSlot) {
                return recipe.getFilter(entry.getKey());
            }
        }
        return ItemStack.EMPTY;
    }

    /** Sparks drifting off the working face; the block only asks for these while it is active. */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void animationTick(RandomSource rand) {
        Level level = getLevel();
        if (level == null || !isActive()) {
            return;
        }
        BlockPos pos = getBlockPos();
        if (rand.nextFloat() < 0.55F) {
            double x = pos.getX() + 0.25D + rand.nextDouble() * 0.5D;
            double z = pos.getZ() + 0.25D + rand.nextDouble() * 0.5D;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, pos.getY() + 1.02D, z,
                    (rand.nextDouble() - 0.5D) * 0.02D, 0.01D + rand.nextDouble() * 0.02D, (rand.nextDouble() - 0.5D) * 0.02D);
        }
        if (rand.nextFloat() < 0.12F) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                    0.0D, 0.015D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- inventory

    private boolean isIngredient(ItemStack stack) {
        for (int i = 0; i < this.ingredientFilters.size(); i++) {
            if (this.ingredientFilters.get(i).matches(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Only ingredients of a loaded recipe may be piped in, so the buffer cannot be clogged. */
    private boolean isValidIngredient(int slot, ItemStack stack) {
        return this.internal || this.ingredientFilters.isEmpty() || isIngredient(stack);
    }

    /** Tells IC2's import upgrades and tubes how much of an ingredient the buffer still takes. */
    @Override
    public int getValidRoom(ItemStack stack) {
        if (!isValidIngredient(SLOT_INPUT, stack)) {
            return 0;
        }
        int room = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack inv = getStackInSlot(SLOT_INPUT + i);
            if (inv.isEmpty()) {
                room += Math.min(getMaxStackSize(SLOT_INPUT + i), stack.getMaxStackSize());
            } else if (StackUtil.isStackEqual(inv, stack)) {
                room += StackUtil.getStackSizeLeft(inv);
            }
        }
        return room;
    }

    /** Reloads when the stick slot no longer matches what the recipes were read from. */
    private boolean syncStick() {
        if (ItemStack.matches(this.stickCache, getStackInSlot(SLOT_STICK))) {
            return false;
        }
        reloadRecipes(true);
        return true;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        super.setStackInSlot(slot, stack);
        if (slot == SLOT_STICK) {
            syncStick();
        }
        if (isSimulating() && !this.internal && slot != SLOT_BATTERY) {
            this.recheck = true;
            addToTick();
        }
    }

    @Override
    public void addItemIntoSlot(int slot, ItemStack stack) {
        super.addItemIntoSlot(slot, stack);
        if (isSimulating()) {
            this.recheck = true;
        }
    }

    @Override
    public void onNotify(IHasInventory inventory, int slot) {
        if (isSimulating()) {
            this.recheck = true;
            addToTick();
        }
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        drops.addAll(this.pending);
        super.addDrops(drops);
    }

    @Override
    public void saveAdditional(CompoundTag compound) {
        super.saveAdditional(compound);
        compound.putFloat("progress", this.progress);
        compound.putInt("last_crafted", this.lastCrafted);
        compound.putInt("selected", this.selectedSlots);
        ListTag list = new ListTag();
        for (ItemStack stack : this.pending) {
            list.add(stack.save(new CompoundTag()));
        }
        if (!list.isEmpty()) {
            compound.put("pending", list);
        }
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        this.progress = compound.getFloat("progress");
        this.lastCrafted = compound.getInt("last_crafted");
        this.selectedSlots = compound.contains("selected") ? compound.getInt("selected") : -1;
        this.pending.clear();
        for (Tag entry : compound.getList("pending", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) {
                this.pending.add(stack);
            }
        }
    }
}
