package dev.dtim.ic2cautocrafter.content;

import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import ic2.core.inventory.gui.components.GuiWidget;
import ic2.core.utils.math.geometry.Box2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws the state of the nine recipe icons on top of the ghost slots and lets the player
 * switch single recipes off with a click, without pulling the memory stick out.
 */
public class RecipePanelComponent extends GuiWidget {
    /** Everything here is an overlay: the recipe icon underneath must stay readable. */
    private static final int DISABLED_TINT = 0x66101014;
    private static final int DISABLED_MARK = 0xDDD03A3A;
    private static final int ACTIVE_FRAME = 0xDD52E06A;
    private static final int ACTIVE_GLOW = 0x1A52E06A;

    private final AutoCrafterTileEntity tile;

    public RecipePanelComponent(AutoCrafterTileEntity tile, Box2i box) {
        super(box);
        this.tile = tile;
    }

    @Override
    protected void addRequests(Set<GuiWidget.ActionRequest> requests) {
        requests.add(GuiWidget.ActionRequest.DRAW_FOREGROUND);
        requests.add(GuiWidget.ActionRequest.TOOLTIP);
        requests.add(GuiWidget.ActionRequest.MOUSE_INPUT);
    }

    private int indexAt(int mouseX, int mouseY) {
        Box2i box = this.getBox();
        int col = (mouseX - box.getX()) / 18;
        int row = (mouseY - box.getY()) / 18;
        if (col < 0 || col > 2 || row < 0 || row > 2) {
            return -1;
        }
        return col + row * 3;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawForeground(PoseStack matrix, int mouseX, int mouseY) {
        Box2i box = this.getBox();
        RenderSystem.enableBlend();
        for (int i = 0; i < AutoCrafterTileEntity.RECIPE_SLOTS; i++) {
            if (!this.tile.isRecipeLoaded(i)) {
                continue;
            }
            int x = box.getX() + 1 + i % 3 * 18;
            int y = box.getY() + 1 + i / 3 * 18;
            if (!this.tile.isRecipeSelected(i)) {
                // light dim plus a red bar in the corner - the icon still reads through it
                this.gui.drawColoredRegion(matrix, x, y, 16.0f, 16.0f, DISABLED_TINT);
                this.gui.drawColoredRegion(matrix, x + 9, y + 13, 6.0f, 2.0f, DISABLED_MARK);
            } else if (this.tile.activeRecipe == i) {
                // thin frame and a whisper of glow instead of a fill
                this.gui.drawColoredRegion(matrix, x, y, 16.0f, 16.0f, ACTIVE_GLOW);
                this.gui.drawColoredRegion(matrix, x - 1, y - 1, 18.0f, 1.0f, ACTIVE_FRAME);
                this.gui.drawColoredRegion(matrix, x - 1, y + 16, 18.0f, 1.0f, ACTIVE_FRAME);
                this.gui.drawColoredRegion(matrix, x - 1, y, 1.0f, 16.0f, ACTIVE_FRAME);
                this.gui.drawColoredRegion(matrix, x + 16, y, 1.0f, 16.0f, ACTIVE_FRAME);
            }
        }
        RenderSystem.disableBlend();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addTooltips(PoseStack matrix, int mouseX, int mouseY, Consumer<Component> tooltips) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return;
        }
        int index = this.indexAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        if (!this.tile.isRecipeLoaded(index)) {
            tooltips.accept(this.translate("gui.ic2c_autocrafter.recipe.empty"));
            return;
        }
        if (this.tile.activeRecipe == index) {
            tooltips.accept(this.translate("gui.ic2c_autocrafter.recipe.active"));
        }
        tooltips.accept(this.translate(this.tile.isRecipeSelected(index)
                ? "gui.ic2c_autocrafter.recipe.enabled"
                : "gui.ic2c_autocrafter.recipe.disabled"));
        if (this.tile.missingRecipe == index) {
            ItemStack missing = this.tile.getMissingIngredient();
            if (!missing.isEmpty()) {
                tooltips.accept(this.translate("gui.ic2c_autocrafter.status.missing_items.detail",
                        missing.getCount() + "x " + missing.getHoverName().getString()));
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean onMouseClick(int mouseX, int mouseY, int mouseButton) {
        int index = this.indexAt(mouseX, mouseY);
        if (index < 0 || !this.tile.isRecipeLoaded(index)) {
            return false;
        }
        this.tile.sendToServer(AutoCrafterTileEntity.EVENT_TOGGLE_RECIPE, index);
        this.tile.selectedSlots ^= 1 << index;
        return true;
    }
}
