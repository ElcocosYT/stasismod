package com.supper.stasis.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

public class TimeTrailParticleFactory implements ParticleFactory<DefaultParticleType> {
	private final SpriteProvider spriteProvider;

	public TimeTrailParticleFactory(SpriteProvider spriteProvider) {
		this.spriteProvider = spriteProvider;
	}

	@Override
	public Particle createParticle(DefaultParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
		TimeTrailParticle particle = new TimeTrailParticle(world, x, y, z, velocityX, velocityY, velocityZ);
		return particle;
	}
}
