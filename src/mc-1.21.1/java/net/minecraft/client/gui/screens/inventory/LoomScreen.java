package net.minecraft.client.gui.screens.inventory;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class LoomScreen extends AbstractContainerScreen<LoomMenu> {
    private static final ResourceLocation BANNER_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/banner_slot");
    private static final ResourceLocation DYE_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/dye_slot");
    private static final ResourceLocation PATTERN_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/pattern_slot");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/scroller_disabled");
    private static final ResourceLocation PATTERN_SELECTED_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/pattern_selected");
    private static final ResourceLocation PATTERN_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/pattern_highlighted");
    private static final ResourceLocation PATTERN_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/pattern");
    private static final ResourceLocation ERROR_SPRITE = ResourceLocation.withDefaultNamespace("container/loom/error");
    private static final ResourceLocation BG_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/loom.png");
    private static final int PATTERN_COLUMNS = 4;
    private static final int PATTERN_ROWS = 4;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    private static final int PATTERN_IMAGE_SIZE = 14;
    private static final int SCROLLER_FULL_HEIGHT = 56;
    private static final int PATTERNS_X = 60;
    private static final int PATTERNS_Y = 13;
    private ModelPart flag;
    @Nullable
    private BannerPatternLayers resultBannerPatterns;
    private ItemStack bannerStack = ItemStack.EMPTY;
    private ItemStack dyeStack = ItemStack.EMPTY;
    private ItemStack patternStack = ItemStack.EMPTY;
    private boolean displayPatterns;
    private boolean hasMaxPatterns;
    private float scrollOffs;
    private boolean scrolling;
    private int startRow;

    public LoomScreen(LoomMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        menu.registerUpdateListener(this::containerChanged);
        this.titleLabelY -= 2;
    }

    @Override
    protected void init() {
        super.init();
        this.flag = this.minecraft.getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
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
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private int totalRowCount() {
        return Mth.positiveCeilDiv(this.menu.getSelectablePatterns().size(), 4);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = this.leftPos;
        int j = this.topPos;
        guiGraphics.blit(BG_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
        Slot slot = this.menu.getBannerSlot();
        Slot slot2 = this.menu.getDyeSlot();
        Slot slot3 = this.menu.getPatternSlot();
        Slot slot4 = this.menu.getResultSlot();
        if (!slot.hasItem()) {
            guiGraphics.blitSprite(BANNER_SLOT_SPRITE, i + slot.x, j + slot.y, 16, 16);
        }

        if (!slot2.hasItem()) {
            guiGraphics.blitSprite(DYE_SLOT_SPRITE, i + slot2.x, j + slot2.y, 16, 16);
        }

        if (!slot3.hasItem()) {
            guiGraphics.blitSprite(PATTERN_SLOT_SPRITE, i + slot3.x, j + slot3.y, 16, 16);
        }

        int k = (int)(41.0F * this.scrollOffs);
        ResourceLocation resourceLocation = this.displayPatterns ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
        guiGraphics.blitSprite(resourceLocation, i + 119, j + 13 + k, 12, 15);
        Lighting.setupForFlatItems();
        if (this.resultBannerPatterns != null && !this.hasMaxPatterns) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(i + 139, j + 52, 0.0F);
            guiGraphics.pose().scale(24.0F, 24.0F, 1.0F);
            guiGraphics.pose().translate(0.5F, -0.5F, 0.5F);
            float f = 0.6666667F;
            guiGraphics.pose().scale(0.6666667F, 0.6666667F, -0.6666667F);
            this.flag.xRot = 0.0F;
            this.flag.y = -32.0F;
            DyeColor dyeColor = ((BannerItem)slot4.getItem().getItem()).getColor();
            BannerRenderer.renderPatterns(
                guiGraphics.pose(),
                guiGraphics.bufferSource(),
                15728880,
                OverlayTexture.NO_OVERLAY,
                this.flag,
                ModelBakery.BANNER_BASE,
                true,
                dyeColor,
                this.resultBannerPatterns
            );
            guiGraphics.pose().popPose();
            guiGraphics.flush();
        } else if (this.hasMaxPatterns) {
            guiGraphics.blitSprite(ERROR_SPRITE, i + slot4.x - 5, j + slot4.y - 5, 26, 26);
        }

        if (this.displayPatterns) {
            int l = i + 60;
            int m = j + 13;
            List<Holder<BannerPattern>> list = this.menu.getSelectablePatterns();

            label64:
            for (int n = 0; n < 4; n++) {
                for (int o = 0; o < 4; o++) {
                    int p = n + this.startRow;
                    int q = p * 4 + o;
                    if (q >= list.size()) {
                        break label64;
                    }

                    int r = l + o * 14;
                    int s = m + n * 14;
                    boolean bl = mouseX >= r && mouseY >= s && mouseX < r + 14 && mouseY < s + 14;
                    ResourceLocation resourceLocation2;
                    if (q == this.menu.getSelectedBannerPatternIndex()) {
                        resourceLocation2 = PATTERN_SELECTED_SPRITE;
                    } else if (bl) {
                        resourceLocation2 = PATTERN_HIGHLIGHTED_SPRITE;
                    } else {
                        resourceLocation2 = PATTERN_SPRITE;
                    }

                    guiGraphics.blitSprite(resourceLocation2, r, s, 14, 14);
                    this.renderPattern(guiGraphics, list.get(q), r, s);
                }
            }
        }

        Lighting.setupFor3DItems();
    }

    private void renderPattern(GuiGraphics guiGraphics, Holder<BannerPattern> patern, int x, int y) {
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(x + 0.5F, y + 16, 0.0F);
        poseStack.scale(6.0F, -6.0F, 1.0F);
        poseStack.translate(0.5F, 0.5F, 0.0F);
        poseStack.translate(0.5F, 0.5F, 0.5F);
        float f = 0.6666667F;
        poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
        this.flag.xRot = 0.0F;
        this.flag.y = -32.0F;
        BannerPatternLayers bannerPatternLayers = new BannerPatternLayers.Builder().add(patern, DyeColor.WHITE).build();
        BannerRenderer.renderPatterns(
            poseStack,
            guiGraphics.bufferSource(),
            15728880,
            OverlayTexture.NO_OVERLAY,
            this.flag,
            ModelBakery.BANNER_BASE,
            true,
            DyeColor.GRAY,
            bannerPatternLayers
        );
        poseStack.popPose();
        guiGraphics.flush();
    }

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was clicked.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.scrolling = false;
        if (this.displayPatterns) {
            int i = this.leftPos + 60;
            int j = this.topPos + 13;

            for (int k = 0; k < 4; k++) {
                for (int l = 0; l < 4; l++) {
                    double d = mouseX - (i + l * 14);
                    double e = mouseY - (j + k * 14);
                    int m = k + this.startRow;
                    int n = m * 4 + l;
                    if (d >= 0.0 && e >= 0.0 && d < 14.0 && e < 14.0 && this.menu.clickMenuButton(this.minecraft.player, n)) {
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0F));
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, n);
                        return true;
                    }
                }
            }

            i = this.leftPos + 119;
            j = this.topPos + 9;
            if (mouseX >= i && mouseX < i + 12 && mouseY >= j && mouseY < j + 56) {
                this.scrolling = true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that is being dragged.
     * @param dragX  the X distance of the drag.
     * @param dragY  the Y distance of the drag.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int i = this.totalRowCount() - 4;
        if (this.scrolling && this.displayPatterns && i > 0) {
            int j = this.topPos + 13;
            int k = j + 56;
            this.scrollOffs = ((float)mouseY - j - 7.5F) / (k - j - 15.0F);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            this.startRow = Math.max((int)(this.scrollOffs * i + 0.5), 0);
            return true;
        } else {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int i = this.totalRowCount() - 4;
        if (this.displayPatterns && i > 0) {
            float f = (float)scrollY / i;
            this.scrollOffs = Mth.clamp(this.scrollOffs - f, 0.0F, 1.0F);
            this.startRow = Math.max((int)(this.scrollOffs * i + 0.5F), 0);
        }

        return true;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return mouseX < guiLeft || mouseY < guiTop || mouseX >= guiLeft + this.imageWidth || mouseY >= guiTop + this.imageHeight;
    }

    private void containerChanged() {
        ItemStack itemStack = this.menu.getResultSlot().getItem();
        if (itemStack.isEmpty()) {
            this.resultBannerPatterns = null;
        } else {
            this.resultBannerPatterns = itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        }

        ItemStack itemStack2 = this.menu.getBannerSlot().getItem();
        ItemStack itemStack3 = this.menu.getDyeSlot().getItem();
        ItemStack itemStack4 = this.menu.getPatternSlot().getItem();
        BannerPatternLayers bannerPatternLayers = itemStack2.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        this.hasMaxPatterns = bannerPatternLayers.layers().size() >= 6;
        if (this.hasMaxPatterns) {
            this.resultBannerPatterns = null;
        }

        if (!ItemStack.matches(itemStack2, this.bannerStack)
            || !ItemStack.matches(itemStack3, this.dyeStack)
            || !ItemStack.matches(itemStack4, this.patternStack)) {
            this.displayPatterns = !itemStack2.isEmpty() && !itemStack3.isEmpty() && !this.hasMaxPatterns && !this.menu.getSelectablePatterns().isEmpty();
        }

        if (this.startRow >= this.totalRowCount()) {
            this.startRow = 0;
            this.scrollOffs = 0.0F;
        }

        this.bannerStack = itemStack2.copy();
        this.dyeStack = itemStack3.copy();
        this.patternStack = itemStack4.copy();
    }
}
