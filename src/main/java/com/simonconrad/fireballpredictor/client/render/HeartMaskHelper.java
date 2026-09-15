package com.simonconrad.fireballpredictor.client.render;

import net.minecraft.util.ARGB;

/**
 * Utility methods for 9x9 heart icon silhouette extraction, border coordinate detection,
 * and dark border pixel filtering. Environment-agnostic and free of client texture references
 * so it can be safely used and tested in headless and server environments (e.g. GameTests).
 */
public final class HeartMaskHelper {

    private HeartMaskHelper() {
    }

    /**
     * Checks if a coordinate in the 9x9 heart grid belongs to the standard 20-pixel outer heart border outline.
     */
    public static boolean isStandardHeartBorderCoord(int x, int y) {
        if (x < 0 || x > 8 || y < 0 || y > 8) {
            return false;
        }
        return switch (y) {
            case 0 -> (x == 2 || x == 3 || x == 5 || x == 6);
            case 1 -> (x == 1 || x == 4 || x == 7);
            case 2, 3, 4 -> (x == 0 || x == 8);
            case 5 -> (x == 1 || x == 7);
            case 6 -> (x == 2 || x == 6);
            case 7 -> (x == 3 || x == 5);
            case 8 -> (x == 4);
            default -> false;
        };
    }

    /**
     * Checks if a pixel represents a dark heart border/outline color.
     */
    public static boolean isDarkBorderPixel(int pixel) {
        int alpha = ARGB.alpha(pixel);
        if (alpha <= 16) {
            return false;
        }
        int r = ARGB.red(pixel);
        int g = ARGB.green(pixel);
        int b = ARGB.blue(pixel);
        int maxChannel = Math.max(r, Math.max(g, b));
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        return maxChannel < 55 || lum < 40;
    }

    /**
     * Returns the standard 34-pixel interior silhouette mask for vanilla hearts.
     */
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

    /**
     * Determines whether a sampled pixel at (x, y) should be marked active in the dynamic damage mask.
     *
     * @param x the grid x coordinate [0, 8]
     * @param y the grid y coordinate [0, 8]
     * @param heartPixel the sampled ARGB pixel from the heart sprite
     * @param isContainerBorder whether the corresponding pixel in container.png is a dark border
     * @return true if the pixel is interior fill damage area, false if transparent or dark border outline
     */
    public static boolean isMaskPixelActive(int x, int y, int heartPixel, boolean isContainerBorder) {
        int alpha = ARGB.alpha(heartPixel);
        if (alpha <= 16) {
            return false;
        }

        boolean isBorderCoord = isStandardHeartBorderCoord(x, y);
        boolean isDarkBorder = isDarkBorderPixel(heartPixel);

        if ((isBorderCoord && isDarkBorder) || (isContainerBorder && isDarkBorder)) {
            return false;
        }
        return true;
    }
}
