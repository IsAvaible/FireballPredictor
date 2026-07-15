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

    private static final int PREDICTION_REFRESH_INTERVAL_TICKS = 1;
    private static FireballPredictorClient INSTANCE;

    private final Map<ExplosiveProjectileEntity, TrackedPrediction> activePredictions = new HashMap<>();
    private java.util.Map<net.minecraft.util.math.BlockPos, Integer> currentlyHighlightedBlocks = new java.util.HashMap<>();
    private boolean impactWarningVisible;
    private float impactWarningProgress;
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
                impactWarningVisible = false;
                impactWarningProgress = 0.0f;
                trackedWorld = null;
                return;
            }

            if (client.world != trackedWorld) {
                resetWorldState(client.world);
            }

            long worldTime = client.world.getTime();

            // Clean up dead fireballs
            Iterator<Map.Entry<ExplosiveProjectileEntity, TrackedPrediction>> it = activePredictions.entrySet().iterator();
            while (it.hasNext()) {
                ExplosiveProjectileEntity fireball = it.next().getKey();
                if (!fireball.isAlive()) {
                    ClientPowerCache.POWER_CACHE.remove(fireball.getId());
                    it.remove();
                }
            }

            for (Map.Entry<ExplosiveProjectileEntity, TrackedPrediction> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                TrackedPrediction trackedPrediction = entry.getValue();

                if (trackedPrediction.shouldRefresh(worldTime)) {
                    trackedPrediction.predictionData = TrajectoryPredictor.predict(fireball, client.world);
                    trackedPrediction.lastPredictionTick = worldTime;
                }
            }

            java.util.Map<net.minecraft.util.math.BlockPos, Integer> newHighlightedBlocks = new java.util.HashMap<>();
            boolean impactWarningDetected = false;
            float mostRelevantWarningProgress = 0.0f;

            ClientPlayerEntity player = client.player;
            Vec3d playerPosition = player != null ? new Vec3d(player.getX(), player.getY(), player.getZ()) : Vec3d.ZERO;
            Vec3d playerVelocity = player != null ? player.getVelocity() : Vec3d.ZERO;

            for (Map.Entry<ExplosiveProjectileEntity, TrackedPrediction> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                PredictionData data = entry.getValue().predictionData;

                if (data == null) {
                    continue;
                }

                if (player != null && data.hitResult != null && data.path != null && data.path.size() > 1) {
                    int ticksToImpact = data.path.size() - 1;
                    float power = ClientPowerCache.POWER_CACHE.getOrDefault(fireball.getId(), fireball instanceof net.minecraft.entity.projectile.FireballEntity ? ModConfig.instance().clientFallbackFireballPower : 1.0f);
                    double dangerRadius = power * 2.0f * 2.0f;
                    double dangerRadiusSq = dangerRadius * dangerRadius;

                    if (isDangerousPath(playerPosition, playerVelocity, data.path, dangerRadiusSq)) {
                        impactWarningDetected = true;
                        float travelProgress = getTravelProgress(fireball.age, ticksToImpact);
                        mostRelevantWarningProgress = Math.max(mostRelevantWarningProgress, travelProgress);
                    }
                }
                
                if (data.brokenBlocks != null) {
                    int ticksRemaining = Math.max(0, data.path.size() - 1);
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
            } else {
                impactWarningProgress = 0.0f;
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
            PredictionRenderer.renderImpactWarningBadge(drawContext, MinecraftClient.getInstance(), impactWarningVisible, impactWarningProgress);
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

        for (Entity entity : world.getEntities()) {
            handleEntityAdded(entity);
        }
    }

    private void handleEntityAdded(Entity entity) {
        if (trackedWorld == null || !entity.isAlive()) {
            return;
        }

        if (entity instanceof ExplosiveProjectileEntity fireball) {
            TrackedPrediction trackedPrediction = new TrackedPrediction();
            trackedPrediction.predictionData = TrajectoryPredictor.predict(fireball, trackedWorld);
            trackedPrediction.lastPredictionTick = trackedWorld.getTime();
            activePredictions.put(fireball, trackedPrediction);
        }
    }

    private void handleEntityRemoved(Entity entity) {
        if (entity instanceof ExplosiveProjectileEntity fireball) {
            activePredictions.remove(fireball);
        }
    }

    private static boolean isDangerousPath(Vec3d playerPosition, Vec3d playerVelocity, java.util.List<Vec3d> path, double dangerRadiusSq) {
        for (int i = 0; i < path.size(); i++) {
            Vec3d predictedPlayerPos = playerPosition.add(playerVelocity.multiply(i));
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
        private long lastPredictionTick = Long.MIN_VALUE;

        private boolean shouldRefresh(long worldTime) {
            return predictionData == null || lastPredictionTick == Long.MIN_VALUE || worldTime - lastPredictionTick >= PREDICTION_REFRESH_INTERVAL_TICKS;
        }
    }
}
