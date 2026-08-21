package com.simonconrad.fireballpredictor.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
        if (entity instanceof Blaze) {
            return ProjectileOwner.BLAZE;
        }
        if (entity instanceof Ghast) {
            return ProjectileOwner.GHAST;
        }
        if (entity instanceof EnderDragon) {
            return ProjectileOwner.ENDER_DRAGON;
        }
        if (entity instanceof WitherBoss) {
            return ProjectileOwner.WITHER;
        }
        if (entity instanceof Breeze) {
            return ProjectileOwner.BREEZE;
        }
        if (entity instanceof Player) {
            return ProjectileOwner.PLAYER;
        }
        return ProjectileOwner.UNKNOWN;
    }

    /**
     * Server/client shared: resolve owner from the projectile's native owner
     * reference, then a facing-dispenser adjacency check, else COMMAND.
     */
    public static ProjectileOwner resolveAuthoritative(AbstractHurtingProjectile fireball) {
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

    public static boolean isNearFacingDispenser(AbstractHurtingProjectile fireball) {
        Level level = fireball.level();
        if (level == null) {
            return false;
        }
        return matchDispenserAt(level, fireball.position(), fireball.getDeltaMovement()) != null;
    }

    /**
     * @return dispenser block position if matched, else {@code null}
     */
    public static BlockPos matchDispenserAt(Level world, Vec3 spawn, Vec3 flight) {
        BlockPos centre = BlockPos.containing(spawn);
        boolean hasFlight = flight != null && flight.lengthSqr() > 1.0e-6;
        Vec3 flightDir = hasFlight ? flight.normalize() : null;
        double bestDistSq = DISPENSER_MATCH_DISTANCE * DISPENSER_MATCH_DISTANCE;
        BlockPos best = null;

        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
            BlockState state = world.getBlockState(pos);
            if (!state.is(Blocks.DISPENSER)) {
                continue;
            }

            Direction facing = state.getValue(DispenserBlock.FACING);
            Vec3 dispensePos = Vec3.atCenterOf(pos).add(
                    facing.getStepX() * 0.7,
                    facing.getStepY() * 0.7,
                    facing.getStepZ() * 0.7
            );

            double distSq = dispensePos.distanceToSqr(spawn);
            if (distSq > bestDistSq) {
                continue;
            }

            if (hasFlight) {
                Vec3 faceDir = facing.getUnitVec3();
                if (faceDir.dot(flightDir) < 0.5) {
                    continue;
                }
            }

            bestDistSq = distSq;
            best = pos.immutable();
        }

        return best;
    }
}
