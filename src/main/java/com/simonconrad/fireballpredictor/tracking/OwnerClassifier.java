package com.simonconrad.fireballpredictor.tracking;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Side-agnostic owner classification helpers shared by the server sync path
 * and the client inference (1.8.9 port of master's OwnerClassifier; no breeze,
 * ender dragon or wind charges exist in this version).
 */
public final class OwnerClassifier {

    /** Maximum distance between the fireball spawn and a dispenser's dispense point. */
    public static final double DISPENSER_MATCH_DISTANCE = 1.75;

    /** Client inference: search radius around a fresh projectile for candidate shooters. */
    public static final double SWEEP_RADIUS = 6.0;

    /** Client inference: minimum look-vector / to-projectile alignment (cos, ~25 degree cone). */
    public static final double MIN_LOOK_DOT = 0.90;

    private OwnerClassifier() {
    }

    public static ProjectileOwner classifyEntity(Entity entity) {
        if (entity == null) {
            return ProjectileOwner.UNKNOWN;
        }
        if (entity instanceof EntityBlaze) {
            return ProjectileOwner.BLAZE;
        }
        if (entity instanceof EntityGhast) {
            return ProjectileOwner.GHAST;
        }
        if (entity instanceof EntityWither) {
            return ProjectileOwner.WITHER;
        }
        if (entity instanceof EntityPlayer) {
            return ProjectileOwner.PLAYER;
        }
        return ProjectileOwner.UNKNOWN;
    }

    /**
     * Server/client shared: resolve owner from the projectile's native owner reference
     * (server-side {@code shootingEntity}; null on clients in multiplayer), then a
     * facing-dispenser adjacency check, else COMMAND.
     */
    public static ProjectileOwner resolveAuthoritative(EntityFireball fireball) {
        Entity owner = fireball.shootingEntity;
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

    public static boolean isNearFacingDispenser(EntityFireball fireball) {
        World world = fireball.worldObj;
        if (world == null) {
            return false;
        }
        return matchDispenserAt(world,
                new Vec3(fireball.posX, fireball.posY, fireball.posZ),
                new Vec3(fireball.motionX, fireball.motionY, fireball.motionZ)) != null;
    }

    /**
     * Finds a dispenser whose dispense point (block centre + 0.7 along the facing)
     * lies within {@link #DISPENSER_MATCH_DISTANCE} of the spawn position and whose
     * facing aligns with the projectile's flight direction (dot &gt;= 0.5).
     *
     * @return dispenser block position if matched, else {@code null}
     */
    public static BlockPos matchDispenserAt(World world, Vec3 spawn, Vec3 flight) {
        BlockPos centre = new BlockPos(spawn.xCoord, spawn.yCoord, spawn.zCoord);
        boolean hasFlight = flight != null
                && flight.lengthVector() > 1.0E-4;
        Vec3 flightDir = null;
        if (hasFlight) {
            double len = flight.lengthVector();
            flightDir = new Vec3(flight.xCoord / len, flight.yCoord / len, flight.zCoord / len);
        }
        double bestDistSq = DISPENSER_MATCH_DISTANCE * DISPENSER_MATCH_DISTANCE;
        BlockPos best = null;

        BlockPos from = centre.add(-2, -2, -2);
        BlockPos to = centre.add(2, 2, 2);
        for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(from, to)) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() != Blocks.dispenser) {
                continue;
            }
            EnumFacing facing = state.getValue(net.minecraft.block.BlockDispenser.FACING);

            double dispenseX = pos.getX() + 0.5 + facing.getFrontOffsetX() * 0.7;
            double dispenseY = pos.getY() + 0.5 + facing.getFrontOffsetY() * 0.7;
            double dispenseZ = pos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.7;

            double ddx = spawn.xCoord - dispenseX;
            double ddy = spawn.yCoord - dispenseY;
            double ddz = spawn.zCoord - dispenseZ;
            double distSq = ddx * ddx + ddy * ddy + ddz * ddz;
            if (distSq > bestDistSq) {
                continue;
            }

            if (flightDir != null) {
                double dot = facing.getFrontOffsetX() * flightDir.xCoord
                        + facing.getFrontOffsetY() * flightDir.yCoord
                        + facing.getFrontOffsetZ() * flightDir.zCoord;
                if (dot < 0.5) {
                    continue;
                }
            }

            bestDistSq = distSq;
            best = new BlockPos(pos);
        }

        return best;
    }

    /**
     * Client-side environmental inference (no mod on the server): finds the nearest
     * capable shooter (blaze / ghast / wither) within {@link #SWEEP_RADIUS} of the
     * projectile whose look vector points at the projectile ({@link #MIN_LOOK_DOT}
     * alignment), mirroring master's OwnerInferenceEngine sweep.
     *
     * @return the classified owner of the best-matching shooter, or {@code null}
     */
    public static ProjectileOwner sweepEnvironment(EntityFireball fireball, World world) {
        if (fireball == null || world == null) {
            return null;
        }
        Vec3 origin = new Vec3(fireball.posX, fireball.posY, fireball.posZ);

        ProjectileOwner best = null;
        double bestDistSq = SWEEP_RADIUS * SWEEP_RADIUS;

        for (Object raw : world.loadedEntityList) {
            if (!(raw instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) raw;
            ProjectileOwner owner = classifyEntity(entity);
            if (!owner.isMob()) {
                continue;
            }
            double ddx = origin.xCoord - entity.posX;
            double ddy = origin.yCoord - entity.posY;
            double ddz = origin.zCoord - entity.posZ;
            double distSq = ddx * ddx + ddy * ddy + ddz * ddz;
            if (distSq > bestDistSq) {
                continue;
            }
            Vec3 toProjectile = new Vec3(
                    fireball.posX - entity.posX,
                    fireball.posY + fireball.getEyeHeight() - entity.posY - entity.getEyeHeight(),
                    fireball.posZ - entity.posZ);
            double len = toProjectile.lengthVector();
            if (len < 1.0E-4) {
                continue;
            }
            Vec3 look = entity.getLookVec();
            double dot = (toProjectile.xCoord / len) * look.xCoord
                    + (toProjectile.yCoord / len) * look.yCoord
                    + (toProjectile.zCoord / len) * look.zCoord;
            if (dot < MIN_LOOK_DOT) {
                continue;
            }
            bestDistSq = distSq;
            best = owner;
        }
        return best;
    }
}
