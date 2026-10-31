package net.minecraft.client.sounds;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * The {@code SoundEngine} class handles the management and playback of sounds in the game.
 */
public class SoundEngine {
    /**
     * The marker used for logging
     */
    private static final Marker MARKER = MarkerFactory.getMarker("SOUNDS");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float PITCH_MIN = 0.5F;
    private static final float PITCH_MAX = 2.0F;
    private static final float VOLUME_MIN = 0.0F;
    private static final float VOLUME_MAX = 1.0F;
    private static final int MIN_SOURCE_LIFETIME = 20;
    /**
     * A set of resource locations for which a missing sound warning has been issued
     */
    private static final Set<ResourceLocation> ONLY_WARN_ONCE = Sets.newHashSet();
    /**
     * The default interval in milliseconds for checking the audio device state
     */
    private static final long DEFAULT_DEVICE_CHECK_INTERVAL_MS = 1000L;
    public static final String MISSING_SOUND = "FOR THE DEBUG!";
    public static final String OPEN_AL_SOFT_PREFIX = "OpenAL Soft on ";
    public static final int OPEN_AL_SOFT_PREFIX_LENGTH = "OpenAL Soft on ".length();
    /**
     * A reference to the sound handler.
     */
    private final SoundManager soundManager;
    /**
     * Reference to the GameSettings object.
     */
    private final Options options;
    /**
     * Set to true when the SoundManager has been initialised.
     */
    private boolean loaded;
    private final Library library = new Library();
    /**
     * The listener object responsible for managing the sound listener position and orientation
     */
    private final Listener listener = this.library.getListener();
    private final SoundBufferLibrary soundBuffers;
    private final SoundEngineExecutor executor = new SoundEngineExecutor();
    private final ChannelAccess channelAccess = new ChannelAccess(this.library, this.executor);
    /**
     * A counter for how long the sound manager has been running
     */
    private int tickCount;
    private long lastDeviceCheckTime;
    /**
     * The current state of the audio device check
     */
    private final AtomicReference<SoundEngine.DeviceCheckState> devicePoolState = new AtomicReference<>(SoundEngine.DeviceCheckState.NO_CHANGE);
    private final Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel = Maps.newHashMap();
    private final Multimap<SoundSource, SoundInstance> instanceBySource = HashMultimap.create();
    /**
     * A subset of playingSounds, this contains only {@linkplain TickableSoundInstance}
     */
    private final List<TickableSoundInstance> tickingSounds = Lists.newArrayList();
    /**
     * Contains sounds to play in n ticks. Type: HashMap<ISound, Integer>
     */
    private final Map<SoundInstance, Integer> queuedSounds = Maps.newHashMap();
    /**
     * The future time in which to stop this sound. Type: HashMap<String, Integer>
     */
    private final Map<SoundInstance, Integer> soundDeleteTime = Maps.newHashMap();
    private final List<SoundEventListener> listeners = Lists.newArrayList();
    private final List<TickableSoundInstance> queuedTickableSounds = Lists.newArrayList();
    private final List<Sound> preloadQueue = Lists.newArrayList();

    public SoundEngine(SoundManager soundManager, Options options, ResourceProvider resourceManager) {
        this.soundManager = soundManager;
        this.options = options;
        this.soundBuffers = new SoundBufferLibrary(resourceManager);
    }

    public void reload() {
        ONLY_WARN_ONCE.clear();

        for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
            if (soundEvent != SoundEvents.EMPTY) {
                ResourceLocation resourceLocation = soundEvent.getLocation();
                if (this.soundManager.getSoundEvent(resourceLocation) == null) {
                    LOGGER.warn("Missing sound for event: {}", BuiltInRegistries.SOUND_EVENT.getKey(soundEvent));
                    ONLY_WARN_ONCE.add(resourceLocation);
                }
            }
        }

        this.destroy();
        this.loadLibrary();
    }

    private synchronized void loadLibrary() {
        if (!this.loaded) {
            try {
                String string = this.options.soundDevice().get();
                this.library.init("".equals(string) ? null : string, this.options.directionalAudio().get());
                this.listener.reset();
                this.listener.setGain(this.options.getSoundSourceVolume(SoundSource.MASTER));
                this.soundBuffers.preload(this.preloadQueue).thenRun(this.preloadQueue::clear);
                this.loaded = true;
                LOGGER.info(MARKER, "Sound engine started");
            } catch (RuntimeException runtimeException) {
                LOGGER.error(MARKER, "Error starting SoundSystem. Turning off sounds & music", runtimeException);
            }
        }
    }

    /**
     * {@return the volume value pinned between 0.0f and 1.0f for a given {@linkplain SoundSource} category}
     */
    private float getVolume(@Nullable SoundSource category) {
        return category != null && category != SoundSource.MASTER ? this.options.getSoundSourceVolume(category) : 1.0F;
    }

    /**
     * Updates the volume for a specific sound category.
     * <p>
     * If the sound engine has not been loaded, the method returns without performing any action.
     * <p>
     * If the category is the "MASTER" category, the overall listener gain (volume) is set to the specified value.
     * <p>
     * For other categories, the volume is updated for each sound instance associated with the category.
     * <p>
     * If the calculated volume for an instance is less than or equal to 0.0, the instance is stopped.
     * Otherwise, the volume of the instance is set to the calculated value.
     */
    public void updateCategoryVolume(SoundSource category, float volume) {
        if (this.loaded) {
            if (category == SoundSource.MASTER) {
                this.listener.setGain(volume);
            } else {
                this.instanceToChannel.forEach((instance, source) -> {
                    float f = this.calculateVolume(instance);
                    source.execute(channel -> {
                        if (f <= 0.0F) {
                            channel.stop();
                        } else {
                            channel.setVolume(f);
                        }
                    });
                });
            }
        }
    }

    public void destroy() {
        if (this.loaded) {
            this.stopAll();
            this.soundBuffers.clear();
            this.library.cleanup();
            this.loaded = false;
        }
    }

    public void emergencyShutdown() {
        if (this.loaded) {
            this.library.cleanup();
        }
    }

    /**
     * Stops the provided {@linkplain SoundInstace} from continuing to play.
     */
    public void stop(SoundInstance sound) {
        if (this.loaded) {
            ChannelAccess.ChannelHandle channelHandle = this.instanceToChannel.get(sound);
            if (channelHandle != null) {
                channelHandle.execute(Channel::stop);
            }
        }
    }

    public void stopAll() {
        if (this.loaded) {
            this.executor.flush();
            this.instanceToChannel.values().forEach(channelHandle -> channelHandle.execute(Channel::stop));
            this.instanceToChannel.clear();
            this.channelAccess.clear();
            this.queuedSounds.clear();
            this.tickingSounds.clear();
            this.instanceBySource.clear();
            this.soundDeleteTime.clear();
            this.queuedTickableSounds.clear();
        }
    }

    public void addEventListener(SoundEventListener listener) {
        this.listeners.add(listener);
    }

    public void removeEventListener(SoundEventListener listener) {
        this.listeners.remove(listener);
    }

    private boolean shouldChangeDevice() {
        if (this.library.isCurrentDeviceDisconnected()) {
            LOGGER.info("Audio device was lost!");
            return true;
        }

        long l = Util.getMillis();
        boolean bl = l - this.lastDeviceCheckTime >= 1000L;
        if (bl) {
            this.lastDeviceCheckTime = l;
            if (this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.NO_CHANGE, SoundEngine.DeviceCheckState.ONGOING)) {
                String string = this.options.soundDevice().get();
                Util.ioPool().execute(() -> {
                    if ("".equals(string)) {
                        if (this.library.hasDefaultDeviceChanged()) {
                            LOGGER.info("System default audio device has changed!");
                            this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.CHANGE_DETECTED);
                        }
                    } else if (!this.library.getCurrentDeviceName().equals(string) && this.library.getAvailableSoundDevices().contains(string)) {
                        LOGGER.info("Preferred audio device has become available!");
                        this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.CHANGE_DETECTED);
                    }

                    this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.NO_CHANGE);
                });
            }
        }

        return this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.CHANGE_DETECTED, SoundEngine.DeviceCheckState.NO_CHANGE);
    }

    /**
     * Ticks all active instances of  {@code TickableSoundInstance}
     */
    public void tick(boolean isGamePaused) {
        if (this.shouldChangeDevice()) {
            this.reload();
        }

        if (!isGamePaused) {
            this.tickNonPaused();
        }

        this.channelAccess.scheduleTick();
    }

    private void tickNonPaused() {
        this.tickCount++;
        this.queuedTickableSounds.stream().filter(SoundInstance::canPlaySound).forEach(this::play);
        this.queuedTickableSounds.clear();

        for (TickableSoundInstance tickableSoundInstance : this.tickingSounds) {
            if (!tickableSoundInstance.canPlaySound()) {
                this.stop(tickableSoundInstance);
            }

            tickableSoundInstance.tick();
            if (tickableSoundInstance.isStopped()) {
                this.stop(tickableSoundInstance);
            } else {
                float f = this.calculateVolume(tickableSoundInstance);
                float g = this.calculatePitch(tickableSoundInstance);
                Vec3 vec3 = new Vec3(tickableSoundInstance.getX(), tickableSoundInstance.getY(), tickableSoundInstance.getZ());
                ChannelAccess.ChannelHandle channelHandle = this.instanceToChannel.get(tickableSoundInstance);
                if (channelHandle != null) {
                    channelHandle.execute(channel -> {
                        channel.setVolume(f);
                        channel.setPitch(g);
                        channel.setSelfPosition(vec3);
                    });
                }
            }
        }

        Iterator<Entry<SoundInstance, ChannelAccess.ChannelHandle>> iterator = this.instanceToChannel.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<SoundInstance, ChannelAccess.ChannelHandle> entry = iterator.next();
            ChannelAccess.ChannelHandle channelHandle2 = entry.getValue();
            SoundInstance soundInstance = entry.getKey();
            float h = this.options.getSoundSourceVolume(soundInstance.getSource());
            if (h <= 0.0F) {
                channelHandle2.execute(Channel::stop);
                iterator.remove();
            } else if (channelHandle2.isStopped()) {
                int i = this.soundDeleteTime.get(soundInstance);
                if (i <= this.tickCount) {
                    if (shouldLoopManually(soundInstance)) {
                        this.queuedSounds.put(soundInstance, this.tickCount + soundInstance.getDelay());
                    }

                    iterator.remove();
                    LOGGER.debug(MARKER, "Removed channel {} because it's not playing anymore", channelHandle2);
                    this.soundDeleteTime.remove(soundInstance);

                    try {
                        this.instanceBySource.remove(soundInstance.getSource(), soundInstance);
                    } catch (RuntimeException var8) {
                    }

                    if (soundInstance instanceof TickableSoundInstance) {
                        this.tickingSounds.remove(soundInstance);
                    }
                }
            }
        }

        Iterator<Entry<SoundInstance, Integer>> iterator2 = this.queuedSounds.entrySet().iterator();

        while (iterator2.hasNext()) {
            Entry<SoundInstance, Integer> entry2 = iterator2.next();
            if (this.tickCount >= entry2.getValue()) {
                SoundInstance soundInstance = entry2.getKey();
                if (soundInstance instanceof TickableSoundInstance) {
                    ((TickableSoundInstance)soundInstance).tick();
                }

                this.play(soundInstance);
                iterator2.remove();
            }
        }
    }

    /**
     * {@return Returns {@code true} if the SoundInstance requires manual looping, {@code false} otherwise
     *
     * @param sound the SoundInstance to check
     */
    private static boolean requiresManualLooping(SoundInstance sound) {
        return sound.getDelay() > 0;
    }

    /**
     * @return Returns {@code true} if the SoundInstance should loop manually, {@code false} otherwise
     *
     * @param sound The SoundInstance to check
     */
    private static boolean shouldLoopManually(SoundInstance sound) {
        return sound.isLooping() && requiresManualLooping(sound);
    }

    /**
     * @return Returns {@code true} if the SoundInstance should loop automatically, {@code false} otherwise
     *
     * @param sound The SoundInstance to check
     */
    private static boolean shouldLoopAutomatically(SoundInstance sound) {
        return sound.isLooping() && !requiresManualLooping(sound);
    }

    /**
     * {@return {@code true} if the {@linkplain SoundInstance} is active, {@code false} otherwise}
     *
     * @param sound the SoundInstance to check
     */
    public boolean isActive(SoundInstance sound) {
        if (!this.loaded) {
            return false;
        } else {
            return this.soundDeleteTime.containsKey(sound) && this.soundDeleteTime.get(sound) <= this.tickCount
                ? true
                : this.instanceToChannel.containsKey(sound);
        }
    }

    /**
     * Plays a given sound instance.
     * <p>
     * If the sound engine is not loaded or the sound instance cannot be played, the method returns early.
     * <p>
     * The method fulfills the following parts:
     * <ul>
     *   <li>Performs a series of checks to determine if it can play a sound</li>
     *   <li>Handles the playing of instances of {@code SoundInstance}</li>
     *   <li>Logs potential errors that may have occured</li>
     *   <li>Handles mapping instances of {@code SoundInstance} to specific audio channels</li>
     *   <li>Handles deletion times for active instances of {@code SoundInstance}</li>
     *   <li>Calculates and handles various sound properties such as volume, pitch, attenuation, looping, position and relative, </li>
     * </ul>
     * <p>
     * @implNote This method assumes proper synchronization or that thread confinement mechanisms are in place.
     *
     * @param sound the sound instance to be played.
     */
    public void play(SoundInstance sound) {
        if (this.loaded) {
            if (sound.canPlaySound()) {
                WeighedSoundEvents weighedSoundEvents = sound.resolve(this.soundManager);
                ResourceLocation resourceLocation = sound.getLocation();
                if (weighedSoundEvents == null) {
                    if (ONLY_WARN_ONCE.add(resourceLocation)) {
                        LOGGER.warn(MARKER, "Unable to play unknown soundEvent: {}", resourceLocation);
                    }
                } else {
                    Sound sound2 = sound.getSound();
                    if (sound2 != SoundManager.INTENTIONALLY_EMPTY_SOUND) {
                        if (sound2 == SoundManager.EMPTY_SOUND) {
                            if (ONLY_WARN_ONCE.add(resourceLocation)) {
                                LOGGER.warn(MARKER, "Unable to play empty soundEvent: {}", resourceLocation);
                            }
                        } else {
                            float f = sound.getVolume();
                            float g = Math.max(f, 1.0F) * sound2.getAttenuationDistance();
                            SoundSource soundSource = sound.getSource();
                            float h = this.calculateVolume(f, soundSource);
                            float i = this.calculatePitch(sound);
                            SoundInstance.Attenuation attenuation = sound.getAttenuation();
                            boolean bl = sound.isRelative();
                            if (h == 0.0F && !sound.canStartSilent()) {
                                LOGGER.debug(MARKER, "Skipped playing sound {}, volume was zero.", sound2.getLocation());
                            } else {
                                Vec3 vec3 = new Vec3(sound.getX(), sound.getY(), sound.getZ());
                                if (!this.listeners.isEmpty()) {
                                    float j = !bl && attenuation != SoundInstance.Attenuation.NONE ? g : Float.POSITIVE_INFINITY;

                                    for (SoundEventListener soundEventListener : this.listeners) {
                                        soundEventListener.onPlaySound(sound, weighedSoundEvents, j);
                                    }
                                }

                                if (this.listener.getGain() <= 0.0F) {
                                    LOGGER.debug(MARKER, "Skipped playing soundEvent: {}, master volume was zero", resourceLocation);
                                } else {
                                    boolean bl2 = shouldLoopAutomatically(sound);
                                    boolean bl3 = sound2.shouldStream();
                                    CompletableFuture<ChannelAccess.ChannelHandle> completableFuture = this.channelAccess
                                        .createHandle(sound2.shouldStream() ? Library.Pool.STREAMING : Library.Pool.STATIC);
                                    ChannelAccess.ChannelHandle channelHandle = completableFuture.join();
                                    if (channelHandle == null) {
                                        if (SharedConstants.IS_RUNNING_IN_IDE) {
                                            LOGGER.warn("Failed to create new sound handle");
                                        }
                                    } else {
                                        LOGGER.debug(MARKER, "Playing sound {} for event {}", sound2.getLocation(), resourceLocation);
                                        this.soundDeleteTime.put(sound, this.tickCount + 20);
                                        this.instanceToChannel.put(sound, channelHandle);
                                        this.instanceBySource.put(soundSource, sound);
                                        channelHandle.execute(channel -> {
                                            channel.setPitch(i);
                                            channel.setVolume(h);
                                            if (attenuation == SoundInstance.Attenuation.LINEAR) {
                                                channel.linearAttenuation(g);
                                            } else {
                                                channel.disableAttenuation();
                                            }

                                            channel.setLooping(bl2 && !bl3);
                                            channel.setSelfPosition(vec3);
                                            channel.setRelative(bl);
                                        });
                                        if (!bl3) {
                                            this.soundBuffers.getCompleteBuffer(sound2.getPath()).thenAccept(soundBuffer -> channelHandle.execute(channel -> {
                                                channel.attachStaticBuffer(soundBuffer);
                                                channel.play();
                                            }));
                                        } else {
                                            this.soundBuffers.getStream(sound2.getPath(), bl2).thenAccept(audioStream -> channelHandle.execute(channel -> {
                                                channel.attachBufferStream(audioStream);
                                                channel.play();
                                            }));
                                        }

                                        if (sound instanceof TickableSoundInstance) {
                                            this.tickingSounds.add((TickableSoundInstance)sound);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Queues a new {@linkplain TickingCodeInstance}
     *
     * @param tickableSound the {@linkplain TickableSoundInstance} to queue
     */
    public void queueTickingSound(TickableSoundInstance tickableSound) {
        this.queuedTickableSounds.add(tickableSound);
    }

    /**
     * Requests a specific {@linkplain Sound} instance to be preloaded.
     */
    public void requestPreload(Sound sound) {
        this.preloadQueue.add(sound);
    }

    /**
     * Calculates the pitch of the sound being played.
     * <p>
     * Clamps the sound between 0.5f and 2.0f.
     *
     * @param sound the {@linkplain SoundInstance} being played
     */
    private float calculatePitch(SoundInstance sound) {
        return Mth.clamp(sound.getPitch(), 0.5F, 2.0F);
    }

    /**
     * Calculates the volume for the sound being played.
     * <p>
     * Delegates to {@code #calculateVolume(float, SoundSource)}
     */
    private float calculateVolume(SoundInstance sound) {
        return this.calculateVolume(sound.getVolume(), sound.getSource());
    }

    /**
     * Calculates the volume of the sound being played.
     * <p>
     * Clamps the sound between 0.0f and 1.0f.
     */
    private float calculateVolume(float volumeMultiplier, SoundSource source) {
        return Mth.clamp(volumeMultiplier * this.getVolume(source), 0.0F, 1.0F);
    }

    public void pause() {
        if (this.loaded) {
            this.channelAccess.executeOnChannels(stream -> stream.forEach(Channel::pause));
        }
    }

    public void resume() {
        if (this.loaded) {
            this.channelAccess.executeOnChannels(stream -> stream.forEach(Channel::unpause));
        }
    }

    /**
     * Adds a sound to play in n ticks
     */
    public void playDelayed(SoundInstance sound, int delay) {
        this.queuedSounds.put(sound, this.tickCount + delay);
    }

    public void updateSource(Camera renderInfo) {
        if (this.loaded && renderInfo.isInitialized()) {
            ListenerTransform listenerTransform = new ListenerTransform(
                renderInfo.getPosition(), new Vec3(renderInfo.getLookVector()), new Vec3(renderInfo.getUpVector())
            );
            this.executor.execute(() -> this.listener.setTransform(listenerTransform));
        }
    }

    public void stop(@Nullable ResourceLocation soundName, @Nullable SoundSource category) {
        if (category != null) {
            for (SoundInstance soundInstance : this.instanceBySource.get(category)) {
                if (soundName == null || soundInstance.getLocation().equals(soundName)) {
                    this.stop(soundInstance);
                }
            }
        } else if (soundName == null) {
            this.stopAll();
        } else {
            for (SoundInstance soundInstance : this.instanceToChannel.keySet()) {
                if (soundInstance.getLocation().equals(soundName)) {
                    this.stop(soundInstance);
                }
            }
        }
    }

    public String getDebugString() {
        return this.library.getDebugString();
    }

    public List<String> getAvailableSoundDevices() {
        return this.library.getAvailableSoundDevices();
    }

    public ListenerTransform getListenerTransform() {
        return this.listener.getTransform();
    }

    enum DeviceCheckState {
        ONGOING,
        CHANGE_DETECTED,
        NO_CHANGE;
    }
}
