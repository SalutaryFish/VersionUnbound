package net.minecraft.client.sounds;

import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * The SoundEventListener interface defines a listener for sound events.
 * Classes implementing this interface can be registered as listeners to receive notifications when a sound is played.
 */
public interface SoundEventListener {
    void onPlaySound(SoundInstance var1, WeighedSoundEvents var2, float var3);
}
