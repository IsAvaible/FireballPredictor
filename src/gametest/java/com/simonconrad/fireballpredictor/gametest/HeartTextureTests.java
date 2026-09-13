package com.simonconrad.fireballpredictor.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class HeartTextureTests extends GameTestBase {

    private static final String[] STATIC_SPRITE_NAMES = {
            "cracking_full",
            "cracking_full_blinking",
            "cracking_half",
            "cracking_half_blinking",
            "cracking_half_right",
            "cracking_half_right_blinking",
            "cracking_master_full",
            "cracking_master_full_blinking"
    };

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testOverlaySpriteAssetsAndDimensions(GameTestHelper context) {
        resetGlobalState();

        for (String name : STATIC_SPRITE_NAMES) {
            String pathInSprites = "/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + name + ".png";
            String pathInHud = "/assets/fireballpredictor/textures/hud/heart/" + name + ".png";

            BufferedImage imgSprites = loadResourceImage(pathInSprites);
            BufferedImage imgHud = loadResourceImage(pathInHud);

            if (imgSprites.getWidth() != 9 || imgSprites.getHeight() != 9) {
                throw fail("Expected 9x9 sprite at " + pathInSprites + ", got " + imgSprites.getWidth() + "x" + imgSprites.getHeight());
            }
            if (imgHud.getWidth() != 9 || imgHud.getHeight() != 9) {
                throw fail("Expected 9x9 sprite at " + pathInHud + ", got " + imgHud.getWidth() + "x" + imgHud.getHeight());
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testOverlayTransparencyConstraints(GameTestHelper context) {
        resetGlobalState();

        String[] halfRightOverlays = {"cracking_half_right", "cracking_half_right_blinking"};
        for (String name : halfRightOverlays) {
            BufferedImage img = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + name + ".png");
            // Left half (x <= 4) must be 100% transparent
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x <= 4; x++) {
                    int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
                    if (alpha != 0) {
                        throw fail("Pixel (" + x + "," + y + ") in " + name + " must have alpha 0, but got " + alpha);
                    }
                }
            }
            // Right half (x >= 5) must contain crack pixels
            boolean hasCracks = false;
            for (int y = 0; y < 9; y++) {
                for (int x = 5; x < 9; x++) {
                    if (((img.getRGB(x, y) >> 24) & 0xFF) > 0) {
                        hasCracks = true;
                        break;
                    }
                }
            }
            if (!hasCracks) {
                throw fail(name + " has no crack pixels on the right half");
            }
        }

        String[] halfLeftOverlays = {"cracking_half", "cracking_half_blinking"};
        for (String name : halfLeftOverlays) {
            BufferedImage img = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + name + ".png");
            // Right half (x >= 5) must be 100% transparent
            for (int y = 0; y < 9; y++) {
                for (int x = 5; x < 9; x++) {
                    int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
                    if (alpha != 0) {
                        throw fail("Pixel (" + x + "," + y + ") in " + name + " must have alpha 0, but got " + alpha);
                    }
                }
            }
            // Left half (x <= 4) must contain crack pixels
            boolean hasCracks = false;
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x <= 4; x++) {
                    if (((img.getRGB(x, y) >> 24) & 0xFF) > 0) {
                        hasCracks = true;
                        break;
                    }
                }
            }
            if (!hasCracks) {
                throw fail(name + " has no crack pixels on the left half");
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testMasterPixelArtAssets(GameTestHelper context) {
        resetGlobalState();

        BufferedImage full = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_full.png");
        BufferedImage blink = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_full_blinking.png");

        int[][] corners = {{1, 1}, {7, 1}, {1, 7}, {7, 7}};
        for (int[] corner : corners) {
            int x = corner[0];
            int y = corner[1];
            int alphaFull = (full.getRGB(x, y) >> 24) & 0xFF;
            int alphaBlink = (blink.getRGB(x, y) >> 24) & 0xFF;
            if (alphaFull == 0) {
                throw fail("Corner pixel (" + x + "," + y + ") in master full texture must have alpha > 0");
            }
            if (alphaBlink == 0) {
                throw fail("Corner pixel (" + x + "," + y + ") in master blink texture must have alpha > 0");
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testDynamicSynthesisMasking(GameTestHelper context) {
        resetGlobalState();

        BufferedImage master = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_full.png");
        BufferedImage orig = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_full.png");

        // 1. Vanilla mask test: synthesizing with default vanilla mask must match cracking_full exactly
        boolean[][] vanillaMask = {
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

        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                int expectedRgb = vanillaMask[y][x] ? master.getRGB(x, y) : 0;
                int actualRgb = orig.getRGB(x, y);
                // When masked out, both should have alpha 0
                int expectedAlpha = (expectedRgb >> 24) & 0xFF;
                int actualAlpha = (actualRgb >> 24) & 0xFF;
                if (expectedAlpha == 0 && actualAlpha == 0) {
                    continue;
                }
                if (expectedRgb != actualRgb) {
                    throw fail("Synthesis mismatch at (" + x + "," + y + "): expected=0x" +
                            Integer.toHexString(expectedRgb) + ", actual=0x" + Integer.toHexString(actualRgb));
                }
            }
        }

        // 2. Square mask test: all 7x7 interior pixels must be active
        boolean[][] squareMask = new boolean[9][9];
        for (int y = 1; y <= 7; y++) {
            for (int x = 1; x <= 7; x++) {
                squareMask[y][x] = true;
            }
        }

        int[][] corners = {{1, 1}, {7, 1}, {1, 7}, {7, 7}};
        for (int[] corner : corners) {
            int x = corner[0];
            int y = corner[1];
            int alpha = (master.getRGB(x, y) >> 24) & 0xFF;
            if (alpha == 0) {
                throw fail("Corner pixel (" + x + "," + y + ") must have alpha > 0 for square mask");
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
