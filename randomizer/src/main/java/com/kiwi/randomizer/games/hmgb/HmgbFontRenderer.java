package com.kiwi.randomizer.games.hmgb;

import java.awt.image.BufferedImage;

/**
 * Decodes and renders the authentic 8x8 2bpp dialogue font tiles directly from
 * Harvest Moon GB (USA) (SGB Enhanced).gbc at Bank 0x11:$4001 (file offset 0x44001).
 */
public final class HmgbFontRenderer {

    /** Verified via disassembly of Bank 0:$3792 (LD HL,$4001; LD A,$11; CALL $3F6C). */
    public static final int FONT_BANK = 0x11;
    public static final int FONT_BANK_ADDR = 0x4001;
    public static final int FONT_FILE_OFFSET = FONT_BANK * 0x4000 + (FONT_BANK_ADDR - 0x4000); // 0x44001
    public static final int BYTES_PER_TILE = 16;

    // Verified from font tile data: color 0 = transparent paper, color 1 = text ink (in-game CGB window palette)
    private static final int[] DIALOGUE_PALETTE_ARGB = new int[] {
        0x00FFFFFF, // color 0: transparent over box paper
        0xFF180808, // color 1: authentic HM GBC deep dark dialogue ink
        0xFF686058, // color 2: accent mid-tone (border/UI tiles)
        0xFF100808  // color 3: dark outline (border/UI tiles)
    };

    private final BufferedImage[] glyphCache = new BufferedImage[256];

    public HmgbFontRenderer(byte[] rom) {
        if (rom == null || rom.length < FONT_FILE_OFFSET + 256 * BYTES_PER_TILE) {
            throw new IllegalArgumentException("ROM does not contain font bank 0x11.");
        }
        for (int code = 0; code < 256; code++) {
            glyphCache[code] = decodeTile(rom, FONT_FILE_OFFSET + code * BYTES_PER_TILE);
        }
    }

    private static BufferedImage decodeTile(byte[] rom, int offset) {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            int lo = rom[offset + y * 2] & 0xFF;
            int hi = rom[offset + y * 2 + 1] & 0xFF;
            for (int x = 0; x < 8; x++) {
                int shift = 7 - x;
                int colorIdx = ((lo >>> shift) & 1) | (((hi >>> shift) & 1) << 1);
                img.setRGB(x, y, DIALOGUE_PALETTE_ARGB[colorIdx]);
            }
        }
        return img;
    }

    public BufferedImage getGlyph(int tileCode) {
        return glyphCache[tileCode & 0xFF];
    }
}
