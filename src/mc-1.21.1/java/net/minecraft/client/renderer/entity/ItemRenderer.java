package net.minecraft.client.renderer.entity;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.math.MatrixUtil;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

public class ItemRenderer implements ResourceManagerReloadListener {
    public static final ResourceLocation ENCHANTED_GLINT_ENTITY = ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_entity.png");
    public static final ResourceLocation ENCHANTED_GLINT_ITEM = ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final Set<Item> IGNORED = Sets.newHashSet(Items.AIR);
    public static final int GUI_SLOT_CENTER_X = 8;
    public static final int GUI_SLOT_CENTER_Y = 8;
    public static final int ITEM_COUNT_BLIT_OFFSET = 200;
    public static final float COMPASS_FOIL_UI_SCALE = 0.5F;
    public static final float COMPASS_FOIL_FIRST_PERSON_SCALE = 0.75F;
    public static final float COMPASS_FOIL_TEXTURE_SCALE = 0.0078125F;
    private static final ModelResourceLocation TRIDENT_MODEL = ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("trident"));
    public static final ModelResourceLocation TRIDENT_IN_HAND_MODEL = ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("trident_in_hand"));
    private static final ModelResourceLocation SPYGLASS_MODEL = ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("spyglass"));
    public static final ModelResourceLocation SPYGLASS_IN_HAND_MODEL = ModelResourceLocation.inventory(
        ResourceLocation.withDefaultNamespace("spyglass_in_hand")
    );
    private final Minecraft minecraft;
    private final ItemModelShaper itemModelShaper;
    private final TextureManager textureManager;
    private final ItemColors itemColors;
    private final BlockEntityWithoutLevelRenderer blockEntityRenderer;

    public ItemRenderer(
        Minecraft minecraft,
        TextureManager textureManager,
        ModelManager modelManager,
        ItemColors itemColors,
        BlockEntityWithoutLevelRenderer blockEntityRenderer
    ) {
        this.minecraft = minecraft;
        this.textureManager = textureManager;
        this.itemModelShaper = new ItemModelShaper(modelManager);
        this.blockEntityRenderer = blockEntityRenderer;

        for (Item item : BuiltInRegistries.ITEM) {
            if (!IGNORED.contains(item)) {
                this.itemModelShaper.register(item, ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item)));
            }
        }

        this.itemColors = itemColors;
    }

    public ItemModelShaper getItemModelShaper() {
        return this.itemModelShaper;
    }

    private void renderModelLists(BakedModel model, ItemStack stack, int combinedLight, int combinedOverlay, PoseStack poseStack, VertexConsumer buffer) {
        RandomSource randomSource = RandomSource.create();
        long l = 42L;

        for (Direction direction : Direction.values()) {
            randomSource.setSeed(42L);
            this.renderQuadList(poseStack, buffer, model.getQuads(null, direction, randomSource), stack, combinedLight, combinedOverlay);
        }

        randomSource.setSeed(42L);
        this.renderQuadList(poseStack, buffer, model.getQuads(null, null, randomSource), stack, combinedLight, combinedOverlay);
    }

    public void render(
        ItemStack itemStack,
        ItemDisplayContext displayContext,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int combinedLight,
        int combinedOverlay,
        BakedModel model
    ) {
        if (!itemStack.isEmpty()) {
            poseStack.pushPose();
            boolean bl = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED;
            if (bl) {
                if (itemStack.is(Items.TRIDENT)) {
                    model = this.itemModelShaper.getModelManager().getModel(TRIDENT_MODEL);
                } else if (itemStack.is(Items.SPYGLASS)) {
                    model = this.itemModelShaper.getModelManager().getModel(SPYGLASS_MODEL);
                }
            }

            model.getTransforms().getTransform(displayContext).apply(leftHand, poseStack);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            if (!model.isCustomRenderer() && (!itemStack.is(Items.TRIDENT) || bl)) {
                boolean bl2;
                if (displayContext != ItemDisplayContext.GUI && !displayContext.firstPerson() && itemStack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    bl2 = !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
                } else {
                    bl2 = true;
                }

                RenderType renderType = ItemBlockRenderTypes.getRenderType(itemStack, bl2);
                VertexConsumer vertexConsumer;
                if (hasAnimatedTexture(itemStack) && itemStack.hasFoil()) {
                    PoseStack.Pose pose = poseStack.last().copy();
                    if (displayContext == ItemDisplayContext.GUI) {
                        MatrixUtil.mulComponentWise(pose.pose(), 0.5F);
                    } else if (displayContext.firstPerson()) {
                        MatrixUtil.mulComponentWise(pose.pose(), 0.75F);
                    }

                    vertexConsumer = getCompassFoilBuffer(bufferSource, renderType, pose);
                } else if (bl2) {
                    vertexConsumer = getFoilBufferDirect(bufferSource, renderType, true, itemStack.hasFoil());
                } else {
                    vertexConsumer = getFoilBuffer(bufferSource, renderType, true, itemStack.hasFoil());
                }

                this.renderModelLists(model, itemStack, combinedLight, combinedOverlay, poseStack, vertexConsumer);
            } else {
                this.blockEntityRenderer.renderByItem(itemStack, displayContext, poseStack, bufferSource, combinedLight, combinedOverlay);
            }

            poseStack.popPose();
        }
    }

    private static boolean hasAnimatedTexture(ItemStack stack) {
        return stack.is(ItemTags.COMPASSES) || stack.is(Items.CLOCK);
    }

    public static VertexConsumer getArmorFoilBuffer(MultiBufferSource bufferSource, RenderType renderType, boolean hasFoil) {
        return hasFoil
            ? VertexMultiConsumer.create(bufferSource.getBuffer(RenderType.armorEntityGlint()), bufferSource.getBuffer(renderType))
            : bufferSource.getBuffer(renderType);
    }

    public static VertexConsumer getCompassFoilBuffer(MultiBufferSource bufferSource, RenderType renderType, PoseStack.Pose pose) {
        return VertexMultiConsumer.create(
            new SheetedDecalTextureGenerator(bufferSource.getBuffer(RenderType.glint()), pose, 0.0078125F), bufferSource.getBuffer(renderType)
        );
    }

    public static VertexConsumer getFoilBuffer(MultiBufferSource bufferSource, RenderType renderType, boolean isItem, boolean glint) {
        if (glint) {
            return Minecraft.useShaderTransparency() && renderType == Sheets.translucentItemSheet()
                ? VertexMultiConsumer.create(bufferSource.getBuffer(RenderType.glintTranslucent()), bufferSource.getBuffer(renderType))
                : VertexMultiConsumer.create(bufferSource.getBuffer(isItem ? RenderType.glint() : RenderType.entityGlint()), bufferSource.getBuffer(renderType));
        } else {
            return bufferSource.getBuffer(renderType);
        }
    }

    public static VertexConsumer getFoilBufferDirect(MultiBufferSource bufferSource, RenderType renderType, boolean noEntity, boolean withGlint) {
        return withGlint
            ? VertexMultiConsumer.create(
                bufferSource.getBuffer(noEntity ? RenderType.glint() : RenderType.entityGlintDirect()), bufferSource.getBuffer(renderType)
            )
            : bufferSource.getBuffer(renderType);
    }

    private void renderQuadList(PoseStack poseStack, VertexConsumer buffer, List<BakedQuad> quads, ItemStack itemStack, int combinedLight, int combinedOverlay) {
        boolean bl = !itemStack.isEmpty();
        PoseStack.Pose pose = poseStack.last();

        for (BakedQuad bakedQuad : quads) {
            int i = -1;
            if (bl && bakedQuad.isTinted()) {
                i = this.itemColors.getColor(itemStack, bakedQuad.getTintIndex());
            }

            float f = FastColor.ARGB32.alpha(i) / 255.0F;
            float g = FastColor.ARGB32.red(i) / 255.0F;
            float h = FastColor.ARGB32.green(i) / 255.0F;
            float j = FastColor.ARGB32.blue(i) / 255.0F;
            buffer.putBulkData(pose, bakedQuad, g, h, j, f, combinedLight, combinedOverlay);
        }
    }

    public BakedModel getModel(ItemStack stack, @Nullable Level level, @Nullable LivingEntity entity, int seed) {
        BakedModel bakedModel;
        if (stack.is(Items.TRIDENT)) {
            bakedModel = this.itemModelShaper.getModelManager().getModel(TRIDENT_IN_HAND_MODEL);
        } else if (stack.is(Items.SPYGLASS)) {
            bakedModel = this.itemModelShaper.getModelManager().getModel(SPYGLASS_IN_HAND_MODEL);
        } else {
            bakedModel = this.itemModelShaper.getItemModel(stack);
        }

        ClientLevel clientLevel = level instanceof ClientLevel ? (ClientLevel)level : null;
        BakedModel bakedModel2 = bakedModel.getOverrides().resolve(bakedModel, stack, clientLevel, entity, seed);
        return bakedModel2 == null ? this.itemModelShaper.getModelManager().getMissingModel() : bakedModel2;
    }

    public void renderStatic(
        ItemStack stack,
        ItemDisplayContext displayContext,
        int combinedLight,
        int combinedOverlay,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        @Nullable Level level,
        int seed
    ) {
        this.renderStatic(null, stack, displayContext, false, poseStack, bufferSource, level, combinedLight, combinedOverlay, seed);
    }

    public void renderStatic(
        @Nullable LivingEntity entity,
        ItemStack itemStack,
        ItemDisplayContext diplayContext,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        @Nullable Level level,
        int combinedLight,
        int combinedOverlay,
        int seed
    ) {
        if (!itemStack.isEmpty()) {
            BakedModel bakedModel = this.getModel(itemStack, level, entity, seed);
            this.render(itemStack, diplayContext, leftHand, poseStack, bufferSource, combinedLight, combinedOverlay, bakedModel);
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.itemModelShaper.rebuildCache();
    }
}
