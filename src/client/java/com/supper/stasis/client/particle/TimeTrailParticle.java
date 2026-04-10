package com.supper.stasis.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class TimeTrailParticle extends Particle {
	private final ParticleTextureSheet textureSheet;
	private float scale;

	public TimeTrailParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
		super(world, x, y, z, velocityX, velocityY, velocityZ);

		// Set particle properties
		this.scale = 0.5f; // Larger than default
		this.maxAge = 60; // 3 seconds at 20 ticks/sec
		this.alpha = 0.5f;

		// Light cyan/blue tint
		this.red = 0.6f;
		this.green = 0.8f;
		this.blue = 1.0f;

		this.velocityX = 0;
		this.velocityY = 0;
		this.velocityZ = 0;

		this.textureSheet = ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void tick() {
		// Fade out over time
		this.alpha = 0.5f * (1.0f - (float) this.age / this.maxAge);

		// Particle doesn't move (it's frozen in place)
		super.tick();
	}

	@Override
	public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float partialTicks) {
		// Build particle quad
		Vector3f vec3f = new Vector3f(1.0f, 0.0f, 0.0f);
		Vector3f vec3f2 = new Vector3f(0.0f, 1.0f, 0.0f);

		Quaternionf quaternionf = camera.getRotation();
		vec3f.rotate(quaternionf);
		vec3f2.rotate(quaternionf);

		float scale = this.scale;
		float x0 = (float)(MathHelper.lerp(partialTicks, this.prevPosX, this.x) - camera.getPos().x);
		float y0 = (float)(MathHelper.lerp(partialTicks, this.prevPosY, this.y) - camera.getPos().y);
		float z0 = (float)(MathHelper.lerp(partialTicks, this.prevPosZ, this.z) - camera.getPos().z);

		vertexConsumer.vertex(x0 - vec3f.x * scale - vec3f2.x * scale, y0 - vec3f.y * scale - vec3f2.y * scale, z0 - vec3f.z * scale - vec3f2.z * scale).texture(0.0f, 1.0f).color(this.red, this.green, this.blue, this.alpha).light(0, 240);
		vertexConsumer.vertex(x0 - vec3f.x * scale + vec3f2.x * scale, y0 - vec3f.y * scale + vec3f2.y * scale, z0 - vec3f.z * scale + vec3f2.z * scale).texture(1.0f, 1.0f).color(this.red, this.green, this.blue, this.alpha).light(0, 240);
		vertexConsumer.vertex(x0 + vec3f.x * scale + vec3f2.x * scale, y0 + vec3f.y * scale + vec3f2.y * scale, z0 + vec3f.z * scale + vec3f2.z * scale).texture(1.0f, 0.0f).color(this.red, this.green, this.blue, this.alpha).light(0, 240);
		vertexConsumer.vertex(x0 + vec3f.x * scale - vec3f2.x * scale, y0 + vec3f.y * scale - vec3f2.y * scale, z0 + vec3f.z * scale - vec3f2.z * scale).texture(0.0f, 0.0f).color(this.red, this.green, this.blue, this.alpha).light(0, 240);
	}

	@Override
	public ParticleTextureSheet getType() {
		return this.textureSheet;
	}
}
