package com.simonconrad.fireballpredictor.client;

import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.client.render.PredictionRenderer;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

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

    private final Map<ExplosiveProjectileEntity, TrackedPrediction> activePredictions = new HashMap<>();
    private java.util.Map<net.minecraft.util.math.BlockPos, Integer> currentlyHighlightedBlocks = new java.util.HashMap<>();
    private boolean impactWarningVisible;
    private float impactWarningProgress;
    private boolean impactWarningIsWindCharge;
    private ClientWorld trackedWorld;

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
            if (client.world == null) {
                activePredictions.clear();
                currentlyHighlightedBlocks.clear();
                ClientPowerCache.POWER_CACHE.clear();
                com.simonconrad.fireballpredictor.client.network.ClientPowerLookup.resetInferredPower();
                com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker.clear();
                impactWarningVisible = false;
                impactWarningProgress = 0.0f;
                impactWarningIsWindCharge = false;
                trackedWorld = null;
                return;
            }

            if (client.world != trackedWorld) {
                resetWorldState(client.world);
            }

            long worldTime = client.world.getTime();

            // Clean up dead fireballs or disabled wither skulls / wind charges
            Iterator<Map.Entry<ExplosiveProjectileEntity, TrackedPrediction>> it = activePredictions.entrySet().iterator();
            while (it.hasNext()) {
                ExplosiveProjectileEntity fireball = it.next().getKey();
                boolean isWitherSkull = fireball instanceof WitherSkullEntity;
                boolean isWindCharge = fireball instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity;
                if (!fireball.isAlive() || 
                    (isWitherSkull && !ModConfig.instance().trackWitherSkulls) ||
                    (isWindCharge && !ModConfig.instance().trackWindCharges)) {
                    ClientPowerCache.POWER_CACHE.remove(fireball.getId());
                    it.remove();
                }
            }

            for (Map.Entry<ExplosiveProjectileEntity, TrackedPrediction> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                TrackedPrediction trackedPrediction = entry.getValue();

                if (trackedPrediction.shouldRefresh(fireball, client.world) && !trackedPrediction.isCalculating) {
                    trackedPrediction.isCalculating = true;
                    float currentPower = com.simonconrad.fireballpredictor.client.network.ClientPowerLookup.getPower(fireball);
                    boolean currentDangerous = fireball instanceof WitherSkullEntity skull && skull.isCharged();
                    TrajectoryPredictor.TrajectoryResult result = TrajectoryPredictor.simulateTrajectory(fireball, client.world);
                    int predictionAge = fireball.age;
                    
                    PREDICTION_EXECUTOR.submit(() -> {
                        try {
                            PredictionData data = TrajectoryPredictor.computePrediction(fireball, result, predictionAge);
                            client.execute(() -> {
                                if (INSTANCE != null && INSTANCE.activePredictions.get(fireball) == trackedPrediction) {
                                    trackedPrediction.predictionData = data;
                                    trackedPrediction.calculatedPower = currentPower;
                                    trackedPrediction.calculatedDangerous = currentDangerous;
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

            java.util.Map<net.minecraft.util.math.BlockPos, Integer> newHighlightedBlocks = new java.util.HashMap<>();
            boolean impactWarningDetected = false;
            float mostRelevantWarningProgress = 0.0f;
            boolean warningIsWindCharge = false;

            ClientPlayerEntity player = client.player;
            Vec3d playerPosition = player != null ? new Vec3d(player.getX(), player.getY(), player.getZ()) : Vec3d.ZERO;
            Vec3d playerVelocity = player != null ? player.getVelocity() : Vec3d.ZERO;

            for (Map.Entry<ExplosiveProjectileEntity, TrackedPrediction> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                PredictionData data = entry.getValue().predictionData;

                if (data == null) {
                    continue;
                }

                int elapsedTicks = Math.max(0, fireball.age - data.predictionAge);

                if (player != null && data.hitResult != null && data.path != null && data.path.size() > 1) {
                    int ticksToImpact = Math.max(0, data.path.size() - 1 - elapsedTicks);
                    float power = ClientPowerCache.POWER_CACHE.getOrDefault(fireball.getId(), fireball instanceof net.minecraft.entity.projectile.FireballEntity ? ModConfig.instance().clientFallbackFireballPower : 1.0f);
                    double dangerRadius = power * 2.0f * 2.0f;
                    double dangerRadiusSq = dangerRadius * dangerRadius;

                    if (isDangerousPath(playerPosition, playerVelocity, data.path, elapsedTicks, dangerRadiusSq)) {
                        impactWarningDetected = true;
                        float travelProgress = getTravelProgress(fireball.age, ticksToImpact);
                        if (travelProgress >= mostRelevantWarningProgress) {
                            mostRelevantWarningProgress = travelProgress;
                            warningIsWindCharge = fireball instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity;
                        }
                    }
                }
                
                if (data.brokenBlocks != null) {
                    int ticksRemaining = Math.max(0, data.path.size() - 1 - elapsedTicks);
                    int age = fireball.age;
                    int totalTicks = age + ticksRemaining;
                    
                    double progress = totalTicks <= 0 ? 1.0 : (double) age / totalTicks;
                    double mappedProgress = 0.3 + (progress * 0.7);
                    int baseStage = Math.min(9, Math.max(0, (int) (mappedProgress * 10)));
                    
                    int period = Math.max(3, ticksRemaining / 4);
                    boolean isVisible = (age % period) < ((period * 3) / 4);
                    int currentStage = isVisible ? baseStage : -1;
                    
                    if (ModConfig.instance().renderParticleAccents && client.world.random.nextInt(2) == 0 && !data.brokenBlocks.isEmpty()) {
                        int particleCount = 1 + client.world.random.nextInt(3);
                        for (int i = 0; i < particleCount; i++) {
                            net.minecraft.util.math.BlockPos randomPos = data.brokenBlocks.get(client.world.random.nextInt(data.brokenBlocks.size()));
                            if (!client.world.getBlockState(randomPos).isAir()) {
                                double px = randomPos.getX() + client.world.random.nextDouble();
                                double py = randomPos.getY() + 1.1;
                                double pz = randomPos.getZ() + client.world.random.nextDouble();
                                
                                int pType = client.world.random.nextInt(3);
                                net.minecraft.particle.ParticleEffect effect = ParticleTypes.FLAME;
                                if (pType == 1) effect = ParticleTypes.LAVA;
                                else if (pType == 2) effect = ParticleTypes.CAMPFIRE_COSY_SMOKE;
                                
                                client.world.addParticleClient(effect, px, py, pz, 0, 0.05, 0);
                            }
                        }
                    }

                    if (ModConfig.instance().renderBlockHighlights) {
                        for (net.minecraft.util.math.BlockPos pos : data.brokenBlocks) {
                            if (!client.world.getBlockState(pos).isAir()) {
                                newHighlightedBlocks.merge(pos, currentStage, Math::max);
                            }
                        }
                    }
                }
            }

            if (impactWarningDetected) {
                impactWarningProgress = mostRelevantWarningProgress;
                impactWarningIsWindCharge = warningIsWindCharge;
            } else {
                impactWarningProgress = 0.0f;
                impactWarningIsWindCharge = false;
            }

            impactWarningVisible = impactWarningDetected;

            for (Map.Entry<net.minecraft.util.math.BlockPos, Integer> entry : currentlyHighlightedBlocks.entrySet()) {
                net.minecraft.util.math.BlockPos pos = entry.getKey();
                if (!newHighlightedBlocks.containsKey(pos)) {
                    client.world.setBlockBreakingInfo(pos.hashCode(), pos, -1);
                }
            }

            for (Map.Entry<net.minecraft.util.math.BlockPos, Integer> entry : newHighlightedBlocks.entrySet()) {
                net.minecraft.util.math.BlockPos pos = entry.getKey();
                int newStage = entry.getValue();
                int oldStage = currentlyHighlightedBlocks.getOrDefault(pos, -2);
                if (newStage != oldStage) {
                    client.world.setBlockBreakingInfo(pos.hashCode(), pos, newStage);
                }
            }

            currentlyHighlightedBlocks = newHighlightedBlocks;
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            PredictionRenderer.renderImpactWarningBadge(drawContext, MinecraftClient.getInstance(), impactWarningVisible, impactWarningProgress, impactWarningIsWindCharge);
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            if (activePredictions.isEmpty()) return;

            for (Map.Entry<ExplosiveProjectileEntity, TrackedPrediction> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                if (fireball.isAlive()) {
                    PredictionData predictionData = entry.getValue().predictionData;
                    if (predictionData != null) {
                        PredictionRenderer.render(context.matrices(), context.consumers(), net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera(), net.minecraft.client.MinecraftClient.getInstance().world, predictionData, fireball);
                    }
                }
            }
        });
    }

    private void resetWorldState(ClientWorld world) {
        trackedWorld = world;
        activePredictions.clear();
        currentlyHighlightedBlocks.clear();
        com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker.clear();
        com.simonconrad.fireballpredictor.client.network.ClientPowerLookup.resetInferredPower();

        for (Entity entity : world.getEntities()) {
            handleEntityAdded(entity);
        }
    }

    private void handleEntityAdded(Entity entity) {
        if (trackedWorld == null || !entity.isAlive()) {
            return;
        }

        if (entity instanceof ExplosiveProjectileEntity fireball) {
            if (fireball instanceof WitherSkullEntity && !ModConfig.instance().trackWitherSkulls) {
                return;
            }
            if (fireball instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity && !ModConfig.instance().trackWindCharges) {
                return;
            }
            TrackedPrediction trackedPrediction = new TrackedPrediction();
            trackedPrediction.predictionData = TrajectoryPredictor.predict(fireball, trackedWorld);
            trackedPrediction.calculatedPower = com.simonconrad.fireballpredictor.client.network.ClientPowerLookup.getPower(fireball);
            trackedPrediction.calculatedDangerous = fireball instanceof WitherSkullEntity skull && skull.isCharged();
            activePredictions.put(fireball, trackedPrediction);
            
            if (trackedPrediction.predictionData != null) {
                Vec3d hitPos = trackedPrediction.predictionData.hitResult != null ? trackedPrediction.predictionData.hitResult.getPos() : null;
                com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker.registerFireballLocation(fireball, hitPos);
            }
        }
    }

    private void handleEntityRemoved(Entity entity) {
        if (entity instanceof ExplosiveProjectileEntity fireball) {
            com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker.unregisterFireballLocation(fireball);
            activePredictions.remove(fireball);
            ClientPowerCache.POWER_CACHE.remove(fireball.getId());
        }
    }

    private static boolean isDangerousPath(Vec3d playerPosition, Vec3d playerVelocity, java.util.List<Vec3d> path, int elapsedTicks, double dangerRadiusSq) {
        for (int i = elapsedTicks; i < path.size(); i++) {
            Vec3d predictedPlayerPos = playerPosition.add(playerVelocity.multiply(i - elapsedTicks));
            if (path.get(i).squaredDistanceTo(predictedPlayerPos) <= dangerRadiusSq) {
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

        return MathHelper.clamp((float) age / (float) totalTicks, 0.0f, 1.0f);
    }

    private static final class TrackedPrediction {
        private PredictionData predictionData;
        private boolean isCalculating = false;
        private float calculatedPower = -1.0f;
        private boolean calculatedDangerous = false;

        private boolean shouldRefresh(ExplosiveProjectileEntity fireball, ClientWorld world) {
            float currentPower = com.simonconrad.fireballpredictor.client.network.ClientPowerLookup.getPower(fireball);
            if (currentPower != calculatedPower) {
                return true;
            }

            if (fireball instanceof WitherSkullEntity witherSkull) {
                boolean currentDangerous = witherSkull.isCharged();
                if (currentDangerous != calculatedDangerous) {
                    return true;
                }
            }

            if (predictionData == null || predictionData.path == null || predictionData.velocities == null) {
                return true;
            }
            
            // Check if the entity was deflected or velocity/position drifted
            int elapsedTicks = fireball.age - predictionData.predictionAge;
            if (elapsedTicks < 0 || elapsedTicks >= predictionData.path.size()) {
                return true;
            }

            Vec3d expectedPos = predictionData.path.get(elapsedTicks);
            Vec3d expectedVel = predictionData.velocities.get(elapsedTicks);
            Vec3d actualPos = fireball.getEntityPos();
            Vec3d actualVel = fireball.getVelocity();

            double maxPosDevSq = 0.25 * 0.25;
            double maxVelDevSq = 0.05 * 0.05;

            if (actualPos.squaredDistanceTo(expectedPos) > maxPosDevSq || actualVel.squaredDistanceTo(expectedVel) > maxVelDevSq) {
                return true;
            }

            // Check if path is obstructed or block states along it changed
            if (predictionData.hitResult != null && predictionData.hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                net.minecraft.util.hit.BlockHitResult blockHit = (net.minecraft.util.hit.BlockHitResult) predictionData.hitResult;
                net.minecraft.util.math.BlockPos hitPos = blockHit.getBlockPos();
                if (world.getBlockState(hitPos).isAir()) {
                    return true;
                }
            }

            for (int i = elapsedTicks; i < predictionData.path.size() - 1; i++) {
                Vec3d pos = predictionData.path.get(i);
                net.minecraft.util.math.BlockPos blockPos = net.minecraft.util.math.BlockPos.ofFloored(pos.x, pos.y, pos.z);
                if (!world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty()) {
                    return true;
                }
            }

            return false;
        }
    }
}
