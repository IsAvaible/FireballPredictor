package com.simonconrad.fireballpredictor.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

import com.simonconrad.fireballpredictor.client.render.HeartOverlayRenderer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

public class HeartTextureTests extends GameTestBase {

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testOverlaySpriteResolution(GameTestHelper context) {
        resetGlobalState();

        // 1. Both lost -> full overlay
        Identifier fullSteady = HeartOverlayRenderer.getOverlaySprite(true, true, false);
        Identifier fullBlink = HeartOverlayRenderer.getOverlaySprite(true, true, true);
        if (!"fireballpredictor:hud/heart/cracking_full".equals(fullSteady.toString())) {
            throw fail("Expected full steady overlay, got " + fullSteady);
        }
        if (!"fireballpredictor:hud/heart/cracking_full_blinking".equals(fullBlink.toString())) {
            throw fail("Expected full blinking overlay, got " + fullBlink);
        }

        // 2. Right lost (left intact) -> half right overlay
        Identifier rightSteady = HeartOverlayRenderer.getOverlaySprite(false, true, false);
        Identifier rightBlink = HeartOverlayRenderer.getOverlaySprite(false, true, true);
        if (!"fireballpredictor:hud/heart/cracking_half_right".equals(rightSteady.toString())) {
            throw fail("Expected half-right steady overlay, got " + rightSteady);
        }
        if (!"fireballpredictor:hud/heart/cracking_half_right_blinking".equals(rightBlink.toString())) {
            throw fail("Expected half-right blinking overlay, got " + rightBlink);
        }

        // 3. Left lost (right empty) -> half left overlay
        Identifier leftSteady = HeartOverlayRenderer.getOverlaySprite(true, false, false);
        Identifier leftBlink = HeartOverlayRenderer.getOverlaySprite(true, false, true);
        if (!"fireballpredictor:hud/heart/cracking_half".equals(leftSteady.toString())) {
            throw fail("Expected half-left steady overlay, got " + leftSteady);
        }
        if (!"fireballpredictor:hud/heart/cracking_half_blinking".equals(leftBlink.toString())) {
            throw fail("Expected half-left blinking overlay, got " + leftBlink);
        }

        // 4. Neither lost -> null (no overlay drawn)
        if (HeartOverlayRenderer.getOverlaySprite(false, false, false) != null) {
            throw fail("Neither lost should return null");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testOverlayTransparencyConstraints(GameTestHelper context) {
        resetGlobalState();

        Identifier[] overlays = {
                HeartOverlayRenderer.fullOverlay(false),
                HeartOverlayRenderer.fullOverlay(true),
                HeartOverlayRenderer.halfLeftOverlay(false),
                HeartOverlayRenderer.halfLeftOverlay(true),
                HeartOverlayRenderer.halfRightOverlay(false),
                HeartOverlayRenderer.halfRightOverlay(true)
        };

        for (Identifier id : overlays) {
            String pathInSprites = "/assets/fireballpredictor/textures/gui/sprites/" + id.getPath() + ".png";
            String pathInHud = "/assets/fireballpredictor/textures/" + id.getPath() + ".png";

            BufferedImage imgSprites = loadResourceImage(pathInSprites);
            BufferedImage imgHud = loadResourceImage(pathInHud);

            if (imgSprites.getWidth() != 9 || imgSprites.getHeight() != 9) {
                throw fail("Expected 9x9 sprite at " + pathInSprites + ", got " + imgSprites.getWidth() + "x" + imgSprites.getHeight());
            }
            if (imgHud.getWidth() != 9 || imgHud.getHeight() != 9) {
                throw fail("Expected 9x9 sprite at " + pathInHud + ", got " + imgHud.getWidth() + "x" + imgHud.getHeight());
            }

            // Test strict half-transparency
            boolean isHalfRight = id.getPath().contains("half_right");
            boolean isHalfLeft = id.getPath().contains("half") && !isHalfRight;

            if (isHalfRight) {
                // Left half (x <= 4) must be 100% transparent to preserve underlying heart
                for (int y = 0; y < 9; y++) {
                    for (int x = 0; x <= 4; x++) {
                        int alpha = (imgSprites.getRGB(x, y) >> 24) & 0xFF;
                        if (alpha != 0) {
                            throw fail("Pixel (" + x + "," + y + ") in half-right overlay " + id + " must have alpha 0, but got " + alpha);
                        }
                    }
                }
                // Right half (x >= 5) must contain crack pixels
                boolean hasCracks = false;
                for (int y = 0; y < 9; y++) {
                    for (int x = 5; x < 9; x++) {
                        if (((imgSprites.getRGB(x, y) >> 24) & 0xFF) > 0) {
                            hasCracks = true;
                            break;
                        }
                    }
                }
                if (!hasCracks) {
                    throw fail("Half-right overlay " + id + " has no crack pixels on the right half");
                }
            } else if (isHalfLeft) {
                // Right half (x >= 5) must be 100% transparent
                for (int y = 0; y < 9; y++) {
                    for (int x = 5; x < 9; x++) {
                        int alpha = (imgSprites.getRGB(x, y) >> 24) & 0xFF;
                        if (alpha != 0) {
                            throw fail("Pixel (" + x + "," + y + ") in half-left overlay " + id + " must have alpha 0, but got " + alpha);
                        }
                    }
                }
                // Left half (x <= 4) must contain crack pixels
                boolean hasCracks = false;
                for (int y = 0; y < 9; y++) {
                    for (int x = 0; x <= 4; x++) {
                        if (((imgSprites.getRGB(x, y) >> 24) & 0xFF) > 0) {
                            hasCracks = true;
                            break;
                        }
                    }
                }
                if (!hasCracks) {
                    throw fail("Half-left overlay " + id + " has no crack pixels on the left half");
                }
            } else {
                // Full overlay must have crack pixels on both halves
                boolean hasLeft = false;
                boolean hasRight = false;
                for (int y = 0; y < 9; y++) {
                    for (int x = 0; x < 9; x++) {
                        if (((imgSprites.getRGB(x, y) >> 24) & 0xFF) > 0) {
                            if (x <= 4) hasLeft = true;
                            if (x >= 5) hasRight = true;
                        }
                    }
                }
                if (!hasLeft || !hasRight) {
                    throw fail("Full overlay " + id + " must span both left and right halves");
                }
            }
        }

        context.succeed();
    }

    private BufferedImage loadResourceImage(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw fail("Missing asset resource: " + path);
            }
            return ImageIO.read(is);
        } catch (Exception e) {
            throw fail("Failed to read image at " + path + ": " + e.getMessage());
        }
    }
}
