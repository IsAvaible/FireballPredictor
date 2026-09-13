package com.simonconrad.fireballpredictor.client.render;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages dynamically synthesized 9x9 pixel-art health bar cracking overlays that adapt
 * to the exact silhouette of whatever heart or health icon is loaded by the active resource pack.
 *
 * <p>Preserves authentic 9x9 Minecraft pixel art, the exact fissure and ember color palette,
 * and 1-to-1 pixel sizing.
 */
public final class DynamicHeartOverlayManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("FireballPredictor/HeartOverlay");

    public static final Identifier DYNAMIC_FULL =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_full");
    public static final Identifier DYNAMIC_FULL_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_full_blinking");
    public static final Identifier DYNAMIC_HALF =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_half");
    public static final Identifier DYNAMIC_HALF_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_half_blinking");
    public static final Identifier DYNAMIC_HALF_RIGHT =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_half_right");
    public static final Identifier DYNAMIC_HALF_RIGHT_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_half_right_blinking");

    private static final Identifier MASTER_FULL_ID =
            Identifier.fromNamespaceAndPath("fireballpredictor", "textures/gui/sprites/hud/heart/cracking_master_full.png");
    private static final Identifier MASTER_BLINK_ID =
            Identifier.fromNamespaceAndPath("fireballpredictor", "textures/gui/sprites/hud/heart/cracking_master_full_blinking.png");
    private static final Identifier VANILLA_HEART_ID =
            Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/full.png");

    private static final DynamicTexture[] DYNAMIC_TEXTURES = new DynamicTexture[6];
    private static volatile boolean initialized = false;

    private DynamicHeartOverlayManager() {
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Resolves the active overlay texture identifier. Returns the dynamic silhouette-adapted
     * texture if initialized, or falls back to static sprite identifier.
     */
    public static Identifier getOverlayTexture(boolean leftLost, boolean rightLost, boolean blinking) {
        if (initialized) {
            if (leftLost && rightLost) {
                return blinking ? DYNAMIC_FULL_BLINKING : DYNAMIC_FULL;
            } else if (leftLost) {
                return blinking ? DYNAMIC_HALF_BLINKING : DYNAMIC_HALF;
            } else if (rightLost) {
                return blinking ? DYNAMIC_HALF_RIGHT_BLINKING : DYNAMIC_HALF_RIGHT;
            }
            return null;
        }
        return HeartOverlayRenderer.getOverlaySprite(leftLost, rightLost, blinking);
    }

    /**
     * Synthesizes dynamic textures by sampling the active heart silhouette mask and
     * applying it to the 9x9 master pixel-art crack templates.
     */
    public static void reload(ResourceManager resourceManager) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        TextureManager textureManager = client.getTextureManager();
        if (textureManager == null || resourceManager == null) {
            return;
        }

        try {
            // 1. Load active heart sprite to determine silhouette
            boolean[][] mask = extractHeartMask(resourceManager);

            // 2. Load master 9x9 pixel art templates
            NativeImage masterFull = loadNativeImage(resourceManager, MASTER_FULL_ID,
                    "/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_full.png");
            NativeImage masterBlink = loadNativeImage(resourceManager, MASTER_BLINK_ID,
                    "/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_full_blinking.png");

            if (masterFull == null || masterBlink == null) {
                LOGGER.warn("Failed to load master pixel-art cracking textures; using static fallback.");
                return;
            }

            try {
                // 3. Synthesize 6 variants
                NativeImage imgFull = synthesize(masterFull, mask, true, true);
                NativeImage imgFullBlink = synthesize(masterBlink, mask, true, true);
                NativeImage imgHalfLeft = synthesize(masterFull, mask, true, false);
                NativeImage imgHalfLeftBlink = synthesize(masterBlink, mask, true, false);
                NativeImage imgHalfRight = synthesize(masterFull, mask, false, true);
                NativeImage imgHalfRightBlink = synthesize(masterBlink, mask, false, true);

                // 4. Close old dynamic textures if any
                closeDynamicTextures();

                // 5. Register with TextureManager
                registerTexture(textureManager, 0, DYNAMIC_FULL, imgFull);
                registerTexture(textureManager, 1, DYNAMIC_FULL_BLINKING, imgFullBlink);
                registerTexture(textureManager, 2, DYNAMIC_HALF, imgHalfLeft);
                registerTexture(textureManager, 3, DYNAMIC_HALF_BLINKING, imgHalfLeftBlink);
                registerTexture(textureManager, 4, DYNAMIC_HALF_RIGHT, imgHalfRight);
                registerTexture(textureManager, 5, DYNAMIC_HALF_RIGHT_BLINKING, imgHalfRightBlink);

                initialized = true;
                LOGGER.info("Successfully synthesized silhouette-adaptive 9x9 pixel-art heart damage overlays.");
            } finally {
                masterFull.close();
                masterBlink.close();
            }
        } catch (Exception e) {
            LOGGER.error("Error synthesizing dynamic heart overlays", e);
            initialized = false;
        }
    }

    /**
     * Synthesizes a 9x9 masked overlay image.
     *
     * @param master the 9x9 master pixel-art texture
     * @param mask 9x9 boolean mask where true indicates an active pixel in the icon silhouette
     * @param includeLeft whether left-half units (x <= 4) are lost/cracked
     * @param includeRight whether right-half units (x >= 5) are lost/cracked
     * @return a new 9x9 NativeImage containing the masked pixels
     */
    public static NativeImage synthesize(NativeImage master, boolean[][] mask, boolean includeLeft, boolean includeRight) {
        NativeImage result = new NativeImage(9, 9, false);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                boolean halfAllowed = (x <= 4 && includeLeft) || (x >= 5 && includeRight);
                if (halfAllowed && mask[y][x]) {
                    result.setPixel(x, y, master.getPixel(x, y));
                } else {
                    result.setPixel(x, y, 0); // alpha 0
                }
            }
        }
        return result;
    }

    /**
     * Extracts a 9x9 boolean silhouette mask from the active heart icon sprite in the resource pack.
     */
    public static boolean[][] extractHeartMask(ResourceManager resourceManager) {
        boolean[][] mask = new boolean[9][9];
        NativeImage heartImg = loadNativeImage(resourceManager, VANILLA_HEART_ID,
                "/assets/minecraft/textures/gui/sprites/hud/heart/full.png");

        if (heartImg == null) {
            // Default to vanilla heart mask if resource cannot be loaded
            return defaultVanillaMask();
        }

        try {
            int w = heartImg.getWidth();
            int h = heartImg.getHeight();

            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 9; x++) {
                    int sampleX = (int) ((x + 0.5f) * w / 9.0f);
                    int sampleY = (int) ((y + 0.5f) * h / 9.0f);
                    sampleX = Math.clamp(sampleX, 0, w - 1);
                    sampleY = Math.clamp(sampleY, 0, h - 1);

                    int pixel = heartImg.getPixel(sampleX, sampleY);
                    int alpha = ARGB.alpha(pixel);
                    mask[y][x] = alpha > 16;
                }
            }
        } finally {
            heartImg.close();
        }
        return mask;
    }

    public static boolean[][] defaultVanillaMask() {
        return new boolean[][] {
                {false, false, false, false, false, false, false, false, false},
                {false, false,  true,  true, false,  true,  true, false, false},
                {false,  true,  true,  true,  true,  true,  true,  true, false},
                {false,  true,  true,  true,  true,  true,  true,  true, false},
                {false,  true,  true,  true,  true,  true,  true,  true, false},
                {false, false,  true,  true,  true,  true,  true, false, false},
                {false, false, false,  true,  true,  true, false, false, false},
                {false, false, false, false,  true, false, false, false, false},
                {false, false, false, false, false, false, false, false, false}
        };
    }

    private static void registerTexture(TextureManager manager, int index, Identifier id, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(() -> "heart_cracking_overlay", image);
        texture.upload();
        manager.register(id, texture);
        DYNAMIC_TEXTURES[index] = texture;
    }

    private static void closeDynamicTextures() {
        for (int i = 0; i < DYNAMIC_TEXTURES.length; i++) {
            if (DYNAMIC_TEXTURES[i] != null) {
                try {
                    DYNAMIC_TEXTURES[i].close();
                } catch (Exception ignored) {
                }
                DYNAMIC_TEXTURES[i] = null;
            }
        }
    }

    private static NativeImage loadNativeImage(ResourceManager resourceManager, Identifier id, String classpathFallback) {
        if (resourceManager != null) {
            Optional<Resource> resource = resourceManager.getResource(id);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    return NativeImage.read(stream);
                } catch (Exception e) {
                    LOGGER.debug("Could not read resource {}, trying fallback", id);
                }
            }
        }
        try (InputStream stream = DynamicHeartOverlayManager.class.getResourceAsStream(classpathFallback)) {
            if (stream != null) {
                return NativeImage.read(stream);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read classpath stream {}", classpathFallback);
        }
        return null;
    }
}
