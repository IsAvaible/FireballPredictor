package com.simonconrad.fireballpredictor.client;

import com.simonconrad.fireballpredictor.client.render.PredictionRenderer;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FireballPredictorClient implements ClientModInitializer {

    private final Map<ExplosiveProjectileEntity, PredictionData> activePredictions = new HashMap<>();
    private java.util.Set<net.minecraft.util.math.BlockPos> currentlyHighlightedBlocks = new java.util.HashSet<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                activePredictions.clear();
                currentlyHighlightedBlocks.clear();
                return;
            }

            // Clean up dead fireballs
            Iterator<Map.Entry<ExplosiveProjectileEntity, PredictionData>> it = activePredictions.entrySet().iterator();
            while (it.hasNext()) {
                if (!it.next().getKey().isAlive()) {
                    it.remove();
                }
            }

            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof ExplosiveProjectileEntity fireball) {
                    activePredictions.put(fireball, TrajectoryPredictor.predict(fireball));
                }
            }

            java.util.Set<net.minecraft.util.math.BlockPos> newHighlightedBlocks = new java.util.HashSet<>();
            for (PredictionData data : activePredictions.values()) {
                if (data.brokenBlocks != null) {
                    for (net.minecraft.util.math.BlockPos pos : data.brokenBlocks) {
                        if (!client.world.getBlockState(pos).isAir()) {
                            newHighlightedBlocks.add(pos);
                        }
                    }
                }
            }

            for (net.minecraft.util.math.BlockPos pos : currentlyHighlightedBlocks) {
                if (!newHighlightedBlocks.contains(pos)) {
                    client.world.setBlockBreakingInfo(pos.hashCode(), pos, -1);
                }
            }

            for (net.minecraft.util.math.BlockPos pos : newHighlightedBlocks) {
                if (!currentlyHighlightedBlocks.contains(pos)) {
                    client.world.setBlockBreakingInfo(pos.hashCode(), pos, 8);
                }
            }

            currentlyHighlightedBlocks = newHighlightedBlocks;
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.world() == null || activePredictions.isEmpty()) return;

            for (Map.Entry<ExplosiveProjectileEntity, PredictionData> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                if (fireball.isAlive()) {
                    PredictionRenderer.render(context.matrixStack(), context.consumers(), context.camera(), context.world(), entry.getValue());
                }
            }
        });
    }
}
