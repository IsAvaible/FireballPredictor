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

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                activePredictions.clear();
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
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.world() == null || activePredictions.isEmpty()) return;

            for (Map.Entry<ExplosiveProjectileEntity, PredictionData> entry : activePredictions.entrySet()) {
                ExplosiveProjectileEntity fireball = entry.getKey();
                if (fireball.isAlive()) {
                    PredictionRenderer.render(context.matrixStack(), context.consumers(), context.camera(), entry.getValue());
                }
            }
        });
    }
}
