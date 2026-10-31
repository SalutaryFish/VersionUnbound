package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

public class GameRenderer implements AutoCloseable {
    private static final ResourceLocation NAUSEA_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/nausea.png");
    private static final ResourceLocation BLUR_LOCATION = ResourceLocation.withDefaultNamespace("shaders/post/blur.json");
    public static final int MAX_BLUR_RADIUS = 10;
    static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEPTH_BUFFER_DEBUG = false;
    public static final float PROJECTION_Z_NEAR = 0.05F;
    private static final float GUI_Z_NEAR = 1000.0F;
    final Minecraft minecraft;
    private final ResourceManager resourceManager;
    private final RandomSource random = RandomSource.create();
    private float renderDistance;
    public final ItemInHandRenderer itemInHandRenderer;
    private final MapRenderer mapRenderer;
    private final RenderBuffers renderBuffers;
    private int confusionAnimationTick;
    private float fov;
    private float oldFov;
    private float darkenWorldAmount;
    private float darkenWorldAmountO;
    private boolean renderHand = true;
    private boolean renderBlockOutline = true;
    private long lastScreenshotAttempt;
    private boolean hasWorldScreenshot;
    private long lastActiveTime = Util.getMillis();
    private final LightTexture lightTexture;
    private final OverlayTexture overlayTexture = new OverlayTexture();
    private boolean panoramicMode;
    private float zoom = 1.0F;
    private float zoomX;
    private float zoomY;
    public static final int ITEM_ACTIVATION_ANIMATION_LENGTH = 40;
    @Nullable
    private ItemStack itemActivationItem;
    private int itemActivationTicks;
    private float itemActivationOffX;
    private float itemActivationOffY;
    @Nullable
    PostChain postEffect;
    @Nullable
    private PostChain blurEffect;
    private boolean effectActive;
    private final Camera mainCamera = new Camera();
    @Nullable
    public ShaderInstance blitShader;
    private final Map<String, ShaderInstance> shaders = Maps.newHashMap();
    @Nullable
    private static ShaderInstance positionShader;
    @Nullable
    private static ShaderInstance positionColorShader;
    @Nullable
    private static ShaderInstance positionTexShader;
    @Nullable
    private static ShaderInstance positionTexColorShader;
    @Nullable
    private static ShaderInstance particleShader;
    @Nullable
    private static ShaderInstance positionColorLightmapShader;
    @Nullable
    private static ShaderInstance positionColorTexLightmapShader;
    @Nullable
    private static ShaderInstance rendertypeSolidShader;
    @Nullable
    private static ShaderInstance rendertypeCutoutMippedShader;
    @Nullable
    private static ShaderInstance rendertypeCutoutShader;
    @Nullable
    private static ShaderInstance rendertypeTranslucentShader;
    @Nullable
    private static ShaderInstance rendertypeTranslucentMovingBlockShader;
    @Nullable
    private static ShaderInstance rendertypeArmorCutoutNoCullShader;
    @Nullable
    private static ShaderInstance rendertypeEntitySolidShader;
    @Nullable
    private static ShaderInstance rendertypeEntityCutoutShader;
    @Nullable
    private static ShaderInstance rendertypeEntityCutoutNoCullShader;
    @Nullable
    private static ShaderInstance rendertypeEntityCutoutNoCullZOffsetShader;
    @Nullable
    private static ShaderInstance rendertypeItemEntityTranslucentCullShader;
    @Nullable
    private static ShaderInstance rendertypeEntityTranslucentCullShader;
    @Nullable
    private static ShaderInstance rendertypeEntityTranslucentShader;
    @Nullable
    private static ShaderInstance rendertypeEntityTranslucentEmissiveShader;
    @Nullable
    private static ShaderInstance rendertypeEntitySmoothCutoutShader;
    @Nullable
    private static ShaderInstance rendertypeBeaconBeamShader;
    @Nullable
    private static ShaderInstance rendertypeEntityDecalShader;
    @Nullable
    private static ShaderInstance rendertypeEntityNoOutlineShader;
    @Nullable
    private static ShaderInstance rendertypeEntityShadowShader;
    @Nullable
    private static ShaderInstance rendertypeEntityAlphaShader;
    @Nullable
    private static ShaderInstance rendertypeEyesShader;
    @Nullable
    private static ShaderInstance rendertypeEnergySwirlShader;
    @Nullable
    private static ShaderInstance rendertypeBreezeWindShader;
    @Nullable
    private static ShaderInstance rendertypeLeashShader;
    @Nullable
    private static ShaderInstance rendertypeWaterMaskShader;
    @Nullable
    private static ShaderInstance rendertypeOutlineShader;
    @Nullable
    private static ShaderInstance rendertypeArmorGlintShader;
    @Nullable
    private static ShaderInstance rendertypeArmorEntityGlintShader;
    @Nullable
    private static ShaderInstance rendertypeGlintTranslucentShader;
    @Nullable
    private static ShaderInstance rendertypeGlintShader;
    @Nullable
    private static ShaderInstance rendertypeGlintDirectShader;
    @Nullable
    private static ShaderInstance rendertypeEntityGlintShader;
    @Nullable
    private static ShaderInstance rendertypeEntityGlintDirectShader;
    @Nullable
    private static ShaderInstance rendertypeTextShader;
    @Nullable
    private static ShaderInstance rendertypeTextBackgroundShader;
    @Nullable
    private static ShaderInstance rendertypeTextIntensityShader;
    @Nullable
    private static ShaderInstance rendertypeTextSeeThroughShader;
    @Nullable
    private static ShaderInstance rendertypeTextBackgroundSeeThroughShader;
    @Nullable
    private static ShaderInstance rendertypeTextIntensitySeeThroughShader;
    @Nullable
    private static ShaderInstance rendertypeLightningShader;
    @Nullable
    private static ShaderInstance rendertypeTripwireShader;
    @Nullable
    private static ShaderInstance rendertypeEndPortalShader;
    @Nullable
    private static ShaderInstance rendertypeEndGatewayShader;
    @Nullable
    private static ShaderInstance rendertypeCloudsShader;
    @Nullable
    private static ShaderInstance rendertypeLinesShader;
    @Nullable
    private static ShaderInstance rendertypeCrumblingShader;
    @Nullable
    private static ShaderInstance rendertypeGuiShader;
    @Nullable
    private static ShaderInstance rendertypeGuiOverlayShader;
    @Nullable
    private static ShaderInstance rendertypeGuiTextHighlightShader;
    @Nullable
    private static ShaderInstance rendertypeGuiGhostRecipeOverlayShader;

    public GameRenderer(Minecraft minecraft, ItemInHandRenderer itemInHandRenderer, ResourceManager resourceManager, RenderBuffers renderBuffers) {
        this.minecraft = minecraft;
        this.resourceManager = resourceManager;
        this.itemInHandRenderer = itemInHandRenderer;
        this.mapRenderer = new MapRenderer(minecraft.getTextureManager(), minecraft.getMapDecorationTextures());
        this.lightTexture = new LightTexture(this, minecraft);
        this.renderBuffers = renderBuffers;
        this.postEffect = null;
    }

    @Override
    public void close() {
        this.lightTexture.close();
        this.mapRenderer.close();
        this.overlayTexture.close();
        this.shutdownEffect();
        this.shutdownShaders();
        if (this.blurEffect != null) {
            this.blurEffect.close();
        }

        if (this.blitShader != null) {
            this.blitShader.close();
        }
    }

    public void setRenderHand(boolean renderHand) {
        this.renderHand = renderHand;
    }

    public void setRenderBlockOutline(boolean renderBlockOutline) {
        this.renderBlockOutline = renderBlockOutline;
    }

    public void setPanoramicMode(boolean panoramicMode) {
        this.panoramicMode = panoramicMode;
    }

    public boolean isPanoramicMode() {
        return this.panoramicMode;
    }

    public void shutdownEffect() {
        if (this.postEffect != null) {
            this.postEffect.close();
        }

        this.postEffect = null;
    }

    public void togglePostEffect() {
        this.effectActive = !this.effectActive;
    }

    /**
     * What shader to use when spectating this entity
     */
    public void checkEntityPostEffect(@Nullable Entity entity) {
        if (this.postEffect != null) {
            this.postEffect.close();
        }

        this.postEffect = null;
        if (entity instanceof Creeper) {
            this.loadEffect(ResourceLocation.withDefaultNamespace("shaders/post/creeper.json"));
        } else if (entity instanceof Spider) {
            this.loadEffect(ResourceLocation.withDefaultNamespace("shaders/post/spider.json"));
        } else if (entity instanceof EnderMan) {
            this.loadEffect(ResourceLocation.withDefaultNamespace("shaders/post/invert.json"));
        }
    }

    private void loadEffect(ResourceLocation resourceLocation) {
        if (this.postEffect != null) {
            this.postEffect.close();
        }

        try {
            this.postEffect = new PostChain(this.minecraft.getTextureManager(), this.resourceManager, this.minecraft.getMainRenderTarget(), resourceLocation);
            this.postEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
            this.effectActive = true;
        } catch (IOException iOException) {
            LOGGER.warn("Failed to load shader: {}", resourceLocation, iOException);
            this.effectActive = false;
        } catch (JsonSyntaxException jsonSyntaxException) {
            LOGGER.warn("Failed to parse shader: {}", resourceLocation, jsonSyntaxException);
            this.effectActive = false;
        }
    }

    private void loadBlurEffect(ResourceProvider resourceProvider) {
        if (this.blurEffect != null) {
            this.blurEffect.close();
        }

        try {
            this.blurEffect = new PostChain(this.minecraft.getTextureManager(), resourceProvider, this.minecraft.getMainRenderTarget(), BLUR_LOCATION);
            this.blurEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
        } catch (IOException iOException) {
            LOGGER.warn("Failed to load shader: {}", BLUR_LOCATION, iOException);
        } catch (JsonSyntaxException jsonSyntaxException) {
            LOGGER.warn("Failed to parse shader: {}", BLUR_LOCATION, jsonSyntaxException);
        }
    }

    public void processBlurEffect(float partialTick) {
        float f = this.minecraft.options.getMenuBackgroundBlurriness();
        if (this.blurEffect != null && f >= 1.0F) {
            this.blurEffect.setUniform("Radius", f);
            this.blurEffect.process(partialTick);
        }
    }

    public PreparableReloadListener createReloadListener() {
        return new SimplePreparableReloadListener<GameRenderer.ResourceCache>() {
            protected GameRenderer.ResourceCache prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                Map<ResourceLocation, Resource> map = resourceManager.listResources(
                    "shaders",
                    resourceLocation -> {
                        String string = resourceLocation.getPath();
                        return string.endsWith(".json")
                            || string.endsWith(Program.Type.FRAGMENT.getExtension())
                            || string.endsWith(Program.Type.VERTEX.getExtension())
                            || string.endsWith(".glsl");
                    }
                );
                Map<ResourceLocation, Resource> map2 = new HashMap<>();
                map.forEach((resourceLocation, resource) -> {
                    try (InputStream inputStream = resource.open()) {
                        byte[] bs = inputStream.readAllBytes();
                        map2.put(resourceLocation, new Resource(resource.source(), () -> new ByteArrayInputStream(bs)));
                    } catch (Exception exception) {
                        GameRenderer.LOGGER.warn("Failed to read resource {}", resourceLocation, exception);
                    }
                });
                return new GameRenderer.ResourceCache(resourceManager, map2);
            }

            protected void apply(GameRenderer.ResourceCache resourceCache, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                GameRenderer.this.reloadShaders(resourceCache);
                if (GameRenderer.this.postEffect != null) {
                    GameRenderer.this.postEffect.close();
                }

                GameRenderer.this.postEffect = null;
                GameRenderer.this.checkEntityPostEffect(GameRenderer.this.minecraft.getCameraEntity());
            }

            @Override
            public String getName() {
                return "Shader Loader";
            }
        };
    }

    public void preloadUiShader(ResourceProvider resourceProvider) {
        if (this.blitShader != null) {
            throw new RuntimeException("Blit shader already preloaded");
        }

        try {
            this.blitShader = new ShaderInstance(resourceProvider, "blit_screen", DefaultVertexFormat.BLIT_SCREEN);
        } catch (IOException iOException) {
            throw new RuntimeException("could not preload blit shader", iOException);
        }

        rendertypeGuiShader = this.preloadShader(resourceProvider, "rendertype_gui", DefaultVertexFormat.POSITION_COLOR);
        rendertypeGuiOverlayShader = this.preloadShader(resourceProvider, "rendertype_gui_overlay", DefaultVertexFormat.POSITION_COLOR);
        positionShader = this.preloadShader(resourceProvider, "position", DefaultVertexFormat.POSITION);
        positionColorShader = this.preloadShader(resourceProvider, "position_color", DefaultVertexFormat.POSITION_COLOR);
        positionTexShader = this.preloadShader(resourceProvider, "position_tex", DefaultVertexFormat.POSITION_TEX);
        positionTexColorShader = this.preloadShader(resourceProvider, "position_tex_color", DefaultVertexFormat.POSITION_TEX_COLOR);
        rendertypeTextShader = this.preloadShader(resourceProvider, "rendertype_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    private ShaderInstance preloadShader(ResourceProvider resourceProvider, String name, VertexFormat format) {
        try {
            ShaderInstance shaderInstance = new ShaderInstance(resourceProvider, name, format);
            this.shaders.put(name, shaderInstance);
            return shaderInstance;
        } catch (Exception exception) {
            throw new IllegalStateException("could not preload shader " + name, exception);
        }
    }

    void reloadShaders(ResourceProvider resourceProvider) {
        RenderSystem.assertOnRenderThread();
        List<Program> list = Lists.newArrayList();
        list.addAll(Program.Type.FRAGMENT.getPrograms().values());
        list.addAll(Program.Type.VERTEX.getPrograms().values());
        list.forEach(Program::close);
        List<Pair<ShaderInstance, Consumer<ShaderInstance>>> list2 = Lists.newArrayListWithCapacity(this.shaders.size());

        try {
            list2.add(Pair.of(new ShaderInstance(resourceProvider, "particle", DefaultVertexFormat.PARTICLE), shader -> particleShader = shader));
            list2.add(Pair.of(new ShaderInstance(resourceProvider, "position", DefaultVertexFormat.POSITION), shader -> positionShader = shader));
            list2.add(
                Pair.of(new ShaderInstance(resourceProvider, "position_color", DefaultVertexFormat.POSITION_COLOR), shader -> positionColorShader = shader)
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "position_color_lightmap", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP),
                    shader -> positionColorLightmapShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "position_color_tex_lightmap", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    shader -> positionColorTexLightmapShader = shader
                )
            );
            list2.add(Pair.of(new ShaderInstance(resourceProvider, "position_tex", DefaultVertexFormat.POSITION_TEX), shader -> positionTexShader = shader));
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "position_tex_color", DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> positionTexColorShader = shader
                )
            );
            list2.add(Pair.of(new ShaderInstance(resourceProvider, "rendertype_solid", DefaultVertexFormat.BLOCK), shader -> rendertypeSolidShader = shader));
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_cutout_mipped", DefaultVertexFormat.BLOCK),
                    shader -> rendertypeCutoutMippedShader = shader
                )
            );
            list2.add(Pair.of(new ShaderInstance(resourceProvider, "rendertype_cutout", DefaultVertexFormat.BLOCK), shader -> rendertypeCutoutShader = shader));
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_translucent", DefaultVertexFormat.BLOCK), shader -> rendertypeTranslucentShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_translucent_moving_block", DefaultVertexFormat.BLOCK),
                    shader -> rendertypeTranslucentMovingBlockShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_armor_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeArmorCutoutNoCullShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_solid", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntitySolidShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_cutout", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityCutoutShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityCutoutNoCullShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_cutout_no_cull_z_offset", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityCutoutNoCullZOffsetShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_item_entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeItemEntityTranslucentCullShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityTranslucentCullShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_translucent", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityTranslucentShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_translucent_emissive", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityTranslucentEmissiveShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_smooth_cutout", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntitySmoothCutoutShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_beacon_beam", DefaultVertexFormat.BLOCK), shader -> rendertypeBeaconBeamShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_decal", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityDecalShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_no_outline", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityNoOutlineShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_shadow", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityShadowShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_alpha", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEntityAlphaShader = shader
                )
            );
            list2.add(Pair.of(new ShaderInstance(resourceProvider, "rendertype_eyes", DefaultVertexFormat.NEW_ENTITY), shader -> rendertypeEyesShader = shader));
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_energy_swirl", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeEnergySwirlShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_leash", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP),
                    shader -> rendertypeLeashShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_water_mask", DefaultVertexFormat.POSITION), shader -> rendertypeWaterMaskShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_outline", DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> rendertypeOutlineShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_armor_entity_glint", DefaultVertexFormat.POSITION_TEX),
                    shader -> rendertypeArmorEntityGlintShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_glint_translucent", DefaultVertexFormat.POSITION_TEX),
                    shader -> rendertypeGlintTranslucentShader = shader
                )
            );
            list2.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_glint", DefaultVertexFormat.POSITION_TEX), shader -> rendertypeGlintShader = shader)
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_glint", DefaultVertexFormat.POSITION_TEX),
                    shader -> rendertypeEntityGlintShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_glint_direct", DefaultVertexFormat.POSITION_TEX),
                    shader -> rendertypeEntityGlintDirectShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    shader -> rendertypeTextShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_background", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP),
                    shader -> rendertypeTextBackgroundShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_intensity", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    shader -> rendertypeTextIntensityShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_see_through", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    shader -> rendertypeTextSeeThroughShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_background_see_through", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP),
                    shader -> rendertypeTextBackgroundSeeThroughShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_intensity_see_through", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    shader -> rendertypeTextIntensitySeeThroughShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_lightning", DefaultVertexFormat.POSITION_COLOR),
                    shader -> rendertypeLightningShader = shader
                )
            );
            list2.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_tripwire", DefaultVertexFormat.BLOCK), shader -> rendertypeTripwireShader = shader)
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_end_portal", DefaultVertexFormat.POSITION), shader -> rendertypeEndPortalShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_end_gateway", DefaultVertexFormat.POSITION), shader -> rendertypeEndGatewayShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_clouds", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                    shader -> rendertypeCloudsShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> rendertypeLinesShader = shader
                )
            );
            list2.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_crumbling", DefaultVertexFormat.BLOCK), shader -> rendertypeCrumblingShader = shader)
            );
            list2.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_gui", DefaultVertexFormat.POSITION_COLOR), shader -> rendertypeGuiShader = shader)
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_gui_overlay", DefaultVertexFormat.POSITION_COLOR),
                    shader -> rendertypeGuiOverlayShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_gui_text_highlight", DefaultVertexFormat.POSITION_COLOR),
                    shader -> rendertypeGuiTextHighlightShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_gui_ghost_recipe_overlay", DefaultVertexFormat.POSITION_COLOR),
                    shader -> rendertypeGuiGhostRecipeOverlayShader = shader
                )
            );
            list2.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_breeze_wind", DefaultVertexFormat.NEW_ENTITY),
                    shader -> rendertypeBreezeWindShader = shader
                )
            );
            this.loadBlurEffect(resourceProvider);
        } catch (IOException iOException) {
            list2.forEach(pair -> pair.getFirst().close());
            throw new RuntimeException("could not reload shaders", iOException);
        }

        this.shutdownShaders();
        list2.forEach(pair -> {
            ShaderInstance shaderInstance = pair.getFirst();
            this.shaders.put(shaderInstance.getName(), shaderInstance);
            pair.getSecond().accept(shaderInstance);
        });
    }

    private void shutdownShaders() {
        RenderSystem.assertOnRenderThread();
        this.shaders.values().forEach(ShaderInstance::close);
        this.shaders.clear();
    }

    @Nullable
    public ShaderInstance getShader(@Nullable String name) {
        return name == null ? null : this.shaders.get(name);
    }

    public void tick() {
        this.tickFov();
        this.lightTexture.tick();
        if (this.minecraft.getCameraEntity() == null) {
            this.minecraft.setCameraEntity(this.minecraft.player);
        }

        this.mainCamera.tick();
        this.itemInHandRenderer.tick();
        this.confusionAnimationTick++;
        if (this.minecraft.level.tickRateManager().runsNormally()) {
            this.minecraft.levelRenderer.tickRain(this.mainCamera);
            this.darkenWorldAmountO = this.darkenWorldAmount;
            if (this.minecraft.gui.getBossOverlay().shouldDarkenScreen()) {
                this.darkenWorldAmount += 0.05F;
                if (this.darkenWorldAmount > 1.0F) {
                    this.darkenWorldAmount = 1.0F;
                }
            } else if (this.darkenWorldAmount > 0.0F) {
                this.darkenWorldAmount -= 0.0125F;
            }

            if (this.itemActivationTicks > 0) {
                this.itemActivationTicks--;
                if (this.itemActivationTicks == 0) {
                    this.itemActivationItem = null;
                }
            }
        }
    }

    @Nullable
    public PostChain currentEffect() {
        return this.postEffect;
    }

    public void resize(int width, int height) {
        if (this.postEffect != null) {
            this.postEffect.resize(width, height);
        }

        if (this.blurEffect != null) {
            this.blurEffect.resize(width, height);
        }

        this.minecraft.levelRenderer.resize(width, height);
    }

    /**
     * Gets the block or object that is being moused over.
     */
    public void pick(float partialTicks) {
        Entity entity = this.minecraft.getCameraEntity();
        if (entity != null) {
            if (this.minecraft.level != null && this.minecraft.player != null) {
                this.minecraft.getProfiler().push("pick");
                double d = this.minecraft.player.blockInteractionRange();
                double e = this.minecraft.player.entityInteractionRange();
                HitResult hitResult = this.pick(entity, d, e, partialTicks);
                this.minecraft.hitResult = hitResult;
                this.minecraft.crosshairPickEntity = hitResult instanceof EntityHitResult entityHitResult ? entityHitResult.getEntity() : null;
                this.minecraft.getProfiler().pop();
            }
        }
    }

    private HitResult pick(Entity entity, double blockInteractionRange, double entityInteractionRange, float partialTick) {
        double d = Math.max(blockInteractionRange, entityInteractionRange);
        double e = Mth.square(d);
        Vec3 vec3 = entity.getEyePosition(partialTick);
        HitResult hitResult = entity.pick(d, partialTick, false);
        double f = hitResult.getLocation().distanceToSqr(vec3);
        if (hitResult.getType() != HitResult.Type.MISS) {
            e = f;
            d = Math.sqrt(e);
        }

        Vec3 vec32 = entity.getViewVector(partialTick);
        Vec3 vec33 = vec3.add(vec32.x * d, vec32.y * d, vec32.z * d);
        float g = 1.0F;
        AABB aABB = entity.getBoundingBox().expandTowards(vec32.scale(d)).inflate(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
            entity, vec3, vec33, aABB, entityx -> !entityx.isSpectator() && entityx.isPickable(), e
        );
        return entityHitResult != null && entityHitResult.getLocation().distanceToSqr(vec3) < f
            ? filterHitResult(entityHitResult, vec3, entityInteractionRange)
            : filterHitResult(hitResult, vec3, blockInteractionRange);
    }

    private static HitResult filterHitResult(HitResult hitResult, Vec3 pos, double blockInteractionRange) {
        Vec3 vec3 = hitResult.getLocation();
        if (!vec3.closerThan(pos, blockInteractionRange)) {
            Vec3 vec32 = hitResult.getLocation();
            Direction direction = Direction.getNearest(vec32.x - pos.x, vec32.y - pos.y, vec32.z - pos.z);
            return BlockHitResult.miss(vec32, direction, BlockPos.containing(vec32));
        } else {
            return hitResult;
        }
    }

    private void tickFov() {
        float f = 1.0F;
        if (this.minecraft.getCameraEntity() instanceof AbstractClientPlayer abstractClientPlayer) {
            f = abstractClientPlayer.getFieldOfViewModifier();
        }

        this.oldFov = this.fov;
        this.fov = this.fov + (f - this.fov) * 0.5F;
        if (this.fov > 1.5F) {
            this.fov = 1.5F;
        }

        if (this.fov < 0.1F) {
            this.fov = 0.1F;
        }
    }

    private double getFov(Camera activeRenderInfo, float partialTicks, boolean useFOVSetting) {
        if (this.panoramicMode) {
            return 90.0;
        }

        double d = 70.0;
        if (useFOVSetting) {
            d = this.minecraft.options.fov().get().intValue();
            d *= Mth.lerp(partialTicks, this.oldFov, this.fov);
        }

        if (activeRenderInfo.getEntity() instanceof LivingEntity && ((LivingEntity)activeRenderInfo.getEntity()).isDeadOrDying()) {
            float f = Math.min(((LivingEntity)activeRenderInfo.getEntity()).deathTime + partialTicks, 20.0F);
            d /= (1.0F - 500.0F / (f + 500.0F)) * 2.0F + 1.0F;
        }

        FogType fogType = activeRenderInfo.getFluidInCamera();
        if (fogType == FogType.LAVA || fogType == FogType.WATER) {
            d *= Mth.lerp(this.minecraft.options.fovEffectScale().get(), 1.0, 0.85714287F);
        }

        return d;
    }

    private void bobHurt(PoseStack poseStack, float partialTicks) {
        if (this.minecraft.getCameraEntity() instanceof LivingEntity livingEntity) {
            float f = livingEntity.hurtTime - partialTicks;
            if (livingEntity.isDeadOrDying()) {
                float g = Math.min(livingEntity.deathTime + partialTicks, 20.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (g + 200.0F)));
            }

            if (f < 0.0F) {
                return;
            }

            f /= livingEntity.hurtDuration;
            f = Mth.sin(f * f * f * f * (float) Math.PI);
            float g = livingEntity.getHurtDir();
            poseStack.mulPose(Axis.YP.rotationDegrees(-g));
            float h = (float)(-f * 14.0 * this.minecraft.options.damageTiltStrength().get());
            poseStack.mulPose(Axis.ZP.rotationDegrees(h));
            poseStack.mulPose(Axis.YP.rotationDegrees(g));
        }
    }

    private void bobView(PoseStack poseStack, float partialTicks) {
        if (this.minecraft.getCameraEntity() instanceof Player) {
            Player player = (Player)this.minecraft.getCameraEntity();
            float f = player.walkDist - player.walkDistO;
            float g = -(player.walkDist + f * partialTicks);
            float h = Mth.lerp(partialTicks, player.oBob, player.bob);
            poseStack.translate(Mth.sin(g * (float) Math.PI) * h * 0.5F, -Math.abs(Mth.cos(g * (float) Math.PI) * h), 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(g * (float) Math.PI) * h * 3.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(g * (float) Math.PI - 0.2F) * h) * 5.0F));
        }
    }

    public void renderZoomed(float zoom, float zoomX, float zoomY) {
        this.zoom = zoom;
        this.zoomX = zoomX;
        this.zoomY = zoomY;
        this.setRenderBlockOutline(false);
        this.setRenderHand(false);
        this.renderLevel(DeltaTracker.ZERO);
        this.zoom = 1.0F;
    }

    private void renderItemInHand(Camera camera, float partialTick, Matrix4f projectionMatrix) {
        if (!this.panoramicMode) {
            this.resetProjectionMatrix(this.getProjectionMatrix(this.getFov(camera, partialTick, false)));
            PoseStack poseStack = new PoseStack();
            poseStack.pushPose();
            poseStack.mulPose(projectionMatrix.invert(new Matrix4f()));
            Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
            matrix4fStack.pushMatrix().mul(projectionMatrix);
            RenderSystem.applyModelViewMatrix();
            this.bobHurt(poseStack, partialTick);
            if (this.minecraft.options.bobView().get()) {
                this.bobView(poseStack, partialTick);
            }

            boolean bl = this.minecraft.getCameraEntity() instanceof LivingEntity && ((LivingEntity)this.minecraft.getCameraEntity()).isSleeping();
            if (this.minecraft.options.getCameraType().isFirstPerson()
                && !bl
                && !this.minecraft.options.hideGui
                && this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
                this.lightTexture.turnOnLightLayer();
                this.itemInHandRenderer
                    .renderHandsWithItems(
                        partialTick,
                        poseStack,
                        this.renderBuffers.bufferSource(),
                        this.minecraft.player,
                        this.minecraft.getEntityRenderDispatcher().getPackedLightCoords(this.minecraft.player, partialTick)
                    );
                this.lightTexture.turnOffLightLayer();
            }

            matrix4fStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            poseStack.popPose();
            if (this.minecraft.options.getCameraType().isFirstPerson() && !bl) {
                ScreenEffectRenderer.renderScreenEffect(this.minecraft, poseStack);
            }
        }
    }

    public void resetProjectionMatrix(Matrix4f matrix) {
        RenderSystem.setProjectionMatrix(matrix, VertexSorting.DISTANCE_TO_ORIGIN);
    }

    public Matrix4f getProjectionMatrix(double fov) {
        Matrix4f matrix4f = new Matrix4f();
        if (this.zoom != 1.0F) {
            matrix4f.translate(this.zoomX, -this.zoomY, 0.0F);
            matrix4f.scale(this.zoom, this.zoom, 1.0F);
        }

        return matrix4f.perspective(
            (float)(fov * (float) (Math.PI / 180.0)),
            (float)this.minecraft.getWindow().getWidth() / this.minecraft.getWindow().getHeight(),
            0.05F,
            this.getDepthFar()
        );
    }

    public float getDepthFar() {
        return this.renderDistance * 4.0F;
    }

    public static float getNightVisionScale(LivingEntity livingEntity, float nanoTime) {
        MobEffectInstance mobEffectInstance = livingEntity.getEffect(MobEffects.NIGHT_VISION);
        return !mobEffectInstance.endsWithin(200) ? 1.0F : 0.7F + Mth.sin((mobEffectInstance.getDuration() - nanoTime) * (float) Math.PI * 0.2F) * 0.3F;
    }

    public void render(DeltaTracker deltaTracker, boolean renderLevel) {
        if (!this.minecraft.isWindowActive()
            && this.minecraft.options.pauseOnLostFocus
            && (!this.minecraft.options.touchscreen().get() || !this.minecraft.mouseHandler.isRightPressed())) {
            if (Util.getMillis() - this.lastActiveTime > 500L) {
                this.minecraft.pauseGame(false);
            }
        } else {
            this.lastActiveTime = Util.getMillis();
        }

        if (!this.minecraft.noRender) {
            boolean bl = this.minecraft.isGameLoadFinished();
            int i = (int)(this.minecraft.mouseHandler.xpos() * this.minecraft.getWindow().getGuiScaledWidth() / this.minecraft.getWindow().getScreenWidth());
            int j = (int)(this.minecraft.mouseHandler.ypos() * this.minecraft.getWindow().getGuiScaledHeight() / this.minecraft.getWindow().getScreenHeight());
            RenderSystem.viewport(0, 0, this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
            if (bl && renderLevel && this.minecraft.level != null) {
                this.minecraft.getProfiler().push("level");
                this.renderLevel(deltaTracker);
                this.tryTakeScreenshotIfNeeded();
                this.minecraft.levelRenderer.doEntityOutline();
                if (this.postEffect != null && this.effectActive) {
                    RenderSystem.disableBlend();
                    RenderSystem.disableDepthTest();
                    RenderSystem.resetTextureMatrix();
                    this.postEffect.process(deltaTracker.getGameTimeDeltaTicks());
                }

                this.minecraft.getMainRenderTarget().bindWrite(true);
            }

            Window window = this.minecraft.getWindow();
            RenderSystem.clear(256, Minecraft.ON_OSX);
            Matrix4f matrix4f = new Matrix4f()
                .setOrtho(0.0F, (float)(window.getWidth() / window.getGuiScale()), (float)(window.getHeight() / window.getGuiScale()), 0.0F, 1000.0F, 21000.0F);
            RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
            Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
            matrix4fStack.pushMatrix();
            matrix4fStack.translation(0.0F, 0.0F, -11000.0F);
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            GuiGraphics guiGraphics = new GuiGraphics(this.minecraft, this.renderBuffers.bufferSource());
            if (bl && renderLevel && this.minecraft.level != null) {
                this.minecraft.getProfiler().popPush("gui");
                if (this.minecraft.player != null) {
                    float f = Mth.lerp(
                        deltaTracker.getGameTimeDeltaPartialTick(false),
                        this.minecraft.player.oSpinningEffectIntensity,
                        this.minecraft.player.spinningEffectIntensity
                    );
                    float g = this.minecraft.options.screenEffectScale().get().floatValue();
                    if (f > 0.0F && this.minecraft.player.hasEffect(MobEffects.CONFUSION) && g < 1.0F) {
                        this.renderConfusionOverlay(guiGraphics, f * (1.0F - g));
                    }
                }

                if (!this.minecraft.options.hideGui) {
                    this.renderItemActivationAnimation(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
                }

                this.minecraft.gui.render(guiGraphics, deltaTracker);
                RenderSystem.clear(256, Minecraft.ON_OSX);
                this.minecraft.getProfiler().pop();
            }

            if (this.minecraft.getOverlay() != null) {
                try {
                    this.minecraft.getOverlay().render(guiGraphics, i, j, deltaTracker.getRealtimeDeltaTicks());
                } catch (Throwable throwable) {
                    CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering overlay");
                    CrashReportCategory crashReportCategory = crashReport.addCategory("Overlay render details");
                    crashReportCategory.setDetail("Overlay name", () -> this.minecraft.getOverlay().getClass().getCanonicalName());
                    throw new ReportedException(crashReport);
                }
            } else if (bl && this.minecraft.screen != null) {
                try {
                    this.minecraft.screen.renderWithTooltip(guiGraphics, i, j, deltaTracker.getRealtimeDeltaTicks());
                } catch (Throwable throwable) {
                    CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering screen");
                    CrashReportCategory crashReportCategory = crashReport.addCategory("Screen render details");
                    crashReportCategory.setDetail("Screen name", () -> this.minecraft.screen.getClass().getCanonicalName());
                    crashReportCategory.setDetail(
                        "Mouse location",
                        () -> String.format(
                            Locale.ROOT, "Scaled: (%d, %d). Absolute: (%f, %f)", i, j, this.minecraft.mouseHandler.xpos(), this.minecraft.mouseHandler.ypos()
                        )
                    );
                    crashReportCategory.setDetail(
                        "Screen size",
                        () -> String.format(
                            Locale.ROOT,
                            "Scaled: (%d, %d). Absolute: (%d, %d). Scale factor of %f",
                            this.minecraft.getWindow().getGuiScaledWidth(),
                            this.minecraft.getWindow().getGuiScaledHeight(),
                            this.minecraft.getWindow().getWidth(),
                            this.minecraft.getWindow().getHeight(),
                            this.minecraft.getWindow().getGuiScale()
                        )
                    );
                    throw new ReportedException(crashReport);
                }

                try {
                    if (this.minecraft.screen != null) {
                        this.minecraft.screen.handleDelayedNarration();
                    }
                } catch (Throwable throwable) {
                    CrashReport crashReport = CrashReport.forThrowable(throwable, "Narrating screen");
                    CrashReportCategory crashReportCategory = crashReport.addCategory("Screen details");
                    crashReportCategory.setDetail("Screen name", () -> this.minecraft.screen.getClass().getCanonicalName());
                    throw new ReportedException(crashReport);
                }
            }

            if (bl && renderLevel && this.minecraft.level != null) {
                this.minecraft.gui.renderSavingIndicator(guiGraphics, deltaTracker);
            }

            if (bl) {
                this.minecraft.getProfiler().push("toasts");
                this.minecraft.getToasts().render(guiGraphics);
                this.minecraft.getProfiler().pop();
            }

            guiGraphics.flush();
            matrix4fStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private void tryTakeScreenshotIfNeeded() {
        if (!this.hasWorldScreenshot && this.minecraft.isLocalServer()) {
            long l = Util.getMillis();
            if (l - this.lastScreenshotAttempt >= 1000L) {
                this.lastScreenshotAttempt = l;
                IntegratedServer integratedServer = this.minecraft.getSingleplayerServer();
                if (integratedServer != null && !integratedServer.isStopped()) {
                    integratedServer.getWorldScreenshotFile().ifPresent(path -> {
                        if (Files.isRegularFile(path)) {
                            this.hasWorldScreenshot = true;
                        } else {
                            this.takeAutoScreenshot(path);
                        }
                    });
                }
            }
        }
    }

    private void takeAutoScreenshot(Path path) {
        if (this.minecraft.levelRenderer.countRenderedSections() > 10 && this.minecraft.levelRenderer.hasRenderedAllSections()) {
            NativeImage nativeImage = Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget());
            Util.ioPool().execute(() -> {
                int i = nativeImage.getWidth();
                int j = nativeImage.getHeight();
                int k = 0;
                int l = 0;
                if (i > j) {
                    k = (i - j) / 2;
                    i = j;
                } else {
                    l = (j - i) / 2;
                    j = i;
                }

                try (NativeImage nativeImage2 = new NativeImage(64, 64, false)) {
                    nativeImage.resizeSubRectTo(k, l, i, j, nativeImage2);
                    nativeImage2.writeToFile(path);
                } catch (IOException iOException) {
                    LOGGER.warn("Couldn't save auto screenshot", iOException);
                } finally {
                    nativeImage.close();
                }
            });
        }
    }

    private boolean shouldRenderBlockOutline() {
        if (!this.renderBlockOutline) {
            return false;
        }

        Entity entity = this.minecraft.getCameraEntity();
        boolean bl = entity instanceof Player && !this.minecraft.options.hideGui;
        if (bl && !((Player)entity).getAbilities().mayBuild) {
            ItemStack itemStack = ((LivingEntity)entity).getMainHandItem();
            HitResult hitResult = this.minecraft.hitResult;
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
                BlockState blockState = this.minecraft.level.getBlockState(blockPos);
                if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
                    bl = blockState.getMenuProvider(this.minecraft.level, blockPos) != null;
                } else {
                    BlockInWorld blockInWorld = new BlockInWorld(this.minecraft.level, blockPos, false);
                    Registry<Block> registry = this.minecraft.level.registryAccess().registryOrThrow(Registries.BLOCK);
                    bl = !itemStack.isEmpty()
                        && (itemStack.canBreakBlockInAdventureMode(blockInWorld) || itemStack.canPlaceOnBlockInAdventureMode(blockInWorld));
                }
            }
        }

        return bl;
    }

    public void renderLevel(DeltaTracker deltaTracker) {
        float f = deltaTracker.getGameTimeDeltaPartialTick(true);
        this.lightTexture.updateLightTexture(f);
        if (this.minecraft.getCameraEntity() == null) {
            this.minecraft.setCameraEntity(this.minecraft.player);
        }

        this.pick(f);
        this.minecraft.getProfiler().push("center");
        boolean bl = this.shouldRenderBlockOutline();
        this.minecraft.getProfiler().popPush("camera");
        Camera camera = this.mainCamera;
        Entity entity = this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity();
        float g = this.minecraft.level.tickRateManager().isEntityFrozen(entity) ? 1.0F : f;
        camera.setup(
            this.minecraft.level, entity, !this.minecraft.options.getCameraType().isFirstPerson(), this.minecraft.options.getCameraType().isMirrored(), g
        );
        this.renderDistance = this.minecraft.options.getEffectiveRenderDistance() * 16;
        double d = this.getFov(camera, f, true);
        Matrix4f matrix4f = this.getProjectionMatrix(d);
        PoseStack poseStack = new PoseStack();
        this.bobHurt(poseStack, camera.getPartialTickTime());
        if (this.minecraft.options.bobView().get()) {
            this.bobView(poseStack, camera.getPartialTickTime());
        }

        matrix4f.mul(poseStack.last().pose());
        float h = this.minecraft.options.screenEffectScale().get().floatValue();
        float i = Mth.lerp(f, this.minecraft.player.oSpinningEffectIntensity, this.minecraft.player.spinningEffectIntensity) * (h * h);
        if (i > 0.0F) {
            int j = this.minecraft.player.hasEffect(MobEffects.CONFUSION) ? 7 : 20;
            float k = 5.0F / (i * i + 5.0F) - i * 0.04F;
            k *= k;
            Vector3f vector3f = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            float l = (this.confusionAnimationTick + f) * j * (float) (Math.PI / 180.0);
            matrix4f.rotate(l, vector3f);
            matrix4f.scale(1.0F / k, 1.0F, 1.0F);
            matrix4f.rotate(-l, vector3f);
        }

        this.resetProjectionMatrix(matrix4f);
        Quaternionf quaternionf = camera.rotation().conjugate(new Quaternionf());
        Matrix4f matrix4f2 = new Matrix4f().rotation(quaternionf);
        this.minecraft
            .levelRenderer
            .prepareCullFrustum(camera.getPosition(), matrix4f2, this.getProjectionMatrix(Math.max(d, this.minecraft.options.fov().get().intValue())));
        this.minecraft.levelRenderer.renderLevel(deltaTracker, bl, camera, this, this.lightTexture, matrix4f2, matrix4f);
        this.minecraft.getProfiler().popPush("hand");
        if (this.renderHand) {
            RenderSystem.clear(256, Minecraft.ON_OSX);
            this.renderItemInHand(camera, f, matrix4f2);
        }

        this.minecraft.getProfiler().pop();
    }

    public void resetData() {
        this.itemActivationItem = null;
        this.mapRenderer.resetData();
        this.mainCamera.reset();
        this.hasWorldScreenshot = false;
    }

    public MapRenderer getMapRenderer() {
        return this.mapRenderer;
    }

    public void displayItemActivation(ItemStack stack) {
        this.itemActivationItem = stack;
        this.itemActivationTicks = 40;
        this.itemActivationOffX = this.random.nextFloat() * 2.0F - 1.0F;
        this.itemActivationOffY = this.random.nextFloat() * 2.0F - 1.0F;
    }

    private void renderItemActivationAnimation(GuiGraphics guiGraphics, float partialTick) {
        if (this.itemActivationItem != null && this.itemActivationTicks > 0) {
            int i = 40 - this.itemActivationTicks;
            float f = (i + partialTick) / 40.0F;
            float g = f * f;
            float h = f * g;
            float j = 10.25F * h * g - 24.95F * g * g + 25.5F * h - 13.8F * g + 4.0F * f;
            float k = j * (float) Math.PI;
            float l = this.itemActivationOffX * (guiGraphics.guiWidth() / 4);
            float m = this.itemActivationOffY * (guiGraphics.guiHeight() / 4);
            PoseStack poseStack = new PoseStack();
            poseStack.pushPose();
            poseStack.translate(
                guiGraphics.guiWidth() / 2 + l * Mth.abs(Mth.sin(k * 2.0F)), guiGraphics.guiHeight() / 2 + m * Mth.abs(Mth.sin(k * 2.0F)), -50.0F
            );
            float n = 50.0F + 175.0F * Mth.sin(k);
            poseStack.scale(n, -n, n);
            poseStack.mulPose(Axis.YP.rotationDegrees(900.0F * Mth.abs(Mth.sin(k))));
            poseStack.mulPose(Axis.XP.rotationDegrees(6.0F * Mth.cos(f * 8.0F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(6.0F * Mth.cos(f * 8.0F)));
            guiGraphics.drawManaged(
                () -> this.minecraft
                    .getItemRenderer()
                    .renderStatic(
                        this.itemActivationItem,
                        ItemDisplayContext.FIXED,
                        15728880,
                        OverlayTexture.NO_OVERLAY,
                        poseStack,
                        guiGraphics.bufferSource(),
                        this.minecraft.level,
                        0
                    )
            );
            poseStack.popPose();
        }
    }

    private void renderConfusionOverlay(GuiGraphics guiGraphics, float scalar) {
        int i = guiGraphics.guiWidth();
        int j = guiGraphics.guiHeight();
        guiGraphics.pose().pushPose();
        float f = Mth.lerp(scalar, 2.0F, 1.0F);
        guiGraphics.pose().translate(i / 2.0F, j / 2.0F, 0.0F);
        guiGraphics.pose().scale(f, f, f);
        guiGraphics.pose().translate(-i / 2.0F, -j / 2.0F, 0.0F);
        float g = 0.2F * scalar;
        float h = 0.4F * scalar;
        float k = 0.2F * scalar;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE
        );
        guiGraphics.setColor(g, h, k, 1.0F);
        guiGraphics.blit(NAUSEA_LOCATION, 0, 0, -90, 0.0F, 0.0F, i, j, i, j);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.pose().popPose();
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    public float getDarkenWorldAmount(float partialTicks) {
        return Mth.lerp(partialTicks, this.darkenWorldAmountO, this.darkenWorldAmount);
    }

    public float getRenderDistance() {
        return this.renderDistance;
    }

    public Camera getMainCamera() {
        return this.mainCamera;
    }

    public LightTexture lightTexture() {
        return this.lightTexture;
    }

    public OverlayTexture overlayTexture() {
        return this.overlayTexture;
    }

    @Nullable
    public static ShaderInstance getPositionShader() {
        return positionShader;
    }

    @Nullable
    public static ShaderInstance getPositionColorShader() {
        return positionColorShader;
    }

    @Nullable
    public static ShaderInstance getPositionTexShader() {
        return positionTexShader;
    }

    @Nullable
    public static ShaderInstance getPositionTexColorShader() {
        return positionTexColorShader;
    }

    @Nullable
    public static ShaderInstance getParticleShader() {
        return particleShader;
    }

    @Nullable
    public static ShaderInstance getPositionColorLightmapShader() {
        return positionColorLightmapShader;
    }

    @Nullable
    public static ShaderInstance getPositionColorTexLightmapShader() {
        return positionColorTexLightmapShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeSolidShader() {
        return rendertypeSolidShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeCutoutMippedShader() {
        return rendertypeCutoutMippedShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeCutoutShader() {
        return rendertypeCutoutShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTranslucentShader() {
        return rendertypeTranslucentShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTranslucentMovingBlockShader() {
        return rendertypeTranslucentMovingBlockShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeArmorCutoutNoCullShader() {
        return rendertypeArmorCutoutNoCullShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntitySolidShader() {
        return rendertypeEntitySolidShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityCutoutShader() {
        return rendertypeEntityCutoutShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityCutoutNoCullShader() {
        return rendertypeEntityCutoutNoCullShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityCutoutNoCullZOffsetShader() {
        return rendertypeEntityCutoutNoCullZOffsetShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeItemEntityTranslucentCullShader() {
        return rendertypeItemEntityTranslucentCullShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityTranslucentCullShader() {
        return rendertypeEntityTranslucentCullShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityTranslucentShader() {
        return rendertypeEntityTranslucentShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityTranslucentEmissiveShader() {
        return rendertypeEntityTranslucentEmissiveShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntitySmoothCutoutShader() {
        return rendertypeEntitySmoothCutoutShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeBeaconBeamShader() {
        return rendertypeBeaconBeamShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityDecalShader() {
        return rendertypeEntityDecalShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityNoOutlineShader() {
        return rendertypeEntityNoOutlineShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityShadowShader() {
        return rendertypeEntityShadowShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityAlphaShader() {
        return rendertypeEntityAlphaShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEyesShader() {
        return rendertypeEyesShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEnergySwirlShader() {
        return rendertypeEnergySwirlShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeBreezeWindShader() {
        return rendertypeBreezeWindShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeLeashShader() {
        return rendertypeLeashShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeWaterMaskShader() {
        return rendertypeWaterMaskShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeOutlineShader() {
        return rendertypeOutlineShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeArmorGlintShader() {
        return rendertypeArmorGlintShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeArmorEntityGlintShader() {
        return rendertypeArmorEntityGlintShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGlintTranslucentShader() {
        return rendertypeGlintTranslucentShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGlintShader() {
        return rendertypeGlintShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGlintDirectShader() {
        return rendertypeGlintDirectShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityGlintShader() {
        return rendertypeEntityGlintShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityGlintDirectShader() {
        return rendertypeEntityGlintDirectShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextShader() {
        return rendertypeTextShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextBackgroundShader() {
        return rendertypeTextBackgroundShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextIntensityShader() {
        return rendertypeTextIntensityShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextSeeThroughShader() {
        return rendertypeTextSeeThroughShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextBackgroundSeeThroughShader() {
        return rendertypeTextBackgroundSeeThroughShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextIntensitySeeThroughShader() {
        return rendertypeTextIntensitySeeThroughShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeLightningShader() {
        return rendertypeLightningShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTripwireShader() {
        return rendertypeTripwireShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEndPortalShader() {
        return rendertypeEndPortalShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEndGatewayShader() {
        return rendertypeEndGatewayShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeCloudsShader() {
        return rendertypeCloudsShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeLinesShader() {
        return rendertypeLinesShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeCrumblingShader() {
        return rendertypeCrumblingShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGuiShader() {
        return rendertypeGuiShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGuiOverlayShader() {
        return rendertypeGuiOverlayShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGuiTextHighlightShader() {
        return rendertypeGuiTextHighlightShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGuiGhostRecipeOverlayShader() {
        return rendertypeGuiGhostRecipeOverlayShader;
    }

    public record ResourceCache(ResourceProvider original, Map<ResourceLocation, Resource> cache) implements ResourceProvider {
        @Override
        public Optional<Resource> getResource(ResourceLocation resourceLocation) {
            Resource resource = this.cache.get(resourceLocation);
            return resource != null ? Optional.of(resource) : this.original.getResource(resourceLocation);
        }
    }
}
