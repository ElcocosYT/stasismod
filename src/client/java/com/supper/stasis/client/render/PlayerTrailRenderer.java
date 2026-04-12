package com.supper.stasis.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.supper.stasis.Stasis;
import com.supper.stasis.StasisPhase;
import com.supper.stasis.StasisTimings;
import com.supper.stasis.client.StasisClientState;
import com.supper.stasis.client.compat.EmfTrailCompat;
import com.supper.stasis.client.compat.IrisShaderpackCompat;
import com.supper.stasis.client.mixin.ClientPlayerLikeStateAccessor;
import com.supper.stasis.client.mixin.LimbAnimatorAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.client.network.ClientPlayerLikeState;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

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
	private static GpuBufferSlice cachedProjectionMatrixBuffer = null;
	private static ProjectionType cachedProjectionType = null;
	private static Matrix4f cachedModelViewMatrix = null;
	private static CameraRenderState cachedCameraRenderState = null;
	private static boolean cachedTrailFramebufferDepthPrepared = false;

	private PlayerTrailRenderer() {
	}

	public static void onClientTick(MinecraftClient client) {
		if (client.world == null) {
			reset();
			return;
		}

		if (!Stasis.CONFIG.trailsActive()) {
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

		Vec3d currentPosition = activatingPlayer.getEntityPos();
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

	public static void beginRenderFrame() {
		cachedTrailFramebufferDepthPrepared = false;
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			cachedTrailFramebufferDepthPrepared = false;
			return;
		}

		if (!Stasis.CONFIG.trailsActive()) {
			cachedTrailFramebufferDepthPrepared = false;
			StasisShaderManager.clearTrailFramebuffer(context.gameRenderer());
			return;
		}

		if (!StasisClientState.isRunning() || !StasisClientState.affectsWorld(client.world)) {
			cachedTrailFramebufferDepthPrepared = false;
			return;
		}

		MatrixStack matrices = context.matrices();
		CameraRenderState cameraRenderState = context.worldState() != null ? context.worldState().cameraRenderState : null;
		if (matrices == null || cameraRenderState == null || cameraRenderState.pos == null) {
			return;
		}

		if (SNAPSHOTS.isEmpty()) {
			cachedTrailFramebufferDepthPrepared = false;
			StasisShaderManager.clearTrailFramebuffer(context.gameRenderer());
			return;
		}

		AbstractClientPlayerEntity activatingPlayer = getActivatingPlayer(client);
		if (activatingPlayer == null) {
			cachedTrailFramebufferDepthPrepared = false;
			StasisShaderManager.clearTrailFramebuffer(context.gameRenderer());
			return;
		}

		cacheRenderState(matrices, cameraRenderState);
		if (shouldRenderTrailFramebufferInPostPass()) {
			Framebuffer trailFramebuffer = StasisShaderManager.getTrailFramebuffer(context.gameRenderer());
			cachedTrailFramebufferDepthPrepared = false;
			if (trailFramebuffer != null) {
				clearTrailFramebuffer(trailFramebuffer);
				trailFramebuffer.copyDepthFrom(client.getFramebuffer());
				cachedTrailFramebufferDepthPrepared = true;
			}
			return;
		}
		if (shouldDeferToPostShader()) {
			cachedTrailFramebufferDepthPrepared = false;
			StasisShaderManager.clearTrailFramebuffer(context.gameRenderer());
			return;
		}

		EntityRenderManager renderManager = client.getEntityRenderDispatcher();
		BufferBuilderStorage bufferBuilders = client.getBufferBuilders();
		Vec3d cameraPos = cameraRenderState.pos;

		float oldYaw = activatingPlayer.getYaw();
		float oldPitch = activatingPlayer.getPitch();
		float oldLastYaw = activatingPlayer.lastYaw;
		float oldLastPitch = activatingPlayer.lastPitch;
		float oldBodyYaw = activatingPlayer.bodyYaw;
		float oldLastBodyYaw = activatingPlayer.lastBodyYaw;
		float oldHeadYaw = activatingPlayer.headYaw;
		float oldLastHeadYaw = activatingPlayer.lastHeadYaw;
		int oldAge = activatingPlayer.age;
		float oldLastHandSwingProgress = activatingPlayer.lastHandSwingProgress;
		float oldHandSwingProgress = activatingPlayer.handSwingProgress;
		int oldHandSwingTicks = activatingPlayer.handSwingTicks;
		boolean oldHandSwinging = activatingPlayer.handSwinging;
		LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) activatingPlayer.limbAnimator;
		float oldLimbPrevSpeed = limbAnimator.stasis$getPrevSpeed();
		float oldLimbSpeed = limbAnimator.stasis$getSpeed();
		float oldLimbPos = limbAnimator.stasis$getPos();
		CapeState oldCapeState = captureCapeState(activatingPlayer);
		ItemStack[] oldEquipment = captureEquipment(activatingPlayer);

		try (RenderDispatcher renderDispatcher = createTrailRenderDispatcher(client, bufferBuilders)) {
			OrderedRenderCommandQueueImpl commandQueue = renderDispatcher.getQueue();
			flushBufferBuilders(bufferBuilders);
			Framebuffer trailFramebuffer = StasisShaderManager.getTrailFramebuffer(context.gameRenderer());
			if (trailFramebuffer != null) {
				clearTrailFramebuffer(trailFramebuffer);
				trailFramebuffer.copyDepthFrom(client.getFramebuffer());

				RenderSystem.outputColorTextureOverride = trailFramebuffer.getColorAttachmentView();
				RenderSystem.outputDepthTextureOverride = trailFramebuffer.useDepthAttachment
						? trailFramebuffer.getDepthAttachmentView()
						: null;
				try {
					renderSnapshots(matrices, renderManager, renderDispatcher, commandQueue, bufferBuilders, activatingPlayer, limbAnimator, cameraPos, cameraRenderState);
				} finally {
					RenderSystem.outputColorTextureOverride = null;
					RenderSystem.outputDepthTextureOverride = null;
				}
			}

			renderSnapshots(matrices, renderManager, renderDispatcher, commandQueue, bufferBuilders, activatingPlayer, limbAnimator, cameraPos, cameraRenderState);
		} finally {
			activatingPlayer.setYaw(oldYaw);
			activatingPlayer.setPitch(oldPitch);
			activatingPlayer.lastYaw = oldLastYaw;
			activatingPlayer.lastPitch = oldLastPitch;
			activatingPlayer.bodyYaw = oldBodyYaw;
			activatingPlayer.lastBodyYaw = oldLastBodyYaw;
			activatingPlayer.headYaw = oldHeadYaw;
			activatingPlayer.lastHeadYaw = oldLastHeadYaw;
			activatingPlayer.age = oldAge;
			activatingPlayer.lastHandSwingProgress = oldLastHandSwingProgress;
			activatingPlayer.handSwingProgress = oldHandSwingProgress;
			activatingPlayer.handSwingTicks = oldHandSwingTicks;
			activatingPlayer.handSwinging = oldHandSwinging;
			limbAnimator.stasis$setPrevSpeed(oldLimbPrevSpeed);
			limbAnimator.stasis$setSpeed(oldLimbSpeed);
			limbAnimator.stasis$setPos(oldLimbPos);
			applyCapeState(activatingPlayer, oldCapeState);
			applyEquipment(activatingPlayer, oldEquipment);
			if (AfterimageRenderState.isActive()) {
				AfterimageRenderState.pop();
			}
		}
	}

	public static void captureTrailFramebufferPostWorld(Camera camera) {
		if (!shouldRenderTrailFramebufferInPostPass()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null
				|| SNAPSHOTS.isEmpty()
				|| camera == null
				|| cachedWorldPositionMatrix == null
				|| cachedProjectionMatrixBuffer == null
				|| cachedProjectionType == null
				|| cachedModelViewMatrix == null
				|| cachedCameraRenderState == null
				|| cachedCameraRenderState.pos == null) {
			return;
		}

		AbstractClientPlayerEntity activatingPlayer = getActivatingPlayer(client);
		if (activatingPlayer == null) {
			return;
		}

		EntityRenderManager renderManager = client.getEntityRenderDispatcher();
		BufferBuilderStorage bufferBuilders = client.getBufferBuilders();
		Vec3d cameraPos = cachedCameraRenderState.pos;
		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(cachedWorldPositionMatrix);
		Matrix4f previousModelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());

		float oldYaw = activatingPlayer.getYaw();
		float oldPitch = activatingPlayer.getPitch();
		float oldLastYaw = activatingPlayer.lastYaw;
		float oldLastPitch = activatingPlayer.lastPitch;
		float oldBodyYaw = activatingPlayer.bodyYaw;
		float oldLastBodyYaw = activatingPlayer.lastBodyYaw;
		float oldHeadYaw = activatingPlayer.headYaw;
		float oldLastHeadYaw = activatingPlayer.lastHeadYaw;
		int oldAge = activatingPlayer.age;
		float oldLastHandSwingProgress = activatingPlayer.lastHandSwingProgress;
		float oldHandSwingProgress = activatingPlayer.handSwingProgress;
		int oldHandSwingTicks = activatingPlayer.handSwingTicks;
		boolean oldHandSwinging = activatingPlayer.handSwinging;
		LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) activatingPlayer.limbAnimator;
		float oldLimbPrevSpeed = limbAnimator.stasis$getPrevSpeed();
		float oldLimbSpeed = limbAnimator.stasis$getSpeed();
		float oldLimbPos = limbAnimator.stasis$getPos();
		CapeState oldCapeState = captureCapeState(activatingPlayer);
		ItemStack[] oldEquipment = captureEquipment(activatingPlayer);

		Framebuffer trailFramebuffer = StasisShaderManager.getTrailFramebuffer(client.gameRenderer);
		if (trailFramebuffer == null) {
			cachedTrailFramebufferDepthPrepared = false;
			return;
		}

		GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
		GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;
		if (cachedTrailFramebufferDepthPrepared) {
			clearTrailFramebufferColorOnly(trailFramebuffer);
		} else {
			clearTrailFramebuffer(trailFramebuffer);
			trailFramebuffer.copyDepthFrom(client.getFramebuffer());
		}
		RenderSystem.outputColorTextureOverride = trailFramebuffer.getColorAttachmentView();
		RenderSystem.outputDepthTextureOverride = trailFramebuffer.useDepthAttachment
				? trailFramebuffer.getDepthAttachmentView()
				: null;

		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(cachedProjectionMatrixBuffer, cachedProjectionType);
		modelViewStack.pushMatrix();
		modelViewStack.identity();
		modelViewStack.mul(cachedModelViewMatrix);

		try (RenderDispatcher renderDispatcher = createTrailRenderDispatcher(client, bufferBuilders)) {
			OrderedRenderCommandQueueImpl commandQueue = renderDispatcher.getQueue();
			flushBufferBuilders(bufferBuilders);
			try {
				renderSnapshots(
						matrices,
						renderManager,
						renderDispatcher,
						commandQueue,
						bufferBuilders,
						activatingPlayer,
						limbAnimator,
						cameraPos,
						cachedCameraRenderState
				);

				Framebuffer mainFramebuffer = client.getFramebuffer();
				RenderSystem.outputColorTextureOverride = mainFramebuffer.getColorAttachmentView();
				RenderSystem.outputDepthTextureOverride = mainFramebuffer.useDepthAttachment
						? mainFramebuffer.getDepthAttachmentView()
						: null;
				renderSnapshots(
						matrices,
						renderManager,
						renderDispatcher,
						commandQueue,
						bufferBuilders,
						activatingPlayer,
						limbAnimator,
						cameraPos,
						cachedCameraRenderState
				);
			} finally {
				modelViewStack.popMatrix();
				modelViewStack.identity();
				modelViewStack.mul(previousModelViewMatrix);
				RenderSystem.restoreProjectionMatrix();
				RenderSystem.outputColorTextureOverride = previousColorOverride;
				RenderSystem.outputDepthTextureOverride = previousDepthOverride;
				activatingPlayer.setYaw(oldYaw);
				activatingPlayer.setPitch(oldPitch);
				activatingPlayer.lastYaw = oldLastYaw;
				activatingPlayer.lastPitch = oldLastPitch;
				activatingPlayer.bodyYaw = oldBodyYaw;
				activatingPlayer.lastBodyYaw = oldLastBodyYaw;
				activatingPlayer.headYaw = oldHeadYaw;
				activatingPlayer.lastHeadYaw = oldLastHeadYaw;
				activatingPlayer.age = oldAge;
				activatingPlayer.lastHandSwingProgress = oldLastHandSwingProgress;
				activatingPlayer.handSwingProgress = oldHandSwingProgress;
				activatingPlayer.handSwingTicks = oldHandSwingTicks;
				activatingPlayer.handSwinging = oldHandSwinging;
				limbAnimator.stasis$setPrevSpeed(oldLimbPrevSpeed);
				limbAnimator.stasis$setSpeed(oldLimbSpeed);
				limbAnimator.stasis$setPos(oldLimbPos);
				applyCapeState(activatingPlayer, oldCapeState);
				applyEquipment(activatingPlayer, oldEquipment);
				if (AfterimageRenderState.isActive()) {
					AfterimageRenderState.pop();
				}
			}
		}
	}

	public static void renderPostShader(Camera camera) {
		if (!shouldDeferToPostShader()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null
				|| SNAPSHOTS.isEmpty()
				|| camera == null
				|| cachedWorldPositionMatrix == null
				|| cachedProjectionMatrixBuffer == null
				|| cachedProjectionType == null
				|| cachedModelViewMatrix == null
				|| cachedCameraRenderState == null
				|| cachedCameraRenderState.pos == null) {
			return;
		}

		AbstractClientPlayerEntity activatingPlayer = getActivatingPlayer(client);
		if (activatingPlayer == null) {
			return;
		}

		EntityRenderManager renderManager = client.getEntityRenderDispatcher();
		BufferBuilderStorage bufferBuilders = client.getBufferBuilders();
		Vec3d cameraPos = cachedCameraRenderState.pos;

		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(cachedWorldPositionMatrix);
		Matrix4f previousModelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());

		float oldYaw = activatingPlayer.getYaw();
		float oldPitch = activatingPlayer.getPitch();
		float oldLastYaw = activatingPlayer.lastYaw;
		float oldLastPitch = activatingPlayer.lastPitch;
		float oldBodyYaw = activatingPlayer.bodyYaw;
		float oldLastBodyYaw = activatingPlayer.lastBodyYaw;
		float oldHeadYaw = activatingPlayer.headYaw;
		float oldLastHeadYaw = activatingPlayer.lastHeadYaw;
		int oldAge = activatingPlayer.age;
		float oldLastHandSwingProgress = activatingPlayer.lastHandSwingProgress;
		float oldHandSwingProgress = activatingPlayer.handSwingProgress;
		int oldHandSwingTicks = activatingPlayer.handSwingTicks;
		boolean oldHandSwinging = activatingPlayer.handSwinging;
		LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) activatingPlayer.limbAnimator;
		float oldLimbPrevSpeed = limbAnimator.stasis$getPrevSpeed();
		float oldLimbSpeed = limbAnimator.stasis$getSpeed();
		float oldLimbPos = limbAnimator.stasis$getPos();
		CapeState oldCapeState = captureCapeState(activatingPlayer);
		ItemStack[] oldEquipment = captureEquipment(activatingPlayer);

		Framebuffer mainFramebuffer = client.getFramebuffer();
		GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
		GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;
		RenderSystem.outputColorTextureOverride = mainFramebuffer.getColorAttachmentView();
		RenderSystem.outputDepthTextureOverride = mainFramebuffer.useDepthAttachment
				? mainFramebuffer.getDepthAttachmentView()
				: null;

		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(cachedProjectionMatrixBuffer, cachedProjectionType);
		modelViewStack.pushMatrix();
		modelViewStack.identity();
		modelViewStack.mul(cachedModelViewMatrix);

		try (RenderDispatcher renderDispatcher = createTrailRenderDispatcher(client, bufferBuilders)) {
			OrderedRenderCommandQueueImpl commandQueue = renderDispatcher.getQueue();
			flushBufferBuilders(bufferBuilders);
			try {
				renderSnapshots(
						matrices,
						renderManager,
						renderDispatcher,
						commandQueue,
						bufferBuilders,
						activatingPlayer,
						limbAnimator,
						cameraPos,
						cachedCameraRenderState
				);
			} finally {
				modelViewStack.popMatrix();
				modelViewStack.identity();
				modelViewStack.mul(previousModelViewMatrix);
				RenderSystem.restoreProjectionMatrix();
				RenderSystem.outputColorTextureOverride = previousColorOverride;
				RenderSystem.outputDepthTextureOverride = previousDepthOverride;
				activatingPlayer.setYaw(oldYaw);
				activatingPlayer.setPitch(oldPitch);
				activatingPlayer.lastYaw = oldLastYaw;
				activatingPlayer.lastPitch = oldLastPitch;
				activatingPlayer.bodyYaw = oldBodyYaw;
				activatingPlayer.lastBodyYaw = oldLastBodyYaw;
				activatingPlayer.headYaw = oldHeadYaw;
				activatingPlayer.lastHeadYaw = oldLastHeadYaw;
				activatingPlayer.age = oldAge;
				activatingPlayer.lastHandSwingProgress = oldLastHandSwingProgress;
				activatingPlayer.handSwingProgress = oldHandSwingProgress;
				activatingPlayer.handSwingTicks = oldHandSwingTicks;
				activatingPlayer.handSwinging = oldHandSwinging;
				limbAnimator.stasis$setPrevSpeed(oldLimbPrevSpeed);
				limbAnimator.stasis$setSpeed(oldLimbSpeed);
				limbAnimator.stasis$setPos(oldLimbPos);
				applyCapeState(activatingPlayer, oldCapeState);
				applyEquipment(activatingPlayer, oldEquipment);
				if (AfterimageRenderState.isActive()) {
					AfterimageRenderState.pop();
				}
			}
		}
	}

	private static boolean shouldDeferToPostShader() {
		return false;
	}

	private static boolean shouldRenderTrailFramebufferInPostPass() {
		return IRIS_LOADED && IrisShaderpackCompat.isShaderPackInUse();
	}

	private static void cacheRenderState(MatrixStack matrices, CameraRenderState cameraRenderState) {
		cachedWorldPositionMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
		cachedProjectionMatrixBuffer = RenderSystem.getProjectionMatrixBuffer();
		cachedProjectionType = RenderSystem.getProjectionType();
		cachedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
		cachedCameraRenderState = cameraRenderState;
	}

	private static void renderSnapshots(
			MatrixStack matrices,
			EntityRenderManager renderManager,
			RenderDispatcher renderDispatcher,
			OrderedRenderCommandQueueImpl commandQueue,
			BufferBuilderStorage bufferBuilders,
			AbstractClientPlayerEntity activatingPlayer,
			LimbAnimatorAccessor limbAnimator,
			Vec3d cameraPos,
			CameraRenderState cameraRenderState
	) {
		int totalSnapshots = SNAPSHOTS.size();
		Vec3d playerPos = activatingPlayer.getEntityPos();
		Vec3d originalRenderPosition = activatingPlayer.getEntityPos();
		double originalLastX = activatingPlayer.lastX;
		double originalLastY = activatingPlayer.lastY;
		double originalLastZ = activatingPlayer.lastZ;
		double originalLastRenderX = activatingPlayer.lastRenderX;
		double originalLastRenderY = activatingPlayer.lastRenderY;
		double originalLastRenderZ = activatingPlayer.lastRenderZ;
		flushBufferBuilders(bufferBuilders);
		GlStateManager._depthMask(false);
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
				activatingPlayer.lastYaw = snapshot.yaw;
				activatingPlayer.lastPitch = snapshot.pitch;
				activatingPlayer.bodyYaw = snapshot.bodyYaw;
				activatingPlayer.lastBodyYaw = snapshot.bodyYaw;
				activatingPlayer.headYaw = snapshot.headYaw;
				activatingPlayer.lastHeadYaw = snapshot.headYaw;
				activatingPlayer.age = snapshot.age;
				activatingPlayer.lastHandSwingProgress = snapshot.lastHandSwingProgress;
				activatingPlayer.handSwingProgress = snapshot.handSwingProgress;
				activatingPlayer.handSwingTicks = snapshot.handSwingTicks;
				activatingPlayer.handSwinging = snapshot.handSwinging;
				limbAnimator.stasis$setPrevSpeed(snapshot.limbPrevSpeed);
				limbAnimator.stasis$setSpeed(snapshot.limbSpeed);
				limbAnimator.stasis$setPos(snapshot.limbPos);
				applyCapeState(activatingPlayer, snapshot.capeState);
				if (!equipmentMatches(snapshot.equippedStacks, lastAppliedEquipment)) {
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
						snapshot.itemUseTimeLeft,
						snapshot.itemUseTime
				);
				try {
					EmfTrailCompat.runWithVanillaPlayerModel(activatingPlayer, () -> {
						EntityRenderState renderState;
						applyRenderPosition(activatingPlayer, snapshot.position);
						try {
							renderState = renderManager.getAndUpdateRenderState(activatingPlayer, 0.0f);
							renderState.shadowRadius = 0.0f;
							renderState.shadowPieces.clear();
						} finally {
							restoreRenderPosition(
									activatingPlayer,
									originalRenderPosition,
									originalLastX,
									originalLastY,
									originalLastZ,
									originalLastRenderX,
									originalLastRenderY,
									originalLastRenderZ
							);
						}
						renderManager.render(
								renderState,
								cameraRenderState,
								snapshot.position.x - cameraPos.x,
								snapshot.position.y - cameraPos.y,
								snapshot.position.z - cameraPos.z,
								matrices,
								commandQueue
						);
						// Build deferred commands while the afterimage overrides are active, but keep the
						// batched buffers alive until the whole pass is ready to draw like 1.21.1 did.
						renderDispatcher.render();
						renderDispatcher.endLayeredCustoms();
					});
				} finally {
					AfterimageRenderState.pop();
				}
			}
		} finally {
			flushBufferBuilders(bufferBuilders);
			restoreRenderPosition(
					activatingPlayer,
					originalRenderPosition,
					originalLastX,
					originalLastY,
					originalLastZ,
					originalLastRenderX,
					originalLastRenderY,
					originalLastRenderZ
			);
			GlStateManager._depthMask(true);
		}
	}

	private static void clearTrailFramebuffer(Framebuffer trailFramebuffer) {
		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
		if (trailFramebuffer.useDepthAttachment && trailFramebuffer.getDepthAttachment() != null) {
			commandEncoder.clearColorAndDepthTextures(
					trailFramebuffer.getColorAttachment(),
					0,
					trailFramebuffer.getDepthAttachment(),
					1.0D
			);
			return;
		}

		commandEncoder.clearColorTexture(trailFramebuffer.getColorAttachment(), 0);
	}

	private static void clearTrailFramebufferColorOnly(Framebuffer trailFramebuffer) {
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(trailFramebuffer.getColorAttachment(), 0);
	}

	private static RenderDispatcher createTrailRenderDispatcher(MinecraftClient client, BufferBuilderStorage bufferBuilders) {
		return new RenderDispatcher(
				new OrderedRenderCommandQueueImpl(),
				client.getBlockRenderManager(),
				bufferBuilders.getEntityVertexConsumers(),
				client.getAtlasManager(),
				bufferBuilders.getOutlineVertexConsumers(),
				bufferBuilders.getEffectVertexConsumers(),
				client.textRenderer
		);
	}

	private static void flushBufferBuilders(BufferBuilderStorage bufferBuilders) {
		bufferBuilders.getEntityVertexConsumers().draw();
		bufferBuilders.getOutlineVertexConsumers().draw();
		bufferBuilders.getEffectVertexConsumers().draw();
	}

	private static void addSnapshot(Vec3d position, AbstractClientPlayerEntity player) {
		if (AfterimageRenderState.isActive()) {
			return;
		}

		LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) player.limbAnimator;
		EntityPose capturedPose = player.getPose();
		boolean capturedSneaking = player.isSneaking();

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
				captureCapeState(player),
				capturedPose,
				capturedSneaking,
				copyEquipmentSnapshot(captureEquipmentSnapshot(player)),
				player.isUsingItem(),
				player.getActiveHand(),
				player.getItemUseTimeLeft(),
				player.getItemUseTime(0.0f)
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
		if (StasisClientState.getPhase() != StasisPhase.TRANSITION_OUT || totalSnapshots <= 0) {
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

	private static CapeState captureCapeState(AbstractClientPlayerEntity player) {
		ClientPlayerLikeStateAccessor state = getCapeStateAccessor(player);
		return new CapeState(
				state.stasis$getLastX(),
				state.stasis$getX(),
				state.stasis$getLastY(),
				state.stasis$getY(),
				state.stasis$getLastZ(),
				state.stasis$getZ(),
				state.stasis$getLastMovement(),
				state.stasis$getMovement(),
				state.stasis$getLastDistanceMoved(),
				state.stasis$getDistanceMoved()
		);
	}

	private static void applyCapeState(AbstractClientPlayerEntity player, CapeState capeState) {
		ClientPlayerLikeStateAccessor state = getCapeStateAccessor(player);
		state.stasis$setLastX(capeState.lastX());
		state.stasis$setX(capeState.x());
		state.stasis$setLastY(capeState.lastY());
		state.stasis$setY(capeState.y());
		state.stasis$setLastZ(capeState.lastZ());
		state.stasis$setZ(capeState.z());
		state.stasis$setLastMovement(capeState.lastMovement());
		state.stasis$setMovement(capeState.movement());
		state.stasis$setLastDistanceMoved(capeState.lastDistanceMoved());
		state.stasis$setDistanceMoved(capeState.distanceMoved());
	}

	private static ClientPlayerLikeStateAccessor getCapeStateAccessor(AbstractClientPlayerEntity player) {
		ClientPlayerLikeState state = ((ClientPlayerLikeEntity) player).getState();
		return (ClientPlayerLikeStateAccessor) state;
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
			if (!ItemStack.areItemsAndComponentsEqual(player.getEquippedStack(slot), getSnapshotStack(equippedStacks, slot))) {
				return false;
			}
		}
		return true;
	}

	private static boolean equipmentMatches(ItemStack[] first, ItemStack[] second) {
		if (first == second) {
			return true;
		}
		if (first == null || second == null || first.length != EquipmentSlot.values().length || second.length != EquipmentSlot.values().length) {
			return false;
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!ItemStack.areItemsAndComponentsEqual(getSnapshotStack(first, slot), getSnapshotStack(second, slot))) {
				return false;
			}
		}
		return true;
	}

	private static ItemStack[] copyEquipmentSnapshot(ItemStack[] equippedStacks) {
		if (equippedStacks == null) {
			return null;
		}

		EquipmentSlot[] slots = EquipmentSlot.values();
		ItemStack[] copiedStacks = new ItemStack[slots.length];
		for (EquipmentSlot slot : slots) {
			copiedStacks[slot.ordinal()] = copySnapshotStack(getSnapshotStack(equippedStacks, slot));
		}
		return copiedStacks;
	}

	private static ItemStack getSnapshotStack(ItemStack[] equippedStacks, EquipmentSlot slot) {
		if (equippedStacks == null || slot.ordinal() >= equippedStacks.length) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = equippedStacks[slot.ordinal()];
		return stack != null ? stack : ItemStack.EMPTY;
	}

	private static ItemStack copySnapshotStack(ItemStack stack) {
		return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
	}

	private static void applyRenderPosition(AbstractClientPlayerEntity player, Vec3d position) {
		player.setPosition(position);
		player.lastX = position.x;
		player.lastY = position.y;
		player.lastZ = position.z;
		player.lastRenderX = position.x;
		player.lastRenderY = position.y;
		player.lastRenderZ = position.z;
	}

	private static void restoreRenderPosition(
			AbstractClientPlayerEntity player,
			Vec3d position,
			double lastX,
			double lastY,
			double lastZ,
			double lastRenderX,
			double lastRenderY,
			double lastRenderZ
	) {
		player.setPosition(position);
		player.lastX = lastX;
		player.lastY = lastY;
		player.lastZ = lastZ;
		player.lastRenderX = lastRenderX;
		player.lastRenderY = lastRenderY;
		player.lastRenderZ = lastRenderZ;
	}

	private static void applyEquipment(AbstractClientPlayerEntity player, ItemStack[] equippedStacks) {
		if (equippedStacks == null) {
			return;
		}

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			player.equipStack(slot, copySnapshotStack(getSnapshotStack(equippedStacks, slot)));
		}
	}

	public static void reset() {
		SNAPSHOTS.clear();
		trackedPlayerUuid = null;
		captureCooldown = 0;
		lastCapturedSourcePosition = null;
		lastCapturedEquipmentSnapshot = null;
		cachedWorldPositionMatrix = null;
		cachedProjectionMatrixBuffer = null;
		cachedProjectionType = null;
		cachedModelViewMatrix = null;
		cachedCameraRenderState = null;
		cachedTrailFramebufferDepthPrepared = false;
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
			CapeState capeState,
			EntityPose pose,
			boolean sneaking,
			ItemStack[] equippedStacks,
			boolean usingItem,
			Hand activeHand,
			int itemUseTimeLeft,
			float itemUseTime
	) {
	}

	private record CapeState(
			double lastX,
			double x,
			double lastY,
			double y,
			double lastZ,
			double z,
			float lastMovement,
			float movement,
			float lastDistanceMoved,
			float distanceMoved
	) {
	}
}
