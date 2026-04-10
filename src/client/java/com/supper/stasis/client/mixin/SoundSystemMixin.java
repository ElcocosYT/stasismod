package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import java.util.Map;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Shadow
    private Map<SoundInstance, Channel.SourceManager> sources;

    @Shadow
    protected abstract float getAdjustedVolume(SoundInstance sound);

    @Shadow
    protected abstract float getAdjustedPitch(SoundInstance sound);

    @Inject(method = "tick(Z)V", at = @At("RETURN"))
    private void stasis$refreshNonTickableWorldSoundPlayback(boolean paused, CallbackInfo ci) {
        if (paused || !StasisClientState.isRunning()) {
            return;
        }

        for (Map.Entry<SoundInstance, Channel.SourceManager> entry : this.sources.entrySet()) {
            SoundInstance sound = entry.getKey();
            if (sound instanceof TickableSoundInstance || !stasis$shouldAffect(sound)) {
                continue;
            }

            float volume = stasis$getScaledVolume(sound);
            float pitch = stasis$getScaledPitch(sound);
            Vec3d position = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
            boolean pauseForActive = StasisClientState.isActive() && stasis$shouldPauseInActive(sound);
            entry.getValue().run(source -> {
                if (pauseForActive) {
                    source.pause();
                } else {
                    source.resume();
                }
                source.setVolume(volume);
                source.setPitch(pitch);
                source.setPosition(position);
            });
        }
    }

    private static boolean stasis$shouldAffect(SoundInstance sound) {
        Identifier soundId = sound.getId();
        if (soundId != null && "stasis".equals(soundId.getNamespace())) {
            return false;
        }

        SoundCategory category = sound.getCategory();
        if (category == SoundCategory.MASTER || category == SoundCategory.VOICE) {
            return false;
        }
        if (category == SoundCategory.MUSIC || category == SoundCategory.RECORDS) {
            return true;
        }

        return !sound.isRelative() && sound.getAttenuationType() != SoundInstance.AttenuationType.NONE;
    }

    private static boolean stasis$shouldPauseInActive(SoundInstance sound) {
        SoundCategory category = sound.getCategory();
        return category == SoundCategory.MUSIC || category == SoundCategory.RECORDS;
    }

    private float stasis$getScaledVolume(SoundInstance sound) {
        float volume = this.getAdjustedVolume(sound);
        if (stasis$shouldApplyDirectTimelineScaling(sound)) {
            volume *= StasisClientState.getWorldSoundVolumeScale();
        }
        return MathHelper.clamp(volume, 0.0F, 1.0F);
    }

    private float stasis$getScaledPitch(SoundInstance sound) {
        float pitch = this.getAdjustedPitch(sound);
        if (stasis$shouldApplyDirectTimelineScaling(sound)) {
            pitch *= StasisClientState.getWorldSoundPitchScale();
        }
        return MathHelper.clamp(pitch, 0.05F, 2.0F);
    }

    private static boolean stasis$shouldApplyDirectTimelineScaling(SoundInstance sound) {
        SoundCategory category = sound.getCategory();
        return category == SoundCategory.MUSIC || category == SoundCategory.RECORDS;
    }
}
