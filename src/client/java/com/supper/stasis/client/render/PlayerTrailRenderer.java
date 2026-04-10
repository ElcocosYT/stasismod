package com.supper.stasis.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.supper.stasis.StasisTimings;
import com.supper.stasis.client.StasisClientState;
import com.supper.stasis.client.mixin.LimbAnimatorAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;

public final class PlayerTrailRenderer {
	private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
	private static final int CAPTURE_INTERVAL_TICKS = 2;
	private static final double MIN_CAPTURE_DISTANCE_SQUARED = 0.0225;
	private static final double INTERPOLATION_DISTANCE = 0.9;
	private static final double MIN_CAMERA_DISTANCE_SQUARED = 0.09;
	private static final double TRAIL_VERTICAL_OFFSET = 0.02;
	private static final double PLAYER_FADE_START_DISTANCE = 0.8;
	private static final double PLAYER_FADE_END_DISTANCE = 2.1;
	private static final double CAMERA_FADE_START_DISTANCE = 0.55;
	private static final double CAMERA_FADE_END_DISTANCE = 1.6;
	private static final float TRAIL_ALPHA_MIN = 0.07f;
	private static final float TRAIL_ALPHA_MAX = 0.34f;
	private static final float TRAIL_DENSITY_REFERENCE = 28.0f;
	private static final float TRAIL_DENSITY_MIN_SCALE = 0.42f;
	private static final int MAX_SNAPSHOTS = 190;
	private static final List<TrailSnapshot> SNAPSHOTS = new ArrayList<>();

	private static UUID trackedPlayerUuid = null;
	private static int captureCooldown = 0;
	private static Vec3d lastCapturedSourcePosition = null;
	private static ItemStack[] lastCapturedEquipmentSnapshot = null;
	private static Matrix4f cachedWorldPositionMatrix = null;
	private static Matrix4f cachedProjectionMatrix = null;
	private static Matrix4f cachedModelViewMatrix = null;
	private static VertexSorter cachedVertexSorter = null;

	private PlayerTrailRenderer() {
	}

	public static void onClientTick(MinecraftClient client) {
		if (client.world == null) {
			reset();
			return;
		}

		if (!StasisClientState.isRunning() || !StasisClientState.affectsWorld(client.world)) {
			reset();
			return;
		}

		AbstractClientPlayerEntity activatingPlayer = getActivatingPlayer(client);
		if (activatingPlayer == null) {
			reset();
			return;
		}

		if (!activatingPlayer.getUuid().equals(trackedPlayerUuid)) {
			reset();
			trackedPlayerUuid = activatingPlayer.getUuid();
		}

		if (!StasisClientState.isActive()) {
			lastCapturedSourcePosition = null;
			captureCooldown = 0;
			return;
		}

		captureCooldown++;
		if (captureCooldown < CAPTURE_INTERVAL_TICKS) {
			return;
		}
		captureCooldown = 0;

		Vec3d currentPosition = activatingPlayer.getPos();
		if (lastCapturedSourcePosition == null) {
			addSnapshot(currentPosition.add(0.0, -TRAIL_VERTICAL_OFFSET, 0.0), activatingPlayer);
			lastCapturedSourcePosition = currentPosition;
			return;
		}

		Vec3d movement = currentPosition.subtract(lastCapturedSourcePosition);
		double movementLengthSquared = movement.lengthSquared();
		if (movementLengthSquared < MIN_CAPTURE_DISTANCE_SQUARED) {
			return;
		}

		double movementLength = Math.sqrt(movementLengthSquared);
		if (movementLength > INTERPOLATION_DISTANCE) {
			Vec3d midpoint = lastCapturedSourcePosition.lerp(currentPosition, 0.5);
			addSnapshot(midpoint.add(0.0, -TRAIL_VERTICAL_OFFSET, 0.0), activatingPlayer);
		}

		addSnapshot(currentPosition.add(0.0, -TRAIL_VERTICAL_OFFSET, 0.0), activatingPlayer);
		lastCapturedSourcePosition = currentPosition;
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || SNAPSHOTS.isEmpty()) {
			return;
		}

		if (!StasisClientState.isRunning() || !StasisClientState.affectsWorld(client.world)) {
			return;
		}

		AbstractClientPlayerEntity activatingPlayer = getActivatingPlayer(client);
		if (activatingPlayer == null) {
			return;
		}

		cacheWorldMatrix(context);
		if (shouldDeferToPostShader()) {
			return;
		}

		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		Vec3d cameraPos = context.camera().getPos();
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEffectVertexConsumers();

		// Save ALL original state
		float oldYaw = activatingPlayer.getYaw();
		float oldPitch = activatingPlayer.getPitch();
		float oldPrevYaw = activatingPlayer.prevYaw;
		float oldPrevPitch = activatingPlayer.prevPitch;
		float oldBodyYaw = activatingPlayer.bodyYaw;
		float oldPrevBodyYaw = activatingPlayer.prevBodyYaw;
		float oldHeadYaw = activatingPlayer.headYaw;
		float oldPrevHeadYaw = activatingPlayer.prevHeadYaw;
		int oldAge = activatingPlayer.age;
		float oldLastHandSwingProgress = activatingPlayer.lastHandSwingProgress;
		float oldHandSwingProgress = activatingPlayer.handSwingProgress;
		int oldHandSwingTicks = activatingPlayer.handSwingTicks;
		boolean oldHandSwinging = activatingPlayer.handSwinging;
		LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) activatingPlayer.limbAnimator;
		float oldLimbPrevSpeed = limbAnimator.stasis$getPrevSpeed();
		float oldLimbSpeed = limbAnimator.stasis$getSpeed();
		float oldLimbPos = limbAnimator.stasis$getPos();
		ItemStack[] oldEquipment = captureEquipment(activatingPlayer);

		dispatcher.setRenderShadows(false);
		try {
			Framebuffer trailFramebuffer = StasisShaderManager.getTrailFramebuffer(client.gameRenderer);
			if (trailFramebuffer != null) {
				trailFramebuffer.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
				trailFramebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
				trailFramebuffer.copyDepthFrom(client.getFramebuffer());
				trailFramebuffer.beginWrite(false);
				renderSnapshots(context.matrixStack(), dispatcher, activatingPlayer, limbAnimator, cameraPos, light, vertexConsumers);
				vertexConsumers.draw();
				trailFramebuffer.endWrite();
				client.getFramebuffer().beginWrite(false);
			}

			renderSnapshots(context.matrixStack(), dispatcher, activatingPlayer, limbAnimator, cameraPos, light, vertexConsumers);
			vertexConsumers.draw();
		} finally {
			dispatcher.setRenderShadows(true);
			// Reset player state to original
			activatingPlayer.setYaw(oldYaw);
			activatingPlayer.setPitch(oldPitch);
			activatingPlayer.prevYaw = oldPrevYaw;
			activatingPlayer.prevPitch = oldPrevPitch;
			activatingPlayer.bodyYaw = oldBodyYaw;
			activatingPlayer.prevBodyYaw = oldPrevBodyYaw;
			activatingPlayer.headYaw = oldHeadYaw;
			activatingPlayer.prevHeadYaw = oldPrevHeadYaw;
			activatingPlayer.age = oldAge;
			activatingPlayer.lastHandSwingProgress = oldLastHandSwingProgress;
			activatingPlayer.handSwingProgress = oldHandSwingProgress;
			activatingPlayer.handSwingTicks = oldHandSwingTicks;
			activatingPlayer.handSwinging = oldHandSwinging;
			limbAnimator.stasis$setPrevSpeed(oldLimbPrevSpeed);
			limbAnimator.stasis$setSpeed(oldLimbSpeed);
			limbAnimator.stasis$setPos(oldLimbPos);
			applyEquipment(activatingPlayer, oldEquipment);
			// CRITICAL: Pop any remaining afterimage state
			if (AfterimageRenderState.isActive()) {
				AfterimageRenderState.pop();
			}
		}
	}

	public static void renderPostShader(Camera camera) {
		if (!shouldDeferToPostShader()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || SNAPSHOTS.isEmpty() || camera == null || cachedWorldPositionMatrix == null) {
			return;
		}

		AbstractClientPlayerEntity activatingPlayer = getActivatingPlayer(client);
		if (activatingPlayer == null) {
			return;
		}

		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		Vec3d cameraPos = camera.getPos();
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEffectVertexConsumers();
		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(cachedWorldPositionMatrix);
		Matrix4f previousModelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());

		float oldYaw = activatingPlayer.getYaw();
		float oldPitch = activatingPlayer.getPitch();
		float oldPrevYaw = activatingPlayer.prevYaw;
		float oldPrevPitch = activatingPlayer.prevPitch;
		float oldBodyYaw = activatingPlayer.bodyYaw;
		float oldPrevBodyYaw = activatingPlayer.prevBodyYaw;
		float oldHeadYaw = activatingPlayer.headYaw;
		float oldPrevHeadYaw = activatingPlayer.prevHeadYaw;
		int oldAge = activatingPlayer.age;
		float oldLastHandSwingProgress = activatingPlayer.lastHandSwingProgress;
		float oldHandSwingProgress = activatingPlayer.handSwingProgress;
		int oldHandSwingTicks = activatingPlayer.handSwingTicks;
		boolean oldHandSwinging = activatingPlayer.handSwinging;
		LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) activatingPlayer.limbAnimator;
		float oldLimbPrevSpeed = limbAnimator.stasis$getPrevSpeed();
		float oldLimbSpeed = limbAnimator.stasis$getSpeed();
		float oldLimbPos = limbAnimator.stasis$getPos();
		ItemStack[] oldEquipment = captureEquipment(activatingPlayer);

		client.getFramebuffer().beginWrite(false);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		dispatcher.setRenderShadows(false);
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(new Matrix4f(cachedProjectionMatrix), cachedVertexSorter);
		RenderSystem.getModelViewStack().push();
		RenderSystem.getModelViewStack().loadIdentity();
		RenderSystem.getModelViewStack().multiplyPositionMatrix(cachedModelViewMatrix);
		RenderSystem.applyModelViewMatrix();
		try {
			renderSnapshots(matrices, dispatcher, activatingPlayer, limbAnimator, cameraPos, light, vertexConsumers);
			vertexConsumers.draw();
		} finally {
			RenderSystem.getModelViewStack().pop();
			RenderSystem.getModelViewStack().loadIdentity();
			RenderSystem.getModelViewStack().multiplyPositionMatrix(previousModelViewMatrix);
			RenderSystem.applyModelViewMatrix();
			RenderSystem.restoreProjectionMatrix();
			dispatcher.setRenderShadows(true);
			activatingPlayer.setYaw(oldYaw);
			activatingPlayer.setPitch(oldPitch);
			activatingPlayer.prevYaw = oldPrevYaw;
			activatingPlayer.prevPitch = oldPrevPitch;
			activatingPlayer.bodyYaw = oldBodyYaw;
			activatingPlayer.prevBodyYaw = oldPrevBodyYaw;
			activatingPlayer.headYaw = oldHeadYaw;
			activatingPlayer.prevHeadYaw = oldPrevHeadYaw;
			activatingPlayer.age = oldAge;
			activatingPlayer.lastHandSwingProgress = oldLastHandSwingProgress;
			activatingPlayer.handSwingProgress = oldHandSwingProgress;
			activatingPlayer.handSwingTicks = oldHandSwingTicks;
			activatingPlayer.handSwinging = oldHandSwinging;
			limbAnimator.stasis$setPrevSpeed(oldLimbPrevSpeed);
			limbAnimator.stasis$setSpeed(oldLimbSpeed);
			limbAnimator.stasis$setPos(oldLimbPos);
			applyEquipment(activatingPlayer, oldEquipment);
			if (AfterimageRenderState.isActive()) {
				AfterimageRenderState.pop();
			}
		}
	}

	private static void renderSnapshots(
			MatrixStack matrices,
			EntityRenderDispatcher dispatcher,
			AbstractClientPlayerEntity activatingPlayer,
			LimbAnimatorAccessor limbAnimator,
			Vec3d cameraPos,
			int light,
			VertexConsumerProvider.Immediate vertexConsumers
	) {
		int totalSnapshots = SNAPSHOTS.size();
		Vec3d playerPos = activatingPlayer.getPos();
		RenderSystem.depthMask(false);
		try {
			ItemStack[] lastAppliedEquipment = null;
			for (int index = 0; index < totalSnapshots; index++) {
				TrailSnapshot snapshot = SNAPSHOTS.get(index);
				if (snapshot.position.squaredDistanceTo(cameraPos) < MIN_CAMERA_DISTANCE_SQUARED) {
					continue;
				}

				float alpha = computeAlpha(index, totalSnapshots)
						* computeTransitionVisibility(index, totalSnapshots)
						* computeViewerFade(snapshot.position, playerPos, cameraPos);
				if (alpha <= 0.01f) {
					continue;
				}

				activatingPlayer.setYaw(snapshot.yaw);
				activatingPlayer.setPitch(snapshot.pitch);
				activatingPlayer.prevYaw = snapshot.yaw;
				activatingPlayer.prevPitch = snapshot.pitch;
				activatingPlayer.bodyYaw = snapshot.bodyYaw;
				activatingPlayer.prevBodyYaw = snapshot.bodyYaw;
				activatingPlayer.headYaw = snapshot.headYaw;
				activatingPlayer.prevHeadYaw = snapshot.headYaw;
				activatingPlayer.age = snapshot.age;
				activatingPlayer.lastHandSwingProgress = snapshot.lastHandSwingProgress;
				activatingPlayer.handSwingProgress = snapshot.handSwingProgress;
				activatingPlayer.handSwingTicks = snapshot.handSwingTicks;
				activatingPlayer.handSwinging = snapshot.handSwinging;
				limbAnimator.stasis$setPrevSpeed(snapshot.limbPrevSpeed);
				limbAnimator.stasis$setSpeed(snapshot.limbSpeed);
				limbAnimator.stasis$setPos(snapshot.limbPos);
				if (snapshot.equippedStacks != lastAppliedEquipment) {
					applyEquipment(activatingPlayer, snapshot.equippedStacks);
					lastAppliedEquipment = snapshot.equippedStacks;
				}

				AfterimageRenderState.push(
						alpha,
						snapshot.pose,
						snapshot.sneaking,
						snapshot.equippedStacks,
						snapshot.usingItem,
						snapshot.activeHand,
						snapshot.itemUseTimeLeft
				);
				try {
					dispatcher.render(
							activatingPlayer,
							snapshot.position.x - cameraPos.x,
							snapshot.position.y - cameraPos.y,
							snapshot.position.z - cameraPos.z,
							snapshot.bodyYaw,
							0.0f,
							matrices,
							vertexConsumers,
							light
					);
				} finally {
					AfterimageRenderState.pop();
				}
			}
		} finally {
			RenderSystem.depthMask(true);
		}
	}

	private static void addSnapshot(Vec3d position, AbstractClientPlayerEntity player) {
		// Don't capture snapshots while rendering afterimages - the mixin overrides can interfere
		if (AfterimageRenderState.isActive()) {
			return;
		}

		LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) player.limbAnimator;
		// Store the ACTUAL player state, bypassing any mixin overrides
		// Check pose directly - if sneaking, it should be CROUCHING
		EntityPose actualPose = player.getPose();
		boolean actualSneaking = player.isSneaking();

		// Use the actual captured values, not derived ones
		EntityPose capturedPose = actualPose;
		boolean capturedSneaking = actualSneaking;

		if (SNAPSHOTS.size() >= MAX_SNAPSHOTS) {
			SNAPSHOTS.remove(0);
		}

		SNAPSHOTS.add(new TrailSnapshot(
				position,
				player.getYaw(),
				player.getPitch(),
				player.getBodyYaw(),
				player.getHeadYaw(),
				player.age,
				limbAnimator.stasis$getPrevSpeed(),
				limbAnimator.stasis$getSpeed(),
				limbAnimator.stasis$getPos(),
				player.lastHandSwingProgress,
				player.handSwingProgress,
				player.handSwingTicks,
				player.handSwinging,
				capturedPose,
				capturedSneaking,
				captureEquipmentSnapshot(player),
				player.isUsingItem(),
				player.getActiveHand(),
				player.getItemUseTimeLeft()
		));
	}

	private static float computeAlpha(int index, int totalSnapshots) {
		if (totalSnapshots <= 1) {
			return TRAIL_ALPHA_MAX;
		}

		float t = index / (float) (totalSnapshots - 1);
		float densityScale = Math.max(TRAIL_DENSITY_MIN_SCALE, Math.min(1.0f, TRAIL_DENSITY_REFERENCE / totalSnapshots));
		return (TRAIL_ALPHA_MIN + (TRAIL_ALPHA_MAX - TRAIL_ALPHA_MIN) * t) * densityScale;
	}

	private static float computeTransitionVisibility(int index, int totalSnapshots) {
		if (StasisClientState.getPhase() != com.supper.stasis.StasisPhase.TRANSITION_OUT || totalSnapshots <= 0) {
			return 1.0f;
		}

		float releasePoint = StasisTimings.getReleaseScale(StasisClientState.getTransitionReleaseProgress()) * totalSnapshots;
		float remaining = (index + 1.0f) - releasePoint;
		return smootherstep(remaining);
	}

	private static float computeViewerFade(Vec3d snapshotPos, Vec3d playerPos, Vec3d cameraPos) {
		float playerFade = smoothHorizontalFade(snapshotPos, playerPos, PLAYER_FADE_START_DISTANCE, PLAYER_FADE_END_DISTANCE);
		float cameraFade = smoothHorizontalFade(snapshotPos, cameraPos, CAMERA_FADE_START_DISTANCE, CAMERA_FADE_END_DISTANCE);
		return playerFade * cameraFade;
	}

	private static float smoothHorizontalFade(Vec3d snapshotPos, Vec3d viewerPos, double startDistance, double endDistance) {
		double dx = snapshotPos.x - viewerPos.x;
		double dz = snapshotPos.z - viewerPos.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= startDistance) {
			return 0.0f;
		}
		if (distance >= endDistance) {
			return 1.0f;
		}

		float t = (float) ((distance - startDistance) / (endDistance - startDistance));
		return smootherstep(t);
	}

	private static float smootherstep(float value) {
		float t = Math.max(0.0f, Math.min(1.0f, value));
		return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
	}

	private static ItemStack[] captureEquipment(AbstractClientPlayerEntity player) {
		EquipmentSlot[] slots = EquipmentSlot.values();
		ItemStack[] equippedStacks = new ItemStack[slots.length];
		for (EquipmentSlot slot : slots) {
			equippedStacks[slot.ordinal()] = player.getEquippedStack(slot).copy();
		}
		return equippedStacks;
	}

	private static ItemStack[] captureEquipmentSnapshot(AbstractClientPlayerEntity player) {
		if (lastCapturedEquipmentSnapshot != null && equipmentMatches(player, lastCapturedEquipmentSnapshot)) {
			return lastCapturedEquipmentSnapshot;
		}
		lastCapturedEquipmentSnapshot = captureEquipment(player);
		return lastCapturedEquipmentSnapshot;
	}

	private static boolean equipmentMatches(AbstractClientPlayerEntity player, ItemStack[] equippedStacks) {
		if (equippedStacks == null || equippedStacks.length != EquipmentSlot.values().length) {
			return false;
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!ItemStack.canCombine(player.getEquippedStack(slot), equippedStacks[slot.ordinal()])) {
				return false;
			}
		}
		return true;
	}

	private static void applyEquipment(AbstractClientPlayerEntity player, ItemStack[] equippedStacks) {
		if (equippedStacks == null) {
			return;
		}

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.ordinal() < equippedStacks.length) {
				player.equipStack(slot, equippedStacks[slot.ordinal()]);
			}
		}
	}

	public static void reset() {
		SNAPSHOTS.clear();
		trackedPlayerUuid = null;
		captureCooldown = 0;
		lastCapturedSourcePosition = null;
		lastCapturedEquipmentSnapshot = null;
		cachedWorldPositionMatrix = null;
		cachedProjectionMatrix = null;
		cachedModelViewMatrix = null;
		cachedVertexSorter = null;
	}

	private static boolean shouldDeferToPostShader() {
		return IRIS_LOADED
				&& (StasisClientState.isActive()
				|| StasisClientState.getPhase() == com.supper.stasis.StasisPhase.TRANSITION_OUT);
	}

	private static void cacheWorldMatrix(WorldRenderContext context) {
		if (context.matrixStack() != null) {
			cachedWorldPositionMatrix = new Matrix4f(context.matrixStack().peek().getPositionMatrix());
			cachedProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
			cachedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
			cachedVertexSorter = RenderSystem.getVertexSorting();
		}
	}

	private static AbstractClientPlayerEntity getActivatingPlayer(MinecraftClient client) {
		if (client.world == null) {
			return null;
		}

		UUID activatingPlayerUuid = StasisClientState.getActivatingPlayerUUID() != null
				? StasisClientState.getActivatingPlayerUUID()
				: trackedPlayerUuid;
		if (activatingPlayerUuid == null) {
			return null;
		}

		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.getUuid().equals(activatingPlayerUuid)) {
				return player;
			}
		}

		return null;
	}

	private record TrailSnapshot(
			Vec3d position,
			float yaw,
			float pitch,
			float bodyYaw,
			float headYaw,
			int age,
			float limbPrevSpeed,
			float limbSpeed,
			float limbPos,
			float lastHandSwingProgress,
			float handSwingProgress,
			int handSwingTicks,
			boolean handSwinging,
			EntityPose pose,
			boolean sneaking,
			ItemStack[] equippedStacks,
			boolean usingItem,
			Hand activeHand,
			int itemUseTimeLeft
	) {
	}
}
