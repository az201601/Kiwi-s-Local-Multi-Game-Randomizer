package com.kiwi.randomizer.ui;

import com.kiwi.randomizer.games.hmgb.HmgbFontRenderer;
import com.kiwi.randomizer.games.hmgb.HmgbTextCodec;
import com.kiwi.randomizer.games.hmgb.HmgbTextCodec.DialogueBox;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Renders an authentic Game Boy Color Harvest Moon GB 20x5-tile dialogue window
 * (2x scale = 320x80 inside a crisp frame). Uses the real 8x8 2bpp font tiles
 * straight from the user's loaded ROM when available, or a clean monospace fallback
 * before a ROM is loaded.
 */
public final class DialoguePreviewPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // 20 tiles wide x 5 tiles tall = 160x40 GBC pixels; rendered at 2x = 320x80
    private static final int SCALE = 2;
    private static final int BOX_W = 160 * SCALE;
    private static final int BOX_H = 40 * SCALE;

    // Authentic colors sampled from Grandpa's Spirit CGB cutscene window
    private static final Color BG_BEAM_OUTER = new Color(0x18, 0xA8, 0x10);
    private static final Color BG_BEAM_INNER = new Color(0x98, 0xE8, 0x48);
    private static final Color BORDER_DARK = new Color(0x10, 0x10, 0x08);
    private static final Color BORDER_LIME = new Color(0xA8, 0xE0, 0x30);
    private static final Color PAPER_WHITE = new Color(0xF8, 0xF8, 0xF8);
    private static final Color TEXT_INK = new Color(0x18, 0x08, 0x08);
    private static final Color ERROR_INK = new Color(0xA8, 0x18, 0x18);

    private HmgbFontRenderer fontRenderer;
    private DialogueBox currentBox;
    private String samplePlayerName = "Kiwi";
    private String errorMessage;

    public DialoguePreviewPanel() {
        setPreferredSize(new Dimension(BOX_W + 24, BOX_H + 24));
        setMinimumSize(new Dimension(BOX_W + 16, BOX_H + 16));
        setOpaque(true);
    }

    public void setFontRenderer(HmgbFontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
        repaint();
    }

    public void setPreview(DialogueBox box, String samplePlayerName, String errorMessage) {
        this.currentBox = box;
        this.samplePlayerName = (samplePlayerName == null || samplePlayerName.isEmpty()) ? "Kiwi" : samplePlayerName;
        this.errorMessage = errorMessage;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int w = getWidth();
            int h = getHeight();

            // Grandpa's spirit green light backdrop
            g2.setColor(BG_BEAM_OUTER);
            g2.fillRect(0, 0, w, h);
            int beamW = Math.max(80, w / 2);
            g2.setColor(BG_BEAM_INNER);
            g2.fillRect((w - beamW) / 2, 0, beamW, h);

            int bx = (w - BOX_W) / 2;
            int by = (h - BOX_H) / 2;

            // Authentic 5-tile-tall GBC dialogue box frame
            g2.setColor(BORDER_DARK);
            g2.fillRect(bx, by, BOX_W, BOX_H);
            g2.setColor(BORDER_LIME);
            g2.fillRect(bx + 2, by + 2, BOX_W - 4, BOX_H - 4);
            g2.setColor(BORDER_DARK);
            g2.fillRect(bx + 6, by + 6, BOX_W - 12, BOX_H - 12);
            g2.setColor(PAPER_WHITE);
            g2.fillRect(bx + 8, by + 8, BOX_W - 16, BOX_H - 16);

            if (errorMessage != null) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(ERROR_INK);
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                drawCenteredString(g2, "Fix text issue to preview box", bx + 12, by + 12, BOX_W - 24, BOX_H - 24);
                return;
            }

            if (currentBox == null) {
                return;
            }

            // In Window tilemap $9C00: Line 1 starts at tile (col=2, row=1), Line 2 at tile (col=2, row=3)
            int textX = bx + 2 * 8 * SCALE;
            int line1Y = by + 1 * 8 * SCALE;
            int line2Y = by + 3 * 8 * SCALE;

            if (fontRenderer != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int[] t1 = HmgbTextCodec.resolveLineTilesForPreview(currentBox.getLine1(), samplePlayerName);
                int[] t2 = HmgbTextCodec.resolveLineTilesForPreview(currentBox.getLine2(), samplePlayerName);
                drawTileRow(g2, t1, textX, line1Y);
                drawTileRow(g2, t2, textX, line2Y);
            } else {
                // Crisp fallback when no ROM has been opened yet
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(TEXT_INK);
                g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
                String s1 = currentBox.formatDisplayLine1(samplePlayerName);
                String s2 = currentBox.formatDisplayLine2(samplePlayerName);
                g2.drawString(s1, textX, line1Y + 14);
                g2.drawString(s2, textX, line2Y + 14);
            }

            // Subtle outer crisp border
            g2.setColor(new Color(0, 0, 0, 70));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(0, 0, w - 1, h - 1);
        } finally {
            g2.dispose();
        }
    }

    private void drawTileRow(Graphics2D g2, int[] tiles, int startX, int y) {
        int tilePx = 8 * SCALE;
        for (int c = 0; c < tiles.length; c++) {
            int code = tiles[c] & 0xFF;
            if (code == HmgbTextCodec.BYTE_SPACE) {
                continue;
            }
            g2.drawImage(fontRenderer.getGlyph(code), startX + c * tilePx, y, tilePx, tilePx, null);
        }
    }

    private static void drawCenteredString(Graphics2D g2, String s, int x, int y, int w, int h) {
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + Math.max(0, (w - fm.stringWidth(s)) / 2);
        int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(s, tx, ty);
    }
}
