package com.simonconrad.fireballpredictor.client;

import com.simonconrad.fireballpredictor.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraft.util.ChatComponentTranslation;
import org.lwjgl.input.Keyboard;

/**
 * Keybindings (both unbound by default, like master):
 *
 * <ul>
 *   <li><b>Open config</b> - opens the in-game configuration screen
 *       (also reachable via {@code /fireballpredictor} or the Mods list).</li>
 *   <li><b>Toggle tracking</b> - master switch for projectile tracking with
 *       chat feedback (port of master's ModKeyBindings).</li>
 * </ul>
 */
public final class ModKeyBindings {

    public static final String CATEGORY = "key.categories.fireballpredictor";

    public static final KeyBinding OPEN_CONFIG = new KeyBinding(
            "key.fireballpredictor.config", Keyboard.KEY_NONE, CATEGORY);

    public static final KeyBinding TOGGLE_TRACKING = new KeyBinding(
            "key.fireballpredictor.toggle_tracking", Keyboard.KEY_NONE, CATEGORY);

    private ModKeyBindings() {
    }

    /** Registers the keybindings (client pre-init). */
    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_CONFIG);
        ClientRegistry.registerKeyBinding(TOGGLE_TRACKING);
    }

    /** Consumes key presses; called every client tick. */
    public static void handleInput(Minecraft mc) {
        while (OPEN_CONFIG.isPressed()) {
            mc.displayGuiScreen(new com.simonconrad.fireballpredictor.client.gui.ModConfigGui(
                    mc.currentScreen));
        }
        while (TOGGLE_TRACKING.isPressed()) {
            ModConfig.masterEnabled = !ModConfig.masterEnabled;
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatComponentMessage(new ChatComponentTranslation(
                        ModConfig.masterEnabled
                                ? "fireballpredictor.message.tracking_enabled"
                                : "fireballpredictor.message.tracking_disabled"));
            }
        }
    }
}
