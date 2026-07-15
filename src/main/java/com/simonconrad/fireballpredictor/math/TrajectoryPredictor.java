package com.simonconrad.fireballpredictor.math;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.entity.projectile.ProjectileUtil;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class TrajectoryPredictor {

    public static PredictionData predict(ExplosiveProjectileEntity fireball) {
        World world = fireball.getWorld();
        Vec3d currentPos = fireball.getPos();
        Vec3d initialVelocity = fireball.getVelocity();
        Vec3d velocity = initialVelocity;
        
        double accelerationPower = fireball.accelerationPower;
        Vec3d acceleration = velocity.normalize().multiply(accelerationPower);
        
        int maxTicks = 200;
        List<Vec3d> path = new ArrayList<>();
        path.add(currentPos);
        
        HitResult finalHit = null;
        
        for (int i = 0; i < maxTicks; i++) {
            Vec3d nextPos = currentPos.add(velocity);
            
            // Raycast for blocks
            HitResult hitResult = world.raycast(new RaycastContext(
                currentPos, 
                nextPos, 
                RaycastContext.ShapeType.COLLIDER, 
                RaycastContext.FluidHandling.NONE, 
                fireball
            ));
            
            if (hitResult.getType() != HitResult.Type.MISS) {
                nextPos = hitResult.getPos();
            }
            
            // Raycast for entities
            Box box = fireball.getBoundingBox().stretch(velocity).expand(1.0);
            EntityHitResult entityHitResult = ProjectileUtil.getEntityCollision(
                world, fireball, currentPos, nextPos, box, entity -> !entity.isSpectator() && entity.canHit()
            );
            
            if (entityHitResult != null) {
                hitResult = entityHitResult;
            }
            
            if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
                path.add(hitResult.getPos());
                finalHit = hitResult;
                break;
            }
            
            currentPos = nextPos;
            path.add(currentPos);
            
            // Add acceleration to velocity and apply drag (usually 0.95)
            velocity = velocity.add(acceleration).multiply(0.95);
        }
        
        List<BlockPos> brokenBlocks = new ArrayList<>();
        if (finalHit != null) {
            brokenBlocks = ImpactPredictor.predictBrokenBlocks(fireball, finalHit);
        }
        
        return new PredictionData(path, finalHit, brokenBlocks, initialVelocity);
    }
}
