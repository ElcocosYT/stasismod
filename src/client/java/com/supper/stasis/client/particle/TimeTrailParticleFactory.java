package com.supper.stasis.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class TimeTrailParticleFactory implements ParticleFactory<SimpleParticleType> {
	private final SpriteProvider spriteProvider;

	public TimeTrailParticleFactory(SpriteProvider spriteProvider) {
		this.spriteProvider = spriteProvider;
	}

	@Override
	public Particle createParticle(
			SimpleParticleType parameters,
			ClientWorld world,
			double x,
			double y,
			double z,
			double velocityX,
			double velocityY,
			double velocityZ,
			Random random
	) {
		TimeTrailParticle particle = new TimeTrailParticle(
				world,
				x,
				y,
				z,
				velocityX,
				velocityY,
				velocityZ,
				this.spriteProvider.getFirst()
		);
		particle.updateSprite(this.spriteProvider);
		return particle;
	}
}
