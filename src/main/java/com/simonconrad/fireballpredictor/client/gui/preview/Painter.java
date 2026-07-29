package com.simonconrad.fireballpredictor.client.gui.preview;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Clipped immediate-mode painter that wraps {@link GuiGraphicsExtractor}.
 *
 * <p>Every primitive is clamped to the rectangle supplied at construction time
 * so nothing can spill into the panel chrome or adjacent widgets.
 */
record Painter(GuiGraphicsExtractor graphics, int clipX0, int clipY0, int clipX1, int clipY1) {

    void fill(int x0, int y0, int x1, int y1, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        int ax = Math.max(x0, clipX0);
        int ay = Math.max(y0, clipY0);
        int bx = Math.min(x1, clipX1);
        int by = Math.min(y1, clipY1);
        if (ax >= bx || ay >= by) {
            return;
        }
        graphics.fill(ax, ay, bx, by, argb);
    }

    /** Float convenience; rounds to the nearest integer pixel boundary. */
    void fillF(float x0, float y0, float x1, float y1, int argb) {
        fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), argb);
    }

    void pixel(int x, int y, int argb) {
        fill(x, y, x + 1, y + 1, argb);
    }

    /** Bresenham line segment, clipped to the painter's bounds. */
    void line(float x1, float y1, float x2, float y2, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        int ix1 = Math.round(x1);
        int iy1 = Math.round(y1);
        int ix2 = Math.round(x2);
        int iy2 = Math.round(y2);
        int dx = Math.abs(ix2 - ix1);
        int dy = Math.abs(iy2 - iy1);
        int sx = ix1 < ix2 ? 1 : -1;
        int sy = iy1 < iy2 ? 1 : -1;
        int err = dx - dy;
        int x = ix1;
        int y = iy1;
        int guard = dx + dy + 2;
        while (guard-- > 0) {
            pixel(x, y, argb);
            if (x == ix2 && y == iy2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
