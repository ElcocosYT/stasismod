package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
	@Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
	private void stasis$scaleVolume(CallbackInfoReturnable<Float> cir) {
		AbstractSoundInstance sound = (AbstractSoundInstance) (Object) this;
		if (!stasis$shouldAffect(sound)) {
			return;
		}

		float scaledVolume = cir.getReturnValue() * StasisClientState.getWorldSoundVolumeScale();
		cir.setReturnValue(Math.max(0.0f, scaledVolume));
	}

	@Inject(method = "getPitch", at = @At("RETURN"), cancellable = true)
	private void stasis$scalePitch(CallbackInfoReturnable<Float> cir) {
		AbstractSoundInstance sound = (AbstractSoundInstance) (Object) this;
		if (!stasis$shouldAffect(sound)) {
			return;
		}

		float scaledPitch = cir.getReturnValue() * StasisClientState.getWorldSoundPitchScale();
		cir.setReturnValue(Math.max(0.05f, scaledPitch));
	}

	private static boolean stasis$shouldAffect(AbstractSoundInstance sound) {
		if (!StasisClientState.isRunning()) {
			return false;
		}

		Identifier soundId = sound.getId();
		if (soundId != null && "stasis".equals(soundId.getNamespace())) {
			return false;
		}

		SoundCategory category = sound.getCategory();
		if (category == SoundCategory.MASTER
				|| category == SoundCategory.MUSIC
				|| category == SoundCategory.RECORDS
				|| category == SoundCategory.VOICE) {
			return false;
		}

		if (sound.isRelative() || sound.getAttenuationType() == SoundInstance.AttenuationType.NONE) {
			return false;
		}

		return true;
	}
}
