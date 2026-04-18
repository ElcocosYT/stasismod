package com.supper.stasis.client;

import com.supper.stasis.StasisTimings;
import com.supper.stasis.StasisPhase;
import com.supper.stasis.client.mixin.LimbAnimatorAccessor;
import com.supper.stasis.client.render.ShockwaveRenderer;
import com.supper.stasis.network.StasisSyncPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


public final class StasisClientState {
    private static StasisPhase phase = StasisPhase.IDLE;
    private static float progress = 0.0f;
    private static UUID activatingPlayerUUID = null;
    private static int activeTicksRemaining = 0;
    private static int warningTicks = 0;
    private static final Map<UUID, ClientFrozenEntityState> frozenEntityStates = new HashMap<>();
    private static final Map<UUID, ClientTransitionTickState> transitionTickStates = new HashMap<>();
    private static final Map<UUID, Float> activeLivingRenderDeltas = new HashMap<>();
    private static final Map<UUID, Float> itemVirtualAges = new HashMap<>();
    private static float lastVanillaRenderTickDelta = 0.0f;
    private static double precipitationTimelineVanilla = Double.NaN;
    private static double precipitationTimelineScaled = 0.0;

    // For continuous per-frame interpolation during transitions (fixes the "32fps" visual)
    private static float renderProgressStart = 0.0f;
    private static float renderProgressEnd = 0.0f;
    private static float renderProgressFrame = 0.0f;


    private StasisClientState() {
    }


    public static void apply(StasisSyncPayload payload) {
        StasisPhase oldPhase = phase;
        StasisPhase newPhase = payload.phase();
        Map<UUID, Float> releaseStartLivingRenderDeltas = null;
        Map<UUID, ClientFrozenEntityState> preservedNonLivingStates = null;
        if (phase != newPhase) {
            if (oldPhase.isRunning() && newPhase.isRunning()) {
                preservedNonLivingStates = preserveNonLivingFrozenStates();
            }
            if (oldPhase == StasisPhase.TRANSITION_IN && newPhase == StasisPhase.ACTIVE) {
                activeLivingRenderDeltas.clear();
                for (Map.Entry<UUID, ClientFrozenEntityState> entry : frozenEntityStates.entrySet()) {
                    ClientFrozenEntityState state = entry.getValue();
                    activeLivingRenderDeltas.put(entry.getKey(), StasisTimings.clamp01(lerp(state.renderProgressStart, state.renderProgressEnd, lastVanillaRenderTickDelta)));
                }
                syncActivePreviousRenderStateForNonLiving();
            } else if (oldPhase == StasisPhase.ACTIVE && newPhase == StasisPhase.TRANSITION_OUT) {
                releaseStartLivingRenderDeltas = new HashMap<>(activeLivingRenderDeltas);
                activeLivingRenderDeltas.clear();
            } else {
                activeLivingRenderDeltas.clear();
            }
            frozenEntityStates.clear();
            transitionTickStates.clear();
            itemVirtualAges.clear();
            if (preservedNonLivingStates != null) {
                frozenEntityStates.putAll(preservedNonLivingStates);
            }
            if (releaseStartLivingRenderDeltas != null) {
                seedTransitionOutLivingStatesFromActivePose(releaseStartLivingRenderDeltas);
            }
        }
        if (newPhase.isTransition()) {
            if (oldPhase == newPhase) {
                renderProgressStart = renderProgressEnd;
            } else {
                renderProgressStart = payload.progress();
            }
            renderProgressEnd = payload.progress();
        } else {
            renderProgressStart = payload.progress();
            renderProgressEnd = payload.progress();
            renderProgressFrame = payload.progress();
        }
        phase = newPhase;
        progress = payload.progress();
        activatingPlayerUUID = payload.activatingPlayerUUID();
        activeTicksRemaining = payload.activeTicksRemaining();
        warningTicks = payload.warningTicks();

        // Trigger shockwave effect when entering TRANSITION_IN
        if (oldPhase == StasisPhase.IDLE && newPhase == StasisPhase.TRANSITION_IN) {
            ShockwaveRenderer.trigger();
        }
        ShockwaveRenderer.tick();
    }


    /**
     * Called once per render frame (from GameRendererMixin) with the current vanilla tickDelta.
     * Updates the per-frame render progress window used to interpolate movement scale continuously.
     */
    public static void updateRenderProgress(float tickDelta) {
        if (phase == StasisPhase.TRANSITION_IN || phase == StasisPhase.TRANSITION_OUT) {
            renderProgressFrame = lerp(renderProgressStart, renderProgressEnd, StasisTimings.clamp01(tickDelta));
        } else if (phase == StasisPhase.ACTIVE) {
            renderProgressFrame = progress;
        } else {
            renderProgressFrame = progress;
        }
        lastVanillaRenderTickDelta = StasisTimings.clamp01(tickDelta);
    }


    public static void reset() {
        phase = StasisPhase.IDLE;
        progress = 0.0f;
        activatingPlayerUUID = null;
        activeTicksRemaining = 0;
        warningTicks = 0;
        frozenEntityStates.clear();
        transitionTickStates.clear();
        activeLivingRenderDeltas.clear();
        itemVirtualAges.clear();
        renderProgressStart = 0.0f;
        renderProgressEnd = 0.0f;
        renderProgressFrame = 0.0f;
        lastVanillaRenderTickDelta = 0.0f;
        precipitationTimelineVanilla = Double.NaN;
        precipitationTimelineScaled = 0.0;
    }


    public static StasisPhase getPhase() {
        return phase;
    }


    public static float getProgress() {
        return progress;
    }

    public static float getRenderProgressFrame() {
        return renderProgressFrame;
    }

    private static float getCurrentPrecipitationMovementScale() {
        float scaleInput = phase == StasisPhase.TRANSITION_IN || phase == StasisPhase.TRANSITION_OUT
                ? renderProgressFrame
                : progress;
        return StasisTimings.getMovementScale(phase, scaleInput);
    }

    /**
     * Integrates precipitation time forward using the current movement scale so the result stays
     * monotonic through transition curves and never "reverses" mid-freeze.
     */
    private static double getPrecipitationTimeline(double vanillaTime) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !isRunning() || !affectsWorld(client.world)) {
            precipitationTimelineVanilla = vanillaTime;
            precipitationTimelineScaled = vanillaTime;
            return vanillaTime;
        }
        if (Double.isNaN(precipitationTimelineVanilla) || vanillaTime < precipitationTimelineVanilla) {
            precipitationTimelineVanilla = vanillaTime;
            precipitationTimelineScaled = vanillaTime;
            return vanillaTime;
        }
        double delta = vanillaTime - precipitationTimelineVanilla;
        if (delta > 0.0) {
            precipitationTimelineScaled += delta * getCurrentPrecipitationMovementScale();
            precipitationTimelineVanilla = vanillaTime;
        }
        return precipitationTimelineScaled;
    }

    public static int getPrecipitationTicks(int vanillaTicks, float vanillaTickProgress) {
        double precipitationTime = getPrecipitationTimeline(vanillaTicks + StasisTimings.clamp01(vanillaTickProgress));
        return (int) Math.floor(precipitationTime + 1.0e-6);
    }

    public static int getPrecipitationTicks(int vanillaTicks) {
        return getPrecipitationTicks(vanillaTicks, lastVanillaRenderTickDelta);
    }

    public static float getPrecipitationSubTickProgress(int vanillaTicks, float vanillaTickProgress) {
        double precipitationTime = getPrecipitationTimeline(vanillaTicks + StasisTimings.clamp01(vanillaTickProgress));
        double precipitationWholeTicks = Math.floor(precipitationTime + 1.0e-6);
        return StasisTimings.clamp01((float) (precipitationTime - precipitationWholeTicks));
    }


    public static UUID getActivatingPlayerUUID() {
        return activatingPlayerUUID;
    }


    public static int getActiveTicksRemaining() {
        return activeTicksRemaining;
    }


    public static int getWarningTicks() {
        return warningTicks;
    }


    public static boolean isRunning() {
        return phase.isRunning();
    }


    public static boolean isActive() {
        return phase == StasisPhase.ACTIVE;
    }


    public static boolean isTransitioning() {
        return phase.isTransition();
    }


    public static boolean affectsWorld(ClientWorld world) {
        return world != null && phase.isRunning();
    }


    public static boolean isPrivilegedEntity(Entity entity) {
        if (entity == null || activatingPlayerUUID == null) {
            return false;
        }
        if (activatingPlayerUUID.equals(entity.getUuid())) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return false;
        }
        Entity activatingEntity = client.world.getPlayerByUuid(activatingPlayerUUID);
        if (activatingEntity == null) {
            return false;
        }
        Entity vehicle = activatingEntity.getVehicle();
        if (vehicle != null && entity == vehicle) {
            return true;
        }
        Entity rootVehicle = activatingEntity.getRootVehicle();
        if (rootVehicle != null && rootVehicle != activatingEntity && entity == rootVehicle) {
            return true;
        }
        return false;
    }


    public static boolean isRestrictedLocalPlayer(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player != null
                && entity == player
                && client.world != null
                && phase == StasisPhase.ACTIVE
                && affectsWorld(client.world)
                && !isPrivilegedEntity(player);
    }


    public static boolean isTransitioningRestrictedLocalPlayer(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player != null
                && entity == player
                && client.world != null
                && phase.isTransition()
                && affectsWorld(client.world)
                && !isPrivilegedEntity(player);
    }


    public static void stabilizeRestrictedLocalPlayer(ClientPlayerEntity player) {
        if (player == null) {
            return;
        }

        player.prevX = player.getX();
        player.prevY = player.getY();
        player.prevZ = player.getZ();
        player.lastRenderX = player.getX();
        player.lastRenderY = player.getY();
        player.lastRenderZ = player.getZ();
        player.prevYaw = player.getYaw();
        player.prevPitch = player.getPitch();
        player.prevBodyYaw = player.getBodyYaw();
        player.prevHeadYaw = player.getHeadYaw();
        player.lastRenderYaw = player.getYaw();
        player.renderYaw = player.getYaw();
        player.lastRenderPitch = player.getPitch();
        player.renderPitch = player.getPitch();
        player.prevHorizontalSpeed = 0.0f;
        player.horizontalSpeed = 0.0f;
        player.prevStrideDistance = 0.0f;
        player.strideDistance = 0.0f;
    }


    public static boolean shouldFreezeEntity(Entity entity) {
        if (!(entity.getWorld() instanceof ClientWorld clientWorld)) {
            return false;
        }
        return phase == StasisPhase.ACTIVE && affectsWorld(clientWorld) && !isPrivilegedEntity(entity);
    }


    public static boolean shouldFreezeVolatileProgress(Entity entity) {
        if (!(entity.getWorld() instanceof ClientWorld clientWorld)) {
            return false;
        }
        return affectsWorld(clientWorld)
                && !isPrivilegedEntity(entity);
    }


    public static boolean shouldAdvanceVolatileProgress(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return true;
        }
        if (!shouldFreezeVolatileProgress(entity)) {
            return true;
        }
        ClientFrozenEntityState state = frozenEntityStates.computeIfAbsent(entity.getUuid(), uuid -> new ClientFrozenEntityState());
        long worldTick = entity.getWorld().getTime();
        if (phase == StasisPhase.ACTIVE) {
            state.lastVolatileProgressTick = worldTick;
            state.advanceVolatileProgressThisTick = false;
            state.volatileProgressBudget = 0.0f;
            state.renderProgressStart = 0.0f;
            state.renderProgressEnd = 0.0f;
            stasis$logVolatileDecision("client", entity, state, 0.0f, false, true);
            return false;
        }
        if (state.lastVolatileProgressTick == worldTick) {
            return state.advanceVolatileProgressThisTick;
        }
        state.lastVolatileProgressTick = worldTick;
        float movementScale = StasisTimings.clamp01(getMovementMultiplier(entity));
        if (movementScale >= 0.9999f) {
            state.volatileProgressBudget = 0.0f;
            state.renderProgressStart = 0.0f;
            state.renderProgressEnd = 1.0f;
            state.advanceVolatileProgressThisTick = true;
            state.forceAdvanceVolatileProgress = false;
            stasis$logVolatileDecision("client", entity, state, movementScale, true, false);
            return true;
        }
        if (state.forceAdvanceVolatileProgress) {
            state.forceAdvanceVolatileProgress = false;
            state.volatileProgressBudget = 0.0f;
            state.renderProgressStart = 0.0f;
            state.renderProgressEnd = 1.0f;
            state.advanceVolatileProgressThisTick = true;
            stasis$logVolatileDecision("client", entity, state, movementScale, true, false);
            return true;
        }
        float previousProgress = state.volatileProgressBudget;
        float nextProgress = previousProgress + movementScale;
        if (nextProgress >= 1.0f) {
            state.volatileProgressBudget = nextProgress - 1.0f;
            // Carry the overflow remainder into the new range so the first frame after
            // a real tick continues smoothly instead of replaying the whole step at [0,1].
            state.renderProgressStart = 0.0f;
            state.renderProgressEnd = state.volatileProgressBudget;
            state.advanceVolatileProgressThisTick = true;
        } else {
            state.renderProgressStart = previousProgress;
            state.renderProgressEnd = nextProgress;
            state.volatileProgressBudget = nextProgress;
            state.advanceVolatileProgressThisTick = false;
        }
        stasis$logVolatileDecision("client", entity, state, movementScale, state.advanceVolatileProgressThisTick, false);
        return state.advanceVolatileProgressThisTick;
    }


    public static float getMovementMultiplier(Entity entity) {
        if (!(entity.getWorld() instanceof ClientWorld clientWorld)) {
            return 1.0f;
        }
        if (!affectsWorld(clientWorld) || isPrivilegedEntity(entity)) {
            return 1.0f;
        }
        return StasisTimings.getMovementScale(phase, progress);
    }


    public static float getTransitionReleaseProgress() {
        return StasisTimings.clamp01(1.0f - progress);
    }


    public static float getWorldSoundVolumeScale() {
        return switch (phase) {
            case IDLE -> 1.0f;
            case TRANSITION_IN -> StasisTimings.getFreezeScale(progress);
            case ACTIVE -> 0.0f;
            case TRANSITION_OUT -> StasisTimings.getReleaseScale(getTransitionReleaseProgress());
        };
    }


    public static float getWorldSoundPitchScale() {
        float minPitch = StasisTimings.MIN_WORLD_SOUND_PITCH;
        return switch (phase) {
            case IDLE -> 1.0f;
            case TRANSITION_IN -> lerp(minPitch, 1.0f, StasisTimings.getFreezeScale(progress));
            case ACTIVE -> minPitch;
            case TRANSITION_OUT -> lerp(minPitch, 1.0f, StasisTimings.getReleaseScale(getTransitionReleaseProgress()));
        };
    }


    public static void captureTransitionTickState(Entity entity) {
        if (!isTransitioning() || isPrivilegedEntity(entity) || entity.isRemoved()) {
            transitionTickStates.remove(entity.getUuid());
            return;
        }
        transitionTickStates.put(entity.getUuid(), ClientTransitionTickState.capture(entity));
    }


    public static void applyTransitionTickState(Entity entity) {
        ClientTransitionTickState state = transitionTickStates.remove(entity.getUuid());
        if (state == null || !isTransitioning() || isPrivilegedEntity(entity)
                || entity.isRemoved()) {
            return;
        }
        float transitionScale = StasisTimings.clamp01(getMovementMultiplier(entity));
        if (entity instanceof LivingEntity livingEntity) {
            Vec3d preTickPosition = state.position;
            Vec3d postTickPosition = entity.getPos();
            Vec3d tickMovementDelta = postTickPosition.subtract(preTickPosition);
            entity.setPosition(preTickPosition.add(tickMovementDelta.multiply(transitionScale)));
            entity.setVelocity(state.velocity.add(entity.getVelocity().subtract(state.velocity).multiply(transitionScale)));
            float yaw = lerpAngle(state.yaw, entity.getYaw(), transitionScale);
            float pitch = lerpAngle(state.pitch, entity.getPitch(), transitionScale);
            entity.setYaw(yaw);
            entity.prevYaw = yaw;
            entity.setPitch(pitch);
            entity.prevPitch = pitch;
            entity.fallDistance = lerp(state.fallDistance, entity.fallDistance, transitionScale);
            float bodyYaw = lerpAngle(state.bodyYaw, livingEntity.getBodyYaw(), transitionScale);
            float headYaw = lerpAngle(state.headYaw, livingEntity.getHeadYaw(), transitionScale);
            livingEntity.setBodyYaw(bodyYaw);
            livingEntity.prevBodyYaw = bodyYaw;
            livingEntity.setHeadYaw(headYaw);
            livingEntity.prevHeadYaw = headYaw;

            // Keep limb animation progression in lockstep with the same transition scale used
            // for body movement so legs/arms/squash don't run at normal speed while slowed.
            LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) livingEntity.limbAnimator;
            float scaledPrevSpeed = lerp(state.limbPrevSpeed, limbAnimator.stasis$getPrevSpeed(), transitionScale);
            float scaledSpeed = lerp(state.limbSpeed, limbAnimator.stasis$getSpeed(), transitionScale);
            float scaledPos = lerp(state.limbPos, limbAnimator.stasis$getPos(), transitionScale);
            limbAnimator.stasis$setPrevSpeed(scaledPrevSpeed);
            limbAnimator.stasis$setSpeed(scaledSpeed);
            limbAnimator.stasis$setPos(scaledPos);
            return;
        }

        if (transitionScale <= 0.0001f) {
            Vec3d frozenVelocity = hasMeaningfulVelocity(entity.getVelocity()) ? entity.getVelocity() : state.velocity;
            entity.setPosition(state.position);
            entity.setVelocity((entity instanceof ProjectileEntity || entity instanceof net.minecraft.entity.ItemEntity) ? frozenVelocity : Vec3d.ZERO);
            entity.setYaw(state.yaw);
            entity.prevYaw = state.yaw;
            entity.setPitch(state.pitch);
            entity.prevPitch = state.pitch;
            entity.fallDistance = state.fallDistance;
            entity.setOnGround(state.onGround);
            entity.horizontalCollision = state.horizontalCollision;
            entity.verticalCollision = state.verticalCollision;
            entity.groundCollision = state.groundCollision;
            entity.velocityModified = state.velocityModified;
            syncNonLivingTransitionRenderState(entity, state.position, state.yaw, state.pitch);
        } else {
            Vec3d scaledPosition = state.position.add(entity.getPos().subtract(state.position).multiply(transitionScale));
            entity.setPosition(scaledPosition);
            entity.setVelocity(lerpVec(state.velocity, entity.getVelocity(), transitionScale));
            float yaw = lerpAngle(state.yaw, entity.getYaw(), transitionScale);
            float pitch = lerpAngle(state.pitch, entity.getPitch(), transitionScale);
            entity.setYaw(yaw);
            entity.prevYaw = yaw;
            entity.setPitch(pitch);
            entity.prevPitch = pitch;
            entity.fallDistance = lerp(state.fallDistance, entity.fallDistance, transitionScale);
            entity.setOnGround(state.onGround || entity.isOnGround());
            entity.horizontalCollision = state.horizontalCollision || entity.horizontalCollision;
            entity.verticalCollision = state.verticalCollision || entity.verticalCollision;
            entity.groundCollision = state.groundCollision || entity.groundCollision;
            entity.velocityModified = state.velocityModified || entity.velocityModified;
            syncNonLivingTransitionRenderState(entity, state.position, yaw, pitch);
        }
    }


    public static void captureEntityMomentum(Entity entity) {
        if (!(entity.getWorld() instanceof ClientWorld clientWorld)
                || !affectsWorld(clientWorld) || isPrivilegedEntity(entity) || entity.isRemoved()) {
            return;
        }
        ClientFrozenEntityState state = frozenEntityStates.computeIfAbsent(entity.getUuid(), uuid -> new ClientFrozenEntityState());
        Vec3d currentVelocity = entity.getVelocity();
        if (!state.initialized) {
            state.velocity = currentVelocity;
            state.resumeVelocity = currentVelocity;
            state.allowVelocityRefresh = !hasMeaningfulVelocity(currentVelocity);
            state.initialized = true;
        }
        if (state.allowVelocityRefresh && hasMeaningfulVelocity(currentVelocity)) {
            state.velocity = currentVelocity;
            state.resumeVelocity = currentVelocity;
            state.allowVelocityRefresh = false;
        }
    }


    public static void prepareTransitionTick(Entity entity) {
        // Projectiles should keep the natural release arc during TRANSITION_OUT.
        // Re-applying the stored resume velocity every tick makes them "sag" and
        // snap back upward between frames instead of following one smooth path.
        if (phase != StasisPhase.TRANSITION_OUT || isPrivilegedEntity(entity)
                || entity instanceof LivingEntity || entity instanceof ProjectileEntity || entity instanceof net.minecraft.entity.ItemEntity || entity.isRemoved()) {
            return;
        }
        ClientFrozenEntityState state = frozenEntityStates.get(entity.getUuid());
        if (state == null) {
            return;
        }
        entity.setVelocity(state.resumeVelocity);
    }

    public static void captureResumeVelocity(Entity entity) {
        if (!phase.isTransition() || isPrivilegedEntity(entity)
                || entity instanceof LivingEntity || entity.isRemoved()) {
            return;
        }
        ClientFrozenEntityState state = frozenEntityStates.get(entity.getUuid());
        if (state == null) {
            return;
        }
        state.resumeVelocity = entity.getVelocity();
    }

    public static void onLivingTransitionTickDecision(LivingEntity entity, boolean willAdvance) {
    }

    public static void onLivingTransitionTickApplied(LivingEntity entity) {
    }

    public static float getEntityRenderTickDelta(Entity entity, float vanillaTickDelta) {
        if (!(entity.getWorld() instanceof ClientWorld clientWorld)) {
            return vanillaTickDelta;
        }
        if (!affectsWorld(clientWorld) || isPrivilegedEntity(entity)) {
            return vanillaTickDelta;
        }

        if (phase == StasisPhase.ACTIVE) {
            if (entity instanceof LivingEntity) {
                return activeLivingRenderDeltas.getOrDefault(entity.getUuid(), 0.0f);
            }
            return 0.0f;
        }

        if (entity instanceof LivingEntity) {
            if (phase.isTransition()) {
                ClientFrozenEntityState state = frozenEntityStates.computeIfAbsent(entity.getUuid(), uuid -> new ClientFrozenEntityState());
                return StasisTimings.clamp01(lerp(state.renderProgressStart, state.renderProgressEnd, vanillaTickDelta));
            }
            return vanillaTickDelta;
        }

        if (phase.isTransition()) {
            // Items use virtual age system: return frac(virtualAge) + movementScale * tickDelta
            if (entity instanceof ItemEntity) {
                return getItemEntityRenderTickDelta(entity, vanillaTickDelta);
            }
            return vanillaTickDelta * getMovementMultiplierFromRenderProgress();
        }

        return vanillaTickDelta;
    }


    /**
     * Returns the movement multiplier based on the continuous per-frame render progress
     * instead of the server-synced tick progress, giving smooth sub-tick interpolation.
     */
    private static float getMovementMultiplierFromRenderProgress() {
        return StasisTimings.getMovementScale(phase, renderProgressFrame);
    }


    /**
     * Advances the virtual age for an ItemEntity during transitions.
     * Called from the tick RETURN inject after the tick has run.
     * Returns the new virtual age so the caller can set itemAge = floor(virtualAge).
     */
    public static float advanceItemVirtualAge(Entity entity, int preTickAge) {
        UUID uuid = entity.getUuid();
        float movementScale = StasisTimings.clamp01(getMovementMultiplier(entity));
        float current = itemVirtualAges.computeIfAbsent(uuid, k -> (float) preTickAge);
        current += movementScale;
        itemVirtualAges.put(uuid, current);
        return current;
    }


    /**
     * Returns the render tickDelta for ItemEntity during transitions.
     * Uses the fractional part of the virtual age plus a smooth per-frame offset
     * so the bobbing animation maintains full frame rate but visually slows.
     */
    private static float getItemEntityRenderTickDelta(Entity entity, float vanillaTickDelta) {
        Float virtualAge = itemVirtualAges.get(entity.getUuid());
        if (virtualAge == null) {
            return vanillaTickDelta * getMovementMultiplierFromRenderProgress();
        }
        float frac = virtualAge - (float) Math.floor(virtualAge);
        float movementScale = getMovementMultiplierFromRenderProgress();
        return frac + movementScale * vanillaTickDelta;
    }

    private static Map<UUID, ClientFrozenEntityState> preserveNonLivingFrozenStates() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || frozenEntityStates.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ClientFrozenEntityState> preservedStates = new HashMap<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == null || entity.isRemoved() || entity instanceof LivingEntity || isPrivilegedEntity(entity)) {
                continue;
            }

            ClientFrozenEntityState state = frozenEntityStates.get(entity.getUuid());
            if (state != null) {
                preservedStates.put(entity.getUuid(), state);
            }
        }
        return preservedStates;
    }

    private static void syncActivePreviousRenderStateForNonLiving() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity == null || entity.isRemoved() || entity instanceof LivingEntity || isPrivilegedEntity(entity)) {
                continue;
            }
            syncNonLivingTransitionRenderState(entity, entity.getPos(), entity.getYaw(), entity.getPitch());
        }
    }

    private static void seedTransitionOutLivingStatesFromActivePose(Map<UUID, Float> releaseStartLivingRenderDeltas) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity) || entity.isRemoved() || isPrivilegedEntity(entity)) {
                continue;
            }
            float heldDelta = StasisTimings.clamp01(releaseStartLivingRenderDeltas.getOrDefault(entity.getUuid(), 0.0f));
            ClientFrozenEntityState state = new ClientFrozenEntityState();
            state.volatileProgressBudget = heldDelta;
            state.renderProgressStart = heldDelta;
            state.renderProgressEnd = heldDelta;
            state.advanceVolatileProgressThisTick = false;
            state.forceAdvanceVolatileProgress = false;
            frozenEntityStates.put(entity.getUuid(), state);
        }
    }


    private static boolean hasMeaningfulVelocity(Vec3d velocity) {
        return velocity.lengthSquared() > 1.0E-6;
    }

    private static void syncNonLivingTransitionRenderState(Entity entity, Vec3d previousPosition, float previousYaw, float previousPitch) {
        entity.prevX = previousPosition.x;
        entity.prevY = previousPosition.y;
        entity.prevZ = previousPosition.z;
        entity.lastRenderX = previousPosition.x;
        entity.lastRenderY = previousPosition.y;
        entity.lastRenderZ = previousPosition.z;
        entity.prevYaw = previousYaw;
        entity.prevPitch = previousPitch;
    }


    private static float lerp(float from, float to, float delta) {
        float clampedDelta = Math.max(0.0f, Math.min(1.0f, delta));
        return from + (to - from) * clampedDelta;
    }


    private static Vec3d lerpVec(Vec3d from, Vec3d to, float delta) {
        float clampedDelta = Math.max(0.0f, Math.min(1.0f, delta));
        return new Vec3d(
                lerp((float) from.x, (float) to.x, clampedDelta),
                lerp((float) from.y, (float) to.y, clampedDelta),
                lerp((float) from.z, (float) to.z, clampedDelta)
        );
    }


    private static float lerpAngle(float from, float to, float delta) {
        float clampedDelta = Math.max(0.0f, Math.min(1.0f, delta));
        return from + MathHelper.wrapDegrees(to - from) * clampedDelta;
    }

    private static void stasis$logVolatileDecision(
            String side,
            Entity entity,
            ClientFrozenEntityState state,
            float movementScale,
            boolean advance,
            boolean activeHold
    ) {
    }

    private static boolean stasis$samePosition(Vec3d first, Vec3d second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.squaredDistanceTo(second) <= 1.0E-8;
    }

    private static final class ClientFrozenEntityState {
        private Vec3d velocity = Vec3d.ZERO;
        private Vec3d resumeVelocity = Vec3d.ZERO;
        private boolean allowVelocityRefresh = true;
        private boolean initialized = false;
        private float volatileProgressBudget = 0.0f;
        private float renderProgressStart = 0.0f;
        private float renderProgressEnd = 0.0f;
        private long lastVolatileProgressTick = Long.MIN_VALUE;
        private boolean advanceVolatileProgressThisTick = true;
        private boolean forceAdvanceVolatileProgress = true;
        private Vec3d debugLastSamplePrevPos = null;
        private Vec3d debugLastSampleCurrentPos = null;
        private float debugLastSampleSpatialDelta = 0.0f;
        private long debugLastSampleWorldTick = Long.MIN_VALUE;
    }


    private static final class ClientTransitionTickState {
        private final Vec3d position;
        private final Vec3d velocity;
        private final float yaw;
        private final float pitch;
        private final float bodyYaw;
        private final float headYaw;
        private final float limbPrevSpeed;
        private final float limbSpeed;
        private final float limbPos;
        private final float fallDistance;
        private final boolean onGround;
        private final boolean horizontalCollision;
        private final boolean verticalCollision;
        private final boolean groundCollision;
        private final boolean velocityModified;


        private ClientTransitionTickState(
                Vec3d position, Vec3d velocity, float yaw, float pitch,
                float bodyYaw, float headYaw, float limbPrevSpeed, float limbSpeed, float limbPos,
                float fallDistance, boolean onGround,
                boolean horizontalCollision, boolean verticalCollision,
                boolean groundCollision, boolean velocityModified
        ) {
            this.position = position;
            this.velocity = velocity;
            this.yaw = yaw;
            this.pitch = pitch;
            this.bodyYaw = bodyYaw;
            this.headYaw = headYaw;
            this.limbPrevSpeed = limbPrevSpeed;
            this.limbSpeed = limbSpeed;
            this.limbPos = limbPos;
            this.fallDistance = fallDistance;
            this.onGround = onGround;
            this.horizontalCollision = horizontalCollision;
            this.verticalCollision = verticalCollision;
            this.groundCollision = groundCollision;
            this.velocityModified = velocityModified;
        }


        private static ClientTransitionTickState capture(Entity entity) {
            float bodyYaw = entity instanceof LivingEntity livingEntity ? livingEntity.getBodyYaw() : entity.getYaw();
            float headYaw = entity instanceof LivingEntity livingEntity ? livingEntity.getHeadYaw() : entity.getYaw();
            float limbPrevSpeed = 0.0f;
            float limbSpeed = 0.0f;
            float limbPos = 0.0f;
            if (entity instanceof LivingEntity livingEntity) {
                LimbAnimatorAccessor limbAnimator = (LimbAnimatorAccessor) livingEntity.limbAnimator;
                limbPrevSpeed = limbAnimator.stasis$getPrevSpeed();
                limbSpeed = limbAnimator.stasis$getSpeed();
                limbPos = limbAnimator.stasis$getPos();
            }
            return new ClientTransitionTickState(
                    entity.getPos(), entity.getVelocity(), entity.getYaw(), entity.getPitch(),
                    bodyYaw, headYaw, limbPrevSpeed, limbSpeed, limbPos, entity.fallDistance, entity.isOnGround(),
                    entity.horizontalCollision, entity.verticalCollision,
                    entity.groundCollision, entity.velocityModified
            );
        }
    }
}
