package com.simonconrad.fireballpredictor.network;

import com.simonconrad.fireballpredictor.client.network.ClientFireballSync;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server -&gt; client sync of everything about a fireball the vanilla 1.8.9 protocol
 * does not transmit (or transmits lossy):
 *
 * <ul>
 *   <li><b>Power</b> - {@code EntityLargeFireball.explosionPower} (the summon NBT
 *       {@code ExplosionPower}) is a plain int on the client, never synced; the client
 *       always assumes 1.</li>
 *   <li><b>Velocity</b> - fireball tracker entries are registered with
 *       {@code sendVelocityUpdates = false} in 1.8.9, so after the spawn packet the
 *       client must guess motion from position deltas (and command-summoned fireballs
 *       without a shooter do not even get the spawn-packet velocity applied).</li>
 *   <li><b>Acceleration</b> - the spawn packet sends the acceleration as the "speed"
 *       payload, but the client constructor normalizes it to a length of 0.1, which is
 *       wrong for {@code /summon} NBT power values of other magnitudes.</li>
 *   <li><b>Owner</b> - authoritative owner classification for the tracking filters.</li>
 * </ul>
 *
 * <p>Sent when a player starts tracking a fireball ({@code PlayerEvent.StartTracking})
 * and refreshed whenever the projectile crosses a chunk boundary.
 */
public class FireballSyncMessage implements IMessage {

    public int entityId;
    /** Explosion power, or a negative value when not statically known on the server. */
    public float power;
    /** {@link com.simonconrad.fireballpredictor.tracking.ProjectileOwner} ordinal. */
    public int ownerOrdinal;
    /** Owner entity id, or -1 when none / not tracked. */
    public int ownerEntityId;
    public double motionX, motionY, motionZ;
    public double accelX, accelY, accelZ;

    public FireballSyncMessage() {
    }

    public FireballSyncMessage(int entityId, float power, int ownerOrdinal, int ownerEntityId,
                               double motionX, double motionY, double motionZ,
                               double accelX, double accelY, double accelZ) {
        this.entityId = entityId;
        this.power = power;
        this.ownerOrdinal = ownerOrdinal;
        this.ownerEntityId = ownerEntityId;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeFloat(power);
        buf.writeByte(ownerOrdinal);
        buf.writeInt(ownerEntityId);
        buf.writeDouble(motionX);
        buf.writeDouble(motionY);
        buf.writeDouble(motionZ);
        buf.writeDouble(accelX);
        buf.writeDouble(accelY);
        buf.writeDouble(accelZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        power = buf.readFloat();
        ownerOrdinal = buf.readByte();
        ownerEntityId = buf.readInt();
        motionX = buf.readDouble();
        motionY = buf.readDouble();
        motionZ = buf.readDouble();
        accelX = buf.readDouble();
        accelY = buf.readDouble();
        accelZ = buf.readDouble();
    }

    /** Client-side handler; delegates to the sync cache on the client thread. */
    public static class Handler implements IMessageHandler<FireballSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(final FireballSyncMessage message, final MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ClientFireballSync.applyFireballSync(message);
                }
            });
            return null;
        }
    }
}
