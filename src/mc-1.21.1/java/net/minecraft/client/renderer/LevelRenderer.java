package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

public class LevelRenderer implements ResourceManagerReloadListener, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int SECTION_SIZE = 16;
    public static final int HALF_SECTION_SIZE = 8;
    private static final float SKY_DISC_RADIUS = 512.0F;
    private static final int MIN_FOG_DISTANCE = 32;
    private static final int RAIN_RADIUS = 10;
    private static final int RAIN_DIAMETER = 21;
    private static final int TRANSPARENT_SORT_COUNT = 15;
    private static final ResourceLocation MOON_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation SUN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    protected static final ResourceLocation CLOUDS_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
    private static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
    private static final ResourceLocation RAIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final ResourceLocation SNOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
    public static final Direction[] DIRECTIONS = Direction.values();
    private final Minecraft minecraft;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    private final RenderBuffers renderBuffers;
    @Nullable
    private ClientLevel level;
    private final SectionOcclusionGraph sectionOcclusionGraph = new SectionOcclusionGraph();
    private final ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections = new ObjectArrayList<>(10000);
    /**
     * Global block entities; these are always rendered, even if off-screen.
     * Any block entity is added to this if {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer#shouldRenderOffScreen(net.minecraft.world.level.block.entity.BlockEntity)} returns {@code true}.
     */
    private final Set<BlockEntity> globalBlockEntities = Sets.newHashSet();
    @Nullable
    private ViewArea viewArea;
    @Nullable
    private VertexBuffer starBuffer;
    @Nullable
    private VertexBuffer skyBuffer;
    @Nullable
    private VertexBuffer darkBuffer;
    private boolean generateClouds = true;
    @Nullable
    private VertexBuffer cloudBuffer;
    private final RunningTrimmedMean frameTimes = new RunningTrimmedMean(100);
    private int ticks;
    private final Int2ObjectMap<BlockDestructionProgress> destroyingBlocks = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress = new Long2ObjectOpenHashMap<>();
    private final Map<BlockPos, SoundInstance> playingJukeboxSongs = Maps.newHashMap();
    @Nullable
    private RenderTarget entityTarget;
    @Nullable
    private PostChain entityEffect;
    @Nullable
    private RenderTarget translucentTarget;
    @Nullable
    private RenderTarget itemEntityTarget;
    @Nullable
    private RenderTarget particlesTarget;
    @Nullable
    private RenderTarget weatherTarget;
    @Nullable
    private RenderTarget cloudsTarget;
    @Nullable
    private PostChain transparencyChain;
    private int lastCameraSectionX = Integer.MIN_VALUE;
    private int lastCameraSectionY = Integer.MIN_VALUE;
    private int lastCameraSectionZ = Integer.MIN_VALUE;
    private double prevCamX = Double.MIN_VALUE;
    private double prevCamY = Double.MIN_VALUE;
    private double prevCamZ = Double.MIN_VALUE;
    private double prevCamRotX = Double.MIN_VALUE;
    private double prevCamRotY = Double.MIN_VALUE;
    private int prevCloudX = Integer.MIN_VALUE;
    private int prevCloudY = Integer.MIN_VALUE;
    private int prevCloudZ = Integer.MIN_VALUE;
    private Vec3 prevCloudColor = Vec3.ZERO;
    @Nullable
    private CloudStatus prevCloudsType;
    @Nullable
    private SectionRenderDispatcher sectionRenderDispatcher;
    private int lastViewDistance = -1;
    private int renderedEntities;
    private int culledEntities;
    private Frustum cullingFrustum;
    private boolean captureFrustum;
    @Nullable
    private Frustum capturedFrustum;
    private final Vector4f[] frustumPoints = new Vector4f[8];
    private final Vector3d frustumPos = new Vector3d(0.0, 0.0, 0.0);
    private double xTransparentOld;
    private double yTransparentOld;
    private double zTransparentOld;
    private int rainSoundTime;
    private final float[] rainSizeX = new float[1024];
    private final float[] rainSizeZ = new float[1024];

    public LevelRenderer(
        Minecraft minecraft,
        EntityRenderDispatcher entityRenderDispatcher,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        RenderBuffers renderBuffers
    ) {
        this.minecraft = minecraft;
        this.entityRenderDispatcher = entityRenderDispatcher;
        this.blockEntityRenderDispatcher = blockEntityRenderDispatcher;
        this.renderBuffers = renderBuffers;

        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                float f = j - 16;
                float g = i - 16;
                float h = Mth.sqrt(f * f + g * g);
                this.rainSizeX[i << 5 | j] = -g / h;
                this.rainSizeZ[i << 5 | j] = f / h;
            }
        }

        this.createStars();
        this.createLightSky();
        this.createDarkSky();
    }

    private void renderSnowAndRain(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ) {
        float f = this.minecraft.level.getRainLevel(partialTick);
        if (!(f <= 0.0F)) {
            lightTexture.turnOnLightLayer();
            Level level = this.minecraft.level;
            int i = Mth.floor(camX);
            int j = Mth.floor(camY);
            int k = Mth.floor(camZ);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = null;
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            int l = 5;
            if (Minecraft.useFancyGraphics()) {
                l = 10;
            }

            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            int m = -1;
            float g = this.ticks + partialTick;
            RenderSystem.setShader(GameRenderer::getParticleShader);
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int n = k - l; n <= k + l; n++) {
                for (int o = i - l; o <= i + l; o++) {
                    int p = (n - k + 16) * 32 + o - i + 16;
                    double d = this.rainSizeX[p] * 0.5;
                    double e = this.rainSizeZ[p] * 0.5;
                    mutableBlockPos.set(o, camY, n);
                    Biome biome = level.getBiome(mutableBlockPos).value();
                    if (biome.hasPrecipitation()) {
                        int q = level.getHeight(Heightmap.Types.MOTION_BLOCKING, o, n);
                        int r = j - l;
                        int s = j + l;
                        if (r < q) {
                            r = q;
                        }

                        if (s < q) {
                            s = q;
                        }

                        int t = q;
                        if (t < j) {
                            t = j;
                        }

                        if (r != s) {
                            RandomSource randomSource = RandomSource.create(o * o * 3121 + o * 45238971 ^ n * n * 418711 + n * 13761);
                            mutableBlockPos.set(o, r, n);
                            Biome.Precipitation precipitation = biome.getPrecipitationAt(mutableBlockPos);
                            if (precipitation == Biome.Precipitation.RAIN) {
                                if (m != 0) {
                                    if (m >= 0) {
                                        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
                                    }

                                    m = 0;
                                    RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                                    bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                                }

                                int u = this.ticks & 131071;
                                int v = o * o * 3121 + o * 45238971 + n * n * 418711 + n * 13761 & 0xFF;
                                float h = 3.0F + randomSource.nextFloat();
                                float w = -(u + v + partialTick) / 32.0F * h;
                                float x = w % 32.0F;
                                double y = o + 0.5 - camX;
                                double z = n + 0.5 - camZ;
                                float aa = (float)Math.sqrt(y * y + z * z) / l;
                                float ab = ((1.0F - aa * aa) * 0.5F + 0.5F) * f;
                                mutableBlockPos.set(o, t, n);
                                int ac = getLightColor(level, mutableBlockPos);
                                bufferBuilder.addVertex((float)(o - camX - d + 0.5), (float)(s - camY), (float)(n - camZ - e + 0.5))
                                    .setUv(0.0F, r * 0.25F + x)
                                    .setColor(1.0F, 1.0F, 1.0F, ab)
                                    .setLight(ac);
                                bufferBuilder.addVertex((float)(o - camX + d + 0.5), (float)(s - camY), (float)(n - camZ + e + 0.5))
                                    .setUv(1.0F, r * 0.25F + x)
                                    .setColor(1.0F, 1.0F, 1.0F, ab)
                                    .setLight(ac);
                                bufferBuilder.addVertex((float)(o - camX + d + 0.5), (float)(r - camY), (float)(n - camZ + e + 0.5))
                                    .setUv(1.0F, s * 0.25F + x)
                                    .setColor(1.0F, 1.0F, 1.0F, ab)
                                    .setLight(ac);
                                bufferBuilder.addVertex((float)(o - camX - d + 0.5), (float)(r - camY), (float)(n - camZ - e + 0.5))
                                    .setUv(0.0F, s * 0.25F + x)
                                    .setColor(1.0F, 1.0F, 1.0F, ab)
                                    .setLight(ac);
                            } else if (precipitation == Biome.Precipitation.SNOW) {
                                if (m != 1) {
                                    if (m >= 0) {
                                        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
                                    }

                                    m = 1;
                                    RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                                    bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                                }

                                float ad = -((this.ticks & 511) + partialTick) / 512.0F;
                                float ae = (float)(randomSource.nextDouble() + g * 0.01 * (float)randomSource.nextGaussian());
                                float h = (float)(randomSource.nextDouble() + g * (float)randomSource.nextGaussian() * 0.001);
                                double af = o + 0.5 - camX;
                                double y = n + 0.5 - camZ;
                                float ag = (float)Math.sqrt(af * af + y * y) / l;
                                float ah = ((1.0F - ag * ag) * 0.3F + 0.5F) * f;
                                mutableBlockPos.set(o, t, n);
                                int ai = getLightColor(level, mutableBlockPos);
                                int aj = ai >> 16 & 65535;
                                int ac = ai & 65535;
                                int ak = (aj * 3 + 240) / 4;
                                int al = (ac * 3 + 240) / 4;
                                bufferBuilder.addVertex((float)(o - camX - d + 0.5), (float)(s - camY), (float)(n - camZ - e + 0.5))
                                    .setUv(0.0F + ae, r * 0.25F + ad + h)
                                    .setColor(1.0F, 1.0F, 1.0F, ah)
                                    .setUv2(al, ak);
                                bufferBuilder.addVertex((float)(o - camX + d + 0.5), (float)(s - camY), (float)(n - camZ + e + 0.5))
                                    .setUv(1.0F + ae, r * 0.25F + ad + h)
                                    .setColor(1.0F, 1.0F, 1.0F, ah)
                                    .setUv2(al, ak);
                                bufferBuilder.addVertex((float)(o - camX + d + 0.5), (float)(r - camY), (float)(n - camZ + e + 0.5))
                                    .setUv(1.0F + ae, s * 0.25F + ad + h)
                                    .setColor(1.0F, 1.0F, 1.0F, ah)
                                    .setUv2(al, ak);
                                bufferBuilder.addVertex((float)(o - camX - d + 0.5), (float)(r - camY), (float)(n - camZ - e + 0.5))
                                    .setUv(0.0F + ae, s * 0.25F + ad + h)
                                    .setColor(1.0F, 1.0F, 1.0F, ah)
                                    .setUv2(al, ak);
                            }
                        }
                    }
                }
            }

            if (m >= 0) {
                BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
            }

            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            lightTexture.turnOffLightLayer();
        }
    }

    public void tickRain(Camera camera) {
        float f = this.minecraft.level.getRainLevel(1.0F) / (Minecraft.useFancyGraphics() ? 1.0F : 2.0F);
        if (!(f <= 0.0F)) {
            RandomSource randomSource = RandomSource.create(this.ticks * 312987231L);
            LevelReader levelReader = this.minecraft.level;
            BlockPos blockPos = BlockPos.containing(camera.getPosition());
            BlockPos blockPos2 = null;
            int i = (int)(100.0F * f * f) / (this.minecraft.options.particles().get() == ParticleStatus.DECREASED ? 2 : 1);

            for (int j = 0; j < i; j++) {
                int k = randomSource.nextInt(21) - 10;
                int l = randomSource.nextInt(21) - 10;
                BlockPos blockPos3 = levelReader.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos.offset(k, 0, l));
                if (blockPos3.getY() > levelReader.getMinBuildHeight() && blockPos3.getY() <= blockPos.getY() + 10 && blockPos3.getY() >= blockPos.getY() - 10) {
                    Biome biome = levelReader.getBiome(blockPos3).value();
                    if (biome.getPrecipitationAt(blockPos3) == Biome.Precipitation.RAIN) {
                        blockPos2 = blockPos3.below();
                        if (this.minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
                            break;
                        }

                        double d = randomSource.nextDouble();
                        double e = randomSource.nextDouble();
                        BlockState blockState = levelReader.getBlockState(blockPos2);
                        FluidState fluidState = levelReader.getFluidState(blockPos2);
                        VoxelShape voxelShape = blockState.getCollisionShape(levelReader, blockPos2);
                        double g = voxelShape.max(Direction.Axis.Y, d, e);
                        double h = fluidState.getHeight(levelReader, blockPos2);
                        double m = Math.max(g, h);
                        ParticleOptions particleOptions = !fluidState.is(FluidTags.LAVA)
                                && !blockState.is(Blocks.MAGMA_BLOCK)
                                && !CampfireBlock.isLitCampfire(blockState)
                            ? ParticleTypes.RAIN
                            : ParticleTypes.SMOKE;
                        this.minecraft.level.addParticle(particleOptions, blockPos2.getX() + d, blockPos2.getY() + m, blockPos2.getZ() + e, 0.0, 0.0, 0.0);
                    }
                }
            }

            if (blockPos2 != null && randomSource.nextInt(3) < this.rainSoundTime++) {
                this.rainSoundTime = 0;
                if (blockPos2.getY() > blockPos.getY() + 1
                    && levelReader.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos).getY() > Mth.floor(blockPos.getY())) {
                    this.minecraft.level.playLocalSound(blockPos2, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 0.1F, 0.5F, false);
                } else {
                    this.minecraft.level.playLocalSound(blockPos2, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.2F, 1.0F, false);
                }
            }
        }
    }

    @Override
    public void close() {
        if (this.entityEffect != null) {
            this.entityEffect.close();
        }

        if (this.transparencyChain != null) {
            this.transparencyChain.close();
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.initOutline();
        if (Minecraft.useShaderTransparency()) {
            this.initTransparency();
        }
    }

    public void initOutline() {
        if (this.entityEffect != null) {
            this.entityEffect.close();
        }

        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("shaders/post/entity_outline.json");

        try {
            this.entityEffect = new PostChain(
                this.minecraft.getTextureManager(), this.minecraft.getResourceManager(), this.minecraft.getMainRenderTarget(), resourceLocation
            );
            this.entityEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
            this.entityTarget = this.entityEffect.getTempTarget("final");
        } catch (IOException iOException) {
            LOGGER.warn("Failed to load shader: {}", resourceLocation, iOException);
            this.entityEffect = null;
            this.entityTarget = null;
        } catch (JsonSyntaxException jsonSyntaxException) {
            LOGGER.warn("Failed to parse shader: {}", resourceLocation, jsonSyntaxException);
            this.entityEffect = null;
            this.entityTarget = null;
        }
    }

    private void initTransparency() {
        this.deinitTransparency();
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("shaders/post/transparency.json");

        try {
            PostChain postChain = new PostChain(
                this.minecraft.getTextureManager(), this.minecraft.getResourceManager(), this.minecraft.getMainRenderTarget(), resourceLocation
            );
            postChain.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
            RenderTarget renderTarget = postChain.getTempTarget("translucent");
            RenderTarget renderTarget2 = postChain.getTempTarget("itemEntity");
            RenderTarget renderTarget3 = postChain.getTempTarget("particles");
            RenderTarget renderTarget4 = postChain.getTempTarget("weather");
            RenderTarget renderTarget5 = postChain.getTempTarget("clouds");
            this.transparencyChain = postChain;
            this.translucentTarget = renderTarget;
            this.itemEntityTarget = renderTarget2;
            this.particlesTarget = renderTarget3;
            this.weatherTarget = renderTarget4;
            this.cloudsTarget = renderTarget5;
        } catch (Exception exception) {
            String string = exception instanceof JsonSyntaxException ? "parse" : "load";
            String string2 = "Failed to " + string + " shader: " + resourceLocation;
            LevelRenderer.TransparencyShaderException transparencyShaderException = new LevelRenderer.TransparencyShaderException(string2, exception);
            if (this.minecraft.getResourcePackRepository().getSelectedIds().size() > 1) {
                Component component = this.minecraft
                    .getResourceManager()
                    .listPacks()
                    .findFirst()
                    .map(levelRenderer -> Component.literal(levelRenderer.packId()))
                    .orElse(null);
                this.minecraft.options.graphicsMode().set(GraphicsStatus.FANCY);
                this.minecraft.clearResourcePacksOnError(transparencyShaderException, component, null);
            } else {
                this.minecraft.options.graphicsMode().set(GraphicsStatus.FANCY);
                this.minecraft.options.save();
                LOGGER.error(LogUtils.FATAL_MARKER, string2, transparencyShaderException);
                this.minecraft.emergencySaveAndCrash(new CrashReport(string2, transparencyShaderException));
            }
        }
    }

    private void deinitTransparency() {
        if (this.transparencyChain != null) {
            this.transparencyChain.close();
            this.translucentTarget.destroyBuffers();
            this.itemEntityTarget.destroyBuffers();
            this.particlesTarget.destroyBuffers();
            this.weatherTarget.destroyBuffers();
            this.cloudsTarget.destroyBuffers();
            this.transparencyChain = null;
            this.translucentTarget = null;
            this.itemEntityTarget = null;
            this.particlesTarget = null;
            this.weatherTarget = null;
            this.cloudsTarget = null;
        }
    }

    public void doEntityOutline() {
        if (this.shouldShowEntityOutlines()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE
            );
            this.entityTarget.blitToScreen(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(), false);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    protected boolean shouldShowEntityOutlines() {
        return !this.minecraft.gameRenderer.isPanoramicMode() && this.entityTarget != null && this.entityEffect != null && this.minecraft.player != null;
    }

    private void createDarkSky() {
        if (this.darkBuffer != null) {
            this.darkBuffer.close();
        }

        this.darkBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.darkBuffer.bind();
        this.darkBuffer.upload(buildSkyDisc(Tesselator.getInstance(), -16.0F));
        VertexBuffer.unbind();
    }

    private void createLightSky() {
        if (this.skyBuffer != null) {
            this.skyBuffer.close();
        }

        this.skyBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.skyBuffer.bind();
        this.skyBuffer.upload(buildSkyDisc(Tesselator.getInstance(), 16.0F));
        VertexBuffer.unbind();
    }

    private static MeshData buildSkyDisc(Tesselator tesselator, float y) {
        float f = Math.signum(y) * 512.0F;
        float g = 512.0F;
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        bufferBuilder.addVertex(0.0F, y, 0.0F);

        for (int i = -180; i <= 180; i += 45) {
            bufferBuilder.addVertex(f * Mth.cos(i * (float) (Math.PI / 180.0)), y, 512.0F * Mth.sin(i * (float) (Math.PI / 180.0)));
        }

        return bufferBuilder.buildOrThrow();
    }

    private void createStars() {
        if (this.starBuffer != null) {
            this.starBuffer.close();
        }

        this.starBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.starBuffer.bind();
        this.starBuffer.upload(this.drawStars(Tesselator.getInstance()));
        VertexBuffer.unbind();
    }

    private MeshData drawStars(Tesselator tesselator) {
        RandomSource randomSource = RandomSource.create(10842L);
        int i = 1500;
        float f = 100.0F;
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int j = 0; j < 1500; j++) {
            float g = randomSource.nextFloat() * 2.0F - 1.0F;
            float h = randomSource.nextFloat() * 2.0F - 1.0F;
            float k = randomSource.nextFloat() * 2.0F - 1.0F;
            float l = 0.15F + randomSource.nextFloat() * 0.1F;
            float m = Mth.lengthSquared(g, h, k);
            if (!(m <= 0.010000001F) && !(m >= 1.0F)) {
                Vector3f vector3f = new Vector3f(g, h, k).normalize(100.0F);
                float n = (float)(randomSource.nextDouble() * (float) Math.PI * 2.0);
                Quaternionf quaternionf = new Quaternionf().rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), vector3f).rotateZ(n);
                bufferBuilder.addVertex(vector3f.add(new Vector3f(l, -l, 0.0F).rotate(quaternionf)));
                bufferBuilder.addVertex(vector3f.add(new Vector3f(l, l, 0.0F).rotate(quaternionf)));
                bufferBuilder.addVertex(vector3f.add(new Vector3f(-l, l, 0.0F).rotate(quaternionf)));
                bufferBuilder.addVertex(vector3f.add(new Vector3f(-l, -l, 0.0F).rotate(quaternionf)));
            }
        }

        return bufferBuilder.buildOrThrow();
    }

    /**
     * @param level the level to set, or {@code null} to clear
     */
    public void setLevel(@Nullable ClientLevel level) {
        this.lastCameraSectionX = Integer.MIN_VALUE;
        this.lastCameraSectionY = Integer.MIN_VALUE;
        this.lastCameraSectionZ = Integer.MIN_VALUE;
        this.entityRenderDispatcher.setLevel(level);
        this.level = level;
        if (level != null) {
            this.allChanged();
        } else {
            if (this.viewArea != null) {
                this.viewArea.releaseAllBuffers();
                this.viewArea = null;
            }

            if (this.sectionRenderDispatcher != null) {
                this.sectionRenderDispatcher.dispose();
            }

            this.sectionRenderDispatcher = null;
            this.globalBlockEntities.clear();
            this.sectionOcclusionGraph.waitAndReset(null);
            this.visibleSections.clear();
        }
    }

    public void graphicsChanged() {
        if (Minecraft.useShaderTransparency()) {
            this.initTransparency();
        } else {
            this.deinitTransparency();
        }
    }

    public void allChanged() {
        if (this.level != null) {
            this.graphicsChanged();
            this.level.clearTintCaches();
            if (this.sectionRenderDispatcher == null) {
                this.sectionRenderDispatcher = new SectionRenderDispatcher(
                    this.level,
                    this,
                    Util.backgroundExecutor(),
                    this.renderBuffers,
                    this.minecraft.getBlockRenderer(),
                    this.minecraft.getBlockEntityRenderDispatcher()
                );
            } else {
                this.sectionRenderDispatcher.setLevel(this.level);
            }

            this.generateClouds = true;
            ItemBlockRenderTypes.setFancy(Minecraft.useFancyGraphics());
            this.lastViewDistance = this.minecraft.options.getEffectiveRenderDistance();
            if (this.viewArea != null) {
                this.viewArea.releaseAllBuffers();
            }

            this.sectionRenderDispatcher.blockUntilClear();
            synchronized (this.globalBlockEntities) {
                this.globalBlockEntities.clear();
            }

            this.viewArea = new ViewArea(this.sectionRenderDispatcher, this.level, this.minecraft.options.getEffectiveRenderDistance(), this);
            this.sectionOcclusionGraph.waitAndReset(this.viewArea);
            this.visibleSections.clear();
            Entity entity = this.minecraft.getCameraEntity();
            if (entity != null) {
                this.viewArea.repositionCamera(entity.getX(), entity.getZ());
            }
        }
    }

    public void resize(int width, int height) {
        this.needsUpdate();
        if (this.entityEffect != null) {
            this.entityEffect.resize(width, height);
        }

        if (this.transparencyChain != null) {
            this.transparencyChain.resize(width, height);
        }
    }

    public String getSectionStatistics() {
        int i = this.viewArea.sections.length;
        int j = this.countRenderedSections();
        return String.format(
            Locale.ROOT,
            "C: %d/%d %sD: %d, %s",
            j,
            i,
            this.minecraft.smartCull ? "(s) " : "",
            this.lastViewDistance,
            this.sectionRenderDispatcher == null ? "null" : this.sectionRenderDispatcher.getStats()
        );
    }

    public SectionRenderDispatcher getSectionRenderDispatcher() {
        return this.sectionRenderDispatcher;
    }

    public double getTotalSections() {
        return this.viewArea.sections.length;
    }

    public double getLastViewDistance() {
        return this.lastViewDistance;
    }

    public int countRenderedSections() {
        int i = 0;

        for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
            if (!renderSection.getCompiled().hasNoRenderableLayers()) {
                i++;
            }
        }

        return i;
    }

    public String getEntityStatistics() {
        return "E: "
            + this.renderedEntities
            + "/"
            + this.level.getEntityCount()
            + ", B: "
            + this.culledEntities
            + ", SD: "
            + this.level.getServerSimulationDistance();
    }

    private void setupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator) {
        Vec3 vec3 = camera.getPosition();
        if (this.minecraft.options.getEffectiveRenderDistance() != this.lastViewDistance) {
            this.allChanged();
        }

        this.level.getProfiler().push("camera");
        double d = this.minecraft.player.getX();
        double e = this.minecraft.player.getY();
        double f = this.minecraft.player.getZ();
        int i = SectionPos.posToSectionCoord(d);
        int j = SectionPos.posToSectionCoord(e);
        int k = SectionPos.posToSectionCoord(f);
        if (this.lastCameraSectionX != i || this.lastCameraSectionY != j || this.lastCameraSectionZ != k) {
            this.lastCameraSectionX = i;
            this.lastCameraSectionY = j;
            this.lastCameraSectionZ = k;
            this.viewArea.repositionCamera(d, f);
        }

        this.sectionRenderDispatcher.setCamera(vec3);
        this.level.getProfiler().popPush("cull");
        this.minecraft.getProfiler().popPush("culling");
        BlockPos blockPos = camera.getBlockPosition();
        double g = Math.floor(vec3.x / 8.0);
        double h = Math.floor(vec3.y / 8.0);
        double l = Math.floor(vec3.z / 8.0);
        if (g != this.prevCamX || h != this.prevCamY || l != this.prevCamZ) {
            this.sectionOcclusionGraph.invalidate();
        }

        this.prevCamX = g;
        this.prevCamY = h;
        this.prevCamZ = l;
        this.minecraft.getProfiler().popPush("update");
        if (!hasCapturedFrustum) {
            boolean bl = this.minecraft.smartCull;
            if (isSpectator && this.level.getBlockState(blockPos).isSolidRender(this.level, blockPos)) {
                bl = false;
            }

            Entity.setViewScale(
                Mth.clamp(this.minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5) * this.minecraft.options.entityDistanceScaling().get()
            );
            this.minecraft.getProfiler().push("section_occlusion_graph");
            this.sectionOcclusionGraph.update(bl, camera, frustum, this.visibleSections);
            this.minecraft.getProfiler().pop();
            double m = Math.floor(camera.getXRot() / 2.0F);
            double n = Math.floor(camera.getYRot() / 2.0F);
            if (this.sectionOcclusionGraph.consumeFrustumUpdate() || m != this.prevCamRotX || n != this.prevCamRotY) {
                this.applyFrustum(offsetFrustum(frustum));
                this.prevCamRotX = m;
                this.prevCamRotY = n;
            }
        }

        this.minecraft.getProfiler().pop();
    }

    public static Frustum offsetFrustum(Frustum frustum) {
        return new Frustum(frustum).offsetToFullyIncludeCameraCube(8);
    }

    private void applyFrustum(Frustum frustum) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("applyFrustum called from wrong thread: " + Thread.currentThread().getName());
        }

        this.minecraft.getProfiler().push("apply_frustum");
        this.visibleSections.clear();
        this.sectionOcclusionGraph.addSectionsInFrustum(frustum, this.visibleSections);
        this.minecraft.getProfiler().pop();
    }

    public void addRecentlyCompiledSection(SectionRenderDispatcher.RenderSection renderSection) {
        this.sectionOcclusionGraph.onSectionCompiled(renderSection);
    }

    private void captureFrustum(Matrix4f viewMatrix, Matrix4f projectionMatrix, double camX, double camY, double camZ, Frustum capturedFrustrum) {
        this.capturedFrustum = capturedFrustrum;
        Matrix4f matrix4f = new Matrix4f(projectionMatrix);
        matrix4f.mul(viewMatrix);
        matrix4f.invert();
        this.frustumPos.x = camX;
        this.frustumPos.y = camY;
        this.frustumPos.z = camZ;
        this.frustumPoints[0] = new Vector4f(-1.0F, -1.0F, -1.0F, 1.0F);
        this.frustumPoints[1] = new Vector4f(1.0F, -1.0F, -1.0F, 1.0F);
        this.frustumPoints[2] = new Vector4f(1.0F, 1.0F, -1.0F, 1.0F);
        this.frustumPoints[3] = new Vector4f(-1.0F, 1.0F, -1.0F, 1.0F);
        this.frustumPoints[4] = new Vector4f(-1.0F, -1.0F, 1.0F, 1.0F);
        this.frustumPoints[5] = new Vector4f(1.0F, -1.0F, 1.0F, 1.0F);
        this.frustumPoints[6] = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.frustumPoints[7] = new Vector4f(-1.0F, 1.0F, 1.0F, 1.0F);

        for (int i = 0; i < 8; i++) {
            matrix4f.transform(this.frustumPoints[i]);
            this.frustumPoints[i].div(this.frustumPoints[i].w());
        }
    }

    public void prepareCullFrustum(Vec3 cameraPosition, Matrix4f frustumMatrix, Matrix4f projectionMatrix) {
        this.cullingFrustum = new Frustum(frustumMatrix, projectionMatrix);
        this.cullingFrustum.prepare(cameraPosition.x(), cameraPosition.y(), cameraPosition.z());
    }

    public void renderLevel(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix
    ) {
        TickRateManager tickRateManager = this.minecraft.level.tickRateManager();
        float f = deltaTracker.getGameTimeDeltaPartialTick(false);
        RenderSystem.setShaderGameTime(this.level.getGameTime(), f);
        this.blockEntityRenderDispatcher.prepare(this.level, camera, this.minecraft.hitResult);
        this.entityRenderDispatcher.prepare(this.level, camera, this.minecraft.crosshairPickEntity);
        ProfilerFiller profilerFiller = this.level.getProfiler();
        profilerFiller.popPush("light_update_queue");
        this.level.pollLightUpdates();
        profilerFiller.popPush("light_updates");
        this.level.getChunkSource().getLightEngine().runLightUpdates();
        Vec3 vec3 = camera.getPosition();
        double d = vec3.x();
        double e = vec3.y();
        double g = vec3.z();
        profilerFiller.popPush("culling");
        boolean bl = this.capturedFrustum != null;
        Frustum frustum;
        if (bl) {
            frustum = this.capturedFrustum;
            frustum.prepare(this.frustumPos.x, this.frustumPos.y, this.frustumPos.z);
        } else {
            frustum = this.cullingFrustum;
        }

        this.minecraft.getProfiler().popPush("captureFrustum");
        if (this.captureFrustum) {
            this.captureFrustum(frustumMatrix, projectionMatrix, vec3.x, vec3.y, vec3.z, bl ? new Frustum(frustumMatrix, projectionMatrix) : frustum);
            this.captureFrustum = false;
        }

        profilerFiller.popPush("clear");
        FogRenderer.setupColor(camera, f, this.minecraft.level, this.minecraft.options.getEffectiveRenderDistance(), gameRenderer.getDarkenWorldAmount(f));
        FogRenderer.levelFogColor();
        RenderSystem.clear(16640, Minecraft.ON_OSX);
        float h = gameRenderer.getRenderDistance();
        boolean bl2 = this.minecraft.level.effects().isFoggyAt(Mth.floor(d), Mth.floor(e)) || this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
        profilerFiller.popPush("sky");
        RenderSystem.setShader(GameRenderer::getPositionShader);
        this.renderSky(frustumMatrix, projectionMatrix, f, camera, bl2, () -> FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, h, bl2, f));
        profilerFiller.popPush("fog");
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, Math.max(h, 32.0F), bl2, f);
        profilerFiller.popPush("terrain_setup");
        this.setupRender(camera, frustum, bl, this.minecraft.player.isSpectator());
        profilerFiller.popPush("compile_sections");
        this.compileSections(camera);
        profilerFiller.popPush("terrain");
        this.renderSectionLayer(RenderType.solid(), d, e, g, frustumMatrix, projectionMatrix);
        this.renderSectionLayer(RenderType.cutoutMipped(), d, e, g, frustumMatrix, projectionMatrix);
        this.renderSectionLayer(RenderType.cutout(), d, e, g, frustumMatrix, projectionMatrix);
        if (this.level.effects().constantAmbientLight()) {
            Lighting.setupNetherLevel();
        } else {
            Lighting.setupLevel();
        }

        profilerFiller.popPush("entities");
        this.renderedEntities = 0;
        this.culledEntities = 0;
        if (this.itemEntityTarget != null) {
            this.itemEntityTarget.clear(Minecraft.ON_OSX);
            this.itemEntityTarget.copyDepthFrom(this.minecraft.getMainRenderTarget());
            this.minecraft.getMainRenderTarget().bindWrite(false);
        }

        if (this.weatherTarget != null) {
            this.weatherTarget.clear(Minecraft.ON_OSX);
        }

        if (this.shouldShowEntityOutlines()) {
            this.entityTarget.clear(Minecraft.ON_OSX);
            this.minecraft.getMainRenderTarget().bindWrite(false);
        }

        Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
        matrix4fStack.pushMatrix();
        matrix4fStack.mul(frustumMatrix);
        RenderSystem.applyModelViewMatrix();
        boolean bl3 = false;
        PoseStack poseStack = new PoseStack();
        MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();

        for (Entity entity : this.level.entitiesForRendering()) {
            if (this.entityRenderDispatcher.shouldRender(entity, frustum, d, e, g) || entity.hasIndirectPassenger(this.minecraft.player)) {
                BlockPos blockPos = entity.blockPosition();
                if ((this.level.isOutsideBuildHeight(blockPos.getY()) || this.isSectionCompiled(blockPos))
                    && (
                        entity != camera.getEntity()
                            || camera.isDetached()
                            || camera.getEntity() instanceof LivingEntity && ((LivingEntity)camera.getEntity()).isSleeping()
                    )
                    && (!(entity instanceof LocalPlayer) || camera.getEntity() == entity)) {
                    this.renderedEntities++;
                    if (entity.tickCount == 0) {
                        entity.xOld = entity.getX();
                        entity.yOld = entity.getY();
                        entity.zOld = entity.getZ();
                    }

                    MultiBufferSource multiBufferSource;
                    if (this.shouldShowEntityOutlines() && this.minecraft.shouldEntityAppearGlowing(entity)) {
                        bl3 = true;
                        OutlineBufferSource outlineBufferSource = this.renderBuffers.outlineBufferSource();
                        multiBufferSource = outlineBufferSource;
                        int i = entity.getTeamColor();
                        outlineBufferSource.setColor(FastColor.ARGB32.red(i), FastColor.ARGB32.green(i), FastColor.ARGB32.blue(i), 255);
                    } else {
                        multiBufferSource = bufferSource;
                    }

                    float j = deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));
                    this.renderEntity(entity, d, e, g, j, poseStack, multiBufferSource);
                }
            }
        }

        bufferSource.endLastBatch();
        this.checkPoseStack(poseStack);
        bufferSource.endBatch(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        bufferSource.endBatch(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        bufferSource.endBatch(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        bufferSource.endBatch(RenderType.entitySmoothCutout(TextureAtlas.LOCATION_BLOCKS));
        profilerFiller.popPush("blockentities");

        for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
            List<BlockEntity> list = renderSection.getCompiled().getRenderableBlockEntities();
            if (!list.isEmpty()) {
                for (BlockEntity blockEntity : list) {
                    BlockPos blockPos2 = blockEntity.getBlockPos();
                    MultiBufferSource multiBufferSource2 = bufferSource;
                    poseStack.pushPose();
                    poseStack.translate(blockPos2.getX() - d, blockPos2.getY() - e, blockPos2.getZ() - g);
                    SortedSet<BlockDestructionProgress> sortedSet = this.destructionProgress.get(blockPos2.asLong());
                    if (sortedSet != null && !sortedSet.isEmpty()) {
                        int k = sortedSet.last().getProgress();
                        if (k >= 0) {
                            PoseStack.Pose pose = poseStack.last();
                            VertexConsumer vertexConsumer = new SheetedDecalTextureGenerator(
                                this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(k)), pose, 1.0F
                            );
                            multiBufferSource2 = lastState -> {
                                VertexConsumer vertexConsumer2x = bufferSource.getBuffer(lastState);
                                return lastState.affectsCrumbling() ? VertexMultiConsumer.create(vertexConsumer, vertexConsumer2x) : vertexConsumer2x;
                            };
                        }
                    }

                    this.blockEntityRenderDispatcher.render(blockEntity, f, poseStack, multiBufferSource2);
                    poseStack.popPose();
                }
            }
        }

        synchronized (this.globalBlockEntities) {
            for (BlockEntity blockEntity2 : this.globalBlockEntities) {
                BlockPos blockPos3 = blockEntity2.getBlockPos();
                poseStack.pushPose();
                poseStack.translate(blockPos3.getX() - d, blockPos3.getY() - e, blockPos3.getZ() - g);
                this.blockEntityRenderDispatcher.render(blockEntity2, f, poseStack, bufferSource);
                poseStack.popPose();
            }
        }

        this.checkPoseStack(poseStack);
        bufferSource.endBatch(RenderType.solid());
        bufferSource.endBatch(RenderType.endPortal());
        bufferSource.endBatch(RenderType.endGateway());
        bufferSource.endBatch(Sheets.solidBlockSheet());
        bufferSource.endBatch(Sheets.cutoutBlockSheet());
        bufferSource.endBatch(Sheets.bedSheet());
        bufferSource.endBatch(Sheets.shulkerBoxSheet());
        bufferSource.endBatch(Sheets.signSheet());
        bufferSource.endBatch(Sheets.hangingSignSheet());
        bufferSource.endBatch(Sheets.chestSheet());
        this.renderBuffers.outlineBufferSource().endOutlineBatch();
        if (bl3) {
            this.entityEffect.process(deltaTracker.getGameTimeDeltaTicks());
            this.minecraft.getMainRenderTarget().bindWrite(false);
        }

        profilerFiller.popPush("destroyProgress");

        for (Entry<SortedSet<BlockDestructionProgress>> entry : this.destructionProgress.long2ObjectEntrySet()) {
            BlockPos blockPos = BlockPos.of(entry.getLongKey());
            double l = blockPos.getX() - d;
            double m = blockPos.getY() - e;
            double n = blockPos.getZ() - g;
            if (!(l * l + m * m + n * n > 1024.0)) {
                SortedSet<BlockDestructionProgress> sortedSet2 = entry.getValue();
                if (sortedSet2 != null && !sortedSet2.isEmpty()) {
                    int o = sortedSet2.last().getProgress();
                    poseStack.pushPose();
                    poseStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - g);
                    PoseStack.Pose pose2 = poseStack.last();
                    VertexConsumer vertexConsumer2 = new SheetedDecalTextureGenerator(
                        this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(o)), pose2, 1.0F
                    );
                    this.minecraft
                        .getBlockRenderer()
                        .renderBreakingTexture(this.level.getBlockState(blockPos), blockPos, this.level, poseStack, vertexConsumer2);
                    poseStack.popPose();
                }
            }
        }

        this.checkPoseStack(poseStack);
        HitResult hitResult = this.minecraft.hitResult;
        if (renderBlockOutline && hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            profilerFiller.popPush("outline");
            BlockPos blockPos4 = ((BlockHitResult)hitResult).getBlockPos();
            BlockState blockState = this.level.getBlockState(blockPos4);
            if (!blockState.isAir() && this.level.getWorldBorder().isWithinBounds(blockPos4)) {
                VertexConsumer vertexConsumer3 = bufferSource.getBuffer(RenderType.lines());
                this.renderHitOutline(poseStack, vertexConsumer3, camera.getEntity(), d, e, g, blockPos4, blockState);
            }
        }

        this.minecraft.debugRenderer.render(poseStack, bufferSource, d, e, g);
        bufferSource.endLastBatch();
        bufferSource.endBatch(Sheets.translucentCullBlockSheet());
        bufferSource.endBatch(Sheets.bannerSheet());
        bufferSource.endBatch(Sheets.shieldSheet());
        bufferSource.endBatch(RenderType.armorEntityGlint());
        bufferSource.endBatch(RenderType.glint());
        bufferSource.endBatch(RenderType.glintTranslucent());
        bufferSource.endBatch(RenderType.entityGlint());
        bufferSource.endBatch(RenderType.entityGlintDirect());
        bufferSource.endBatch(RenderType.waterMask());
        this.renderBuffers.crumblingBufferSource().endBatch();
        if (this.transparencyChain != null) {
            bufferSource.endBatch(RenderType.lines());
            bufferSource.endBatch();
            this.translucentTarget.clear(Minecraft.ON_OSX);
            this.translucentTarget.copyDepthFrom(this.minecraft.getMainRenderTarget());
            profilerFiller.popPush("translucent");
            this.renderSectionLayer(RenderType.translucent(), d, e, g, frustumMatrix, projectionMatrix);
            profilerFiller.popPush("string");
            this.renderSectionLayer(RenderType.tripwire(), d, e, g, frustumMatrix, projectionMatrix);
            this.particlesTarget.clear(Minecraft.ON_OSX);
            this.particlesTarget.copyDepthFrom(this.minecraft.getMainRenderTarget());
            RenderStateShard.PARTICLES_TARGET.setupRenderState();
            profilerFiller.popPush("particles");
            this.minecraft.particleEngine.render(lightTexture, camera, f);
            RenderStateShard.PARTICLES_TARGET.clearRenderState();
        } else {
            profilerFiller.popPush("translucent");
            if (this.translucentTarget != null) {
                this.translucentTarget.clear(Minecraft.ON_OSX);
            }

            this.renderSectionLayer(RenderType.translucent(), d, e, g, frustumMatrix, projectionMatrix);
            bufferSource.endBatch(RenderType.lines());
            bufferSource.endBatch();
            profilerFiller.popPush("string");
            this.renderSectionLayer(RenderType.tripwire(), d, e, g, frustumMatrix, projectionMatrix);
            profilerFiller.popPush("particles");
            this.minecraft.particleEngine.render(lightTexture, camera, f);
        }

        if (this.minecraft.options.getCloudsType() != CloudStatus.OFF) {
            if (this.transparencyChain != null) {
                this.cloudsTarget.clear(Minecraft.ON_OSX);
            }

            profilerFiller.popPush("clouds");
            this.renderClouds(poseStack, frustumMatrix, projectionMatrix, f, d, e, g);
        }

        if (this.transparencyChain != null) {
            RenderStateShard.WEATHER_TARGET.setupRenderState();
            profilerFiller.popPush("weather");
            this.renderSnowAndRain(lightTexture, f, d, e, g);
            this.renderWorldBorder(camera);
            RenderStateShard.WEATHER_TARGET.clearRenderState();
            this.transparencyChain.process(deltaTracker.getGameTimeDeltaTicks());
            this.minecraft.getMainRenderTarget().bindWrite(false);
        } else {
            RenderSystem.depthMask(false);
            profilerFiller.popPush("weather");
            this.renderSnowAndRain(lightTexture, f, d, e, g);
            this.renderWorldBorder(camera);
            RenderSystem.depthMask(true);
        }

        this.renderDebug(poseStack, bufferSource, camera);
        bufferSource.endLastBatch();
        matrix4fStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        FogRenderer.setupNoFog();
    }

    /**
     * Asserts that the specified {@code poseStack} is {@linkplain com.mojang.blaze3d.vertex.PoseStack#clear() clear}.
     * @throws java.lang.IllegalStateException if the specified {@code poseStack} is not clear
     */
    private void checkPoseStack(PoseStack poseStack) {
        if (!poseStack.clear()) {
            throw new IllegalStateException("Pose stack not empty");
        }
    }

    private void renderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        double d = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double e = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double f = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        float g = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        this.entityRenderDispatcher
            .render(
                entity,
                d - camX,
                e - camY,
                f - camZ,
                g,
                partialTick,
                poseStack,
                bufferSource,
                this.entityRenderDispatcher.getPackedLightCoords(entity, partialTick)
            );
    }

    private void renderSectionLayer(RenderType renderType, double x, double y, double z, Matrix4f frustrumMatrix, Matrix4f projectionMatrix) {
        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();
        if (renderType == RenderType.translucent()) {
            this.minecraft.getProfiler().push("translucent_sort");
            double d = x - this.xTransparentOld;
            double e = y - this.yTransparentOld;
            double f = z - this.zTransparentOld;
            if (d * d + e * e + f * f > 1.0) {
                int i = SectionPos.posToSectionCoord(x);
                int j = SectionPos.posToSectionCoord(y);
                int k = SectionPos.posToSectionCoord(z);
                boolean bl = i != SectionPos.posToSectionCoord(this.xTransparentOld)
                    || k != SectionPos.posToSectionCoord(this.zTransparentOld)
                    || j != SectionPos.posToSectionCoord(this.yTransparentOld);
                this.xTransparentOld = x;
                this.yTransparentOld = y;
                this.zTransparentOld = z;
                int l = 0;

                for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
                    if (l < 15
                        && (bl || renderSection.isAxisAlignedWith(i, j, k))
                        && renderSection.resortTransparency(renderType, this.sectionRenderDispatcher)) {
                        l++;
                    }
                }
            }

            this.minecraft.getProfiler().pop();
        }

        this.minecraft.getProfiler().push("filterempty");
        this.minecraft.getProfiler().popPush(() -> "render_" + renderType);
        boolean bl2 = renderType != RenderType.translucent();
        ObjectListIterator<SectionRenderDispatcher.RenderSection> objectListIterator = this.visibleSections.listIterator(bl2 ? 0 : this.visibleSections.size());
        ShaderInstance shaderInstance = RenderSystem.getShader();
        shaderInstance.setDefaultUniforms(VertexFormat.Mode.QUADS, frustrumMatrix, projectionMatrix, this.minecraft.getWindow());
        shaderInstance.apply();
        Uniform uniform = shaderInstance.CHUNK_OFFSET;

        while (bl2 ? objectListIterator.hasNext() : objectListIterator.hasPrevious()) {
            SectionRenderDispatcher.RenderSection renderSection2 = bl2 ? objectListIterator.next() : objectListIterator.previous();
            if (!renderSection2.getCompiled().isEmpty(renderType)) {
                VertexBuffer vertexBuffer = renderSection2.getBuffer(renderType);
                BlockPos blockPos = renderSection2.getOrigin();
                if (uniform != null) {
                    uniform.set((float)(blockPos.getX() - x), (float)(blockPos.getY() - y), (float)(blockPos.getZ() - z));
                    uniform.upload();
                }

                vertexBuffer.bind();
                vertexBuffer.draw();
            }
        }

        if (uniform != null) {
            uniform.set(0.0F, 0.0F, 0.0F);
        }

        shaderInstance.clear();
        VertexBuffer.unbind();
        this.minecraft.getProfiler().pop();
        renderType.clearRenderState();
    }

    private void renderDebug(PoseStack poseStack, MultiBufferSource buffer, Camera camera) {
        if (this.minecraft.sectionPath || this.minecraft.sectionVisibility) {
            double d = camera.getPosition().x();
            double e = camera.getPosition().y();
            double f = camera.getPosition().z();

            for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
                SectionOcclusionGraph.Node node = this.sectionOcclusionGraph.getNode(renderSection);
                if (node != null) {
                    BlockPos blockPos = renderSection.getOrigin();
                    poseStack.pushPose();
                    poseStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
                    Matrix4f matrix4f = poseStack.last().pose();
                    if (this.minecraft.sectionPath) {
                        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.lines());
                        int i = node.step == 0 ? 0 : Mth.hsvToRgb(node.step / 50.0F, 0.9F, 0.9F);
                        int j = i >> 16 & 0xFF;
                        int k = i >> 8 & 0xFF;
                        int l = i & 0xFF;

                        for (int m = 0; m < DIRECTIONS.length; m++) {
                            if (node.hasSourceDirection(m)) {
                                Direction direction = DIRECTIONS[m];
                                vertexConsumer.addVertex(matrix4f, 8.0F, 8.0F, 8.0F)
                                    .setColor(j, k, l, 255)
                                    .setNormal(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                                vertexConsumer.addVertex(matrix4f, 8 - 16 * direction.getStepX(), 8 - 16 * direction.getStepY(), 8 - 16 * direction.getStepZ())
                                    .setColor(j, k, l, 255)
                                    .setNormal(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                            }
                        }
                    }

                    if (this.minecraft.sectionVisibility && !renderSection.getCompiled().hasNoRenderableLayers()) {
                        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.lines());
                        int i = 0;

                        for (Direction direction2 : DIRECTIONS) {
                            for (Direction direction3 : DIRECTIONS) {
                                boolean bl = renderSection.getCompiled().facesCanSeeEachother(direction2, direction3);
                                if (!bl) {
                                    i++;
                                    vertexConsumer.addVertex(
                                            matrix4f, 8 + 8 * direction2.getStepX(), 8 + 8 * direction2.getStepY(), 8 + 8 * direction2.getStepZ()
                                        )
                                        .setColor(255, 0, 0, 255)
                                        .setNormal(direction2.getStepX(), direction2.getStepY(), direction2.getStepZ());
                                    vertexConsumer.addVertex(
                                            matrix4f, 8 + 8 * direction3.getStepX(), 8 + 8 * direction3.getStepY(), 8 + 8 * direction3.getStepZ()
                                        )
                                        .setColor(255, 0, 0, 255)
                                        .setNormal(direction3.getStepX(), direction3.getStepY(), direction3.getStepZ());
                                }
                            }
                        }

                        if (i > 0) {
                            VertexConsumer vertexConsumer2 = buffer.getBuffer(RenderType.debugQuads());
                            float g = 0.5F;
                            float h = 0.2F;
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                        }
                    }

                    poseStack.popPose();
                }
            }
        }

        if (this.capturedFrustum != null) {
            poseStack.pushPose();
            poseStack.translate(
                (float)(this.frustumPos.x - camera.getPosition().x),
                (float)(this.frustumPos.y - camera.getPosition().y),
                (float)(this.frustumPos.z - camera.getPosition().z)
            );
            Matrix4f matrix4f2 = poseStack.last().pose();
            VertexConsumer vertexConsumer3 = buffer.getBuffer(RenderType.debugQuads());
            this.addFrustumQuad(vertexConsumer3, matrix4f2, 0, 1, 2, 3, 0, 1, 1);
            this.addFrustumQuad(vertexConsumer3, matrix4f2, 4, 5, 6, 7, 1, 0, 0);
            this.addFrustumQuad(vertexConsumer3, matrix4f2, 0, 1, 5, 4, 1, 1, 0);
            this.addFrustumQuad(vertexConsumer3, matrix4f2, 2, 3, 7, 6, 0, 0, 1);
            this.addFrustumQuad(vertexConsumer3, matrix4f2, 0, 4, 7, 3, 0, 1, 0);
            this.addFrustumQuad(vertexConsumer3, matrix4f2, 1, 5, 6, 2, 1, 0, 1);
            VertexConsumer vertexConsumer4 = buffer.getBuffer(RenderType.lines());
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 0);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 1);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 1);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 2);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 2);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 3);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 3);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 0);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 4);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 5);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 5);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 6);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 6);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 7);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 7);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 4);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 0);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 4);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 1);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 5);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 2);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 6);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 3);
            this.addFrustumVertex(vertexConsumer4, matrix4f2, 7);
            poseStack.popPose();
        }
    }

    private void addFrustumVertex(VertexConsumer consumer, Matrix4f matrix, int vertexIndex) {
        consumer.addVertex(matrix, this.frustumPoints[vertexIndex].x(), this.frustumPoints[vertexIndex].y(), this.frustumPoints[vertexIndex].z())
            .setColor(-16777216)
            .setNormal(0.0F, 0.0F, -1.0F);
    }

    private void addFrustumQuad(VertexConsumer consumer, Matrix4f matrix, int index1, int index2, int index3, int index4, int red, int green, int blue) {
        float f = 0.25F;
        consumer.addVertex(matrix, this.frustumPoints[index1].x(), this.frustumPoints[index1].y(), this.frustumPoints[index1].z())
            .setColor(red, green, blue, 0.25F);
        consumer.addVertex(matrix, this.frustumPoints[index2].x(), this.frustumPoints[index2].y(), this.frustumPoints[index2].z())
            .setColor(red, green, blue, 0.25F);
        consumer.addVertex(matrix, this.frustumPoints[index3].x(), this.frustumPoints[index3].y(), this.frustumPoints[index3].z())
            .setColor(red, green, blue, 0.25F);
        consumer.addVertex(matrix, this.frustumPoints[index4].x(), this.frustumPoints[index4].y(), this.frustumPoints[index4].z())
            .setColor(red, green, blue, 0.25F);
    }

    public void captureFrustum() {
        this.captureFrustum = true;
    }

    public void killFrustum() {
        this.capturedFrustum = null;
    }

    public void tick() {
        if (this.level.tickRateManager().runsNormally()) {
            this.ticks++;
        }

        if (this.ticks % 20 == 0) {
            Iterator<BlockDestructionProgress> iterator = this.destroyingBlocks.values().iterator();

            while (iterator.hasNext()) {
                BlockDestructionProgress blockDestructionProgress = iterator.next();
                int i = blockDestructionProgress.getUpdatedRenderTick();
                if (this.ticks - i > 400) {
                    iterator.remove();
                    this.removeProgress(blockDestructionProgress);
                }
            }
        }
    }

    private void removeProgress(BlockDestructionProgress progress) {
        long l = progress.getPos().asLong();
        Set<BlockDestructionProgress> set = this.destructionProgress.get(l);
        set.remove(progress);
        if (set.isEmpty()) {
            this.destructionProgress.remove(l);
        }
    }

    private void renderEndSky(PoseStack poseStack) {
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, END_SKY_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();

        for (int i = 0; i < 6; i++) {
            poseStack.pushPose();
            if (i == 1) {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }

            if (i == 2) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }

            if (i == 3) {
                poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            }

            if (i == 4) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            }

            if (i == 5) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            }

            Matrix4f matrix4f = poseStack.last().pose();
            BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferBuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(-14145496);
            bufferBuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(-14145496);
            bufferBuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(-14145496);
            bufferBuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(-14145496);
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
            poseStack.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public void renderSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup) {
        skyFogSetup.run();
        if (!isFoggy) {
            FogType fogType = camera.getFluidInCamera();
            if (fogType != FogType.POWDER_SNOW && fogType != FogType.LAVA && !this.doesMobEffectBlockSky(camera)) {
                PoseStack poseStack = new PoseStack();
                poseStack.mulPose(frustumMatrix);
                if (this.minecraft.level.effects().skyType() == DimensionSpecialEffects.SkyType.END) {
                    this.renderEndSky(poseStack);
                } else if (this.minecraft.level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL) {
                    Vec3 vec3 = this.level.getSkyColor(this.minecraft.gameRenderer.getMainCamera().getPosition(), partialTick);
                    float f = (float)vec3.x;
                    float g = (float)vec3.y;
                    float h = (float)vec3.z;
                    FogRenderer.levelFogColor();
                    Tesselator tesselator = Tesselator.getInstance();
                    RenderSystem.depthMask(false);
                    RenderSystem.setShaderColor(f, g, h, 1.0F);
                    ShaderInstance shaderInstance = RenderSystem.getShader();
                    this.skyBuffer.bind();
                    this.skyBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shaderInstance);
                    VertexBuffer.unbind();
                    RenderSystem.enableBlend();
                    float[] fs = this.level.effects().getSunriseColor(this.level.getTimeOfDay(partialTick), partialTick);
                    if (fs != null) {
                        RenderSystem.setShader(GameRenderer::getPositionColorShader);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        poseStack.pushPose();
                        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                        float i = Mth.sin(this.level.getSunAngle(partialTick)) < 0.0F ? 180.0F : 0.0F;
                        poseStack.mulPose(Axis.ZP.rotationDegrees(i));
                        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
                        float j = fs[0];
                        float k = fs[1];
                        float l = fs[2];
                        Matrix4f matrix4f = poseStack.last().pose();
                        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
                        bufferBuilder.addVertex(matrix4f, 0.0F, 100.0F, 0.0F).setColor(j, k, l, fs[3]);
                        int m = 16;

                        for (int n = 0; n <= 16; n++) {
                            float o = n * (float) (Math.PI * 2) / 16.0F;
                            float p = Mth.sin(o);
                            float q = Mth.cos(o);
                            bufferBuilder.addVertex(matrix4f, p * 120.0F, q * 120.0F, -q * 40.0F * fs[3]).setColor(fs[0], fs[1], fs[2], 0.0F);
                        }

                        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
                        poseStack.popPose();
                    }

                    RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
                    );
                    poseStack.pushPose();
                    float i = 1.0F - this.level.getRainLevel(partialTick);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, i);
                    poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                    poseStack.mulPose(Axis.XP.rotationDegrees(this.level.getTimeOfDay(partialTick) * 360.0F));
                    Matrix4f matrix4f2 = poseStack.last().pose();
                    float k = 30.0F;
                    RenderSystem.setShader(GameRenderer::getPositionTexShader);
                    RenderSystem.setShaderTexture(0, SUN_LOCATION);
                    BufferBuilder bufferBuilder2 = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                    bufferBuilder2.addVertex(matrix4f2, -k, 100.0F, -k).setUv(0.0F, 0.0F);
                    bufferBuilder2.addVertex(matrix4f2, k, 100.0F, -k).setUv(1.0F, 0.0F);
                    bufferBuilder2.addVertex(matrix4f2, k, 100.0F, k).setUv(1.0F, 1.0F);
                    bufferBuilder2.addVertex(matrix4f2, -k, 100.0F, k).setUv(0.0F, 1.0F);
                    BufferUploader.drawWithShader(bufferBuilder2.buildOrThrow());
                    k = 20.0F;
                    RenderSystem.setShaderTexture(0, MOON_LOCATION);
                    int r = this.level.getMoonPhase();
                    int s = r % 4;
                    int m = r / 4 % 2;
                    float t = (s + 0) / 4.0F;
                    float o = (m + 0) / 2.0F;
                    float p = (s + 1) / 4.0F;
                    float q = (m + 1) / 2.0F;
                    bufferBuilder2 = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                    bufferBuilder2.addVertex(matrix4f2, -k, -100.0F, k).setUv(p, q);
                    bufferBuilder2.addVertex(matrix4f2, k, -100.0F, k).setUv(t, q);
                    bufferBuilder2.addVertex(matrix4f2, k, -100.0F, -k).setUv(t, o);
                    bufferBuilder2.addVertex(matrix4f2, -k, -100.0F, -k).setUv(p, o);
                    BufferUploader.drawWithShader(bufferBuilder2.buildOrThrow());
                    float u = this.level.getStarBrightness(partialTick) * i;
                    if (u > 0.0F) {
                        RenderSystem.setShaderColor(u, u, u, u);
                        FogRenderer.setupNoFog();
                        this.starBuffer.bind();
                        this.starBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, GameRenderer.getPositionShader());
                        VertexBuffer.unbind();
                        skyFogSetup.run();
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                    poseStack.popPose();
                    RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
                    double d = this.minecraft.player.getEyePosition(partialTick).y - this.level.getLevelData().getHorizonHeight(this.level);
                    if (d < 0.0) {
                        poseStack.pushPose();
                        poseStack.translate(0.0F, 12.0F, 0.0F);
                        this.darkBuffer.bind();
                        this.darkBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shaderInstance);
                        VertexBuffer.unbind();
                        poseStack.popPose();
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.depthMask(true);
                }
            }
        }
    }

    private boolean doesMobEffectBlockSky(Camera camera) {
        return !(camera.getEntity() instanceof LivingEntity livingEntity)
            ? false
            : livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS);
    }

    public void renderClouds(PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, double camX, double camY, double camZ) {
        float f = this.level.effects().getCloudHeight();
        if (!Float.isNaN(f)) {
            float g = 12.0F;
            float h = 4.0F;
            double d = 2.0E-4;
            double e = (this.ticks + partialTick) * 0.03F;
            double i = (camX + e) / 12.0;
            double j = f - (float)camY + 0.33F;
            double k = camZ / 12.0 + 0.33F;
            i -= Mth.floor(i / 2048.0) * 2048;
            k -= Mth.floor(k / 2048.0) * 2048;
            float l = (float)(i - Mth.floor(i));
            float m = (float)(j / 4.0 - Mth.floor(j / 4.0)) * 4.0F;
            float n = (float)(k - Mth.floor(k));
            Vec3 vec3 = this.level.getCloudColor(partialTick);
            int o = (int)Math.floor(i);
            int p = (int)Math.floor(j / 4.0);
            int q = (int)Math.floor(k);
            if (o != this.prevCloudX
                || p != this.prevCloudY
                || q != this.prevCloudZ
                || this.minecraft.options.getCloudsType() != this.prevCloudsType
                || this.prevCloudColor.distanceToSqr(vec3) > 2.0E-4) {
                this.prevCloudX = o;
                this.prevCloudY = p;
                this.prevCloudZ = q;
                this.prevCloudColor = vec3;
                this.prevCloudsType = this.minecraft.options.getCloudsType();
                this.generateClouds = true;
            }

            if (this.generateClouds) {
                this.generateClouds = false;
                if (this.cloudBuffer != null) {
                    this.cloudBuffer.close();
                }

                this.cloudBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                this.cloudBuffer.bind();
                this.cloudBuffer.upload(this.buildClouds(Tesselator.getInstance(), i, j, k, vec3));
                VertexBuffer.unbind();
            }

            FogRenderer.levelFogColor();
            poseStack.pushPose();
            poseStack.mulPose(frustumMatrix);
            poseStack.scale(12.0F, 1.0F, 12.0F);
            poseStack.translate(-l, m, -n);
            if (this.cloudBuffer != null) {
                this.cloudBuffer.bind();
                int r = this.prevCloudsType == CloudStatus.FANCY ? 0 : 1;

                for (int s = r; s < 2; s++) {
                    RenderType renderType = s == 0 ? RenderType.cloudsDepthOnly() : RenderType.clouds();
                    renderType.setupRenderState();
                    ShaderInstance shaderInstance = RenderSystem.getShader();
                    this.cloudBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shaderInstance);
                    renderType.clearRenderState();
                }

                VertexBuffer.unbind();
            }

            poseStack.popPose();
        }
    }

    private MeshData buildClouds(Tesselator tesselator, double x, double y, double z, Vec3 cloudColor) {
        float f = 4.0F;
        float g = 0.00390625F;
        int i = 8;
        int j = 4;
        float h = 9.765625E-4F;
        float k = Mth.floor(x) * 0.00390625F;
        float l = Mth.floor(z) * 0.00390625F;
        float m = (float)cloudColor.x;
        float n = (float)cloudColor.y;
        float o = (float)cloudColor.z;
        float p = m * 0.9F;
        float q = n * 0.9F;
        float r = o * 0.9F;
        float s = m * 0.7F;
        float t = n * 0.7F;
        float u = o * 0.7F;
        float v = m * 0.8F;
        float w = n * 0.8F;
        float aa = o * 0.8F;
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        float ab = (float)Math.floor(y / 4.0) * 4.0F;
        if (this.prevCloudsType == CloudStatus.FANCY) {
            for (int ac = -3; ac <= 4; ac++) {
                for (int ad = -3; ad <= 4; ad++) {
                    float ae = ac * 8;
                    float af = ad * 8;
                    if (ab > -5.0F) {
                        bufferBuilder.addVertex(ae + 0.0F, ab + 0.0F, af + 8.0F)
                            .setUv((ae + 0.0F) * 0.00390625F + k, (af + 8.0F) * 0.00390625F + l)
                            .setColor(s, t, u, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                        bufferBuilder.addVertex(ae + 8.0F, ab + 0.0F, af + 8.0F)
                            .setUv((ae + 8.0F) * 0.00390625F + k, (af + 8.0F) * 0.00390625F + l)
                            .setColor(s, t, u, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                        bufferBuilder.addVertex(ae + 8.0F, ab + 0.0F, af + 0.0F)
                            .setUv((ae + 8.0F) * 0.00390625F + k, (af + 0.0F) * 0.00390625F + l)
                            .setColor(s, t, u, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                        bufferBuilder.addVertex(ae + 0.0F, ab + 0.0F, af + 0.0F)
                            .setUv((ae + 0.0F) * 0.00390625F + k, (af + 0.0F) * 0.00390625F + l)
                            .setColor(s, t, u, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                    }

                    if (ab <= 5.0F) {
                        bufferBuilder.addVertex(ae + 0.0F, ab + 4.0F - 9.765625E-4F, af + 8.0F)
                            .setUv((ae + 0.0F) * 0.00390625F + k, (af + 8.0F) * 0.00390625F + l)
                            .setColor(m, n, o, 0.8F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                        bufferBuilder.addVertex(ae + 8.0F, ab + 4.0F - 9.765625E-4F, af + 8.0F)
                            .setUv((ae + 8.0F) * 0.00390625F + k, (af + 8.0F) * 0.00390625F + l)
                            .setColor(m, n, o, 0.8F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                        bufferBuilder.addVertex(ae + 8.0F, ab + 4.0F - 9.765625E-4F, af + 0.0F)
                            .setUv((ae + 8.0F) * 0.00390625F + k, (af + 0.0F) * 0.00390625F + l)
                            .setColor(m, n, o, 0.8F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                        bufferBuilder.addVertex(ae + 0.0F, ab + 4.0F - 9.765625E-4F, af + 0.0F)
                            .setUv((ae + 0.0F) * 0.00390625F + k, (af + 0.0F) * 0.00390625F + l)
                            .setColor(m, n, o, 0.8F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                    }

                    if (ac > -1) {
                        for (int ag = 0; ag < 8; ag++) {
                            bufferBuilder.addVertex(ae + ag + 0.0F, ab + 0.0F, af + 8.0F)
                                .setUv((ae + ag + 0.5F) * 0.00390625F + k, (af + 8.0F) * 0.00390625F + l)
                                .setColor(p, q, r, 0.8F)
                                .setNormal(-1.0F, 0.0F, 0.0F);
                            bufferBuilder.addVertex(ae + ag + 0.0F, ab + 4.0F, af + 8.0F)
                                .setUv((ae + ag + 0.5F) * 0.00390625F + k, (af + 8.0F) * 0.00390625F + l)
                                .setColor(p, q, r, 0.8F)
                                .setNormal(-1.0F, 0.0F, 0.0F);
                            bufferBuilder.addVertex(ae + ag + 0.0F, ab + 4.0F, af + 0.0F)
                                .setUv((ae + ag + 0.5F) * 0.00390625F + k, (af + 0.0F) * 0.00390625F + l)
                                .setColor(p, q, r, 0.8F)
                                .setNormal(-1.0F, 0.0F, 0.0F);
                            bufferBuilder.addVertex(ae + ag + 0.0F, ab + 0.0F, af + 0.0F)
                                .setUv((ae + ag + 0.5F) * 0.00390625F + k, (af + 0.0F) * 0.00390625F + l)
                                .setColor(p, q, r, 0.8F)
                                .setNormal(-1.0F, 0.0F, 0.0F);
                        }
                    }

                    if (ac <= 1) {
                        for (int ag = 0; ag < 8; ag++) {
                            bufferBuilder.addVertex(ae + ag + 1.0F - 9.765625E-4F, ab + 0.0F, af + 8.0F)
                                .setUv((ae + ag + 0.5F) * 0.00390625F + k, (af + 8.0F) * 0.00390625F + l)
                                .setColor(p, q, r, 0.8F)
                                .setNormal(1.0F, 0.0F, 0.0F);
                            bufferBuilder.addVertex(ae + ag + 1.0F - 9.765625E-4F, ab + 4.0F, af + 8.0F)
                                .setUv((ae + ag + 0.5F) * 0.00390625F + k, (af + 8.0F) * 0.00390625F + l)
                                .setColor(p, q, r, 0.8F)
                                .setNormal(1.0F, 0.0F, 0.0F);
                            bufferBuilder.addVertex(ae + ag + 1.0F - 9.765625E-4F, ab + 4.0F, af + 0.0F)
                                .setUv((ae + ag + 0.5F) * 0.00390625F + k, (af + 0.0F) * 0.00390625F + l)
                                .setColor(p, q, r, 0.8F)
                                .setNormal(1.0F, 0.0F, 0.0F);
                            bufferBuilder.addVertex(ae + ag + 1.0F - 9.765625E-4F, ab + 0.0F, af + 0.0F)
                                .setUv((ae + ag + 0.5F) * 0.00390625F + k, (af + 0.0F) * 0.00390625F + l)
                                .setColor(p, q, r, 0.8F)
                                .setNormal(1.0F, 0.0F, 0.0F);
                        }
                    }

                    if (ad > -1) {
                        for (int ag = 0; ag < 8; ag++) {
                            bufferBuilder.addVertex(ae + 0.0F, ab + 4.0F, af + ag + 0.0F)
                                .setUv((ae + 0.0F) * 0.00390625F + k, (af + ag + 0.5F) * 0.00390625F + l)
                                .setColor(v, w, aa, 0.8F)
                                .setNormal(0.0F, 0.0F, -1.0F);
                            bufferBuilder.addVertex(ae + 8.0F, ab + 4.0F, af + ag + 0.0F)
                                .setUv((ae + 8.0F) * 0.00390625F + k, (af + ag + 0.5F) * 0.00390625F + l)
                                .setColor(v, w, aa, 0.8F)
                                .setNormal(0.0F, 0.0F, -1.0F);
                            bufferBuilder.addVertex(ae + 8.0F, ab + 0.0F, af + ag + 0.0F)
                                .setUv((ae + 8.0F) * 0.00390625F + k, (af + ag + 0.5F) * 0.00390625F + l)
                                .setColor(v, w, aa, 0.8F)
                                .setNormal(0.0F, 0.0F, -1.0F);
                            bufferBuilder.addVertex(ae + 0.0F, ab + 0.0F, af + ag + 0.0F)
                                .setUv((ae + 0.0F) * 0.00390625F + k, (af + ag + 0.5F) * 0.00390625F + l)
                                .setColor(v, w, aa, 0.8F)
                                .setNormal(0.0F, 0.0F, -1.0F);
                        }
                    }

                    if (ad <= 1) {
                        for (int ag = 0; ag < 8; ag++) {
                            bufferBuilder.addVertex(ae + 0.0F, ab + 4.0F, af + ag + 1.0F - 9.765625E-4F)
                                .setUv((ae + 0.0F) * 0.00390625F + k, (af + ag + 0.5F) * 0.00390625F + l)
                                .setColor(v, w, aa, 0.8F)
                                .setNormal(0.0F, 0.0F, 1.0F);
                            bufferBuilder.addVertex(ae + 8.0F, ab + 4.0F, af + ag + 1.0F - 9.765625E-4F)
                                .setUv((ae + 8.0F) * 0.00390625F + k, (af + ag + 0.5F) * 0.00390625F + l)
                                .setColor(v, w, aa, 0.8F)
                                .setNormal(0.0F, 0.0F, 1.0F);
                            bufferBuilder.addVertex(ae + 8.0F, ab + 0.0F, af + ag + 1.0F - 9.765625E-4F)
                                .setUv((ae + 8.0F) * 0.00390625F + k, (af + ag + 0.5F) * 0.00390625F + l)
                                .setColor(v, w, aa, 0.8F)
                                .setNormal(0.0F, 0.0F, 1.0F);
                            bufferBuilder.addVertex(ae + 0.0F, ab + 0.0F, af + ag + 1.0F - 9.765625E-4F)
                                .setUv((ae + 0.0F) * 0.00390625F + k, (af + ag + 0.5F) * 0.00390625F + l)
                                .setColor(v, w, aa, 0.8F)
                                .setNormal(0.0F, 0.0F, 1.0F);
                        }
                    }
                }
            }
        } else {
            int ac = 1;
            int ad = 32;

            for (int ah = -32; ah < 32; ah += 32) {
                for (int ai = -32; ai < 32; ai += 32) {
                    bufferBuilder.addVertex(ah + 0, ab, ai + 32)
                        .setUv((ah + 0) * 0.00390625F + k, (ai + 32) * 0.00390625F + l)
                        .setColor(m, n, o, 0.8F)
                        .setNormal(0.0F, -1.0F, 0.0F);
                    bufferBuilder.addVertex(ah + 32, ab, ai + 32)
                        .setUv((ah + 32) * 0.00390625F + k, (ai + 32) * 0.00390625F + l)
                        .setColor(m, n, o, 0.8F)
                        .setNormal(0.0F, -1.0F, 0.0F);
                    bufferBuilder.addVertex(ah + 32, ab, ai + 0)
                        .setUv((ah + 32) * 0.00390625F + k, (ai + 0) * 0.00390625F + l)
                        .setColor(m, n, o, 0.8F)
                        .setNormal(0.0F, -1.0F, 0.0F);
                    bufferBuilder.addVertex(ah + 0, ab, ai + 0)
                        .setUv((ah + 0) * 0.00390625F + k, (ai + 0) * 0.00390625F + l)
                        .setColor(m, n, o, 0.8F)
                        .setNormal(0.0F, -1.0F, 0.0F);
                }
            }
        }

        return bufferBuilder.buildOrThrow();
    }

    private void compileSections(Camera camera) {
        this.minecraft.getProfiler().push("populate_sections_to_compile");
        LevelLightEngine levelLightEngine = this.level.getLightEngine();
        RenderRegionCache renderRegionCache = new RenderRegionCache();
        BlockPos blockPos = camera.getBlockPosition();
        List<SectionRenderDispatcher.RenderSection> list = Lists.newArrayList();

        for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
            SectionPos sectionPos = SectionPos.of(renderSection.getOrigin());
            if (renderSection.isDirty() && levelLightEngine.lightOnInSection(sectionPos)) {
                boolean bl = false;
                if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.NEARBY) {
                    BlockPos blockPos2 = renderSection.getOrigin().offset(8, 8, 8);
                    bl = blockPos2.distSqr(blockPos) < 768.0 || renderSection.isDirtyFromPlayer();
                } else if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
                    bl = renderSection.isDirtyFromPlayer();
                }

                if (bl) {
                    this.minecraft.getProfiler().push("build_near_sync");
                    this.sectionRenderDispatcher.rebuildSectionSync(renderSection, renderRegionCache);
                    renderSection.setNotDirty();
                    this.minecraft.getProfiler().pop();
                } else {
                    list.add(renderSection);
                }
            }
        }

        this.minecraft.getProfiler().popPush("upload");
        this.sectionRenderDispatcher.uploadAllPendingUploads();
        this.minecraft.getProfiler().popPush("schedule_async_compile");

        for (SectionRenderDispatcher.RenderSection renderSection : list) {
            renderSection.rebuildSectionAsync(this.sectionRenderDispatcher, renderRegionCache);
            renderSection.setNotDirty();
        }

        this.minecraft.getProfiler().pop();
    }

    private void renderWorldBorder(Camera camera) {
        WorldBorder worldBorder = this.level.getWorldBorder();
        double d = this.minecraft.options.getEffectiveRenderDistance() * 16;
        if (!(camera.getPosition().x < worldBorder.getMaxX() - d)
            || !(camera.getPosition().x > worldBorder.getMinX() + d)
            || !(camera.getPosition().z < worldBorder.getMaxZ() - d)
            || !(camera.getPosition().z > worldBorder.getMinZ() + d)) {
            double e = 1.0 - worldBorder.getDistanceToBorder(camera.getPosition().x, camera.getPosition().z) / d;
            e = Math.pow(e, 4.0);
            e = Mth.clamp(e, 0.0, 1.0);
            double f = camera.getPosition().x;
            double g = camera.getPosition().z;
            double h = this.minecraft.gameRenderer.getDepthFar();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
            );
            RenderSystem.setShaderTexture(0, FORCEFIELD_LOCATION);
            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            int i = worldBorder.getStatus().getColor();
            float j = (i >> 16 & 0xFF) / 255.0F;
            float k = (i >> 8 & 0xFF) / 255.0F;
            float l = (i & 0xFF) / 255.0F;
            RenderSystem.setShaderColor(j, k, l, (float)e);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.polygonOffset(-3.0F, -3.0F);
            RenderSystem.enablePolygonOffset();
            RenderSystem.disableCull();
            float m = (float)(Util.getMillis() % 3000L) / 3000.0F;
            float n = (float)(-Mth.frac(camera.getPosition().y * 0.5));
            float o = n + (float)h;
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            double p = Math.max(Mth.floor(g - d), worldBorder.getMinZ());
            double q = Math.min(Mth.ceil(g + d), worldBorder.getMaxZ());
            float r = (Mth.floor(p) & 1) * 0.5F;
            if (f > worldBorder.getMaxX() - d) {
                float s = r;

                for (double t = p; t < q; s += 0.5F) {
                    double u = Math.min(1.0, q - t);
                    float v = (float)u * 0.5F;
                    bufferBuilder.addVertex((float)(worldBorder.getMaxX() - f), (float)(-h), (float)(t - g)).setUv(m - s, m + o);
                    bufferBuilder.addVertex((float)(worldBorder.getMaxX() - f), (float)(-h), (float)(t + u - g)).setUv(m - (v + s), m + o);
                    bufferBuilder.addVertex((float)(worldBorder.getMaxX() - f), (float)h, (float)(t + u - g)).setUv(m - (v + s), m + n);
                    bufferBuilder.addVertex((float)(worldBorder.getMaxX() - f), (float)h, (float)(t - g)).setUv(m - s, m + n);
                    t++;
                }
            }

            if (f < worldBorder.getMinX() + d) {
                float s = r;

                for (double t = p; t < q; s += 0.5F) {
                    double u = Math.min(1.0, q - t);
                    float v = (float)u * 0.5F;
                    bufferBuilder.addVertex((float)(worldBorder.getMinX() - f), (float)(-h), (float)(t - g)).setUv(m + s, m + o);
                    bufferBuilder.addVertex((float)(worldBorder.getMinX() - f), (float)(-h), (float)(t + u - g)).setUv(m + v + s, m + o);
                    bufferBuilder.addVertex((float)(worldBorder.getMinX() - f), (float)h, (float)(t + u - g)).setUv(m + v + s, m + n);
                    bufferBuilder.addVertex((float)(worldBorder.getMinX() - f), (float)h, (float)(t - g)).setUv(m + s, m + n);
                    t++;
                }
            }

            p = Math.max(Mth.floor(f - d), worldBorder.getMinX());
            q = Math.min(Mth.ceil(f + d), worldBorder.getMaxX());
            r = (Mth.floor(p) & 1) * 0.5F;
            if (g > worldBorder.getMaxZ() - d) {
                float s = r;

                for (double t = p; t < q; s += 0.5F) {
                    double u = Math.min(1.0, q - t);
                    float v = (float)u * 0.5F;
                    bufferBuilder.addVertex((float)(t - f), (float)(-h), (float)(worldBorder.getMaxZ() - g)).setUv(m + s, m + o);
                    bufferBuilder.addVertex((float)(t + u - f), (float)(-h), (float)(worldBorder.getMaxZ() - g)).setUv(m + v + s, m + o);
                    bufferBuilder.addVertex((float)(t + u - f), (float)h, (float)(worldBorder.getMaxZ() - g)).setUv(m + v + s, m + n);
                    bufferBuilder.addVertex((float)(t - f), (float)h, (float)(worldBorder.getMaxZ() - g)).setUv(m + s, m + n);
                    t++;
                }
            }

            if (g < worldBorder.getMinZ() + d) {
                float s = r;

                for (double t = p; t < q; s += 0.5F) {
                    double u = Math.min(1.0, q - t);
                    float v = (float)u * 0.5F;
                    bufferBuilder.addVertex((float)(t - f), (float)(-h), (float)(worldBorder.getMinZ() - g)).setUv(m - s, m + o);
                    bufferBuilder.addVertex((float)(t + u - f), (float)(-h), (float)(worldBorder.getMinZ() - g)).setUv(m - (v + s), m + o);
                    bufferBuilder.addVertex((float)(t + u - f), (float)h, (float)(worldBorder.getMinZ() - g)).setUv(m - (v + s), m + n);
                    bufferBuilder.addVertex((float)(t - f), (float)h, (float)(worldBorder.getMinZ() - g)).setUv(m - s, m + n);
                    t++;
                }
            }

            MeshData meshData = bufferBuilder.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }

            RenderSystem.enableCull();
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
        }
    }

    private void renderHitOutline(
        PoseStack poseStack, VertexConsumer consumer, Entity entity, double camX, double camY, double camZ, BlockPos pos, BlockState state
    ) {
        renderShape(
            poseStack,
            consumer,
            state.getShape(this.level, pos, CollisionContext.of(entity)),
            pos.getX() - camX,
            pos.getY() - camY,
            pos.getZ() - camZ,
            0.0F,
            0.0F,
            0.0F,
            0.4F
        );
    }

    private static Vec3 mixColor(float hue) {
        float f = 5.99999F;
        int i = (int)(Mth.clamp(hue, 0.0F, 1.0F) * 5.99999F);
        float g = hue * 5.99999F - i;

        return switch (i) {
            case 0 -> new Vec3(1.0, g, 0.0);
            case 1 -> new Vec3(1.0F - g, 1.0, 0.0);
            case 2 -> new Vec3(0.0, 1.0, g);
            case 3 -> new Vec3(0.0, 1.0 - g, 1.0);
            case 4 -> new Vec3(g, 0.0, 1.0);
            case 5 -> new Vec3(1.0, 0.0, 1.0 - g);
            default -> throw new IllegalStateException("Unexpected value: " + i);
        };
    }

    private static Vec3 shiftHue(float red, float green, float blue, float hue) {
        Vec3 vec3 = mixColor(hue).scale(red);
        Vec3 vec32 = mixColor((hue + 0.33333334F) % 1.0F).scale(green);
        Vec3 vec33 = mixColor((hue + 0.6666667F) % 1.0F).scale(blue);
        Vec3 vec34 = vec3.add(vec32).add(vec33);
        double d = Math.max(Math.max(1.0, vec34.x), Math.max(vec34.y, vec34.z));
        return new Vec3(vec34.x / d, vec34.y / d, vec34.z / d);
    }

    public static void renderVoxelShape(
        PoseStack poseStack,
        VertexConsumer consumer,
        VoxelShape shape,
        double x,
        double y,
        double z,
        float red,
        float green,
        float blue,
        float alpha,
        boolean bl
    ) {
        List<AABB> list = shape.toAabbs();
        if (!list.isEmpty()) {
            int i = bl ? list.size() : list.size() * 8;
            renderShape(poseStack, consumer, Shapes.create(list.get(0)), x, y, z, red, green, blue, alpha);

            for (int j = 1; j < list.size(); j++) {
                AABB aABB = list.get(j);
                float f = (float)j / i;
                Vec3 vec3 = shiftHue(red, green, blue, f);
                renderShape(poseStack, consumer, Shapes.create(aABB), x, y, z, (float)vec3.x, (float)vec3.y, (float)vec3.z, alpha);
            }
        }
    }

    private static void renderShape(
        PoseStack poseStack, VertexConsumer consumer, VoxelShape shape, double x, double y, double z, float red, float green, float blue, float alpha
    ) {
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            float k = (float)(maxX - minX);
            float l = (float)(maxY - minY);
            float m = (float)(maxZ - minZ);
            float n = Mth.sqrt(k * k + l * l + m * m);
            k /= n;
            l /= n;
            m /= n;
            consumer.addVertex(pose, (float)(minX + x), (float)(minY + y), (float)(minZ + z)).setColor(red, green, blue, alpha).setNormal(pose, k, l, m);
            consumer.addVertex(pose, (float)(maxX + x), (float)(maxY + y), (float)(maxZ + z)).setColor(red, green, blue, alpha).setNormal(pose, k, l, m);
        });
    }

    public static void renderLineBox(
        VertexConsumer consumer, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float red, float green, float blue, float alpha
    ) {
        renderLineBox(new PoseStack(), consumer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha, red, green, blue);
    }

    public static void renderLineBox(PoseStack poseStack, VertexConsumer buffer, AABB box, float red, float green, float blue, float alpha) {
        renderLineBox(poseStack, buffer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha, red, green, blue);
    }

    public static void renderLineBox(
        PoseStack poseStack,
        VertexConsumer consumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        renderLineBox(poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha, red, green, blue);
    }

    public static void renderLineBox(
        PoseStack poseStack,
        VertexConsumer consumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha,
        float red2,
        float green2,
        float blue2
    ) {
        PoseStack.Pose pose = poseStack.last();
        float f = (float)minX;
        float g = (float)minY;
        float h = (float)minZ;
        float i = (float)maxX;
        float j = (float)maxY;
        float k = (float)maxZ;
        consumer.addVertex(pose, f, g, h).setColor(red, green2, blue2, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(pose, i, g, h).setColor(red, green2, blue2, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(pose, f, g, h).setColor(red2, green, blue2, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, f, j, h).setColor(red2, green, blue2, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, f, g, h).setColor(red2, green2, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, f, g, k).setColor(red2, green2, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, i, g, h).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, i, j, h).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, i, j, h).setColor(red, green, blue, alpha).setNormal(pose, -1.0F, 0.0F, 0.0F);
        consumer.addVertex(pose, f, j, h).setColor(red, green, blue, alpha).setNormal(pose, -1.0F, 0.0F, 0.0F);
        consumer.addVertex(pose, f, j, h).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, f, j, k).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, f, j, k).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, -1.0F, 0.0F);
        consumer.addVertex(pose, f, g, k).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, -1.0F, 0.0F);
        consumer.addVertex(pose, f, g, k).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(pose, i, g, k).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(pose, i, g, k).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, -1.0F);
        consumer.addVertex(pose, i, g, h).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, -1.0F);
        consumer.addVertex(pose, f, j, k).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(pose, i, j, k).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(pose, i, g, k).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, i, j, k).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, i, j, h).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, i, j, k).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    public static void addChainedFilledBoxVertices(
        PoseStack poseStack,
        VertexConsumer consumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        addChainedFilledBoxVertices(poseStack, consumer, (float)minX, (float)minY, (float)minZ, (float)maxX, (float)maxY, (float)maxZ, red, green, blue, alpha);
    }

    public static void addChainedFilledBoxVertices(
        PoseStack poseStack,
        VertexConsumer consumer,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        Matrix4f matrix4f = poseStack.last().pose();
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
    }

    public static void renderFace(
        PoseStack poseStack,
        VertexConsumer buffer,
        Direction face,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        Matrix4f matrix4f = poseStack.last().pose();
        switch (face) {
            case DOWN:
                buffer.addVertex(matrix4f, x1, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y1, z2).setColor(red, green, blue, alpha);
                break;
            case UP:
                buffer.addVertex(matrix4f, x1, y2, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z1).setColor(red, green, blue, alpha);
                break;
            case NORTH:
                buffer.addVertex(matrix4f, x1, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z1).setColor(red, green, blue, alpha);
                break;
            case SOUTH:
                buffer.addVertex(matrix4f, x1, y1, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z2).setColor(red, green, blue, alpha);
                break;
            case WEST:
                buffer.addVertex(matrix4f, x1, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y1, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z1).setColor(red, green, blue, alpha);
                break;
            case EAST:
                buffer.addVertex(matrix4f, x2, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z2).setColor(red, green, blue, alpha);
        }
    }

    public void blockChanged(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        this.setBlockDirty(pos, (flags & 8) != 0);
    }

    private void setBlockDirty(BlockPos pos, boolean reRenderOnMainThread) {
        for (int i = pos.getZ() - 1; i <= pos.getZ() + 1; i++) {
            for (int j = pos.getX() - 1; j <= pos.getX() + 1; j++) {
                for (int k = pos.getY() - 1; k <= pos.getY() + 1; k++) {
                    this.setSectionDirty(
                        SectionPos.blockToSectionCoord(j), SectionPos.blockToSectionCoord(k), SectionPos.blockToSectionCoord(i), reRenderOnMainThread
                    );
                }
            }
        }
    }

    /**
     * Re-renders all blocks in the specified range.
     */
    public void setBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int i = minZ - 1; i <= maxZ + 1; i++) {
            for (int j = minX - 1; j <= maxX + 1; j++) {
                for (int k = minY - 1; k <= maxY + 1; k++) {
                    this.setSectionDirty(SectionPos.blockToSectionCoord(j), SectionPos.blockToSectionCoord(k), SectionPos.blockToSectionCoord(i));
                }
            }
        }
    }

    public void setBlockDirty(BlockPos pos, BlockState oldState, BlockState newState) {
        if (this.minecraft.getModelManager().requiresRender(oldState, newState)) {
            this.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public void setSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ) {
        for (int i = sectionZ - 1; i <= sectionZ + 1; i++) {
            for (int j = sectionX - 1; j <= sectionX + 1; j++) {
                for (int k = sectionY - 1; k <= sectionY + 1; k++) {
                    this.setSectionDirty(j, k, i);
                }
            }
        }
    }

    public void setSectionDirty(int sectionX, int sectionY, int sectionZ) {
        this.setSectionDirty(sectionX, sectionY, sectionZ, false);
    }

    private void setSectionDirty(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread) {
        this.viewArea.setDirty(sectionX, sectionY, sectionZ, reRenderOnMainThread);
    }

    public void playJukeboxSong(Holder<JukeboxSong> song, BlockPos pos) {
        if (this.level != null) {
            this.stopJukeboxSong(pos);
            JukeboxSong jukeboxSong = song.value();
            SoundEvent soundEvent = jukeboxSong.soundEvent().value();
            SoundInstance soundInstance = SimpleSoundInstance.forJukeboxSong(soundEvent, Vec3.atCenterOf(pos));
            this.playingJukeboxSongs.put(pos, soundInstance);
            this.minecraft.getSoundManager().play(soundInstance);
            this.minecraft.gui.setNowPlaying(jukeboxSong.description());
            this.notifyNearbyEntities(this.level, pos, true);
        }
    }

    private void stopJukeboxSong(BlockPos pos) {
        SoundInstance soundInstance = this.playingJukeboxSongs.remove(pos);
        if (soundInstance != null) {
            this.minecraft.getSoundManager().stop(soundInstance);
        }
    }

    public void stopJukeboxSongAndNotifyNearby(BlockPos pos) {
        this.stopJukeboxSong(pos);
        if (this.level != null) {
            this.notifyNearbyEntities(this.level, pos, false);
        }
    }

    /**
     * Notifies living entities in a 3 block range of the specified {@code pos} that a record is or isn't playing nearby, dependent on the specified {@code playing} parameter.
     * This is used to make parrots start or stop partying.
     */
    private void notifyNearbyEntities(Level level, BlockPos pos, boolean playing) {
        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(3.0))) {
            livingEntity.setRecordPlayingNearby(pos, playing);
        }
    }

    /**
     * @param force if {@code true}, the particle will be created regardless of its
     *              distance from the camera and the {@linkplain #
     *              calculateParticleLevel(boolean) calculated particle level}
     */
    public void addParticle(ParticleOptions options, boolean force, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this.addParticle(options, force, false, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * @param force     if {@code true}, the particle will be created regardless of
     *                  its distance from the camera and the {@linkplain #
     *                  calculateParticleLevel(boolean) calculated particle level}
     * @param decreased if {@code true}, and the {@linkplain
     *                  net.minecraft.client.Options#particles particles option} is
     *                  set to minimal, attempts to spawn the particle at a decreased
     *                  level
     */
    public void addParticle(
        ParticleOptions options, boolean force, boolean decreased, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        try {
            this.addParticleInternal(options, force, decreased, x, y, z, xSpeed, ySpeed, zSpeed);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception while adding particle");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Particle being added");
            crashReportCategory.setDetail("ID", BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()));
            crashReportCategory.setDetail(
                "Parameters",
                () -> ParticleTypes.CODEC.encodeStart(this.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), options).toString()
            );
            crashReportCategory.setDetail("Position", () -> CrashReportCategory.formatLocation(this.level, x, y, z));
            throw new ReportedException(crashReport);
        }
    }

    private <T extends ParticleOptions> void addParticle(T options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this.addParticle(options, options.getType().getOverrideLimiter(), x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * @param force if {@code true}, the particle will be created regardless of its
     *              distance from the camera and the {@linkplain #
     *              calculateParticleLevel(boolean) calculated particle level}
     */
    @Nullable
    private Particle addParticleInternal(ParticleOptions options, boolean force, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        return this.addParticleInternal(options, force, false, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * @param force     if {@code true}, the particle will be created regardless of
     *                  its distance from the camera and the {@linkplain #
     *                  calculateParticleLevel(boolean) calculated particle level}
     * @param decreased if {@code true}, and the {@linkplain
     *                  net.minecraft.client.Options#particles particles option} is
     *                  set to minimal, attempts to spawn the particle at a decreased
     *                  level
     */
    @Nullable
    private Particle addParticleInternal(
        ParticleOptions options, boolean force, boolean decreased, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        ParticleStatus particleStatus = this.calculateParticleLevel(decreased);
        if (force) {
            return this.minecraft.particleEngine.createParticle(options, x, y, z, xSpeed, ySpeed, zSpeed);
        } else if (camera.getPosition().distanceToSqr(x, y, z) > 1024.0) {
            return null;
        } else {
            return particleStatus == ParticleStatus.MINIMAL ? null : this.minecraft.particleEngine.createParticle(options, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }

    /**
     * Calculates the level of particles to use based on the {@linkplain net.minecraft.client.Options#particles particles option} and the specified {@code decreased} parameter. This leads to randomly generating more or less particles than the set option.
     *
     * @param decreased if {@code true}, and the {@linkplain
     *                  net.minecraft.client.Options#particles particles option} is
     *                  set to minimal, has a 1 in 10 chance to return a decreased
     *                  level and a further 1 in 3 chance to minimise it
     */
    private ParticleStatus calculateParticleLevel(boolean decreased) {
        ParticleStatus particleStatus = this.minecraft.options.particles().get();
        if (decreased && particleStatus == ParticleStatus.MINIMAL && this.level.random.nextInt(10) == 0) {
            particleStatus = ParticleStatus.DECREASED;
        }

        if (particleStatus == ParticleStatus.DECREASED && this.level.random.nextInt(3) == 0) {
            particleStatus = ParticleStatus.MINIMAL;
        }

        return particleStatus;
    }

    public void clear() {
    }

    /**
     * Handles a global level event. This includes playing sounds that should be heard by any player, regardless of position and dimension, such as the Wither spawning.
     *
     * @param type the type of level event to handle. This method only handles {@
     *             linkplain net.minecraft.world.level.block.LevelEvent#
     *             SOUND_WITHER_BOSS_SPAWN the wither boss spawn sound}, {@linkplain
     *             net.minecraft.world.level.block.LevelEvent#SOUND_DRAGON_DEATH the
     *             dragon's death sound}, and {@linkplain
     *             net.minecraft.world.level.block.LevelEvent#SOUND_END_PORTAL_SPAWN
     *             the end portal spawn sound}.
     */
    public void globalLevelEvent(int type, BlockPos pos, int data) {
        switch (type) {
            case 1023:
            case 1028:
            case 1038:
                Camera camera = this.minecraft.gameRenderer.getMainCamera();
                if (camera.isInitialized()) {
                    double d = pos.getX() - camera.getPosition().x;
                    double e = pos.getY() - camera.getPosition().y;
                    double f = pos.getZ() - camera.getPosition().z;
                    double g = Math.sqrt(d * d + e * e + f * f);
                    double h = camera.getPosition().x;
                    double i = camera.getPosition().y;
                    double j = camera.getPosition().z;
                    if (g > 0.0) {
                        h += d / g * 2.0;
                        i += e / g * 2.0;
                        j += f / g * 2.0;
                    }

                    if (type == 1023) {
                        this.level.playLocalSound(h, i, j, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F, false);
                    } else if (type == 1038) {
                        this.level.playLocalSound(h, i, j, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F, false);
                    } else {
                        this.level.playLocalSound(h, i, j, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 5.0F, 1.0F, false);
                    }
                }
        }
    }

    public void levelEvent(int type, BlockPos pos, int data) {
        RandomSource randomSource = this.level.random;
        switch (type) {
            case 1000:
                this.level.playLocalSound(pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1001:
                this.level.playLocalSound(pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0F, 1.2F, false);
                break;
            case 1002:
                this.level.playLocalSound(pos, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0F, 1.2F, false);
                break;
            case 1004:
                this.level.playLocalSound(pos, SoundEvents.FIREWORK_ROCKET_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.2F, false);
                break;
            case 1009:
                if (data == 0) {
                    this.level
                        .playLocalSound(
                            pos,
                            SoundEvents.FIRE_EXTINGUISH,
                            SoundSource.BLOCKS,
                            0.5F,
                            2.6F + (randomSource.nextFloat() - randomSource.nextFloat()) * 0.8F,
                            false
                        );
                } else if (data == 1) {
                    this.level
                        .playLocalSound(
                            pos,
                            SoundEvents.GENERIC_EXTINGUISH_FIRE,
                            SoundSource.BLOCKS,
                            0.7F,
                            1.6F + (randomSource.nextFloat() - randomSource.nextFloat()) * 0.4F,
                            false
                        );
                }
                break;
            case 1010:
                this.level
                    .registryAccess()
                    .registryOrThrow(Registries.JUKEBOX_SONG)
                    .getHolder(data)
                    .ifPresent(reference -> this.playJukeboxSong(reference, pos));
                break;
            case 1011:
                this.stopJukeboxSongAndNotifyNearby(pos);
                break;
            case 1015:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.GHAST_WARN, SoundSource.HOSTILE, 10.0F, (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F, false
                    );
                break;
            case 1016:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 10.0F, (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F, false
                    );
                break;
            case 1017:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ENDER_DRAGON_SHOOT,
                        SoundSource.HOSTILE,
                        10.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1018:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F, false
                    );
                break;
            case 1019:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1020:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_ATTACK_IRON_DOOR,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1021:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1022:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.WITHER_BREAK_BLOCK,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1024:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.0F, (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F, false
                    );
                break;
            case 1025:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.BAT_TAKEOFF, SoundSource.NEUTRAL, 0.05F, (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F, false
                    );
                break;
            case 1026:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 2.0F, (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F, false
                    );
                break;
            case 1027:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1029:
                this.level.playLocalSound(pos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.0F, randomSource.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1030:
                this.level.playLocalSound(pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, randomSource.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1031:
                this.level.playLocalSound(pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1032:
                this.minecraft
                    .getSoundManager()
                    .play(SimpleSoundInstance.forLocalAmbience(SoundEvents.PORTAL_TRAVEL, randomSource.nextFloat() * 0.4F + 0.8F, 0.25F));
                break;
            case 1033:
                this.level.playLocalSound(pos, SoundEvents.CHORUS_FLOWER_GROW, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1034:
                this.level.playLocalSound(pos, SoundEvents.CHORUS_FLOWER_DEATH, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1035:
                this.level.playLocalSound(pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1039:
                this.level.playLocalSound(pos, SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 0.3F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1040:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_CONVERTED_TO_DROWNED,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1041:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.HUSK_CONVERTED_TO_ZOMBIE,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1042:
                this.level.playLocalSound(pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1043:
                this.level.playLocalSound(pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1044:
                this.level.playLocalSound(pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1045:
                this.level
                    .playLocalSound(pos, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS, 2.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1046:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA_INTO_CAULDRON,
                        SoundSource.BLOCKS,
                        2.0F,
                        this.level.random.nextFloat() * 0.1F + 0.9F,
                        false
                    );
                break;
            case 1047:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON,
                        SoundSource.BLOCKS,
                        2.0F,
                        this.level.random.nextFloat() * 0.1F + 0.9F,
                        false
                    );
                break;
            case 1048:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.SKELETON_CONVERTED_TO_STRAY,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1049:
                this.level.playLocalSound(pos, SoundEvents.CRAFTER_CRAFT, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1050:
                this.level.playLocalSound(pos, SoundEvents.CRAFTER_FAIL, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1051:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.WIND_CHARGE_THROW, SoundSource.BLOCKS, 0.5F, 0.4F / (this.level.getRandom().nextFloat() * 0.4F + 0.8F), false
                    );
            case 2010:
                this.shootParticles(data, pos, randomSource, ParticleTypes.WHITE_SMOKE);
                break;
            case 1500:
                ComposterBlock.handleFill(this.level, pos, data > 0);
                break;
            case 1501:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (randomSource.nextFloat() - randomSource.nextFloat()) * 0.8F, false
                    );

                for (int m = 0; m < 8; m++) {
                    this.level
                        .addParticle(
                            ParticleTypes.LARGE_SMOKE,
                            pos.getX() + randomSource.nextDouble(),
                            pos.getY() + 1.2,
                            pos.getZ() + randomSource.nextDouble(),
                            0.0,
                            0.0,
                            0.0
                        );
                }
                break;
            case 1502:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.REDSTONE_TORCH_BURNOUT,
                        SoundSource.BLOCKS,
                        0.5F,
                        2.6F + (randomSource.nextFloat() - randomSource.nextFloat()) * 0.8F,
                        false
                    );

                for (int m = 0; m < 5; m++) {
                    double g = pos.getX() + randomSource.nextDouble() * 0.6 + 0.2;
                    double n = pos.getY() + randomSource.nextDouble() * 0.6 + 0.2;
                    double o = pos.getZ() + randomSource.nextDouble() * 0.6 + 0.2;
                    this.level.addParticle(ParticleTypes.SMOKE, g, n, o, 0.0, 0.0, 0.0);
                }
                break;
            case 1503:
                this.level.playLocalSound(pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.0F, false);

                for (int m = 0; m < 16; m++) {
                    double g = pos.getX() + (5.0 + randomSource.nextDouble() * 6.0) / 16.0;
                    double n = pos.getY() + 0.8125;
                    double o = pos.getZ() + (5.0 + randomSource.nextDouble() * 6.0) / 16.0;
                    this.level.addParticle(ParticleTypes.SMOKE, g, n, o, 0.0, 0.0, 0.0);
                }
                break;
            case 1504:
                PointedDripstoneBlock.spawnDripParticle(this.level, pos, this.level.getBlockState(pos));
                break;
            case 1505:
                BoneMealItem.addGrowthParticles(this.level, pos, data);
                this.level.playLocalSound(pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 2000:
                this.shootParticles(data, pos, randomSource, ParticleTypes.SMOKE);
                break;
            case 2001:
                BlockState blockState = Block.stateById(data);
                if (!blockState.isAir()) {
                    SoundType soundType = blockState.getSoundType();
                    this.level
                        .playLocalSound(
                            pos, soundType.getBreakSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F, false
                        );
                }

                this.level.addDestroyBlockEffect(pos, blockState);
                break;
            case 2002:
            case 2007:
                Vec3 vec3 = Vec3.atBottomCenterOf(pos);

                for (int j = 0; j < 8; j++) {
                    this.addParticle(
                        new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SPLASH_POTION)),
                        vec3.x,
                        vec3.y,
                        vec3.z,
                        randomSource.nextGaussian() * 0.15,
                        randomSource.nextDouble() * 0.2,
                        randomSource.nextGaussian() * 0.15
                    );
                }

                float h = (data >> 16 & 0xFF) / 255.0F;
                float k = (data >> 8 & 0xFF) / 255.0F;
                float l = (data >> 0 & 0xFF) / 255.0F;
                ParticleOptions particleOptions = type == 2007 ? ParticleTypes.INSTANT_EFFECT : ParticleTypes.EFFECT;

                for (int m = 0; m < 100; m++) {
                    double g = randomSource.nextDouble() * 4.0;
                    double n = randomSource.nextDouble() * Math.PI * 2.0;
                    double o = Math.cos(n) * g;
                    double p = 0.01 + randomSource.nextDouble() * 0.5;
                    double q = Math.sin(n) * g;
                    Particle particle = this.addParticleInternal(
                        particleOptions, particleOptions.getType().getOverrideLimiter(), vec3.x + o * 0.1, vec3.y + 0.3, vec3.z + q * 0.1, o, p, q
                    );
                    if (particle != null) {
                        float r = 0.75F + randomSource.nextFloat() * 0.25F;
                        particle.setColor(h * r, k * r, l * r);
                        particle.setPower((float)g);
                    }
                }

                this.level.playLocalSound(pos, SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL, 1.0F, randomSource.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 2003:
                double d = pos.getX() + 0.5;
                double e = pos.getY();
                double f = pos.getZ() + 0.5;

                for (int i = 0; i < 8; i++) {
                    this.addParticle(
                        new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ENDER_EYE)),
                        d,
                        e,
                        f,
                        randomSource.nextGaussian() * 0.15,
                        randomSource.nextDouble() * 0.2,
                        randomSource.nextGaussian() * 0.15
                    );
                }

                for (double g = 0.0; g < Math.PI * 2; g += Math.PI / 20) {
                    this.addParticle(ParticleTypes.PORTAL, d + Math.cos(g) * 5.0, e - 0.4, f + Math.sin(g) * 5.0, Math.cos(g) * -5.0, 0.0, Math.sin(g) * -5.0);
                    this.addParticle(ParticleTypes.PORTAL, d + Math.cos(g) * 5.0, e - 0.4, f + Math.sin(g) * 5.0, Math.cos(g) * -7.0, 0.0, Math.sin(g) * -7.0);
                }
                break;
            case 2004:
                for (int sx = 0; sx < 20; sx++) {
                    double t = pos.getX() + 0.5 + (randomSource.nextDouble() - 0.5) * 2.0;
                    double u = pos.getY() + 0.5 + (randomSource.nextDouble() - 0.5) * 2.0;
                    double v = pos.getZ() + 0.5 + (randomSource.nextDouble() - 0.5) * 2.0;
                    this.level.addParticle(ParticleTypes.SMOKE, t, u, v, 0.0, 0.0, 0.0);
                    this.level.addParticle(ParticleTypes.FLAME, t, u, v, 0.0, 0.0, 0.0);
                }
                break;
            case 2006:
                for (int m = 0; m < 200; m++) {
                    float ab = randomSource.nextFloat() * 4.0F;
                    float ag = randomSource.nextFloat() * (float) (Math.PI * 2);
                    double n = Mth.cos(ag) * ab;
                    double o = 0.01 + randomSource.nextDouble() * 0.5;
                    double p = Mth.sin(ag) * ab;
                    Particle particle2 = this.addParticleInternal(
                        ParticleTypes.DRAGON_BREATH, false, pos.getX() + n * 0.1, pos.getY() + 0.3, pos.getZ() + p * 0.1, n, o, p
                    );
                    if (particle2 != null) {
                        particle2.setPower(ab);
                    }
                }

                if (data == 1) {
                    this.level
                        .playLocalSound(pos, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 1.0F, randomSource.nextFloat() * 0.1F + 0.9F, false);
                }
                break;
            case 2008:
                this.level.addParticle(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
                break;
            case 2009:
                for (int m = 0; m < 8; m++) {
                    this.level
                        .addParticle(
                            ParticleTypes.CLOUD,
                            pos.getX() + randomSource.nextDouble(),
                            pos.getY() + 1.2,
                            pos.getZ() + randomSource.nextDouble(),
                            0.0,
                            0.0,
                            0.0
                        );
                }
                break;
            case 2011:
                ParticleUtils.spawnParticleInBlock(this.level, pos, data, ParticleTypes.HAPPY_VILLAGER);
                break;
            case 2012:
                ParticleUtils.spawnParticleInBlock(this.level, pos, data, ParticleTypes.HAPPY_VILLAGER);
                break;
            case 2013:
                ParticleUtils.spawnSmashAttackParticles(this.level, pos, data);
                break;
            case 3000:
                this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.END_GATEWAY_SPAWN,
                        SoundSource.BLOCKS,
                        10.0F,
                        (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F,
                        false
                    );
                break;
            case 3001:
                this.level.playLocalSound(pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 64.0F, 0.8F + this.level.random.nextFloat() * 0.3F, false);
                break;
            case 3002:
                if (data >= 0 && data < Direction.Axis.VALUES.length) {
                    ParticleUtils.spawnParticlesAlongAxis(
                        Direction.Axis.VALUES[data], this.level, pos, 0.125, ParticleTypes.ELECTRIC_SPARK, UniformInt.of(10, 19)
                    );
                } else {
                    ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.ELECTRIC_SPARK, UniformInt.of(3, 5));
                }
                break;
            case 3003:
                ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.WAX_ON, UniformInt.of(3, 5));
                this.level.playLocalSound(pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 3004:
                ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.WAX_OFF, UniformInt.of(3, 5));
                break;
            case 3005:
                ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.SCRAPE, UniformInt.of(3, 5));
                break;
            case 3006:
                int s = data >> 6;
                if (s > 0) {
                    if (randomSource.nextFloat() < 0.3F + s * 0.1F) {
                        float l = 0.15F + 0.02F * s * s * randomSource.nextFloat();
                        float w = 0.4F + 0.3F * s * randomSource.nextFloat();
                        this.level.playLocalSound(pos, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.BLOCKS, l, w, false);
                    }

                    byte b = (byte)(data & 63);
                    IntProvider intProvider = UniformInt.of(0, s);
                    float x = 0.005F;
                    Supplier<Vec3> supplier = () -> new Vec3(
                        Mth.nextDouble(randomSource, -0.005F, 0.005F),
                        Mth.nextDouble(randomSource, -0.005F, 0.005F),
                        Mth.nextDouble(randomSource, -0.005F, 0.005F)
                    );
                    if (b == 0) {
                        for (Direction direction : Direction.values()) {
                            float y = direction == Direction.DOWN ? (float) Math.PI : 0.0F;
                            double p = direction.getAxis() == Direction.Axis.Y ? 0.65 : 0.57;
                            ParticleUtils.spawnParticlesOnBlockFace(this.level, pos, new SculkChargeParticleOptions(y), intProvider, direction, supplier, p);
                        }
                    } else {
                        for (Direction direction2 : MultifaceBlock.unpack(b)) {
                            float z = direction2 == Direction.UP ? (float) Math.PI : 0.0F;
                            double o = 0.35;
                            ParticleUtils.spawnParticlesOnBlockFace(this.level, pos, new SculkChargeParticleOptions(z), intProvider, direction2, supplier, 0.35);
                        }
                    }
                } else {
                    this.level.playLocalSound(pos, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                    boolean bl = this.level.getBlockState(pos).isCollisionShapeFullBlock(this.level, pos);
                    int aa = bl ? 40 : 20;
                    float x = bl ? 0.45F : 0.25F;
                    float ab = 0.07F;

                    for (int ac = 0; ac < aa; ac++) {
                        float ad = 2.0F * randomSource.nextFloat() - 1.0F;
                        float z = 2.0F * randomSource.nextFloat() - 1.0F;
                        float ae = 2.0F * randomSource.nextFloat() - 1.0F;
                        this.level
                            .addParticle(
                                ParticleTypes.SCULK_CHARGE_POP,
                                pos.getX() + 0.5 + ad * x,
                                pos.getY() + 0.5 + z * x,
                                pos.getZ() + 0.5 + ae * x,
                                ad * 0.07F,
                                z * 0.07F,
                                ae * 0.07F
                            );
                    }
                }
                break;
            case 3007:
                for (int af = 0; af < 10; af++) {
                    this.level
                        .addParticle(
                            new ShriekParticleOption(af * 5), false, pos.getX() + 0.5, pos.getY() + SculkShriekerBlock.TOP_Y, pos.getZ() + 0.5, 0.0, 0.0, 0.0
                        );
                }

                BlockState blockState3 = this.level.getBlockState(pos);
                boolean bl2 = blockState3.hasProperty(BlockStateProperties.WATERLOGGED) && blockState3.getValue(BlockStateProperties.WATERLOGGED);
                if (!bl2) {
                    this.level
                        .playLocalSound(
                            pos.getX() + 0.5,
                            pos.getY() + SculkShriekerBlock.TOP_Y,
                            pos.getZ() + 0.5,
                            SoundEvents.SCULK_SHRIEKER_SHRIEK,
                            SoundSource.BLOCKS,
                            2.0F,
                            0.6F + this.level.random.nextFloat() * 0.4F,
                            false
                        );
                }
                break;
            case 3008:
                BlockState blockState2 = Block.stateById(data);
                if (blockState2.getBlock() instanceof BrushableBlock brushableBlock) {
                    this.level.playLocalSound(pos, brushableBlock.getBrushCompletedSound(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
                }

                this.level.addDestroyBlockEffect(pos, blockState2);
                break;
            case 3009:
                ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.EGG_CRACK, UniformInt.of(3, 6));
                break;
            case 3011:
                TrialSpawner.addSpawnParticles(this.level, pos, randomSource, TrialSpawner.FlameParticle.decode(data).particleType);
                break;
            case 3012:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_SPAWN_MOB,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addSpawnParticles(this.level, pos, randomSource, TrialSpawner.FlameParticle.decode(data).particleType);
                break;
            case 3013:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addDetectPlayerParticles(this.level, pos, randomSource, data, ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER);
                break;
            case 3014:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_EJECT_ITEM,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addEjectItemParticles(this.level, pos, randomSource);
                break;
            case 3015:
                if (this.level.getBlockEntity(pos) instanceof VaultBlockEntity vaultBlockEntity) {
                    VaultBlockEntity.Client.emitActivationParticles(
                        this.level,
                        vaultBlockEntity.getBlockPos(),
                        vaultBlockEntity.getBlockState(),
                        vaultBlockEntity.getSharedData(),
                        data == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SOUL_FIRE_FLAME
                    );
                    this.level
                        .playLocalSound(
                            pos,
                            SoundEvents.VAULT_ACTIVATE,
                            SoundSource.BLOCKS,
                            1.0F,
                            (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                            true
                        );
                }
                break;
            case 3016:
                VaultBlockEntity.Client.emitDeactivationParticles(this.level, pos, data == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SOUL_FIRE_FLAME);
                this.level
                    .playLocalSound(
                        pos, SoundEvents.VAULT_DEACTIVATE, SoundSource.BLOCKS, 1.0F, (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F, true
                    );
                break;
            case 3017:
                TrialSpawner.addEjectItemParticles(this.level, pos, randomSource);
                break;
            case 3018:
                for (int sx = 0; sx < 10; sx++) {
                    double t = randomSource.nextGaussian() * 0.02;
                    double u = randomSource.nextGaussian() * 0.02;
                    double v = randomSource.nextGaussian() * 0.02;
                    this.level
                        .addParticle(
                            ParticleTypes.POOF,
                            pos.getX() + randomSource.nextDouble(),
                            pos.getY() + randomSource.nextDouble(),
                            pos.getZ() + randomSource.nextDouble(),
                            t,
                            u,
                            v
                        );
                }

                this.level
                    .playLocalSound(
                        pos, SoundEvents.COBWEB_PLACE, SoundSource.BLOCKS, 1.0F, (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F, true
                    );
                break;
            case 3019:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addDetectPlayerParticles(this.level, pos, randomSource, data, ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS);
                break;
            case 3020:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                        SoundSource.BLOCKS,
                        data == 0 ? 0.3F : 1.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addDetectPlayerParticles(this.level, pos, randomSource, 0, ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS);
                TrialSpawner.addBecomeOminousParticles(this.level, pos, randomSource);
                break;
            case 3021:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomSource.nextFloat() - randomSource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addSpawnParticles(this.level, pos, randomSource, TrialSpawner.FlameParticle.decode(data).particleType);
        }
    }

    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        if (progress >= 0 && progress < 10) {
            BlockDestructionProgress blockDestructionProgress = this.destroyingBlocks.get(breakerId);
            if (blockDestructionProgress != null) {
                this.removeProgress(blockDestructionProgress);
            }

            if (blockDestructionProgress == null
                || blockDestructionProgress.getPos().getX() != pos.getX()
                || blockDestructionProgress.getPos().getY() != pos.getY()
                || blockDestructionProgress.getPos().getZ() != pos.getZ()) {
                blockDestructionProgress = new BlockDestructionProgress(breakerId, pos);
                this.destroyingBlocks.put(breakerId, blockDestructionProgress);
            }

            blockDestructionProgress.setProgress(progress);
            blockDestructionProgress.updateTick(this.ticks);
            this.destructionProgress.computeIfAbsent(blockDestructionProgress.getPos().asLong(), l -> Sets.newTreeSet()).add(blockDestructionProgress);
        } else {
            BlockDestructionProgress blockDestructionProgress = this.destroyingBlocks.remove(breakerId);
            if (blockDestructionProgress != null) {
                this.removeProgress(blockDestructionProgress);
            }
        }
    }

    public boolean hasRenderedAllSections() {
        return this.sectionRenderDispatcher.isQueueEmpty();
    }

    public void onChunkLoaded(ChunkPos chunkPos) {
        this.sectionOcclusionGraph.onChunkLoaded(chunkPos);
    }

    public void needsUpdate() {
        this.sectionOcclusionGraph.invalidate();
        this.generateClouds = true;
    }

    public void updateGlobalBlockEntities(Collection<BlockEntity> blockEntitiesToRemove, Collection<BlockEntity> blockEntitiesToAdd) {
        synchronized (this.globalBlockEntities) {
            this.globalBlockEntities.removeAll(blockEntitiesToRemove);
            this.globalBlockEntities.addAll(blockEntitiesToAdd);
        }
    }

    public static int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        return getLightColor(level, level.getBlockState(pos), pos);
    }

    public static int getLightColor(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        if (state.emissiveRendering(level, pos)) {
            return 15728880;
        }

        int i = level.getBrightness(LightLayer.SKY, pos);
        int j = level.getBrightness(LightLayer.BLOCK, pos);
        int k = state.getLightEmission();
        if (j < k) {
            j = k;
        }

        return i << 20 | j << 4;
    }

    public boolean isSectionCompiled(BlockPos pos) {
        SectionRenderDispatcher.RenderSection renderSection = this.viewArea.getRenderSectionAt(pos);
        return renderSection != null && renderSection.compiled.get() != SectionRenderDispatcher.CompiledSection.UNCOMPILED;
    }

    @Nullable
    public RenderTarget entityTarget() {
        return this.entityTarget;
    }

    @Nullable
    public RenderTarget getTranslucentTarget() {
        return this.translucentTarget;
    }

    @Nullable
    public RenderTarget getItemEntityTarget() {
        return this.itemEntityTarget;
    }

    @Nullable
    public RenderTarget getParticlesTarget() {
        return this.particlesTarget;
    }

    @Nullable
    public RenderTarget getWeatherTarget() {
        return this.weatherTarget;
    }

    @Nullable
    public RenderTarget getCloudsTarget() {
        return this.cloudsTarget;
    }

    private void shootParticles(int direction, BlockPos pos, RandomSource random, SimpleParticleType particleType) {
        Direction direction2 = Direction.from3DDataValue(direction);
        int i = direction2.getStepX();
        int j = direction2.getStepY();
        int k = direction2.getStepZ();
        double d = pos.getX() + i * 0.6 + 0.5;
        double e = pos.getY() + j * 0.6 + 0.5;
        double f = pos.getZ() + k * 0.6 + 0.5;

        for (int l = 0; l < 10; l++) {
            double g = random.nextDouble() * 0.2 + 0.01;
            double h = d + i * 0.01 + (random.nextDouble() - 0.5) * k * 0.5;
            double m = e + j * 0.01 + (random.nextDouble() - 0.5) * j * 0.5;
            double n = f + k * 0.01 + (random.nextDouble() - 0.5) * i * 0.5;
            double o = i * g + random.nextGaussian() * 0.01;
            double p = j * g + random.nextGaussian() * 0.01;
            double q = k * g + random.nextGaussian() * 0.01;
            this.addParticle(particleType, h, m, n, o, p, q);
        }
    }

    public static class TransparencyShaderException extends RuntimeException {
        public TransparencyShaderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
