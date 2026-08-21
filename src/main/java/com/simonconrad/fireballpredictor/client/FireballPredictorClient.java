package com.simonconrad.fireballpredictor.client;

import com.simonconrad.fireballpredictor.FireballPredictor;
import com.simonconrad.fireballpredictor.client.compat.IrisCompat;
import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.client.network.ClientPowerCacheReceiver;
import com.simonconrad.fireballpredictor.client.network.ClientPowerLookup;
import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker;
import com.simonconrad.fireballpredictor.client.tracking.ClientOwnerCache;
import com.simonconrad.fireballpredictor.client.tracking.ClientOwnerCacheReceiver;
import com.simonconrad.fireballpredictor.client.tracking.InferenceResult;
import com.simonconrad.fireballpredictor.client.tracking.ServerTrackingRules;
import com.simonconrad.fireballpredictor.client.tracking.ServerTrackingRulesReceiver;
import com.simonconrad.fireballpredictor.client.tracking.TrackedProjectile;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.client.render.HeartOverlayRenderer;
import com.simonconrad.fireballpredictor.client.render.PredictionPipelines;
import com.simonconrad.fireballpredictor.client.render.PredictionRenderer;
import com.simonconrad.fireballpredictor.projectile.WarningProjectileType;
import com.simonconrad.fireballpredictor.math.DamageCalculator;
import com.simonconrad.fireballpredictor.math.DamageCalculator.DamageEstimate;
import com.simonconrad.fireballpredictor.math.ImpactPredictor;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FireballPredictorClient implements ClientModInitializer {
    private static FireballPredictorClient INSTANCE;

    private static final java.util.concurrent.ExecutorService PREDICTION_EXECUTOR = 
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "FireballPredictor-Worker");
            thread.setDaemon(true);
            return thread;
        });

    private final Map<Integer, TrackedPrediction> activePredictions = new HashMap<>();
    private final Map<Integer, TrackedProjectile> trackedOwners = new HashMap<>();
    private final Map<net.minecraft.core.BlockPos, Integer> highlightedBlocks = new java.util.HashMap<>();
    private final Map<net.minecraft.core.BlockPos, Integer> previousHighlightedBlocks = new java.util.HashMap<>();
    private boolean impactWarningVisible;
    private float impactWarningProgress;
    private WarningProjectileType impactWarningType = WarningProjectileType.FIREBALL;
    private DamageEstimate currentDamageEstimate = DamageEstimate.NONE;
    private WarningProjectileType currentDamageEstimateType = WarningProjectileType.FIREBALL;
    private boolean damageOverlayActive;
    private ClientLevel trackedWorld;

    public FireballPredictorClient() {
        INSTANCE = this;
    }

    public static void trackWorldEntity(Entity entity) {
        if (INSTANCE != null) {
            INSTANCE.handleEntityAdded(entity);
        }
    }

    public static void untrackWorldEntity(Entity entity) {
        if (INSTANCE != null) {
            INSTANCE.handleEntityRemoved(entity);
        }
    }

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        PredictionPipelines.class.getName();
        IrisCompat.init(); 
        ClientPowerCacheReceiver.registerReceivers();
        ClientOwnerCacheReceiver.registerReceivers();
        ClientOwnerCache.setUpdateListener(this::onOwnerPacketReceived);
        ServerTrackingRulesReceiver.registerReceivers();

        com.simonconrad.fireballpredictor.client.render.ThemePreviewGallery.register();

        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("fireballpredictor", "preview_icons");
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resourceManager) {
                        com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.invalidateIconTextureCache();
                    }
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                resetClientState(null);
                return;
            }

            if (client.level != trackedWorld) {
                resetWorldState(client.level);
            }

            long worldTime = client.level.getGameTime();

            // Clean up dead projectiles and tick owner attribution / filters
            Iterator<Map.Entry<Integer, TrackedPrediction>> it = activePredictions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, TrackedPrediction> entry = it.next();
                int entityId = entry.getKey();
                AbstractHurtingProjectile fireball = getProjectile(client.level, entityId);
                if (fireball == null || !fireball.isAlive()) {
                    ClientPowerCache.remove(entityId);
                    ClientOwnerCache.remove(entityId);
                    trackedOwners.remove(entityId);
                    entry.getValue().cancelActiveTask();
                    it.remove();
                    continue;
                }

                TrackedProjectile tracked = trackedOwners.get(entityId);
                if (tracked != null) {
                    tracked.tick(client.level);
                }

                boolean filteredOut = TrackedProjectile.isOwnerFilterable(fireball)
                        && tracked != null
                        && !tracked.shouldRender();

                if (filteredOut) {
                    ClientPowerCache.remove(entityId);
                    // Keep owner attribution so re-enabling a filter can restore tracking without re-inferring
                    entry.getValue().cancelActiveTask();
                    it.remove();
                }
            }

            // Clean up dead non-active tracked owners and re-admit allowed projectiles
            Iterator<Map.Entry<Integer, TrackedProjectile>> ownerIt = trackedOwners.entrySet().iterator();
            while (ownerIt.hasNext()) {
                Map.Entry<Integer, TrackedProjectile> ownerEntry = ownerIt.next();
                int entityId = ownerEntry.getKey();
                AbstractHurtingProjectile fireball = getProjectile(client.level, entityId);
                if (fireball == null || !fireball.isAlive()) {
                    ClientPowerCache.remove(entityId);
                    ClientOwnerCache.remove(entityId);
                    ownerIt.remove();
                    continue;
                }
                if (activePredictions.containsKey(entityId)) {
                    continue;
                }
                TrackedProjectile tracked = ownerEntry.getValue();
                tracked.tick(client.level);
                if (tracked.shouldRender()) {
                    createAndRegisterPrediction(fireball, client.level);
                }
            }

            for (Map.Entry<Integer, TrackedPrediction> entry : activePredictions.entrySet()) {
                int entityId = entry.getKey();
                AbstractHurtingProjectile fireball = getProjectile(client.level, entityId);
                if (fireball == null) {
                    continue;
                }
                TrackedPrediction trackedPrediction = entry.getValue();

                if (trackedPrediction.shouldRefresh(fireball, client.level)) {
                    schedulePrediction(entityId, trackedPrediction, fireball, client.level);
                }
            }

            previousHighlightedBlocks.clear();
            previousHighlightedBlocks.putAll(highlightedBlocks);
            highlightedBlocks.clear();

            boolean impactWarningDetected = false;
            int minTicksToImpact = Integer.MAX_VALUE;
            float mostRelevantWarningProgress = 0.0f;
            WarningProjectileType warningType = WarningProjectileType.FIREBALL;

            LocalPlayer player = client.player;

            for (Map.Entry<Integer, TrackedPrediction> entry : activePredictions.entrySet()) {
                int entityId = entry.getKey();
                AbstractHurtingProjectile fireball = getProjectile(client.level, entityId);
                if (fireball == null) {
                    continue;
                }
                PredictionData data = entry.getValue().predictionData;

                if (data == null) {
                    continue;
                }

                int elapsedTicks = Math.max(0, fireball.tickCount - data.predictionAge());

                if (player != null && data.hitResult() != null && data.path() != null && data.path().size() > 1) {
                    int ticksToImpact = Math.max(0, data.path().size() - 1 - elapsedTicks);
                    float power = ImpactPredictor.resolveExplosionPower(fireball);
                    float warningPower = power <= 0.0f ? 1.0f : power;
                    double dangerRadius = warningPower * 2.0f * 2.0f;
                    double dangerRadiusSq = dangerRadius * dangerRadius;

                    TrackedPrediction trackedPrediction = entry.getValue();
                    if (isThreateningPlayer(player, fireball, trackedPrediction, elapsedTicks, dangerRadiusSq)) {
                        impactWarningDetected = true;
                        float travelProgress = getTravelProgress(fireball.tickCount, ticksToImpact);
                        if (ticksToImpact < minTicksToImpact) {
                            minTicksToImpact = ticksToImpact;
                            mostRelevantWarningProgress = travelProgress;
                            warningType = WarningProjectileType.fromProjectile(fireball);
                        }
                    }
                }
                
                if (data.brokenBlocks() != null) {
                    int ticksRemaining = Math.max(0, data.path().size() - 1 - elapsedTicks);
                    int age = fireball.tickCount;
                    int totalTicks = age + ticksRemaining;
                    
                    double progress = totalTicks <= 0 ? 1.0 : (double) age / totalTicks;
                    double mappedProgress = 0.3 + (progress * 0.7);
                    int baseStage = Math.min(9, Math.max(0, (int) (mappedProgress * 10)));
                    
                    int period = Math.max(3, ticksRemaining / 4);
                    boolean isVisible = (age % period) < ((period * 3) / 4);
                    int currentStage = isVisible ? baseStage : -1;
                    
                    if (!client.isPaused() && ModConfig.instance().renderParticleAccents && client.level.getRandom().nextInt(2) == 0 && !data.brokenBlocks().isEmpty()) {
                        int particleCount = 1 + client.level.getRandom().nextInt(3);
                        for (int i = 0; i < particleCount; i++) {
                            net.minecraft.core.BlockPos randomPos = data.brokenBlocks().get(client.level.getRandom().nextInt(data.brokenBlocks().size()));
                            if (!client.level.getBlockState(randomPos).isAir()) {
                                double px = randomPos.getX() + client.level.getRandom().nextDouble();
                                double py = randomPos.getY() + 1.1;
                                double pz = randomPos.getZ() + client.level.getRandom().nextDouble();
                                
                                net.minecraft.core.particles.ParticleOptions effect = getThematicParticle(ModConfig.instance().getThemeFor(fireball), client.level.getRandom());
                                client.level.addParticle(effect, px, py, pz, 0, 0.05, 0);
                            }
                        }
                    }

                    if (ModConfig.instance().renderBlockHighlights) {
                        for (net.minecraft.core.BlockPos pos : data.brokenBlocks()) {
                            if (!client.level.getBlockState(pos).isAir()) {
                                highlightedBlocks.merge(pos, currentStage, Math::max);
                            }
                        }
                    }
                }
            }

            if (impactWarningDetected) {
                impactWarningProgress = mostRelevantWarningProgress;
                impactWarningType = warningType;
            } else {
                impactWarningProgress = 0.0f;
                impactWarningType = WarningProjectileType.FIREBALL;
            }

            impactWarningVisible = impactWarningDetected;

            for (Map.Entry<net.minecraft.core.BlockPos, Integer> entry : previousHighlightedBlocks.entrySet()) {
                net.minecraft.core.BlockPos pos = entry.getKey();
                if (!highlightedBlocks.containsKey(pos)) {
                    client.level.destroyBlockProgress(pos.hashCode(), pos, -1);
                }
            }

            for (Map.Entry<net.minecraft.core.BlockPos, Integer> entry : highlightedBlocks.entrySet()) {
                net.minecraft.core.BlockPos pos = entry.getKey();
                int newStage = entry.getValue();
                int oldStage = previousHighlightedBlocks.getOrDefault(pos, -2);
                if (newStage != oldStage) {
                    client.level.destroyBlockProgress(pos.hashCode(), pos, newStage);
                }
            }

            // Damage & knockback estimation for the cracking-hearts HUD overlay.
            // Computed on the main thread every tick: DamageCalculator.getSeenPercent raycasts the
            // level (level.clip) and is not thread-safe, so it must never run on the worker thread.
            // The most threatening in-range threat (highest final damage, tie-broken by knockback) drives the overlay.
            DamageEstimate bestEstimate = DamageEstimate.NONE;
            WarningProjectileType bestEstimateType = WarningProjectileType.FIREBALL;
            boolean estimateFound = false;
            ModConfig config = ModConfig.instance();
            if (player != null && (config.renderDamageHeartsOverlay || config.showKnockbackEstimator)) {
                for (Map.Entry<Integer, TrackedPrediction> entry : activePredictions.entrySet()) {
                    AbstractHurtingProjectile fireball = getProjectile(client.level, entry.getKey());
                    TrackedPrediction trackedPrediction = entry.getValue();
                    PredictionData data = trackedPrediction.predictionData;
                    if (fireball == null || data == null) {
                        continue;
                    }
                    HitResult damageHit = trackedPrediction.getOrComputeDamageHit(client.level, fireball, fireball.tickCount);
                    if (damageHit == null) {
                        continue;
                    }
                    Vec3 hitPos = damageHit.getLocation();
                    Vec3 playerPos = player.position();

                    float power = ImpactPredictor.resolveExplosionPower(fireball);
                    float radius = power * DamageCalculator.BLAST_RADIUS_MULTIPLIER;
                    boolean isDirectHit = damageHit.getType() == HitResult.Type.ENTITY
                            && damageHit instanceof EntityHitResult entityHit
                            && entityHit.getEntity() == player;

                    // Skip 27 world raycasts if the player is safely out of blast range
                    if (!isDirectHit && (radius <= 0.0f || playerPos.distanceToSqr(hitPos) > (radius * radius))) {
                        continue;
                    }

                    float seenPercent;
                    if (trackedPrediction.cachedSeenPercent >= 0.0f
                            && trackedPrediction.lastEstimatePlayerPos != null
                            && trackedPrediction.lastEstimateHitPos != null
                            && playerPos.distanceToSqr(trackedPrediction.lastEstimatePlayerPos) < 0.0025
                            && hitPos.distanceToSqr(trackedPrediction.lastEstimateHitPos) < 0.0025) {
                        seenPercent = trackedPrediction.cachedSeenPercent;
                    } else {
                        seenPercent = DamageCalculator.getSeenPercent(client.level, hitPos, player);
                        trackedPrediction.cachedSeenPercent = seenPercent;
                        trackedPrediction.lastEstimatePlayerPos = playerPos;
                        trackedPrediction.lastEstimateHitPos = hitPos;
                    }

                    TrackedProjectile tracked = trackedOwners.get(entry.getKey());
                    Entity owner = tracked != null ? tracked.ownerEntity() : null;

                    DamageEstimate estimate;
                    if (isDirectHit) {
                        estimate = DamageCalculator.calculateDirectHitFromSeenPercent(
                                hitPos, power, player, client.level, fireball, owner, seenPercent);
                    } else {
                        DamageSource explosionSource = client.level.damageSources().explosion(fireball, owner);
                        estimate = DamageCalculator.calculateFromSeenPercent(
                                hitPos, power, player, explosionSource, seenPercent);
                    }

                    if (!estimate.inRange()) {
                        continue;
                    }
                    if (!estimateFound
                            || estimate.finalDamage() > bestEstimate.finalDamage()
                            || (estimate.finalDamage() == bestEstimate.finalDamage()
                                && estimate.knockbackBlocksPerSecond() > bestEstimate.knockbackBlocksPerSecond())) {
                        bestEstimate = estimate;
                        bestEstimateType = WarningProjectileType.fromProjectile(fireball);
                        estimateFound = true;
                    }
                }
            }
            currentDamageEstimate = estimateFound ? bestEstimate : DamageEstimate.NONE;
            currentDamageEstimateType = estimateFound ? bestEstimateType : WarningProjectileType.FIREBALL;
            damageOverlayActive = estimateFound;

            if (com.simonconrad.fireballpredictor.client.render.ThemePreviewGallery.isActive()) {
                com.simonconrad.fireballpredictor.client.render.ThemePreviewGallery.tick(client);
            }
        });

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("fireballpredictor", "impact_warning"),
            (graphics, tickCounter) -> {
                PredictionRenderer.renderImpactWarningBadge(graphics, Minecraft.getInstance(), impactWarningVisible, impactWarningProgress, impactWarningType);
            }
        );

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HEALTH_BAR,
            Identifier.fromNamespaceAndPath("fireballpredictor", "damage_hearts"),
            (graphics, tickCounter) -> {
                HeartOverlayRenderer.render(graphics, Minecraft.getInstance(), damageOverlayActive, currentDamageEstimate, currentDamageEstimateType);
            }
        );

        LevelRenderEvents.END_MAIN.register(context -> {
            if (com.simonconrad.fireballpredictor.client.render.ThemePreviewGallery.isActive()) {
                com.simonconrad.fireballpredictor.client.render.ThemePreviewGallery.render(
                    context.poseStack(),
                    context.submitNodeCollector(),
                    net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera(),
                    net.minecraft.client.Minecraft.getInstance().level
                );
            }

            if (activePredictions.isEmpty()) return;

            ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
            for (Map.Entry<Integer, TrackedPrediction> entry : activePredictions.entrySet()) {
                AbstractHurtingProjectile fireball = getProjectile(level, entry.getKey());
                if (fireball != null && fireball.isAlive()) {
                    PredictionData predictionData = entry.getValue().predictionData;
                    if (predictionData != null) {
                        PredictionRenderer.render(context.poseStack(), context.submitNodeCollector(), net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera(), level, predictionData, fireball);
                    }
                }
            }
        });
    }

    private static AbstractHurtingProjectile getProjectile(ClientLevel level, int entityId) {
        if (level == null) {
            return null;
        }
        Entity entity = level.getEntity(entityId);
        return entity instanceof AbstractHurtingProjectile projectile ? projectile : null;
    }

    private void schedulePrediction(int entityId, TrackedPrediction trackedPrediction, AbstractHurtingProjectile fireball, ClientLevel world) {
        trackedPrediction.cancelActiveTask();
        trackedPrediction.isCalculating = true;
        long taskId = ++trackedPrediction.currentTaskId;

        float currentPower = ImpactPredictor.resolveExplosionPower(fireball);
        boolean currentDangerous = fireball instanceof WitherSkull skull && skull.isDangerous();
        TrajectoryPredictor.TrajectoryResult result = TrajectoryPredictor.simulateTrajectory(fireball, world);
        int predictionAge = fireball.tickCount;

        // Set preliminary prediction immediately for zero-latency frame 0 trajectory rendering
        trackedPrediction.predictionData = TrajectoryPredictor.createPreliminaryPrediction(result, predictionAge);
        trackedPrediction.calculatedPower = currentPower;
        trackedPrediction.calculatedDangerous = currentDangerous;
        trackedPrediction.cachedDamageHitTick = -1;

        Vec3 hitPos = result.hitResult() != null ? result.hitResult().getLocation() : null;
        FireballInferenceTracker.registerFireballLocation(fireball, hitPos);

        Minecraft client = Minecraft.getInstance();

        java.util.concurrent.Future<?> future = PREDICTION_EXECUTOR.submit(() -> {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                PredictionData data = TrajectoryPredictor.computePrediction(result, predictionAge);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                client.execute(() -> {
                    if (INSTANCE != null && INSTANCE.activePredictions.get(entityId) == trackedPrediction && trackedPrediction.isCurrentTask(taskId)) {
                        trackedPrediction.predictionData = data;
                        trackedPrediction.calculatedPower = currentPower;
                        trackedPrediction.calculatedDangerous = currentDangerous;
                        trackedPrediction.cachedDamageHitTick = -1;
                        trackedPrediction.isCalculating = false;
                        trackedPrediction.activeTask = null;
                    }
                });
            } catch (Exception e) {
                if (!(e instanceof java.util.concurrent.CancellationException) && !Thread.currentThread().isInterrupted()) {
                    FireballPredictor.LOGGER.error("Failed to calculate fireball prediction", e);
                }
                client.execute(() -> {
                    if (INSTANCE != null && INSTANCE.activePredictions.get(entityId) == trackedPrediction && trackedPrediction.isCurrentTask(taskId)) {
                        trackedPrediction.isCalculating = false;
                        trackedPrediction.activeTask = null;
                    }
                });
            }
        });

        trackedPrediction.activeTask = future;
    }

    private void createAndRegisterPrediction(AbstractHurtingProjectile fireball, ClientLevel world) {
        int entityId = fireball.getId();
        TrackedPrediction trackedPrediction = new TrackedPrediction();
        TrackedPrediction existing = activePredictions.put(entityId, trackedPrediction);
        if (existing != null) {
            existing.cancelActiveTask();
        }
        schedulePrediction(entityId, trackedPrediction, fireball, world);
    }

    private void resetClientState(ClientLevel world) {
        trackedWorld = world;
        for (TrackedPrediction tracked : activePredictions.values()) {
            tracked.cancelActiveTask();
        }
        activePredictions.clear();
        trackedOwners.clear();
        highlightedBlocks.clear();
        previousHighlightedBlocks.clear();
        ClientPowerCache.clear();
        ClientOwnerCache.clear();
        FireballInferenceTracker.clear();
        ClientPowerLookup.resetInferredPower();
        com.simonconrad.fireballpredictor.client.render.ThemePreviewGallery.disable(null);
        impactWarningVisible = false;
        impactWarningProgress = 0.0f;
        impactWarningType = WarningProjectileType.FIREBALL;
        currentDamageEstimate = DamageEstimate.NONE;
        currentDamageEstimateType = WarningProjectileType.FIREBALL;
        damageOverlayActive = false;
        if (world == null) {
            ServerTrackingRules.clear();
        }
    }

    private void resetWorldState(ClientLevel world) {
        resetClientState(world);

        for (Entity entity : world.entitiesForRendering()) {
            handleEntityAdded(entity);
        }
    }

    private void handleEntityAdded(Entity entity) {
        if (trackedWorld == null || !entity.isAlive()) {
            return;
        }

        if (entity instanceof AbstractHurtingProjectile fireball) {
            int entityId = fireball.getId();

            TrackedProjectile ownerTracked = null;
            if (TrackedProjectile.isOwnerFilterable(fireball)) {
                ownerTracked = TrackedProjectile.of(fireball, trackedWorld);
                trackedOwners.put(entityId, ownerTracked);
                if (!ownerTracked.shouldRender()) {
                    // Still keep owner state for live filter toggles / packet upgrades
                    return;
                }
            }

            createAndRegisterPrediction(fireball, trackedWorld);
        }
    }

    /**
     * Called when a server owner packet arrives (or is upgraded). Re-evaluates
     * filter state and starts prediction if the projectile is now allowed.
     */
    private void onOwnerPacketReceived(int entityId) {
        if (trackedWorld == null) {
            return;
        }
        AbstractHurtingProjectile fireball = getProjectile(trackedWorld, entityId);
        if (fireball == null || !fireball.isAlive()) {
            return;
        }

        InferenceResult packetResult = ClientOwnerCache.get(entityId);
        if (packetResult == null) {
            return;
        }

        TrackedProjectile tracked = trackedOwners.get(entityId);
        if (tracked == null) {
            if (!TrackedProjectile.isOwnerFilterable(fireball)) {
                return;
            }
            tracked = TrackedProjectile.of(fireball, trackedWorld);
            trackedOwners.put(entityId, tracked);
        }
        tracked.applyPacketResult(packetResult);

        if (tracked.shouldRender() && !activePredictions.containsKey(entityId)) {
            // Filter now allows this projectile — begin prediction
            createAndRegisterPrediction(fireball, trackedWorld);
        }
    }

    private void handleEntityRemoved(Entity entity) {
        if (entity instanceof AbstractHurtingProjectile fireball) {
            FireballInferenceTracker.recordFinalFireballLocation(fireball);
            int entityId = fireball.getId();
            TrackedPrediction tracked = activePredictions.remove(entityId);
            if (tracked != null) {
                tracked.cancelActiveTask();
            }
            trackedOwners.remove(entityId);
            ClientPowerCache.remove(entityId);
            ClientOwnerCache.remove(entityId);
        }
    }

    private static boolean isThreateningPlayer(LocalPlayer player, AbstractHurtingProjectile projectile, TrackedPrediction trackedPrediction, int elapsedTicks, double dangerRadiusSq) {
        if (player == null || trackedPrediction == null) {
            return false;
        }
        PredictionData data = trackedPrediction.predictionData;
        if (data == null || data.path() == null || data.path().isEmpty()) {
            return false;
        }

        // 1. Direct entity hit on the player along the path
        HitResult damageHit = trackedPrediction.getOrComputeDamageHit(player.level(), projectile, projectile.tickCount);
        if (damageHit instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() == player) {
            return true;
        }
        if (data.hitResult() instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() == player) {
            return true;
        }

        // 2. Impact detonation point is within blast danger radius of the player
        Vec3 playerPos = player.position();
        Vec3 impactPos = damageHit != null ? damageHit.getLocation() : (data.hitResult() != null ? data.hitResult().getLocation() : null);
        if (impactPos != null && playerPos.distanceToSqr(impactPos) <= dangerRadiusSq) {
            return true;
        }

        // 3. Proximity along the flight path (current player position + short-term velocity extrapolation)
        Vec3 playerVel = player.getDeltaMovement();
        for (int i = elapsedTicks; i < data.path().size(); i++) {
            Vec3 pathPoint = data.path().get(i);
            if (pathPoint.distanceToSqr(playerPos) <= dangerRadiusSq) {
                return true;
            }
            int lookahead = Math.min(5, i - elapsedTicks);
            if (lookahead > 0) {
                Vec3 shortExtrapolated = playerPos.add(playerVel.scale(lookahead));
                if (pathPoint.distanceToSqr(shortExtrapolated) <= dangerRadiusSq) {
                    return true;
                }
            }
        }

        return false;
    }

    private static float getTravelProgress(int age, int ticksToImpact) {
        int totalTicks = age + ticksToImpact;
        if (totalTicks <= 0) {
            return 1.0f;
        }

        return Mth.clamp((float) age / (float) totalTicks, 0.0f, 1.0f);
    }

    private static final class TrackedPrediction {
        private PredictionData predictionData;
        private boolean isCalculating = false;
        private java.util.concurrent.Future<?> activeTask = null;
        private long currentTaskId = 0L;
        private float calculatedPower = -1.0f;
        private boolean calculatedDangerous = false;
        private float cachedSeenPercent = -1.0f;
        private Vec3 lastEstimatePlayerPos;
        private Vec3 lastEstimateHitPos;
        private HitResult cachedDamageHit;
        private int cachedDamageHitTick = -1;

        public void cancelActiveTask() {
            if (activeTask != null) {
                activeTask.cancel(true);
                activeTask = null;
            }
            isCalculating = false;
        }

        public boolean isCurrentTask(long taskId) {
            return isCalculating && this.currentTaskId == taskId;
        }

        public HitResult getOrComputeDamageHit(net.minecraft.world.level.Level world, AbstractHurtingProjectile fireball, int tick) {
            if (cachedDamageHitTick == tick) {
                return cachedDamageHit;
            }
            cachedDamageHit = TrajectoryPredictor.findDamageHitResult(world, fireball, predictionData);
            cachedDamageHitTick = tick;
            return cachedDamageHit;
        }

        private boolean shouldRefresh(AbstractHurtingProjectile fireball, ClientLevel world) {
            float currentPower = ImpactPredictor.resolveExplosionPower(fireball);
            if (currentPower != calculatedPower) {
                return true;
            }

            if (fireball instanceof WitherSkull witherSkull) {
                boolean currentDangerous = witherSkull.isDangerous();
                if (currentDangerous != calculatedDangerous) {
                    return true;
                }
            }

            if (predictionData == null || predictionData.path() == null || predictionData.velocities() == null) {
                return true;
            }
            
            // Check if the entity was deflected or velocity/position drifted
            int elapsedTicks = fireball.tickCount - predictionData.predictionAge();
            if (elapsedTicks < 0 || elapsedTicks >= predictionData.path().size()) {
                return true;
            }

            Vec3 expectedPos = predictionData.path().get(elapsedTicks);
            Vec3 expectedVel = predictionData.velocities().get(elapsedTicks);
            Vec3 actualPos = fireball.position();
            Vec3 actualVel = fireball.getDeltaMovement();

            double maxPosDevSq = 0.25 * 0.25;
            double maxVelDevSq = 0.05 * 0.05;

            if (actualPos.distanceToSqr(expectedPos) > maxPosDevSq || actualVel.distanceToSqr(expectedVel) > maxVelDevSq) {
                return true;
            }

            // Check if path is obstructed or block states along it changed
            if (predictionData.hitResult() != null && predictionData.hitResult().getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) predictionData.hitResult();
                net.minecraft.core.BlockPos hitPos = blockHit.getBlockPos();
                if (world.getBlockState(hitPos).isAir()) {
                    return true;
                }
            }

            // Check the immediate next 5 ticks ahead on every tick
            int immediateAhead = Math.min(predictionData.path().size() - 1, elapsedTicks + 5);
            for (int i = elapsedTicks; i < immediateAhead; i++) {
                Vec3 pos = predictionData.path().get(i);
                net.minecraft.core.BlockPos blockPos = net.minecraft.core.BlockPos.containing(pos.x, pos.y, pos.z);
                if (!world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty()) {
                    return true;
                }
            }

            // Throttle full-path obstruction rescans to once every 5 ticks to avoid redundant O(N) rescans
            if (fireball.tickCount % 5 == 0 && immediateAhead < predictionData.path().size() - 1) {
                for (int i = immediateAhead; i < predictionData.path().size() - 1; i++) {
                    Vec3 pos = predictionData.path().get(i);
                    net.minecraft.core.BlockPos blockPos = net.minecraft.core.BlockPos.containing(pos.x, pos.y, pos.z);
                    if (!world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public static net.minecraft.core.particles.ParticleOptions getThematicParticle(
            com.simonconrad.fireballpredictor.config.VisualTheme theme,
            net.minecraft.util.RandomSource random
    ) {
        if (theme == null) {
            theme = com.simonconrad.fireballpredictor.config.VisualTheme.DEFAULT;
        }
        return switch (theme) {
            case SAKURA -> ParticleTypes.CHERRY_LEAVES;
            case GHOST -> ParticleTypes.SOUL_FIRE_FLAME;
            case ELECTRIC_ARC -> ParticleTypes.ELECTRIC_SPARK;
            case SCULK_VOID -> ParticleTypes.SCULK_SOUL;
            case AURORA, CRYSTAL, CELESTIAL -> ParticleTypes.END_ROD;
            case MATRIX, CYBERPUNK -> ParticleTypes.ENCHANT;
            case SINGULARITY -> ParticleTypes.PORTAL;
            case ARCADE, RAINBOW -> ParticleTypes.GLOW;
            default -> {
                int pType = random.nextInt(3);
                if (pType == 1) yield ParticleTypes.LAVA;
                else if (pType == 2) yield ParticleTypes.CAMPFIRE_COSY_SMOKE;
                else yield ParticleTypes.FLAME;
            }
        };
    }
}
