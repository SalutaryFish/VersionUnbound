package net.minecraft.client.gui.screens;

import com.mojang.text2speech.Narrator;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AccessibilityOnboardingScreen extends Screen {
    private static final Component TITLE = Component.translatable("accessibility.onboarding.screen.title");
    private static final Component ONBOARDING_NARRATOR_MESSAGE = Component.translatable("accessibility.onboarding.screen.narrator");
    private static final int PADDING = 4;
    private static final int TITLE_PADDING = 16;
    private final LogoRenderer logoRenderer;
    private final Options options;
    private final boolean narratorAvailable;
    private boolean hasNarrated;
    private float timer;
    private final Runnable onClose;
    @Nullable
    private FocusableTextWidget textWidget;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, this.initTitleYPos(), 33);

    public AccessibilityOnboardingScreen(Options options, Runnable onClose) {
        super(TITLE);
        this.options = options;
        this.onClose = onClose;
        this.logoRenderer = new LogoRenderer(true);
        this.narratorAvailable = Minecraft.getInstance().getNarrator().isActive();
    }

    @Override
    public void init() {
        LinearLayout linearLayout = this.layout.addToContents(LinearLayout.vertical());
        linearLayout.defaultCellSetting().alignHorizontallyCenter().padding(4);
        this.textWidget = linearLayout.addChild(new FocusableTextWidget(this.width, this.title, this.font), layoutSettings -> layoutSettings.padding(8));
        if (this.options.narrator().createButton(this.options) instanceof CycleButton cycleButton) {
            this.narratorButton = cycleButton;
            this.narratorButton.active = this.narratorAvailable;
            linearLayout.addChild(this.narratorButton);
        }

        linearLayout.addChild(
            CommonButtons.accessibility(150, button -> this.closeAndSetScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)), false)
        );
        linearLayout.addChild(
            CommonButtons.language(
                150, button -> this.closeAndSetScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())), false
            )
        );
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_CONTINUE, button -> this.onClose()).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.textWidget != null) {
            this.textWidget.containWithin(this.width);
        }

        this.layout.arrangeElements();
    }

    @Override
    protected void setInitialFocus() {
        if (this.narratorAvailable && this.narratorButton != null) {
            this.setInitialFocus(this.narratorButton);
        } else {
            super.setInitialFocus();
        }
    }

    private int initTitleYPos() {
        return 90;
    }

    @Override
    public void onClose() {
        this.close(true, this.onClose);
    }

    private void closeAndSetScreen(Screen screen) {
        this.close(false, () -> this.minecraft.setScreen(screen));
    }

    private void close(boolean markAsFinished, Runnable onClose) {
        if (markAsFinished) {
            this.options.onboardingAccessibilityFinished();
        }

        Narrator.getNarrator().clear();
        onClose.run();
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
        this.handleInitialNarrationDelay();
        this.logoRenderer.renderLogo(guiGraphics, this.width, 1.0F);
    }

    @Override
    protected void renderPanorama(GuiGraphics guiGraphics, float partialTick) {
        PANORAMA.render(guiGraphics, this.width, this.height, 1.0F, 0.0F);
    }

    private void handleInitialNarrationDelay() {
        if (!this.hasNarrated && this.narratorAvailable) {
            if (this.timer < 40.0F) {
                this.timer++;
            } else if (this.minecraft.isWindowActive()) {
                Narrator.getNarrator().say(ONBOARDING_NARRATOR_MESSAGE.getString(), true);
                this.hasNarrated = true;
            }
        }
    }
}
