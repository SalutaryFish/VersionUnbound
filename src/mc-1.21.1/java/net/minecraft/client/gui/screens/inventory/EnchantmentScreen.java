package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchantmentScreen extends AbstractContainerScreen<EnchantmentMenu> {
    private static final ResourceLocation[] ENABLED_LEVEL_SPRITES = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/enchanting_table/level_1"),
        ResourceLocation.withDefaultNamespace("container/enchanting_table/level_2"),
        ResourceLocation.withDefaultNamespace("container/enchanting_table/level_3")
    };
    private static final ResourceLocation[] DISABLED_LEVEL_SPRITES = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/enchanting_table/level_1_disabled"),
        ResourceLocation.withDefaultNamespace("container/enchanting_table/level_2_disabled"),
        ResourceLocation.withDefaultNamespace("container/enchanting_table/level_3_disabled")
    };
    private static final ResourceLocation ENCHANTMENT_SLOT_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace(
        "container/enchanting_table/enchantment_slot_disabled"
    );
    private static final ResourceLocation ENCHANTMENT_SLOT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace(
        "container/enchanting_table/enchantment_slot_highlighted"
    );
    private static final ResourceLocation ENCHANTMENT_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot");
    /**
     * The ResourceLocation containing the Enchantment GUI texture location
     */
    private static final ResourceLocation ENCHANTING_TABLE_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/enchanting_table.png");
    /**
     * The ResourceLocation containing the texture for the Book rendered above the enchantment table
     */
    private static final ResourceLocation ENCHANTING_BOOK_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enchanting_table_book.png");
    /**
     * A Random instance for use with the enchantment gui
     */
    private final RandomSource random = RandomSource.create();
    private BookModel bookModel;
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    private ItemStack last = ItemStack.EMPTY;

    public EnchantmentScreen(EnchantmentMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.bookModel = new BookModel(this.minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.tickBook();
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
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        for (int k = 0; k < 3; k++) {
            double d = mouseX - (i + 60);
            double e = mouseY - (j + 14 + 19 * k);
            if (d >= 0.0 && e >= 0.0 && d < 108.0 && e < 19.0 && this.menu.clickMenuButton(this.minecraft.player, k)) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, k);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(ENCHANTING_TABLE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
        this.renderBook(guiGraphics, i, j, partialTick);
        EnchantmentNames.getInstance().initSeed(this.menu.getEnchantmentSeed());
        int k = this.menu.getGoldCount();

        for (int l = 0; l < 3; l++) {
            int m = i + 60;
            int n = m + 20;
            int o = this.menu.costs[l];
            if (o == 0) {
                RenderSystem.enableBlend();
                guiGraphics.blitSprite(ENCHANTMENT_SLOT_DISABLED_SPRITE, m, j + 14 + 19 * l, 108, 19);
                RenderSystem.disableBlend();
            } else {
                String string = o + "";
                int p = 86 - this.font.width(string);
                FormattedText formattedText = EnchantmentNames.getInstance().getRandomName(this.font, p);
                int q = 6839882;
                if ((k < l + 1 || this.minecraft.player.experienceLevel < o) && !this.minecraft.player.getAbilities().instabuild) {
                    RenderSystem.enableBlend();
                    guiGraphics.blitSprite(ENCHANTMENT_SLOT_DISABLED_SPRITE, m, j + 14 + 19 * l, 108, 19);
                    guiGraphics.blitSprite(DISABLED_LEVEL_SPRITES[l], m + 1, j + 15 + 19 * l, 16, 16);
                    RenderSystem.disableBlend();
                    guiGraphics.drawWordWrap(this.font, formattedText, n, j + 16 + 19 * l, p, (q & 16711422) >> 1);
                    q = 4226832;
                } else {
                    int r = mouseX - (i + 60);
                    int s = mouseY - (j + 14 + 19 * l);
                    RenderSystem.enableBlend();
                    if (r >= 0 && s >= 0 && r < 108 && s < 19) {
                        guiGraphics.blitSprite(ENCHANTMENT_SLOT_HIGHLIGHTED_SPRITE, m, j + 14 + 19 * l, 108, 19);
                        q = 16777088;
                    } else {
                        guiGraphics.blitSprite(ENCHANTMENT_SLOT_SPRITE, m, j + 14 + 19 * l, 108, 19);
                    }

                    guiGraphics.blitSprite(ENABLED_LEVEL_SPRITES[l], m + 1, j + 15 + 19 * l, 16, 16);
                    RenderSystem.disableBlend();
                    guiGraphics.drawWordWrap(this.font, formattedText, n, j + 16 + 19 * l, p, q);
                    q = 8453920;
                }

                guiGraphics.drawString(this.font, string, n + 86 - this.font.width(string), j + 16 + 19 * l + 7, q);
            }
        }
    }

    private void renderBook(GuiGraphics guiGraphics, int x, int y, float partialTick) {
        float f = Mth.lerp(partialTick, this.oOpen, this.open);
        float g = Mth.lerp(partialTick, this.oFlip, this.flip);
        Lighting.setupForEntityInInventory();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + 33.0F, y + 31.0F, 100.0F);
        float h = 40.0F;
        guiGraphics.pose().scale(-40.0F, 40.0F, 40.0F);
        guiGraphics.pose().mulPose(Axis.XP.rotationDegrees(25.0F));
        guiGraphics.pose().translate((1.0F - f) * 0.2F, (1.0F - f) * 0.1F, (1.0F - f) * 0.25F);
        float i = -(1.0F - f) * 90.0F - 90.0F;
        guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(i));
        guiGraphics.pose().mulPose(Axis.XP.rotationDegrees(180.0F));
        float j = Mth.clamp(Mth.frac(g + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float k = Mth.clamp(Mth.frac(g + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        this.bookModel.setupAnim(0.0F, j, k, f);
        VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(this.bookModel.renderType(ENCHANTING_BOOK_LOCATION));
        this.bookModel.renderToBuffer(guiGraphics.pose(), vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY);
        guiGraphics.flush();
        guiGraphics.pose().popPose();
        Lighting.setupFor3DItems();
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
        boolean bl = this.minecraft.player.getAbilities().instabuild;
        int i = this.menu.getGoldCount();

        for (int j = 0; j < 3; j++) {
            int k = this.menu.costs[j];
            Optional<Holder.Reference<Enchantment>> optional = this.minecraft
                .level
                .registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(this.menu.enchantClue[j]);
            if (!optional.isEmpty()) {
                int l = this.menu.levelClue[j];
                int m = j + 1;
                if (this.isHovering(60, 14 + 19 * j, 108, 17, mouseX, mouseY) && k > 0 && l >= 0 && optional != null) {
                    List<Component> list = Lists.newArrayList();
                    list.add(Component.translatable("container.enchant.clue", Enchantment.getFullname(optional.get(), l)).withStyle(ChatFormatting.WHITE));
                    if (!bl) {
                        list.add(CommonComponents.EMPTY);
                        if (this.minecraft.player.experienceLevel < k) {
                            list.add(Component.translatable("container.enchant.level.requirement", this.menu.costs[j]).withStyle(ChatFormatting.RED));
                        } else {
                            MutableComponent mutableComponent;
                            if (m == 1) {
                                mutableComponent = Component.translatable("container.enchant.lapis.one");
                            } else {
                                mutableComponent = Component.translatable("container.enchant.lapis.many", m);
                            }

                            list.add(mutableComponent.withStyle(i >= m ? ChatFormatting.GRAY : ChatFormatting.RED));
                            MutableComponent mutableComponent2;
                            if (m == 1) {
                                mutableComponent2 = Component.translatable("container.enchant.level.one");
                            } else {
                                mutableComponent2 = Component.translatable("container.enchant.level.many", m);
                            }

                            list.add(mutableComponent2.withStyle(ChatFormatting.GRAY));
                        }
                    }

                    guiGraphics.renderComponentTooltip(this.font, list, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    public void tickBook() {
        ItemStack itemStack = this.menu.getSlot(0).getItem();
        if (!ItemStack.matches(itemStack, this.last)) {
            this.last = itemStack;

            do {
                this.flipT = this.flipT + (this.random.nextInt(4) - this.random.nextInt(4));
            } while (this.flip <= this.flipT + 1.0F && this.flip >= this.flipT - 1.0F);
        }

        this.time++;
        this.oFlip = this.flip;
        this.oOpen = this.open;
        boolean bl = false;

        for (int i = 0; i < 3; i++) {
            if (this.menu.costs[i] != 0) {
                bl = true;
            }
        }

        if (bl) {
            this.open += 0.2F;
        } else {
            this.open -= 0.2F;
        }

        this.open = Mth.clamp(this.open, 0.0F, 1.0F);
        float f = (this.flipT - this.flip) * 0.4F;
        float g = 0.2F;
        f = Mth.clamp(f, -0.2F, 0.2F);
        this.flipA = this.flipA + (f - this.flipA) * 0.9F;
        this.flip = this.flip + this.flipA;
    }
}
