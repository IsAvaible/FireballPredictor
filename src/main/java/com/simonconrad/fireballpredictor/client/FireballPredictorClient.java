package com.simonconrad.fireballpredictor.client;

import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.client.render.PredictionRenderer;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.particle.ParticleTypes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FireballPredictorClient implements ClientModInitializer {

    private final Map<ExplosiveProjectileEntity, PredictionData> activePredictions = new HashMap<>();
    private java.util.Map<net.minecraft.util.math.BlockPos, Integer> currentlyHighlightedBlocks = new java.util.HashMap<>();

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        ClientPowerCache.registerReceivers();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                activePredictions.clear();
                currentlyHighlightedBlocks.clear();
                ClientPowerCache.POWER_CACHE.clear();
                return;
            }

            // Clean up dead fireballs
            Iterator<Map.Entry<ExplosiveProjectileEntity, PredictionData>> it = activePredictions.entrySet().iterator();
            while (it.hasNext()) {
                ExplosiveProjectileEntity fireball = it.next().getKey();
                if (!fireball.isAlive()) {
                    ClientPowerCache.POWER_CACHE.remove(fireball.getId());
                    it.remove();
                }
            }

            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof ExplosiveProjectileEntity fireball) {
                    activePredictions.put(fireball, TrajectoryPredictor.predict(fireball, client.world));
                }
            }

            java.util.Map<net.minecraft.util.math.BlockPos, Integer> newHighlightedBlocks = new java.util.HashMap<>();
            for (Map.Entry<ExplosiveProjectileEntity, PredictionData> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                PredictionData data = entry.getValue();
                
                if (data.brokenBlocks != null) {
                    int ticksRemaining = Math.max(0, data.path.size() - 1);
                    int age = fireball.age;
                    int totalTicks = age + ticksRemaining;
                    
                    double progress = totalTicks <= 0 ? 1.0 : (double) age / totalTicks;
                    double mappedProgress = 0.3 + (progress * 0.7);
                    int baseStage = Math.min(9, Math.max(0, (int) (mappedProgress * 10)));
                    
                    int period = Math.max(2, ticksRemaining / 7);
                    boolean isVisible = (age % period) < (period / 2);
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

        WorldRenderEvents.END_MAIN.register(context -> {
            if (activePredictions.isEmpty()) return;

            for (Map.Entry<ExplosiveProjectileEntity, PredictionData> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                if (fireball.isAlive()) {
                    PredictionRenderer.render(context.matrices(), context.consumers(), net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera(), net.minecraft.client.MinecraftClient.getInstance().world, entry.getValue(), fireball);
                }
            }
        });
    }
}
