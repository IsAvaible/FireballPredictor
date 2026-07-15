package com.simonconrad.fireballpredictor;

import com.simonconrad.fireballpredictor.math.ImpactPredictor;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FireballPredictor implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final String MOD_ID = "fireballpredictor";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world from FireballPredictor!");

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ExplosiveProjectileEntity fireball) {
				HitResult predictedHit = TrajectoryPredictor.predict(fireball);
				if (predictedHit != null && predictedHit.getType() != HitResult.Type.MISS) {
					Vec3d pos = predictedHit.getPos();
					LOGGER.info("Fireball spawned! Predicted impact at: X={}, Y={}, Z={}", 
						String.format("%.2f", pos.x), 
						String.format("%.2f", pos.y), 
						String.format("%.2f", pos.z));
						
					// Predict which blocks will be broken by the explosion
					List<BlockPos> brokenBlocks = ImpactPredictor.predictBrokenBlocks(fireball, predictedHit);
					LOGGER.info("Predicted to break {} blocks upon impact.", brokenBlocks.size());
					
					// Optional: Log the first few block positions for debugging
					if (!brokenBlocks.isEmpty()) {
					    int displayCount = Math.min(brokenBlocks.size(), 5);
					    StringBuilder sb = new StringBuilder();
					    for (int i = 0; i < displayCount; i++) {
					        BlockPos bp = brokenBlocks.get(i);
					        sb.append(String.format("[%d, %d, %d] ", bp.getX(), bp.getY(), bp.getZ()));
					    }
					    if (brokenBlocks.size() > displayCount) {
					        sb.append("...");
					    }
					    LOGGER.info("Sample broken blocks: {}", sb.toString());
					}
					
				} else {
					LOGGER.info("Fireball spawned! No impact predicted within simulation limit.");
				}
			}
		});
	}
}
