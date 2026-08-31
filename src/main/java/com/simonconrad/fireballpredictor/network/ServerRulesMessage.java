package com.simonconrad.fireballpredictor.network;

import com.simonconrad.fireballpredictor.client.network.ClientFireballSync;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server -&gt; client push of server-wide restrictions that the vanilla protocol never
 * tells the client about:
 *
 * <ul>
 *   <li>The disabled-owner bitmask from the server config ("fair play gamerules",
 *       see {@link com.simonconrad.fireballpredictor.config.ServerConfig}).</li>
 *   <li>The {@code mobGriefing} gamerule: when off, fireball explosions do not destroy
 *       blocks in 1.8.9 ({@code EntityLargeFireball.onImpact} passes it as the
 *       {@code smoking} flag of {@code World.newExplosion}), so the client must not
 *       predict block destruction or render crack highlights.</li>
 * </ul>
 *
 * <p>Sent to each player on join, re-broadcast after {@code /fireballpredictor reload}
 * and after any {@code /gamerule} command. A mask of {@code 0} lifts all restrictions.
 */
public class ServerRulesMessage implements IMessage {

    /** Bitmask of {@link com.simonconrad.fireballpredictor.tracking.TrackingRules} bits. */
    public int disabledOwnerMask;
    /** Value of the {@code mobGriefing} gamerule in the sender's world. */
    public boolean mobGriefing;

    public ServerRulesMessage() {
    }

    public ServerRulesMessage(int disabledOwnerMask, boolean mobGriefing) {
        this.disabledOwnerMask = disabledOwnerMask;
        this.mobGriefing = mobGriefing;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(disabledOwnerMask);
        buf.writeBoolean(mobGriefing);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        disabledOwnerMask = buf.readInt();
        mobGriefing = buf.readBoolean();
    }

    /** Client-side handler; delegates to the rules store on the client thread. */
    public static class Handler implements IMessageHandler<ServerRulesMessage, IMessage> {
        @Override
        public IMessage onMessage(final ServerRulesMessage message, final MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ClientFireballSync.applyServerRules(message.disabledOwnerMask, message.mobGriefing);
                }
            });
            return null;
        }
    }
}
