package com.simonconrad.fireballpredictor.tracking;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;

/**
 * Side-agnostic owner classification helpers shared by the server sync path
 * and the client inference engine.
 */
public final class OwnerClassifier {

    public static final double DISPENSER_MATCH_DISTANCE = 1.75;

    private OwnerClassifier() {
    }

    public static ProjectileOwner classifyEntity(Entity entity) {
        if (entity == null) {
            return ProjectileOwner.UNKNOWN;
        }
        if (entity instanceof BlazeEntity) {
            return ProjectileOwner.BLAZE;
        }
        if (entity instanceof GhastEntity) {
            return ProjectileOwner.GHAST;
        }
        if (entity instanceof EnderDragonEntity) {
            return ProjectileOwner.ENDER_DRAGON;
        }
        if (entity instanceof WitherEntity) {
            return ProjectileOwner.WITHER;
        }
        if (entity instanceof PlayerEntity) {
            return ProjectileOwner.PLAYER;
        }
        return ProjectileOwner.UNKNOWN;
    }

    /**
     * Server/client shared: resolve owner from the projectile's native owner
     * reference, then a facing-dispenser adjacency check, else COMMAND.
     */
    public static ProjectileOwner resolveAuthoritative(ExplosiveProjectileEntity fireball) {
        Entity owner = fireball.getOwner();
        if (owner != null) {
            ProjectileOwner classified = classifyEntity(owner);
            if (classified != ProjectileOwner.UNKNOWN) {
                return classified;
            }
        }
        if (isNearFacingDispenser(fireball)) {
            return ProjectileOwner.DISPENSER;
        }
        return ProjectileOwner.COMMAND;
    }

    public static boolean isNearFacingDispenser(ExplosiveProjectileEntity fireball) {
        World level = fireball.getEntityWorld();
        if (level == null) {
            return false;
        }
        return matchDispenserAt(level, fireball.getEntityPos(), fireball.getVelocity()) != null;
    }

    /**
     * @return dispenser block position if matched, else {@code null}
     */
    public static BlockPos matchDispenserAt(World world, Vec3d spawn, Vec3d flight) {
        BlockPos centre = BlockPos.ofFloored(spawn);
        boolean hasFlight = flight != null && flight.lengthSquared() > 1.0e-6;
        Vec3d flightDir = hasFlight ? flight.normalize() : null;
        double bestDistSq = DISPENSER_MATCH_DISTANCE * DISPENSER_MATCH_DISTANCE;
        BlockPos best = null;

        for (BlockPos pos : BlockPos.iterate(centre.add(-2, -2, -2), centre.add(2, 2, 2))) {
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(Blocks.DISPENSER)) {
                continue;
            }

            Direction facing = state.get(DispenserBlock.FACING);
            Vec3d dispensePos = Vec3d.ofCenter(pos).add(
                    facing.getOffsetX() * 0.7,
                    facing.getOffsetY() * 0.7,
                    facing.getOffsetZ() * 0.7
            );

            double distSq = dispensePos.squaredDistanceTo(spawn);
            if (distSq > bestDistSq) {
                continue;
            }

            if (hasFlight) {
                Vec3d faceDir = new Vec3d(facing.getUnitVector().x, facing.getUnitVector().y, facing.getUnitVector().z);
                if (faceDir.dotProduct(flightDir) < 0.5) {
                    continue;
                }
            }

            bestDistSq = distSq;
            best = pos.toImmutable();
        }

        return best;
    }
}
