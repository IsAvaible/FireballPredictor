package com.simonconrad.fireballpredictor.client.gui;

import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game configuration screen for the 1.8.9 backport (the 26.2 original uses YACL,
 * which does not exist for 1.8.9 - this screen reproduces its core UX with vanilla
 * widgets: categories, toggles, sliders, color cyclers and enum cyclers).
 *
 * <p>Every change applies live (the runtime reads the {@code ModConfig} statics each
 * frame/tick). "Done" persists to the Forge {@code .cfg} file; "Cancel"/ESC reloads the
 * file and reverts unsaved edits.
 *
 * <p>Opened through the Mods list config button ({@code ModConfigGuiFactory}), the
 * {@code /fireballpredictor} client command or the (unbound) keybind.
 */
public class ModConfigGui extends GuiScreen {

    private static final String[] CATEGORIES = {
            "General", "Trajectory", "Shockwave Dome", "Blocks", "HUD", "Tracking", "Prediction"
    };

    /** Curated palette for the color cyclers (name, RRGGBB). */
    private static final String[][] PALETTE = {
            {"Orange", "FF8000"}, {"Red", "FF4040"}, {"Crimson", "DC143C"}, {"Gold", "FFD700"},
            {"Yellow", "FFFF55"}, {"Lime", "7CFC00"}, {"Green", "3CB371"}, {"Emerald", "50C878"},
            {"Cyan", "00CED1"}, {"Aqua", "00FFFF"}, {"Blue", "4169E1"}, {"Navy", "000080"},
            {"Purple", "9370DB"}, {"Magenta", "FF00FF"}, {"Pink", "FF69B4"}, {"White", "FFFFFF"},
            {"Silver", "C0C0C0"}, {"Gray", "808080"}, {"Black", "2B2B2B"}
    };

    private static final int CATEGORY_BUTTON_ID = 1000;
    private static final int DONE_ID = 1;
    private static final int CANCEL_ID = 2;

    private static final int CONTENT_TOP = 40;
    private static final int CONTENT_BOTTOM_MARGIN = 44;
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 128;
    private static final int LEFT_PANE_WIDTH = 110;

    private final GuiScreen parent;
    private int categoryIndex;
    private final List<Entry> entries = new ArrayList<Entry>();
    private int scroll;
    private boolean committed;
    private boolean draggingScrollbar;
    private double scrollbarClickOffset;

    /** True while the left mouse button is held over the rows area (slider drags). */
    private Entry activeDragEntry;

    public ModConfigGui(GuiScreen parent) {
        this.parent = parent;
    }

    // ------------------------------------------------------------ boilerplate

    @Override
    public void initGui() {
        buttonList.clear();
        for (int i = 0; i < CATEGORIES.length; i++) {
            buttonList.add(new GuiButton(CATEGORY_BUTTON_ID + i, 8, CONTENT_TOP - 12 + i * 22,
                    LEFT_PANE_WIDTH - 8, 20, CATEGORIES[i]));
        }
        buttonList.add(new GuiButton(DONE_ID, this.width / 2 - 155, this.height - 28, 150, 20, "Done"));
        buttonList.add(new GuiButton(CANCEL_ID, this.width / 2 + 5, this.height - 28, 150, 20, "Cancel"));
        selectCategory(categoryIndex);
    }

    private void selectCategory(int index) {
        categoryIndex = index;
        scroll = 0;
        entries.clear();
        buildEntries(CATEGORIES[index]);
        clampScroll();
        for (Object raw : buttonList) {
            GuiButton button = (GuiButton) raw;
            button.enabled = button.id < CATEGORY_BUTTON_ID
                    || button.id - CATEGORY_BUTTON_ID != categoryIndex;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawCenteredString(this.fontRendererObj, "Fireball Predictor",
                this.width / 2, 12, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, CATEGORIES[categoryIndex],
                this.width / 2, 24, 0xFFAAAA);

        // Category buttons + Done/Cancel.
        super.drawScreen(mouseX, mouseY, partialTicks);

        int rowsLeft = 8 + LEFT_PANE_WIDTH + 6;
        int rowsRight = this.width - 16;
        int rowsTop = CONTENT_TOP;
        int rowsBottom = this.height - CONTENT_BOTTOM_MARGIN;
        int contentHeight = rowsBottom - rowsTop;

        // Rows.
        int y = rowsTop - scroll;
        int visible = 0;
        for (Entry entry : entries) {
            if (y + entry.height() > rowsTop && y < rowsBottom) {
                boolean last = y + entry.height() > rowsBottom;
                int clipped = last ? entry.height() - (rowsBottom - y) : 0;
                entry.draw(mc, fontRendererObj, rowsLeft, y,
                        (rowsRight - rowsLeft) - 10, mouseX, mouseY, clipped);
                visible++;
            }
            y += entry.height();
        }

        // Scrollbar when the content overflows.
        int totalHeight = 0;
        for (Entry entry : entries) {
            totalHeight += entry.height();
        }
        if (totalHeight > contentHeight) {
            int barX = this.width - 13;
            drawRect(barX, rowsTop, barX + 6, rowsBottom, 0x88222222);
            int thumbH = Math.max(18, (int) ((long) contentHeight * contentHeight / totalHeight));
            int thumbY = rowsTop + (int) ((long) (contentHeight - thumbH) * scroll
                    / Math.max(1, totalHeight - contentHeight));
            drawRect(barX + 1, thumbY, barX + 5, thumbY + thumbH, 0xFF888888);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= CATEGORY_BUTTON_ID) {
            selectCategory(button.id - CATEGORY_BUTTON_ID);
            playClick();
        } else if (button.id == DONE_ID) {
            committed = true;
            ModConfig.save();
            this.mc.displayGuiScreen(parent);
        } else if (button.id == CANCEL_ID) {
            this.mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }

        int rowsLeft = 8 + LEFT_PANE_WIDTH + 6;
        int rowsWidth = this.width - 16 - rowsLeft - 10;
        int rowsTop = CONTENT_TOP;
        int rowsBottom = this.height - CONTENT_BOTTOM_MARGIN;

        // Scrollbar hit?
        int totalHeight = 0;
        for (Entry entry : entries) {
            totalHeight += entry.height();
        }
        if (totalHeight > rowsBottom - rowsTop && mouseX >= this.width - 13 && mouseX <= this.width - 7
                && mouseY >= rowsTop && mouseY <= rowsBottom) {
            draggingScrollbar = true;
            scrollbarClickOffset = 0;
            scrollByThumb(mouseY, rowsTop, rowsBottom, totalHeight);
            return;
        }

        // Entry hit?
        int y = rowsTop - scroll;
        for (Entry entry : entries) {
            if (y + entry.height() > rowsTop && y < rowsBottom
                    && mouseY >= y && mouseY < Math.min(y + entry.height(), rowsBottom)) {
                if (entry.click(mc, fontRendererObj, rowsLeft, y, rowsWidth, mouseX, mouseY)) {
                    playClick();
                    if (entry.wantsDrag()) {
                        activeDragEntry = entry;
                    }
                    return;
                }
            }
            y += entry.height();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        draggingScrollbar = false;
        if (activeDragEntry != null) {
            activeDragEntry.release();
            activeDragEntry = null;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedButton, timeSinceLastClick);
        if (draggingScrollbar) {
            int rowsTop = CONTENT_TOP;
            int rowsBottom = this.height - CONTENT_BOTTOM_MARGIN;
            int totalHeight = 0;
            for (Entry entry : entries) {
                totalHeight += entry.height();
            }
            scrollByThumb(mouseY, rowsTop, rowsBottom, totalHeight);
            return;
        }
        if (activeDragEntry != null) {
            int rowsLeft = 8 + LEFT_PANE_WIDTH + 6;
            int rowsWidth = this.width - 16 - rowsLeft - 10;
            int rowsTop = CONTENT_TOP;
            int y = rowsTop - scroll;
            for (Entry entry : entries) {
                if (entry == activeDragEntry) {
                    entry.drag(rowsLeft, y, rowsWidth, mouseX, mouseY);
                    return;
                }
                y += entry.height();
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += wheel > 0 ? -ROW_HEIGHT * 2 : ROW_HEIGHT * 2;
            clampScroll();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC key
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        if (!committed) {
            // Revert unsaved live edits (Done persists them instead).
            ModConfig.reload();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private void scrollByThumb(int mouseY, int rowsTop, int rowsBottom, int totalHeight) {
        int contentHeight = rowsBottom - rowsTop;
        float frac = (float) (mouseY - rowsTop) / (float) contentHeight;
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        scroll = (int) (frac * (totalHeight - contentHeight));
        clampScroll();
    }

    private void clampScroll() {
        int totalHeight = 0;
        for (Entry entry : entries) {
            totalHeight += entry.height();
        }
        int contentHeight = this.height - CONTENT_BOTTOM_MARGIN - CONTENT_TOP;
        if (totalHeight <= contentHeight) {
            scroll = 0;
        } else {
            scroll = Math.max(0, Math.min(totalHeight - contentHeight, scroll));
        }
    }

    private void playClick() {
        this.mc.getSoundHandler().playSound(
                PositionedSoundRecord.create(new ResourceLocation("gui.button"), 1.0F));
    }

    // ----------------------------------------------------------------- entries

    private interface BoolAccess {
        boolean get();

        void set(boolean value);
    }

    private interface FloatAccess {
        float get();

        void set(float value);
    }

    private interface IntAccess {
        int get();

        void set(int value);
    }

    /** Convenience factories so the category builders stay one-liners. */
    private ToggleEntry toggle(String label, final BoolAccess access) {
        return new ToggleEntry(label, access);
    }

    private ToggleEntry toggle(String label, final java.util.function.Supplier<Boolean> get,
                                      final java.util.function.Consumer<Boolean> set) {
        return new ToggleEntry(label, new BoolAccess() {
            @Override
            public boolean get() {
                return get.get();
            }

            @Override
            public void set(boolean value) {
                set.accept(value);
            }
        });
    }

    private SliderEntry slider(String label, final java.util.function.Supplier<Float> get,
                                      final java.util.function.Consumer<Float> set,
                                      float min, float max, boolean integer) {
        return new SliderEntry(label, new FloatAccess() {
            @Override
            public float get() {
                return get.get();
            }

            @Override
            public void set(float value) {
                set.accept(value);
            }
        }, min, max, integer);
    }

    private ColorEntry color(String label, final java.util.function.Supplier<Integer> get,
                                    final java.util.function.Consumer<Integer> set) {
        return new ColorEntry(label, new IntAccess() {
            @Override
            public int get() {
                return get.get();
            }

            @Override
            public void set(int value) {
                set.accept(value);
            }
        });
    }

    /** One row of the right-hand settings pane. */
    private abstract static class Entry {
        abstract int height();

        abstract void draw(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                           int x, int y, int width, int mouseX, int mouseY, int clippedBottom);

        boolean click(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                      int x, int y, int width, int mouseX, int mouseY) {
            return false;
        }

        void drag(int x, int y, int width, int mouseX, int mouseY) {
        }

        void release() {
        }

        boolean wantsDrag() {
            return false;
        }

        protected boolean hovered(int x, int y, int w, int h, int mouseX, int mouseY, int clipped) {
            return mouseX >= x && mouseX <= x + w
                    && mouseY >= y && mouseY <= y + h - clipped;
        }
    }

    /** Plain section header row. */
    private static class HeaderEntry extends Entry {
        private final String text;

        HeaderEntry(String text) {
            this.text = text;
        }

        @Override
        int height() {
            return 18;
        }

        @Override
        void draw(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                  int x, int y, int width, int mouseX, int mouseY, int clipped) {
            font.drawString(text, x + 2, y + 5, 0xFFFFFF99);
        }
    }

    /** Boolean toggle rendered as a button showing Enabled/Disabled. */
    private class ToggleEntry extends Entry {
        private final String label;
        private final BoolAccess access;

        ToggleEntry(String label, BoolAccess access) {
            this.label = label;
            this.access = access;
        }

        @Override
        int height() {
            return ROW_HEIGHT;
        }

        @Override
        void draw(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                  int x, int y, int width, int mouseX, int mouseY, int clipped) {
            font.drawString(label, x + 4, y + 8, 0xE0E0E0);
            int bx = x + width - WIDGET_WIDTH;
            int by = y + 2;
            boolean on = access.get();
            boolean hover = hovered(bx, by, WIDGET_WIDTH, 20, mouseX, mouseY, clipped);
            drawRect(bx, by, bx + WIDGET_WIDTH, by + 20, hover ? 0xFF6B6B6B : 0xFF585858);
            drawRect(bx + 1, by + 1, bx + WIDGET_WIDTH - 1, by + 19, 0xFF3A3A3A);
            String state = on ? "Enabled" : "Disabled";
            font.drawString(state,
                    bx + (WIDGET_WIDTH - font.getStringWidth(state)) / 2, by + 6,
                    on ? 0xFF70FF70 : 0xFFFF7070);
        }

        @Override
        boolean click(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                      int x, int y, int width, int mouseX, int mouseY) {
            int bx = x + width - WIDGET_WIDTH;
            if (mouseX >= bx && mouseX <= bx + WIDGET_WIDTH && mouseY >= y + 2 && mouseY <= y + 22) {
                access.set(!access.get());
                return true;
            }
            return false;
        }
    }

    /** Draggable slider for float values with live formatting. */
    private class SliderEntry extends Entry {
        private final String label;
        private final FloatAccess access;
        private final float min;
        private final float max;
        private final boolean integer;
        private boolean dragging;

        SliderEntry(String label, FloatAccess access, float min, float max, boolean integer) {
            this.label = label;
            this.access = access;
            this.min = min;
            this.max = max;
            this.integer = integer;
        }

        @Override
        int height() {
            return ROW_HEIGHT + 2;
        }

        private float fraction() {
            return Math.max(0.0F, Math.min(1.0F, (access.get() - min) / (max - min)));
        }

        private String valueString() {
            float value = access.get();
            return integer ? String.valueOf(Math.round(value))
                    : String.format(Locale.ROOT, "%.2f", value);
        }

        @Override
        void draw(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                  int x, int y, int width, int mouseX, int mouseY, int clipped) {
            String text = label + ": " + valueString();
            font.drawString(text, x + 4, y + 3, 0xE0E0E0);

            int bx = x + width - WIDGET_WIDTH;
            int by = y + 14;
            int bw = WIDGET_WIDTH;
            boolean hover = hovered(bx, by, bw, 10, mouseX, mouseY, Math.max(0, clipped - 14));
            drawRect(bx, by + 3, bx + bw, by + 7, 0xFF222222);
            drawRect(bx + 1, by + 4, bx + 1 + (int) ((bw - 2) * fraction()), by + 6, 0xFFE67A00);

            int handleX = bx + (int) ((bw - 10) * fraction());
            drawRect(handleX, by, handleX + 10, by + 10, hover || dragging ? 0xFFF2F2F2 : 0xFFC8C8C8);
            drawRect(handleX + 1, by + 1, handleX + 9, by + 9, 0xFF8A8A8A);
        }

        @Override
        boolean click(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                      int x, int y, int width, int mouseX, int mouseY) {
            int bx = x + width - WIDGET_WIDTH;
            int by = y + 14;
            boolean hit = mouseX >= bx && mouseX <= bx + WIDGET_WIDTH && mouseY >= y + 8 && mouseY <= y + 24;
            if (hit) {
                dragging = true;
                updateFromMouse(bx, WIDGET_WIDTH, mouseX);
            }
            return hit;
        }

        @Override
        boolean wantsDrag() {
            return true;
        }

        @Override
        void drag(int x, int y, int width, int mouseX, int mouseY) {
            if (dragging) {
                updateFromMouse(x + width - WIDGET_WIDTH, WIDGET_WIDTH, mouseX);
            }
        }

        @Override
        void release() {
            dragging = false;
        }

        private void updateFromMouse(int bx, int bw, int mouseX) {
            float frac = (float) (mouseX - bx - 5) / (float) (bw - 10);
            frac = Math.max(0.0F, Math.min(1.0F, frac));
            float value = min + frac * (max - min);
            if (integer) {
                access.set(Math.round(value));
            } else {
                access.set(value);
            }
        }
    }

    /** Color cycler: palette swatch with prev/next arrows. */
    private class ColorEntry extends Entry {
        private final String label;
        private final IntAccess access;

        ColorEntry(String label, IntAccess access) {
            this.label = label;
            this.access = access;
        }

        private int paletteIndex() {
            int rgb = access.get() & 0xFFFFFF;
            for (int i = 0; i < PALETTE.length; i++) {
                if (Integer.parseInt(PALETTE[i][1], 16) == rgb) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        int height() {
            return ROW_HEIGHT;
        }

        @Override
        void draw(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                  int x, int y, int width, int mouseX, int mouseY, int clipped) {
            font.drawString(label, x + 4, y + 8, 0xE0E0E0);
            int bx = x + width - WIDGET_WIDTH;
            int by = y + 2;

            // Swatch.
            drawRect(bx, by + 2, bx + 16, by + 18, 0xFF000000 | (access.get() & 0xFFFFFF));
            drawRect(bx, by + 2, bx + 16, by + 3, 0x66FFFFFF);
            drawRect(bx, by + 17, bx + 16, by + 18, 0x66000000);

            int idx = paletteIndex();
            String name = idx >= 0 ? PALETTE[idx][0]
                    : String.format(Locale.ROOT, "#%06X", access.get() & 0xFFFFFF);
            int nameX = bx + 20;
            int nameW = WIDGET_WIDTH - 42;
            String fit = font.trimStringToWidth(name, nameW);
            font.drawString(fit, nameX + (nameW - font.getStringWidth(fit)) / 2, by + 6, 0xE0E0E0);

            font.drawString("<", bx + WIDGET_WIDTH - 20, by + 6, 0xFFFFFF);
            font.drawString(">", bx + WIDGET_WIDTH - 8, by + 6, 0xFFFFFF);
        }

        @Override
        boolean click(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                      int x, int y, int width, int mouseX, int mouseY) {
            int bx = x + width - WIDGET_WIDTH;
            if (mouseY < y + 2 || mouseY > y + 22) {
                return false;
            }
            if (mouseX >= bx + WIDGET_WIDTH - 22 && mouseX <= bx + WIDGET_WIDTH - 10) {
                cycle(-1);
                return true;
            }
            if (mouseX >= bx + WIDGET_WIDTH - 10 && mouseX <= bx + WIDGET_WIDTH) {
                cycle(1);
                return true;
            }
            return false;
        }

        private void cycle(int direction) {
            int idx = paletteIndex();
            if (idx < 0) {
                idx = 0;
                if (direction < 0) {
                    idx = PALETTE.length - 1;
                }
            } else {
                idx = (idx + direction + PALETTE.length) % PALETTE.length;
            }
            access.set(0xFF000000 | Integer.parseInt(PALETTE[idx][1], 16));
        }
    }

    /** Enum cycler (trajectory style). */
    private class EnumEntry extends Entry {
        private final String label;

        EnumEntry(String label) {
            this.label = label;
        }

        @Override
        int height() {
            return ROW_HEIGHT;
        }

        @Override
        void draw(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                  int x, int y, int width, int mouseX, int mouseY, int clipped) {
            font.drawString(label, x + 4, y + 8, 0xE0E0E0);
            int bx = x + width - WIDGET_WIDTH;
            int by = y + 2;
            boolean hover = hovered(bx, by, WIDGET_WIDTH, 20, mouseX, mouseY, clipped);
            drawRect(bx, by, bx + WIDGET_WIDTH, by + 20, hover ? 0xFF6B6B6B : 0xFF585858);
            drawRect(bx + 1, by + 1, bx + WIDGET_WIDTH - 1, by + 19, 0xFF3A3A3A);
            String value = ModConfig.trajectoryStyle.name();
            font.drawString(value,
                    bx + (WIDGET_WIDTH - font.getStringWidth(value)) / 2, by + 6, 0xFFE67A00);
            font.drawString("<", bx + 4, by + 6, 0xFFFFFF);
            font.drawString(">", bx + WIDGET_WIDTH - 10, by + 6, 0xFFFFFF);
        }

        @Override
        boolean click(Minecraft mc, net.minecraft.client.gui.FontRenderer font,
                      int x, int y, int width, int mouseX, int mouseY) {
            int bx = x + width - WIDGET_WIDTH;
            if (mouseX >= bx && mouseX <= bx + WIDGET_WIDTH && mouseY >= y + 2 && mouseY <= y + 22) {
                TrajectoryStyle[] values = TrajectoryStyle.values();
                ModConfig.trajectoryStyle = values[(ModConfig.trajectoryStyle.ordinal() + 1) % values.length];
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------ row building

    private void buildEntries(String category) {
        if (category.equals("General")) {
            entries.add(new HeaderEntry("General"));
            entries.add(toggle("Mod enabled",
                    () -> ModConfig.masterEnabled, v -> ModConfig.masterEnabled = v));
        } else if (category.equals("Trajectory")) {
            entries.add(new HeaderEntry("Trajectory ribbon"));
            entries.add(toggle("Render trajectory",
                    () -> ModConfig.renderTrajectory, v -> ModConfig.renderTrajectory = v));
            entries.add(slider("Width (blocks)",
                    () -> ModConfig.trajectoryWidth, v -> ModConfig.trajectoryWidth = v,
                    0.1F, 2.0F, false));
            entries.add(color("Color",
                    () -> ModConfig.trajectoryColor, v -> ModConfig.trajectoryColor = v));
            entries.add(new EnumEntry("Style"));
            entries.add(toggle("Core glow",
                    () -> ModConfig.renderCoreGlow, v -> ModConfig.renderCoreGlow = v));
            entries.add(toggle("Ribbon pulse",
                    () -> ModConfig.enableRibbonPulse, v -> ModConfig.enableRibbonPulse = v));
        } else if (category.equals("Shockwave Dome")) {
            entries.add(new HeaderEntry("Shockwave dome"));
            entries.add(toggle("Render dome",
                    () -> ModConfig.renderShockwaveDome, v -> ModConfig.renderShockwaveDome = v));
            entries.add(color("Color",
                    () -> ModConfig.domeColor, v -> ModConfig.domeColor = v));
            entries.add(slider("Fresnel strength",
                    () -> ModConfig.domeFresnelStrength, v -> ModConfig.domeFresnelStrength = v,
                    0.0F, 1.0F, false));
        } else if (category.equals("Blocks")) {
            entries.add(new HeaderEntry("Block destruction"));
            entries.add(toggle("Highlight broken blocks",
                    () -> ModConfig.renderBlockHighlights, v -> ModConfig.renderBlockHighlights = v));
        } else if (category.equals("HUD")) {
            entries.add(new HeaderEntry("HUD overlays"));
            entries.add(toggle("Impact warning badge",
                    () -> ModConfig.renderImpactWarning, v -> ModConfig.renderImpactWarning = v));
            entries.add(toggle("Damage / knockback readout",
                    () -> ModConfig.renderDamageText, v -> ModConfig.renderDamageText = v));
            entries.add(toggle("Cracking hearts overlay",
                    () -> ModConfig.renderHeartsOverlay, v -> ModConfig.renderHeartsOverlay = v));
            entries.add(slider("Badge X offset",
                    () -> (float) ModConfig.badgeOffsetX, v -> ModConfig.badgeOffsetX = Math.round(v),
                    -200.0F, 200.0F, true));
            entries.add(slider("Badge Y offset",
                    () -> (float) ModConfig.badgeOffsetY, v -> ModConfig.badgeOffsetY = Math.round(v),
                    -200.0F, 200.0F, true));
        } else if (category.equals("Tracking")) {
            entries.add(new HeaderEntry("Owner tracking filters"));
            entries.add(toggle("Mob projectiles",
                    () -> ModConfig.trackMobProjectiles, v -> ModConfig.trackMobProjectiles = v));
            entries.add(toggle("Other-owner projectiles",
                    () -> ModConfig.trackOtherOwnerProjectiles,
                    v -> ModConfig.trackOtherOwnerProjectiles = v));
            entries.add(toggle("Player projectiles",
                    () -> ModConfig.trackPlayerProjectiles, v -> ModConfig.trackPlayerProjectiles = v));
            entries.add(toggle("Dispenser projectiles",
                    () -> ModConfig.trackDispenserProjectiles,
                    v -> ModConfig.trackDispenserProjectiles = v));
            entries.add(toggle("Command-summoned projectiles",
                    () -> ModConfig.trackCommandProjectiles,
                    v -> ModConfig.trackCommandProjectiles = v));
            entries.add(new HeaderEntry("Server restrictions always apply"));
        } else if (category.equals("Prediction")) {
            entries.add(new HeaderEntry("Prediction parameters"));
            entries.add(slider("Ray power multiplier",
                    () -> ModConfig.rayPowerMultiplier, v -> ModConfig.rayPowerMultiplier = v,
                    0.7F, 1.3F, false));
            entries.add(slider("Max tracked projectiles",
                    () -> (float) ModConfig.maxTrackedProjectiles,
                    v -> ModConfig.maxTrackedProjectiles = Math.round(v), 1.0F, 64.0F, true));
            entries.add(slider("Max lookahead ticks",
                    () -> (float) ModConfig.maxTicks, v -> ModConfig.maxTicks = Math.round(v),
                    20.0F, 600.0F, true));
        }
    }
}
