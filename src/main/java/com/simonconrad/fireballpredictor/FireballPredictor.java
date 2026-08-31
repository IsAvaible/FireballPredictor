package com.simonconrad.fireballpredictor;

import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import com.simonconrad.fireballpredictor.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Fireball Predictor - 1.8.9 Forge backport.
 *
 * Client-side mod that predicts and visualizes the trajectory, impact point,
 * explosion radius, broken blocks and resulting damage of fireballs, small
 * fireballs and wither skulls.
 *
 * Backport of IsAvaible's Fireball Predictor (originally a Fabric mod for 26.2):
 * https://github.com/IsAvaible/FireballPredictor
 * The original is licensed LGPL-3.0; this backport keeps that license.
 */
@Mod(modid = FireballPredictor.MODID, name = FireballPredictor.NAME, version = FireballPredictor.VERSION, acceptedMinecraftVersions = "[1.8.9]")
public class FireballPredictor {

    public static final String MODID = "fireballpredictor";
    public static final String NAME = "Fireball Predictor";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());

        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new FireballPredictorClient());
        }
    }
}
