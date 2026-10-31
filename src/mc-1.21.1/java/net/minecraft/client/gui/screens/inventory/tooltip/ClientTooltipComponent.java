package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.joml.Matrix4f;

public interface ClientTooltipComponent {
    static ClientTooltipComponent create(FormattedCharSequence text) {
        return new ClientTextTooltip(text);
    }

    static ClientTooltipComponent create(TooltipComponent visualTooltipComponent) {
        if (visualTooltipComponent instanceof BundleTooltip bundleTooltip) {
            return new ClientBundleTooltip(bundleTooltip.contents());
        } else if (visualTooltipComponent instanceof ClientActivePlayersTooltip.ActivePlayersTooltip activePlayersTooltip) {
            return new ClientActivePlayersTooltip(activePlayersTooltip);
        } else {
            throw new IllegalArgumentException("Unknown TooltipComponent");
        }
    }

    int getHeight();

    int getWidth(Font var1);

    default void renderText(Font font, int mouseX, int mouseY, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
    }

    default void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
    }
}
