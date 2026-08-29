package dev.dtim.ic2cautocrafter.content;

import dev.dtim.ic2cautocrafter.AutoCrafterMod;
import ic2.core.block.base.drops.IBlockDropProvider;
import ic2.core.block.machines.BaseMachineBlock;
import ic2.core.platform.rendering.features.ITextureProvider;
import ic2.core.utils.tooltips.helper.ITooltipProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AutoCrafterBlock extends BaseMachineBlock {

    public AutoCrafterBlock() {
        super(AutoCrafterMod.MOD_ID + ":auto_crafter", IBlockDropProvider.SELF,
                ITextureProvider.toggle(AutoCrafterMod.MOD_ID, "machines/auto_crafter"), null);
        setTooltips(ITooltipProvider.MV_MACHINE);
        enableAnimations();
        addTooltip(ITooltipProvider.consumptionUse(dev.dtim.ic2cautocrafter.AutoCrafterConfig.energyPerTick()));
        addTooltip(ITooltipProvider.tooltip("tooltip.block.ic2c_autocrafter.auto_crafter"));
        addTooltip(ITooltipProvider.tooltip("tooltip.block.ic2c_autocrafter.auto_crafter.usage"));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoCrafterTileEntity(pos, state);
    }
}
