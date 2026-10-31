package net.minecraft.client.gui.screens.options;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

public class UnsupportedGraphicsWarningScreen extends Screen {
    private static final int BUTTON_PADDING = 20;
    private static final int BUTTON_MARGIN = 5;
    private static final int BUTTON_HEIGHT = 20;
    private final Component narrationMessage;
    private final List<Component> message;
    private final ImmutableList<UnsupportedGraphicsWarningScreen.ButtonOption> buttonOptions;
    private MultiLineLabel messageLines = MultiLineLabel.EMPTY;
    private int contentTop;
    private int buttonWidth;

    protected UnsupportedGraphicsWarningScreen(
        Component title, List<Component> message, ImmutableList<UnsupportedGraphicsWarningScreen.ButtonOption> buttonOptions
    ) {
        super(title);
        this.message = message;
        this.narrationMessage = CommonComponents.joinForNarration(title, ComponentUtils.formatList(message, CommonComponents.EMPTY));
        this.buttonOptions = buttonOptions;
    }

    @Override
    public Component getNarrationMessage() {
        return this.narrationMessage;
    }

    @Override
    public void init() {
        for (UnsupportedGraphicsWarningScreen.ButtonOption buttonOption : this.buttonOptions) {
            this.buttonWidth = Math.max(this.buttonWidth, 20 + this.font.width(buttonOption.message) + 20);
        }

        int i = 5 + this.buttonWidth + 5;
        int j = i * this.buttonOptions.size();
        this.messageLines = MultiLineLabel.create(this.font, j, this.message.toArray(new Component[0]));
        int k = this.messageLines.getLineCount() * 9;
        this.contentTop = (int)(this.height / 2.0 - k / 2.0);
        int l = this.contentTop + k + 9 * 2;
        int m = (int)(this.width / 2.0 - j / 2.0);

        for (UnsupportedGraphicsWarningScreen.ButtonOption buttonOption2 : this.buttonOptions) {
            this.addRenderableWidget(Button.builder(buttonOption2.message, buttonOption2.onPress).bounds(m, l, this.buttonWidth, 20).build());
            m += i;
        }
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.contentTop - 9 * 2, -1);
        this.messageLines.renderCentered(guiGraphics, this.width / 2, this.contentTop);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    public static final class ButtonOption {
        final Component message;
        final Button.OnPress onPress;

        public ButtonOption(Component message, Button.OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }
    }
}
