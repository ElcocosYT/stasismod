package com.supper.stasis.client.particle;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;

public class TimeTrailParticle extends BillboardParticle {
	public TimeTrailParticle(
			ClientWorld world,
			double x,
			double y,
			double z,
			double velocityX,
			double velocityY,
			double velocityZ,
			Sprite sprite
	) {
		super(world, x, y, z, velocityX, velocityY, velocityZ, sprite);
		this.scale = 0.5f;
		this.maxAge = 60;
		this.setAlpha(0.5f);
		this.setColor(0.6f, 0.8f, 1.0f);
		this.velocityX = 0.0;
		this.velocityY = 0.0;
		this.velocityZ = 0.0;
	}

	@Override
	public void tick() {
		super.tick();
		this.setAlpha(0.5f * (1.0f - (float) this.age / this.maxAge));
	}

	@Override
	public BillboardParticle.RenderType getRenderType() {
		return BillboardParticle.RenderType.PARTICLE_ATLAS_TRANSLUCENT;
	}

	@Override
	protected int getBrightness(float tint) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}
}
