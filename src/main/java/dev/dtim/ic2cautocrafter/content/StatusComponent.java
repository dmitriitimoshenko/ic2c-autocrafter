package dev.dtim.ic2cautocrafter.content;

import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import ic2.core.inventory.gui.components.GuiWidget;
import ic2.core.utils.math.geometry.Box2i;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Explains on hover why the machine is standing still instead of leaving the player guessing. */
public class StatusComponent extends GuiWidget {
    private static final String[] KEYS = {
            "gui.ic2c_autocrafter.status.working",
            "gui.ic2c_autocrafter.status.no_stick",
            "gui.ic2c_autocrafter.status.no_recipes",
            "gui.ic2c_autocrafter.status.all_disabled",
            "gui.ic2c_autocrafter.status.missing_items",
            "gui.ic2c_autocrafter.status.output_full",
            "gui.ic2c_autocrafter.status.no_energy",
            "gui.ic2c_autocrafter.status.redstone"
    };

    private final AutoCrafterTileEntity tile;

    public StatusComponent(AutoCrafterTileEntity tile, Box2i box) {
        super(box);
        this.tile = tile;
    }

    @Override
    protected void addRequests(Set<GuiWidget.ActionRequest> requests) {
        requests.add(GuiWidget.ActionRequest.TOOLTIP);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addTooltips(PoseStack matrix, int mouseX, int mouseY, Consumer<Component> tooltips) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return;
        }
        int status = this.tile.status;
        if (status < 0 || status >= KEYS.length) {
            return;
        }
        tooltips.accept(this.translate(KEYS[status]).withStyle(
                status == AutoCrafterTileEntity.STATUS_WORKING ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        if (status == AutoCrafterTileEntity.STATUS_MISSING_ITEMS) {
            ItemStack missing = this.tile.getMissingIngredient();
            if (!missing.isEmpty()) {
                tooltips.accept(this.translate("gui.ic2c_autocrafter.status.missing_items.detail",
                        missing.getCount() + "x " + missing.getHoverName().getString()).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
