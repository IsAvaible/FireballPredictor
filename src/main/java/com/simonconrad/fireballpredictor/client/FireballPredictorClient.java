package com.simonconrad.fireballpredictor.client;

import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.client.render.PredictionRenderer;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
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

    private final Map<AbstractHurtingProjectile, TrackedPrediction> activePredictions = new HashMap<>();
    private java.util.Map<net.minecraft.core.BlockPos, Integer> currentlyHighlightedBlocks = new java.util.HashMap<>();
    private boolean impactWarningVisible;
    private float impactWarningProgress;
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
        ClientPowerCache.registerReceivers();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                activePredictions.clear();
                currentlyHighlightedBlocks.clear();
                ClientPowerCache.POWER_CACHE.clear();
                impactWarningVisible = false;
                impactWarningProgress = 0.0f;
                trackedWorld = null;
                return;
            }

            if (client.level != trackedWorld) {
                resetWorldState(client.level);
            }

            long worldTime = client.level.getGameTime();

            // Clean up dead fireballs or disabled wither skulls
            Iterator<Map.Entry<AbstractHurtingProjectile, TrackedPrediction>> it = activePredictions.entrySet().iterator();
            while (it.hasNext()) {
                AbstractHurtingProjectile fireball = it.next().getKey();
                boolean isWitherSkull = fireball instanceof WitherSkull;
                if (!fireball.isAlive() || (isWitherSkull && !ModConfig.instance().trackWitherSkulls)) {
                    ClientPowerCache.POWER_CACHE.remove(fireball.getId());
                    it.remove();
                }
            }

            for (Map.Entry<AbstractHurtingProjectile, TrackedPrediction> entry : activePredictions.entrySet()) {
                AbstractHurtingProjectile fireball = entry.getKey();
                TrackedPrediction trackedPrediction = entry.getValue();

                if (trackedPrediction.shouldRefresh(fireball, client.level) && !trackedPrediction.isCalculating) {
                    trackedPrediction.isCalculating = true;
                    TrajectoryPredictor.TrajectoryResult result = TrajectoryPredictor.simulateTrajectory(fireball, client.level);
                    int predictionAge = fireball.tickCount;
                    
                    PREDICTION_EXECUTOR.submit(() -> {
                        try {
                            PredictionData data = TrajectoryPredictor.computePrediction(fireball, result, predictionAge);
                            client.execute(() -> {
                                if (INSTANCE != null && INSTANCE.activePredictions.get(fireball) == trackedPrediction) {
                                    trackedPrediction.predictionData = data;
                                    trackedPrediction.isCalculating = false;
                                }
                            });
                        } catch (Exception e) {
                            com.simonconrad.fireballpredictor.FireballPredictor.LOGGER.error("Failed to calculate fireball prediction", e);
                            client.execute(() -> {
                                trackedPrediction.isCalculating = false;
                            });
                        }
                    });
                }
            }

            java.util.Map<net.minecraft.core.BlockPos, Integer> newHighlightedBlocks = new java.util.HashMap<>();
            boolean impactWarningDetected = false;
            float mostRelevantWarningProgress = 0.0f;

            LocalPlayer player = client.player;
            Vec3 playerPosition = player != null ? new Vec3(player.getX(), player.getY(), player.getZ()) : Vec3.ZERO;
            Vec3 playerVelocity = player != null ? player.getDeltaMovement() : Vec3.ZERO;

            for (Map.Entry<AbstractHurtingProjectile, TrackedPrediction> entry : activePredictions.entrySet()) {
                AbstractHurtingProjectile fireball = entry.getKey();
                PredictionData data = entry.getValue().predictionData;

                if (data == null) {
                    continue;
                }

                int elapsedTicks = Math.max(0, fireball.tickCount - data.predictionAge);

                if (player != null && data.hitResult != null && data.path != null && data.path.size() > 1) {
                    int ticksToImpact = Math.max(0, data.path.size() - 1 - elapsedTicks);
                    float power = ClientPowerCache.POWER_CACHE.getOrDefault(fireball.getId(), fireball instanceof net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball ? ModConfig.instance().clientFallbackFireballPower : 1.0f);
                    double dangerRadius = power * 2.0f * 2.0f;
                    double dangerRadiusSq = dangerRadius * dangerRadius;

                    if (isDangerousPath(playerPosition, playerVelocity, data.path, elapsedTicks, dangerRadiusSq)) {
                        impactWarningDetected = true;
                        float travelProgress = getTravelProgress(fireball.tickCount, ticksToImpact);
                        mostRelevantWarningProgress = Math.max(mostRelevantWarningProgress, travelProgress);
                    }
                }
                
                if (data.brokenBlocks != null) {
                    int ticksRemaining = Math.max(0, data.path.size() - 1 - elapsedTicks);
                    int age = fireball.tickCount;
                    int totalTicks = age + ticksRemaining;
                    
                    double progress = totalTicks <= 0 ? 1.0 : (double) age / totalTicks;
                    double mappedProgress = 0.3 + (progress * 0.7);
                    int baseStage = Math.min(9, Math.max(0, (int) (mappedProgress * 10)));
                    
                    int period = Math.max(3, ticksRemaining / 4);
                    boolean isVisible = (age % period) < ((period * 3) / 4);
                    int currentStage = isVisible ? baseStage : -1;
                    
                    if (ModConfig.instance().renderParticleAccents && client.level.getRandom().nextInt(2) == 0 && !data.brokenBlocks.isEmpty()) {
                        int particleCount = 1 + client.level.getRandom().nextInt(3);
                        for (int i = 0; i < particleCount; i++) {
                            net.minecraft.core.BlockPos randomPos = data.brokenBlocks.get(client.level.getRandom().nextInt(data.brokenBlocks.size()));
                            if (!client.level.getBlockState(randomPos).isAir()) {
                                double px = randomPos.getX() + client.level.getRandom().nextDouble();
                                double py = randomPos.getY() + 1.1;
                                double pz = randomPos.getZ() + client.level.getRandom().nextDouble();
                                
                                int pType = client.level.getRandom().nextInt(3);
                                net.minecraft.core.particles.ParticleOptions effect = ParticleTypes.FLAME;
                                if (pType == 1) effect = ParticleTypes.LAVA;
                                else if (pType == 2) effect = ParticleTypes.CAMPFIRE_COSY_SMOKE;
                                
                                client.level.addParticle(effect, px, py, pz, 0, 0.05, 0);
                            }
                        }
                    }

                    if (ModConfig.instance().renderBlockHighlights) {
                        for (net.minecraft.core.BlockPos pos : data.brokenBlocks) {
                            if (!client.level.getBlockState(pos).isAir()) {
                                newHighlightedBlocks.merge(pos, currentStage, Math::max);
                            }
                        }
                    }
                }
            }

            if (impactWarningDetected) {
                impactWarningProgress = mostRelevantWarningProgress;
            } else {
                impactWarningProgress = 0.0f;
            }

            impactWarningVisible = impactWarningDetected;

            for (Map.Entry<net.minecraft.core.BlockPos, Integer> entry : currentlyHighlightedBlocks.entrySet()) {
                net.minecraft.core.BlockPos pos = entry.getKey();
                if (!newHighlightedBlocks.containsKey(pos)) {
                    client.level.destroyBlockProgress(pos.hashCode(), pos, -1);
                }
            }

            for (Map.Entry<net.minecraft.core.BlockPos, Integer> entry : newHighlightedBlocks.entrySet()) {
                net.minecraft.core.BlockPos pos = entry.getKey();
                int newStage = entry.getValue();
                int oldStage = currentlyHighlightedBlocks.getOrDefault(pos, -2);
                if (newStage != oldStage) {
                    client.level.destroyBlockProgress(pos.hashCode(), pos, newStage);
                }
            }

            currentlyHighlightedBlocks = newHighlightedBlocks;
        });

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("fireballpredictor", "impact_warning"),
            (graphics, tickCounter) -> {
                PredictionRenderer.renderImpactWarningBadge(graphics, Minecraft.getInstance(), impactWarningVisible, impactWarningProgress);
            }
        );

        LevelRenderEvents.END_MAIN.register(context -> {
            if (activePredictions.isEmpty()) return;

            for (Map.Entry<AbstractHurtingProjectile, TrackedPrediction> entry : activePredictions.entrySet()) {
                AbstractHurtingProjectile fireball = entry.getKey();
                if (fireball.isAlive()) {
                    PredictionData predictionData = entry.getValue().predictionData;
                    if (predictionData != null) {
                        PredictionRenderer.render(context.poseStack(), context.submitNodeCollector(), net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera(), net.minecraft.client.Minecraft.getInstance().level, predictionData, fireball);
                    }
                }
            }
        });
    }

    private void resetWorldState(ClientLevel world) {
        trackedWorld = world;
        activePredictions.clear();
        currentlyHighlightedBlocks.clear();

        for (Entity entity : world.entitiesForRendering()) {
            handleEntityAdded(entity);
        }
    }

    private void handleEntityAdded(Entity entity) {
        if (trackedWorld == null || !entity.isAlive()) {
            return;
        }

        if (entity instanceof AbstractHurtingProjectile fireball) {
            if (fireball instanceof WitherSkull && !ModConfig.instance().trackWitherSkulls) {
                return;
            }
            TrackedPrediction trackedPrediction = new TrackedPrediction();
            trackedPrediction.predictionData = TrajectoryPredictor.predict(fireball, trackedWorld);
            activePredictions.put(fireball, trackedPrediction);
        }
    }

    private void handleEntityRemoved(Entity entity) {
        if (entity instanceof AbstractHurtingProjectile fireball) {
            activePredictions.remove(fireball);
            ClientPowerCache.POWER_CACHE.remove(fireball.getId());
        }
    }

    private static boolean isDangerousPath(Vec3 playerPosition, Vec3 playerVelocity, java.util.List<Vec3> path, int elapsedTicks, double dangerRadiusSq) {
        for (int i = elapsedTicks; i < path.size(); i++) {
            Vec3 predictedPlayerPos = playerPosition.add(playerVelocity.scale(i - elapsedTicks));
            if (path.get(i).distanceToSqr(predictedPlayerPos) <= dangerRadiusSq) {
                return true;
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

        private boolean shouldRefresh(AbstractHurtingProjectile fireball, ClientLevel world) {
            if (predictionData == null || predictionData.path == null || predictionData.velocities == null) {
                return true;
            }
            
            // Check if the entity was deflected or velocity/position drifted
            int elapsedTicks = fireball.tickCount - predictionData.predictionAge;
            if (elapsedTicks < 0 || elapsedTicks >= predictionData.path.size()) {
                return true;
            }

            Vec3 expectedPos = predictionData.path.get(elapsedTicks);
            Vec3 expectedVel = predictionData.velocities.get(elapsedTicks);
            Vec3 actualPos = fireball.position();
            Vec3 actualVel = fireball.getDeltaMovement();

            double maxPosDevSq = 0.25 * 0.25;
            double maxVelDevSq = 0.05 * 0.05;

            if (actualPos.distanceToSqr(expectedPos) > maxPosDevSq || actualVel.distanceToSqr(expectedVel) > maxVelDevSq) {
                return true;
            }

            // Check if path is obstructed or block states along it changed
            if (predictionData.hitResult != null && predictionData.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) predictionData.hitResult;
                net.minecraft.core.BlockPos hitPos = blockHit.getBlockPos();
                if (world.getBlockState(hitPos).isAir()) {
                    return true;
                }
            }

            for (int i = elapsedTicks; i < predictionData.path.size() - 1; i++) {
                Vec3 pos = predictionData.path.get(i);
                net.minecraft.core.BlockPos blockPos = net.minecraft.core.BlockPos.containing(pos.x, pos.y, pos.z);
                if (!world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty()) {
                    return true;
                }
            }

            return false;
        }
    }
}
