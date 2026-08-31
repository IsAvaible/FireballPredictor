package com.simonconrad.fireballpredictor;

import com.simonconrad.fireballpredictor.client.ClientCommandFireballPredictor;
import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import com.simonconrad.fireballpredictor.client.ModKeyBindings;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.network.FireballSyncMessage;
import com.simonconrad.fireballpredictor.network.ServerRulesMessage;
import com.simonconrad.fireballpredictor.server.CommandFireballPredictor;
import com.simonconrad.fireballpredictor.server.FireballPredictorServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fireball Predictor - 1.8.9 Forge backport.
 *
 * Client-side mod that predicts and visualizes the trajectory, impact point,
 * explosion radius, broken blocks and resulting damage of fireballs, small
 * fireballs and wither skulls. When installed on a server as well, it
 * additionally syncs explosion power, projectile velocity/acceleration,
 * owner classification, tracking restrictions and the mobGriefing gamerule
 * to its clients (see {@link FireballPredictorServer}).
 *
 * Backport of IsAvaible's Fireball Predictor (originally a Fabric mod for 26.2):
 * https://github.com/IsAvaible/FireballPredictor
 * The original is licensed LGPL-3.0; this backport keeps that license.
 */
@Mod(
        modid = FireballPredictor.MODID,
        name = FireballPredictor.NAME,
        version = FireballPredictor.VERSION,
        acceptedMinecraftVersions = "[1.8.9]",
        guiFactory = FireballPredictor.GUI_FACTORY
)
public class FireballPredictor {

    public static final String MODID = "fireballpredictor";
    public static final String NAME = "Fireball Predictor";
    public static final String VERSION = "1.0.0";
    public static final String GUI_FACTORY = "com.simonconrad.fireballpredictor.client.gui.ModConfigGuiFactory";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /** FML simple channel used for all server -> client sync traffic. */
    public static SimpleNetworkWrapper network;

    /** Dedicated-server stand-in handler (these messages are only ever handled on clients). */
    private static final net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler<
            net.minecraftforge.fml.common.network.simpleimpl.IMessage,
            net.minecraftforge.fml.common.network.simpleimpl.IMessage> NOOP_HANDLER =
            new net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler<
                    net.minecraftforge.fml.common.network.simpleimpl.IMessage,
                    net.minecraftforge.fml.common.network.simpleimpl.IMessage>() {
                @Override
                public net.minecraftforge.fml.common.network.simpleimpl.IMessage onMessage(
                        net.minecraftforge.fml.common.network.simpleimpl.IMessage message,
                        net.minecraftforge.fml.common.network.simpleimpl.MessageContext ctx) {
                    return null;
                }
            };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Fireball Predictor {} loading", VERSION);

        ModConfig.load(event.getSuggestedConfigurationFile());

        // Server-side fair-play switches (config/fireballpredictor-server.json); loads on
        // dedicated servers without touching client-only classes.
        ServerConfig.init(event.getModConfigurationDirectory());
        ServerConfig.load();

        // Custom channel for power / velocity / acceleration / owner / rules syncing.
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        // FML instantiates handler classes on BOTH sides, so the real handlers (which
        // reference client-only classes) are only registered in the client process; the
        // dedicated server registers no-op handlers (it only ever SENDS these messages).
        if (event.getSide().isClient()) {
            network.registerMessage(FireballSyncMessage.Handler.class, FireballSyncMessage.class, 0, Side.CLIENT);
            network.registerMessage(ServerRulesMessage.Handler.class, ServerRulesMessage.class, 1, Side.CLIENT);
        } else {
            network.registerMessage(NOOP_HANDLER, FireballSyncMessage.class, 0, Side.CLIENT);
            network.registerMessage(NOOP_HANDLER, ServerRulesMessage.class, 1, Side.CLIENT);
        }

        FireballPredictorServer serverHandler = new FireballPredictorServer();
        MinecraftForge.EVENT_BUS.register(serverHandler);
        FMLCommonHandler.instance().bus().register(serverHandler);

        if (event.getSide().isClient()) {
            ModKeyBindings.register();
            MinecraftForge.EVENT_BUS.register(new FireballPredictorClient());
            net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(
                    new ClientCommandFireballPredictor());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandFireballPredictor());
    }
}
