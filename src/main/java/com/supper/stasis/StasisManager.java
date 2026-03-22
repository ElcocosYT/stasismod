package com.supper.stasis;

import com.supper.stasis.network.StasisSyncPayload;
import com.supper.stasis.mixin.EntityAccessor;
import com.supper.stasis.mixin.LivingEntityAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

public class StasisManager {
    private static StasisManager instance;

    private StasisPhase phase = StasisPhase.IDLE;
    private UUID activatingPlayerUUID = null;
    private int transitionTicksRemaining = 0;
    private int activeTicksRemaining = 0;
    private int warningTicks = 0;
    private final List<QueuedHit> queuedHits = new ArrayList<>();
    private final List<QueuedNonEntityDamage> queuedNonEntityDamage = new ArrayList<>();
    private final List<QueuedActivatingPlayerDamage> queuedActivatingPlayerDamage = new ArrayList<>();
    private final Map<UUID, FrozenEntityState> frozenEntityStates = new HashMap<>();
    private final Map<UUID, TransitionTickState> transitionTickStates = new HashMap<>();
    private final List<QueuedRedstoneAction> queuedRedstoneActions = new ArrayList<>();
    private boolean applyingQueuedDamage = false;
    private int redstoneActionIndex = 0;

    public static synchronized StasisManager getInstance() {
        if (instance == null) {
            instance = new StasisManager();
        }
        return instance;
    }

    public synchronized boolean activate(ServerWorld world, UUID playerUUID, int activeTicks, int warningTicks) {
        if (phase.isRunning()) {
            return false;
        }
        if (hasPendingRedstoneReplay()) {
            queuedRedstoneActions.clear();
            redstoneActionIndex = 0;
        }
        phase = StasisPhase.TRANSITION_IN;
        activatingPlayerUUID = playerUUID;
        transitionTicksRemaining = StasisTimings.getTransitionInTicks();
        activeTicksRemaining = Math.max(1, activeTicks);
        this.warningTicks = Math.max(0, warningTicks);
        queuedHits.clear();
        queuedNonEntityDamage.clear();
        frozenEntityStates.clear();
        transitionTickStates.clear();
        queuedRedstoneActions.clear();
        applyingQueuedDamage = false;
        redstoneActionIndex = 0;
        captureInitialMomentum(world.getServer());
        syncToClients(world.getServer());
        return true;
    }

    public synchronized void tick(MinecraftServer server) {
        if (!phase.isRunning()) {
            drainOneQueuedActivatingPlayerDamage(server);
            drainOneQueuedRedstoneAction(server);
            return;
        }

        switch (phase) {
            case TRANSITION_IN -> {
                if (transitionTicksRemaining > 0) {
                    transitionTicksRemaining--;
                }
                if (transitionTicksRemaining <= 0) {
                    phase = StasisPhase.ACTIVE;
                }
            }
            case ACTIVE -> {
                if (activeTicksRemaining > 0) {
                    activeTicksRemaining--;
                }
                if (activeTicksRemaining <= 0) {
                    beginTransitionOut(server);
                }
            }
            case TRANSITION_OUT -> {
                drainOneQueuedActivatingPlayerDamage(server);
                drainOneQueuedRedstoneAction(server);
                if (transitionTicksRemaining > 0) {
                    transitionTicksRemaining--;
                }
                if (transitionTicksRemaining <= 0) {
                    finishStasis(server);
                    return;
                }
            }
            case IDLE -> {
                return;
            }
        }

        syncToClients(server);
    }

    private void beginTransitionOut(MinecraftServer server) {
        phase = StasisPhase.TRANSITION_OUT;
        transitionTicksRemaining = StasisTimings.getTransitionOutTicks();
        resetVolatileProgressBudgets();
        applyQueuedHits(server);
        redstoneActionIndex = 0;
    }

    private void applyQueuedHits(MinecraftServer server) {
        if (!queuedHits.isEmpty()) {
            applyingQueuedDamage = true;
            try {
                for (QueuedHit queuedHit : new ArrayList<>(queuedHits)) {
                    ServerWorld world = server.getWorld(queuedHit.worldKey);
                    if (world == null) {
                        continue;
                    }
                    Entity entity = world.getEntity(queuedHit.targetUUID);
                    if (entity instanceof LivingEntity livingEntity) {
                        resetDamageCooldown(livingEntity);
                        livingEntity.damage(queuedHit.source, queuedHit.amount);
                    }
                }
            } finally {
                queuedHits.clear();
                applyingQueuedDamage = false;
            }
        }

        if (!queuedNonEntityDamage.isEmpty()) {
            applyingQueuedDamage = true;
            try {
                for (QueuedNonEntityDamage queuedDamage : new ArrayList<>(queuedNonEntityDamage)) {
                    ServerWorld world = server.getWorld(queuedDamage.worldKey);
                    if (world == null) {
                        continue;
                    }
                    Entity entity = world.getEntity(queuedDamage.targetUUID);
                    if (entity instanceof LivingEntity livingEntity) {
                        resetDamageCooldown(livingEntity);
                        livingEntity.damage(queuedDamage.source, queuedDamage.amount);
                    }
                }
            } finally {
                queuedNonEntityDamage.clear();
                applyingQueuedDamage = false;
            }
        }
    }

    public synchronized void deactivate(MinecraftServer server) {
        resetState();
        syncToClients(server);
    }

    public synchronized boolean emergencyBreak(MinecraftServer server) {
        if (!phase.isRunning()) {
            return false;
        }
        ServerWorld soundWorld = getSoundWorld(server);
        ServerPlayerEntity activatingPlayer = getActivatingPlayer(server);
        if (soundWorld != null) {
            double x;
            double y;
            double z;
            if (activatingPlayer != null) {
                x = activatingPlayer.getX();
                y = activatingPlayer.getY();
                z = activatingPlayer.getZ();
            } else {
                BlockPos spawnPos = soundWorld.getSpawnPos();
                x = spawnPos.getX() + 0.5;
                y = spawnPos.getY() + 0.5;
                z = spawnPos.getZ() + 0.5;
            }
            soundWorld.playSound(null, x, y, z, Stasis.TIMESTOP_BREAK_SOUND, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        deactivate(server);
        return true;
    }

    private void finishStasis(MinecraftServer server) {
        phase = StasisPhase.IDLE;
        activatingPlayerUUID = null;
        transitionTicksRemaining = 0;
        activeTicksRemaining = 0;
        warningTicks = 0;
        queuedHits.clear();
        queuedNonEntityDamage.clear();
        frozenEntityStates.clear();
        transitionTickStates.clear();
        applyingQueuedDamage = false;
        syncToClients(server);
    }

    private void resetState() {
        phase = StasisPhase.IDLE;
        activatingPlayerUUID = null;
        transitionTicksRemaining = 0;
        activeTicksRemaining = 0;
        warningTicks = 0;
        queuedHits.clear();
        queuedNonEntityDamage.clear();
        frozenEntityStates.clear();
        transitionTickStates.clear();
        queuedRedstoneActions.clear();
        applyingQueuedDamage = false;
        redstoneActionIndex = 0;
    }

    public synchronized void queueHit(ServerWorld world, LivingEntity target, DamageSource source, float amount) {
        if (amount > 0.0f && shouldFreezeWorld(world) && !isPrivilegedEntity(target)) {
            queuedHits.add(new QueuedHit(world.getRegistryKey(), target.getUuid(), source, amount));
        }
    }

    public synchronized void queueNonEntityDamage(ServerWorld world, LivingEntity target, DamageSource source, float amount) {
        if (amount > 0.0f && shouldFreezeWorld(world)) {
            queuedNonEntityDamage.add(new QueuedNonEntityDamage(world.getRegistryKey(), target.getUuid(), source, amount));
        }
    }

    public synchronized void queueActivatingPlayerDamage(ServerWorld world, LivingEntity target, DamageSource source, float amount) {
        if (amount > 0.0f && shouldFreezeWorld(world) && isActivatingPlayer(target) && !applyingQueuedDamage) {
            queuedActivatingPlayerDamage.add(new QueuedActivatingPlayerDamage(world.getRegistryKey(), target.getUuid(), source, amount));
        }
    }

    public synchronized boolean isEntityDamage(DamageSource source) {
        return source.getAttacker() != null || source.getSource() != null;
    }

    public synchronized boolean shouldIgnoreDamageForActivatingPlayer(LivingEntity entity, DamageSource source) {
        return phase == StasisPhase.ACTIVE && isActivatingPlayer(entity) && isEntityDamage(source);
    }

    public synchronized boolean shouldQueueActivatingPlayerDamage(LivingEntity entity, DamageSource source) {
        return phase == StasisPhase.ACTIVE
                && isActivatingPlayer(entity)
                && !isEntityDamage(source)
                && !applyingQueuedDamage;
    }

    public synchronized void captureEntityMomentum(Entity entity) {
        if (!shouldTrackMomentum(entity) || entity.isRemoved()) {
            return;
        }
        FrozenEntityState existingState = getOrCreateFrozenEntityState(entity, false);
        if (existingState.allowVelocityRefresh && hasMeaningfulVelocity(entity.getVelocity())) {
            existingState.velocity = entity.getVelocity();
            existingState.resumeVelocity = entity.getVelocity();
            existingState.allowVelocityRefresh = false;
        }
    }

    public synchronized void captureFrozenEntityState(Entity entity) {
        captureEntityMomentum(entity);
    }

    public synchronized void captureTransitionTickState(Entity entity) {
        if (!isTransitioning(entity.getWorld()) || isPrivilegedEntity(entity) || entity.isRemoved()) {
            transitionTickStates.remove(entity.getUuid());
            return;
        }
        transitionTickStates.put(entity.getUuid(), TransitionTickState.capture(entity));
    }

    public synchronized void prepareTransitionTick(Entity entity) {
        if (!isTransitioningOut(entity.getWorld()) || isPrivilegedEntity(entity)
                || entity instanceof LivingEntity || entity.isRemoved()) {
            return;
        }
        FrozenEntityState state = frozenEntityStates.get(entity.getUuid());
        if (state == null) {
            return;
        }
        if (!hasMeaningfulVelocity(entity.getVelocity())) {
            entity.setVelocity(state.resumeVelocity);
        }
    }

    public synchronized void captureResumeVelocity(Entity entity) {
        if (!isTransitioning(entity.getWorld()) || !shouldTrackMomentum(entity)
                || entity.isRemoved() || entity instanceof LivingEntity) {
            return;
        }
        FrozenEntityState state = frozenEntityStates.get(entity.getUuid());
        if (state == null) {
            return;
        }
        Vec3d currentVelocity = entity.getVelocity();
        state.resumeVelocity = currentVelocity;
    }

    public synchronized void onEntitySpawned(Entity entity) {
        if (!phase.isRunning() || entity.isRemoved()) {
            return;
        }
        getOrCreateFrozenEntityState(entity, true);
    }

    public synchronized boolean queueRedstoneInteraction(ServerPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        if (!shouldFreezeWorld(player.getWorld())) {
            return false;
        }
        BlockState state = player.getWorld().getBlockState(hitResult.getBlockPos());
        if (!shouldQueueRedstoneBlock(state)) {
            return false;
        }
        ItemStack queuedStack = player.getStackInHand(hand).copy();
        int queuedSlot = getQueuedHandSlot(player, hand);
        queuedRedstoneActions.add(QueuedRedstoneAction.blockUse(
                player.getWorld().getRegistryKey(),
                player.getUuid(),
                hand,
                new BlockHitResult(hitResult.getPos(), hitResult.getSide(), hitResult.getBlockPos(), hitResult.isInsideBlock()),
                queuedSlot,
                queuedStack
        ));
        return true;
    }

    public synchronized void debugFrozenBlockInteractionPassthrough(ServerPlayerEntity player, BlockHitResult hitResult) {
    }

    public synchronized void queuePressurePlateCollision(ServerWorld world, BlockPos pos, Entity entity) {
        if (!shouldFreezeWorld(world) || !isPrivilegedEntity(entity)) {
            return;
        }
        queuedRedstoneActions.add(QueuedRedstoneAction.pressureCollision(
                world.getRegistryKey(),
                entity.getUuid(),
                pos.toImmutable()
        ));
    }

    public synchronized void syncToClients(MinecraftServer server) {
        StasisSyncPayload payload = createPayload();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public synchronized void syncToClient(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, createPayload());
    }

    private StasisSyncPayload createPayload() {
        return new StasisSyncPayload(phase, getProgress(), activatingPlayerUUID, activeTicksRemaining, warningTicks);
    }

    public synchronized StasisPhase getPhase() {
        return phase;
    }

    public synchronized float getProgress() {
        return switch (phase) {
            case IDLE -> 0.0f;
            case TRANSITION_IN -> 1.0f - (transitionTicksRemaining / (float) StasisTimings.getTransitionInTicks());
            case ACTIVE -> 1.0f;
            case TRANSITION_OUT -> transitionTicksRemaining / (float) StasisTimings.getTransitionOutTicks();
        };
    }

    public synchronized UUID getActivatingPlayerUUID() {
        return activatingPlayerUUID;
    }

    public synchronized boolean shouldFreezeWorld(World world) {
        return phase == StasisPhase.ACTIVE && isServerWorld(world);
    }

    public synchronized boolean isTransitioning(World world) {
        return phase.isTransition() && isServerWorld(world);
    }

    public synchronized boolean isTransitioningIn(World world) {
        return phase == StasisPhase.TRANSITION_IN && isServerWorld(world);
    }

    public synchronized boolean isTransitioningOut(World world) {
        return phase == StasisPhase.TRANSITION_OUT && isServerWorld(world);
    }

    public synchronized boolean isRestrictedPlayer(ServerPlayerEntity player) {
        return shouldFreezeWorld(player.getWorld()) && !isPrivilegedEntity(player);
    }

    public synchronized boolean shouldFreezeEntity(Entity entity) {
        return shouldFreezeWorld(entity.getWorld()) && !isPrivilegedEntity(entity);
    }

    public synchronized boolean shouldFreezeVolatileProgress(Entity entity) {
        return entity != null
                && isServerWorld(entity.getWorld())
                && phase != StasisPhase.IDLE
                && !isPrivilegedEntity(entity);
    }

    public synchronized boolean shouldAdvanceVolatileProgress(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return true;
        }
        if (!shouldFreezeVolatileProgress(entity)) {
            return true;
        }
        FrozenEntityState state = getOrCreateFrozenEntityState(entity, false);
        long worldTick = entity.getWorld().getTime();
        if (state.lastVolatileProgressTick == worldTick) {
            return state.advanceVolatileProgressThisTick;
        }
        state.lastVolatileProgressTick = worldTick;
        float movementScale = StasisTimings.clamp01(getMovementMultiplier(entity.getWorld()));
        if (movementScale >= 0.9999f) {
            state.advanceVolatileProgressThisTick = true;
            state.forceAdvanceVolatileProgress = false;
            stasis$logVolatileDecision(entity, state, movementScale, true);
            return true;
        }
        if (state.forceAdvanceVolatileProgress) {
            state.forceAdvanceVolatileProgress = false;
            state.advanceVolatileProgressThisTick = true;
            stasis$logVolatileDecision(entity, state, movementScale, true);
            return true;
        }
        state.volatileProgressBudget += movementScale;
        if (state.volatileProgressBudget >= 1.0f) {
            state.volatileProgressBudget -= 1.0f;
            state.advanceVolatileProgressThisTick = true;
        } else {
            state.advanceVolatileProgressThisTick = false;
        }
        stasis$logVolatileDecision(entity, state, movementScale, state.advanceVolatileProgressThisTick);
        return state.advanceVolatileProgressThisTick;
    }

    public synchronized boolean shouldQueueDamage(LivingEntity entity) {
        return shouldFreezeWorld(entity.getWorld()) && !isPrivilegedEntity(entity) && !applyingQueuedDamage;
    }

    public synchronized boolean shouldBypassDamageCooldown(LivingEntity entity) {
        return isTransitioningOut(entity.getWorld()) && !isPrivilegedEntity(entity);
    }

    public synchronized boolean isActivatingEntity(Entity entity) {
        return activatingPlayerUUID != null && activatingPlayerUUID.equals(entity.getUuid());
    }

    public synchronized boolean isActivatingPlayer(LivingEntity entity) {
        return entity instanceof ServerPlayerEntity && isActivatingEntity(entity);
    }

    public synchronized boolean isPrivilegedEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (isActivatingEntity(entity)) {
            return true;
        }
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        ServerPlayerEntity activatingPlayer = getActivatingPlayer(serverWorld.getServer());
        if (activatingPlayer == null) {
            return false;
        }
        if (entity == activatingPlayer) {
            return true;
        }
        Entity vehicle = activatingPlayer.getVehicle();
        if (vehicle != null && entity == vehicle) {
            return true;
        }
        Entity rootVehicle = activatingPlayer.getRootVehicle();
        if (rootVehicle != null && rootVehicle != activatingPlayer && entity == rootVehicle) {
            return true;
        }
        return false;
    }

    private synchronized boolean shouldTrackMomentum(Entity entity) {
        return phase.isRunning()
                && isServerWorld(entity.getWorld())
                && !isPrivilegedEntity(entity);
    }

    public synchronized float getMovementMultiplier(World world) {
        if (!isServerWorld(world)) {
            return 1.0f;
        }
        return StasisTimings.getMovementScale(phase, getProgress());
    }

    public synchronized void applyTransitionVelocity(Entity entity) {
        if (!isTransitioning(entity.getWorld()) || isPrivilegedEntity(entity)) {
            return;
        }
        FrozenEntityState state = frozenEntityStates.get(entity.getUuid());
        if (state == null) {
            return;
        }
        float targetScale = StasisTimings.clamp01(getMovementMultiplier(entity.getWorld()));
        if (targetScale <= 0.0001f) {
            entity.setVelocity(Vec3d.ZERO);
            state.lastAppliedMovementScale = 0.0f;
            return;
        }
        if (state.lastAppliedMovementScale <= 0.0001f) {
            boolean useStoredResumeVelocity = !(entity instanceof LivingEntity);
            Vec3d baseVelocity = useStoredResumeVelocity && hasMeaningfulVelocity(state.resumeVelocity)
                    ? state.resumeVelocity
                    : entity.getVelocity();
            entity.setVelocity(baseVelocity.multiply(targetScale));
        } else {
            float scaleRatio = targetScale / state.lastAppliedMovementScale;
            entity.setVelocity(entity.getVelocity().multiply(scaleRatio));
        }
        state.lastAppliedMovementScale = targetScale;
    }

    public synchronized void applyTransitionTickState(Entity entity) {
        TransitionTickState state = transitionTickStates.remove(entity.getUuid());
        if (state == null || !isTransitioning(entity.getWorld()) || isPrivilegedEntity(entity)
                || entity.isRemoved()) {
            return;
        }
        float transitionScale = StasisTimings.clamp01(getMovementMultiplier(entity.getWorld()));
        if (entity instanceof LivingEntity livingEntity) {
            Vec3d preTickPosition = state.position;
            Vec3d postTickPosition = entity.getPos();
            Vec3d tickMovementDelta = postTickPosition.subtract(preTickPosition);
            entity.setPosition(preTickPosition.add(tickMovementDelta.multiply(transitionScale)));
            entity.setVelocity(state.velocity.add(entity.getVelocity().subtract(state.velocity).multiply(transitionScale)));
            setEntityRotation(entity, lerpAngle(state.yaw, entity.getYaw(), transitionScale),
                    lerpAngle(state.pitch, entity.getPitch(), transitionScale));
            entity.fallDistance = lerp(state.fallDistance, entity.fallDistance, transitionScale);
            setLivingRotation(livingEntity,
                    lerpAngle(state.bodyYaw, livingEntity.getBodyYaw(), transitionScale),
                    lerpAngle(state.headYaw, livingEntity.getHeadYaw(), transitionScale));
            return;
        }

        if (transitionScale <= 0.0001f) {
            Vec3d frozenVelocity = hasMeaningfulVelocity(entity.getVelocity()) ? entity.getVelocity() : state.velocity;
            entity.setPosition(state.position);
            entity.setVelocity(entity instanceof ProjectileEntity ? frozenVelocity : Vec3d.ZERO);
            setEntityRotation(entity, state.yaw, state.pitch);
            entity.fallDistance = state.fallDistance;
            entity.setOnGround(state.onGround);
            entity.horizontalCollision = state.horizontalCollision;
            entity.verticalCollision = state.verticalCollision;
            entity.groundCollision = state.groundCollision;
            entity.velocityModified = state.velocityModified;
        } else {
            Vec3d scaledPosition = state.position.add(entity.getPos().subtract(state.position).multiply(transitionScale));
            entity.setPosition(scaledPosition);
            entity.setVelocity(lerpVec(state.velocity, entity.getVelocity(), transitionScale));
            setEntityRotation(entity, lerpAngle(state.yaw, entity.getYaw(), transitionScale),
                    lerpAngle(state.pitch, entity.getPitch(), transitionScale));
            entity.fallDistance = lerp(state.fallDistance, entity.fallDistance, transitionScale);
            entity.setOnGround(state.onGround || entity.isOnGround());
            entity.horizontalCollision = state.horizontalCollision || entity.horizontalCollision;
            entity.verticalCollision = state.verticalCollision || entity.verticalCollision;
            entity.groundCollision = state.groundCollision || entity.groundCollision;
            entity.velocityModified = state.velocityModified || entity.velocityModified;
        }
    }

    private void captureInitialMomentum(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity != null && !entity.isRemoved()) {
                    captureEntityMomentum(entity);
                }
            }
        }
    }

    private void resetVolatileProgressBudgets() {
        for (FrozenEntityState state : frozenEntityStates.values()) {
            state.volatileProgressBudget = 0.0f;
            state.lastVolatileProgressTick = Long.MIN_VALUE;
            state.advanceVolatileProgressThisTick = false;
            state.forceAdvanceVolatileProgress = false;
        }
    }

    private void drainOneQueuedRedstoneAction(MinecraftServer server) {
        if (hasPendingRedstoneReplay()) {
            replayNextRedstoneAction(server);
            clearQueuedRedstoneActionsIfDrained();
        }
    }

    private void drainOneQueuedActivatingPlayerDamage(MinecraftServer server) {
        if (queuedActivatingPlayerDamage.isEmpty()) {
            return;
        }

        QueuedActivatingPlayerDamage queuedDamage = queuedActivatingPlayerDamage.removeFirst();
        ServerWorld world = server.getWorld(queuedDamage.worldKey);
        if (world == null) {
            return;
        }
        Entity entity = world.getEntity(queuedDamage.targetUUID);
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        applyingQueuedDamage = true;
        try {
            resetDamageCooldown(livingEntity);
            livingEntity.damage(queuedDamage.source, queuedDamage.amount);
        } finally {
            applyingQueuedDamage = false;
        }
    }

    private void stasis$logVolatileDecision(Entity entity, FrozenEntityState state, float movementScale, boolean advance) {
    }

    private FrozenEntityState getOrCreateFrozenEntityState(Entity entity, boolean allowVelocityRefresh) {
        FrozenEntityState existingState = frozenEntityStates.get(entity.getUuid());
        if (existingState != null) {
            if (allowVelocityRefresh && existingState.allowVelocityRefresh != allowVelocityRefresh) {
                existingState.allowVelocityRefresh = true;
            }
            return existingState;
        }
        Vec3d velocity = entity.getVelocity();
        FrozenEntityState newState = new FrozenEntityState(
                entity.getWorld().getRegistryKey(),
                velocity,
                velocity,
                allowVelocityRefresh && !hasMeaningfulVelocity(velocity),
                getMovementMultiplier(entity.getWorld())
        );
        frozenEntityStates.put(entity.getUuid(), newState);
        return newState;
    }

    public synchronized void resetDamageCooldown(LivingEntity livingEntity) {
        if (livingEntity instanceof EntityAccessor entityAccessor) {
            entityAccessor.stasis$setTimeUntilRegen(0);
        }
        if (livingEntity instanceof LivingEntityAccessor accessor) {
            accessor.stasis$setHurtTime(0);
            accessor.stasis$setMaxHurtTime(0);
        }
    }

    private synchronized void replayNextRedstoneAction(MinecraftServer server) {
        if (redstoneActionIndex >= queuedRedstoneActions.size()) {
            return;
        }
        QueuedRedstoneAction action = queuedRedstoneActions.get(redstoneActionIndex);
        redstoneActionIndex++;
        ServerWorld world = server.getWorld(action.worldKey);
        if (world == null) {
            return;
        }
        switch (action.type) {
            case BLOCK_USE -> replayQueuedBlockUse(server, world, action);
            case PRESSURE_COLLISION -> replayQueuedPressureCollision(world, action);
        }
    }

    private void replayQueuedBlockUse(MinecraftServer server, ServerWorld world, QueuedRedstoneAction action) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(action.actorUuid);
        if (player == null) {
            return;
        }
        BlockState state = world.getBlockState(action.hitResult.getBlockPos());
        int handSlot = getQueuedHandSlot(player, action.hand);
        int sourceSlot = stasis$resolveQueuedItemSlot(player, action);
        if (!action.queuedStack.isEmpty() && sourceSlot < 0) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean swappedSlots = sourceSlot >= 0 && sourceSlot != handSlot;
        if (swappedSlots) {
            ItemStack handStack = inventory.getStack(handSlot).copy();
            ItemStack sourceStack = inventory.getStack(sourceSlot).copy();
            inventory.setStack(handSlot, sourceStack);
            inventory.setStack(sourceSlot, handStack);
        }
        ActionResult result = player.interactionManager.interactBlock(
                player,
                world,
                player.getStackInHand(action.hand),
                action.hand,
                action.hitResult
        );
        if (swappedSlots) {
            ItemStack mutatedHandStack = inventory.getStack(handSlot).copy();
            ItemStack displacedStack = inventory.getStack(sourceSlot).copy();
            inventory.setStack(sourceSlot, mutatedHandStack);
            inventory.setStack(handSlot, displacedStack);
        }
        inventory.markDirty();
        player.playerScreenHandler.syncState();
    }

    private void replayQueuedPressureCollision(ServerWorld world, QueuedRedstoneAction action) {
        Entity entity = world.getEntity(action.actorUuid);
        if (entity == null || entity.isRemoved()) {
            return;
        }
        BlockState state = world.getBlockState(action.pos);
        state.onEntityCollision(world, action.pos, entity);
    }

    private boolean shouldQueueRedstoneBlock(BlockState state) {
        return state.emitsRedstonePower()
                || state.getBlock() instanceof JukeboxBlock
                || state.getBlock() instanceof AbstractPressurePlateBlock;
    }

    private int getQueuedHandSlot(ServerPlayerEntity player, Hand hand) {
        return hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : PlayerInventory.OFF_HAND_SLOT;
    }

    private int stasis$resolveQueuedItemSlot(ServerPlayerEntity player, QueuedRedstoneAction action) {
        if (action.queuedStack.isEmpty()) {
            return getQueuedHandSlot(player, action.hand);
        }
        PlayerInventory inventory = player.getInventory();
        if (action.inventorySlot >= 0 && stasis$stackMatches(inventory.getStack(action.inventorySlot), action.queuedStack)) {
            return action.inventorySlot;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (stasis$stackMatches(inventory.getStack(slot), action.queuedStack)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean stasis$stackMatches(ItemStack actualStack, ItemStack queuedStack) {
        return !actualStack.isEmpty()
                && ItemStack.areItemsAndComponentsEqual(actualStack, queuedStack)
                && actualStack.getCount() >= queuedStack.getCount();
    }

    private String stasis$describeItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return stack.getItem() + "x" + stack.getCount();
    }

    private boolean isServerWorld(World world) {
        return world instanceof ServerWorld;
    }

    private boolean hasMeaningfulVelocity(Vec3d velocity) {
        return velocity.lengthSquared() > 1.0E-6;
    }

    private boolean hasPendingRedstoneReplay() {
        return redstoneActionIndex < queuedRedstoneActions.size();
    }

    private void clearQueuedRedstoneActionsIfDrained() {
        if (!hasPendingRedstoneReplay()) {
            queuedRedstoneActions.clear();
            redstoneActionIndex = 0;
        }
    }

    private void setEntityRotation(Entity entity, float yaw, float pitch) {
        entity.setYaw(yaw);
        entity.prevYaw = yaw;
        entity.setPitch(pitch);
        entity.prevPitch = pitch;
    }

    private void setLivingRotation(LivingEntity livingEntity, float bodyYaw, float headYaw) {
        livingEntity.setBodyYaw(bodyYaw);
        livingEntity.prevBodyYaw = bodyYaw;
        livingEntity.setHeadYaw(headYaw);
        livingEntity.prevHeadYaw = headYaw;
    }

    private float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }

    private Vec3d lerpVec(Vec3d from, Vec3d to, float delta) {
        float clampedDelta = StasisTimings.clamp01(delta);
        return from.add(to.subtract(from).multiply(clampedDelta));
    }

    private float lerpAngle(float from, float to, float delta) {
        float clampedDelta = StasisTimings.clamp01(delta);
        return from + MathHelper.wrapDegrees(to - from) * clampedDelta;
    }

    private ServerWorld getSoundWorld(MinecraftServer server) {
        ServerPlayerEntity activatingPlayer = getActivatingPlayer(server);
        if (activatingPlayer != null) {
            return activatingPlayer.getServerWorld();
        }
        List<ServerWorld> worlds = new ArrayList<>();
        server.getWorlds().forEach(worlds::add);
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    public synchronized ServerPlayerEntity getActivatingPlayer(MinecraftServer server) {
        return activatingPlayerUUID == null ? null : server.getPlayerManager().getPlayer(activatingPlayerUUID);
    }

    private static final class QueuedHit {
        private final RegistryKey<World> worldKey;
        private final UUID targetUUID;
        private final DamageSource source;
        private final float amount;

        private QueuedHit(RegistryKey<World> worldKey, UUID targetUUID, DamageSource source, float amount) {
            this.worldKey = worldKey;
            this.targetUUID = targetUUID;
            this.source = source;
            this.amount = amount;
        }
    }

    private static final class QueuedNonEntityDamage {
        private final RegistryKey<World> worldKey;
        private final UUID targetUUID;
        private final DamageSource source;
        private final float amount;

        private QueuedNonEntityDamage(RegistryKey<World> worldKey, UUID targetUUID, DamageSource source, float amount) {
            this.worldKey = worldKey;
            this.targetUUID = targetUUID;
            this.source = source;
            this.amount = amount;
        }
    }

    private static final class FrozenEntityState {
        private final RegistryKey<World> worldKey;
        private Vec3d velocity;
        private Vec3d resumeVelocity;
        private boolean allowVelocityRefresh;
        private float lastAppliedMovementScale;
        private float volatileProgressBudget;
        private long lastVolatileProgressTick;
        private boolean advanceVolatileProgressThisTick;
        private boolean forceAdvanceVolatileProgress;

        private FrozenEntityState(
                RegistryKey<World> worldKey,
                Vec3d velocity,
                Vec3d resumeVelocity,
                boolean allowVelocityRefresh,
                float lastAppliedMovementScale
        ) {
            this.worldKey = worldKey;
            this.velocity = velocity;
            this.resumeVelocity = resumeVelocity;
            this.allowVelocityRefresh = allowVelocityRefresh;
            this.lastAppliedMovementScale = lastAppliedMovementScale;
            this.volatileProgressBudget = 0.0f;
            this.lastVolatileProgressTick = Long.MIN_VALUE;
            this.advanceVolatileProgressThisTick = true;
            this.forceAdvanceVolatileProgress = true;
        }
    }

    private static final class QueuedActivatingPlayerDamage {
        private final RegistryKey<World> worldKey;
        private final UUID targetUUID;
        private final DamageSource source;
        private final float amount;

        private QueuedActivatingPlayerDamage(RegistryKey<World> worldKey, UUID targetUUID, DamageSource source, float amount) {
            this.worldKey = worldKey;
            this.targetUUID = targetUUID;
            this.source = source;
            this.amount = amount;
        }
    }

    private static final class TransitionTickState {
        private final Vec3d position;
        private final Vec3d velocity;
        private final float yaw;
        private final float pitch;
        private final float bodyYaw;
        private final float headYaw;
        private final float fallDistance;
        private final boolean onGround;
        private final boolean horizontalCollision;
        private final boolean verticalCollision;
        private final boolean groundCollision;
        private final boolean velocityModified;

        private TransitionTickState(
                Vec3d position, Vec3d velocity, float yaw, float pitch,
                float bodyYaw, float headYaw, float fallDistance, boolean onGround,
                boolean horizontalCollision, boolean verticalCollision,
                boolean groundCollision, boolean velocityModified
        ) {
            this.position = position;
            this.velocity = velocity;
            this.yaw = yaw;
            this.pitch = pitch;
            this.bodyYaw = bodyYaw;
            this.headYaw = headYaw;
            this.fallDistance = fallDistance;
            this.onGround = onGround;
            this.horizontalCollision = horizontalCollision;
            this.verticalCollision = verticalCollision;
            this.groundCollision = groundCollision;
            this.velocityModified = velocityModified;
        }

        private static TransitionTickState capture(Entity entity) {
            float bodyYaw = entity instanceof LivingEntity livingEntity ? livingEntity.getBodyYaw() : entity.getYaw();
            float headYaw = entity instanceof LivingEntity livingEntity ? livingEntity.getHeadYaw() : entity.getYaw();
            return new TransitionTickState(
                    entity.getPos(), entity.getVelocity(), entity.getYaw(), entity.getPitch(),
                    bodyYaw, headYaw, entity.fallDistance, entity.isOnGround(),
                    entity.horizontalCollision, entity.verticalCollision,
                    entity.groundCollision, entity.velocityModified
            );
        }
    }

    private static final class QueuedRedstoneAction {
        private final RedstoneActionType type;
        private final RegistryKey<World> worldKey;
        private final UUID actorUuid;
        private final Hand hand;
        private final BlockHitResult hitResult;
        private final int inventorySlot;
        private final ItemStack queuedStack;
        private final BlockPos pos;

        private QueuedRedstoneAction(
                RedstoneActionType type, RegistryKey<World> worldKey, UUID actorUuid,
                Hand hand, BlockHitResult hitResult, int inventorySlot, ItemStack queuedStack, BlockPos pos
        ) {
            this.type = type;
            this.worldKey = worldKey;
            this.actorUuid = actorUuid;
            this.hand = hand;
            this.hitResult = hitResult;
            this.inventorySlot = inventorySlot;
            this.queuedStack = queuedStack;
            this.pos = pos;
        }

        private static QueuedRedstoneAction blockUse(
                RegistryKey<World> worldKey,
                UUID actorUuid,
                Hand hand,
                BlockHitResult hitResult,
                int inventorySlot,
                ItemStack queuedStack
        ) {
            return new QueuedRedstoneAction(
                    RedstoneActionType.BLOCK_USE,
                    worldKey,
                    actorUuid,
                    hand,
                    hitResult,
                    inventorySlot,
                    queuedStack,
                    hitResult.getBlockPos()
            );
        }

        private static QueuedRedstoneAction pressureCollision(RegistryKey<World> worldKey, UUID actorUuid, BlockPos pos) {
            return new QueuedRedstoneAction(RedstoneActionType.PRESSURE_COLLISION, worldKey, actorUuid, null, null, -1, ItemStack.EMPTY, pos);
        }
    }

    private enum RedstoneActionType {
        BLOCK_USE,
        PRESSURE_COLLISION
    }
}
