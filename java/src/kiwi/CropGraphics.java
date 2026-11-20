package kiwi;

/** Loads the original crop art into the farm tileset for the seeded growing season. */
final class CropGraphics {
    // First live metatile record and stage count, in shop catalog order.
    private static final int[][] STAGES = {{46, 9}, {55, 13}, {4, 17}, {21, 25}, {68, 9}, {77, 13}, {90, 13}, {103, 17}};
    private static final int PALETTE = 0x1fd8;
    private static final int[] PALETTE_HOOKS = {0x1f9e, 0x1fc2};
    private static final int PALETTE_HELPER = 0x3fe6;
    private static final int EXTRA_PALETTES = 0xe4;
    private static final int EXTRA_START = 0x90;
    private static final int[] EXTRA_COLORS = {5, 5, 5, 5, 7, 7, 7, 7};
    // Live tool metatiles, map/scroll metatiles, farm loader hook, trampoline,
    // and larger run of verified padding for the new loader and crop tiles.
    private static final int[][] MODES = {
        {0x22500, 0x46ba9, 0x40da, 0x7fc1, 0x27597},
        {0xa2525, 0xc6ba9, 0x840a3, 0x87ff4, 0xa7567}
    };
    // Vanilla farm art: two streams (tile IDs 00–7F and 80–FF) per season.
    private static final int[][] ART = {
        {0x2c001, 0x2c758, 0x2cc1a, 0x2d457, 0x2d9fa, 0x2e150, 0x2e612, 0x2edc8},
        {0xac001, 0xac78a, 0xacc4e, 0xad4c8, 0xada75, 0xae205, 0xae6c9, 0xaeec3}
    };
    private static final int TILE_BYTES = 16;

    private CropGraphics() {}

    static void patch(byte[] rom, int[][] rows) {
        // Select the extra palette table only for the added farm tiles. Both
        // scrolling and live updates still use the original lookup everywhere else.
        put(rom, EXTRA_PALETTES, EXTRA_COLORS);
        put(rom, PALETTE_HELPER,
            0xf5, 0x21, 0xd8, 0x1f,       // push af; ld hl,$1fd8 (vanilla table)
            0xd6, 0x90, 0xfe, 0x08,       // sub $90; cp 8
            0x30, 0x0a,                   // jr nc, done
            0xfa, 0xa7, 0xc0, 0xfe, 0x01, // ld a,[$c0a7]; cp 1 (farm)
            0x20, 0x03,                   // jr nz, done
            0x21, 0x54, 0x00,             // ld hl, extraTable - $90
            0xf1, 0xc9);                  // done: pop af; ret
        for (int hook : PALETTE_HOOKS) call(rom, hook, PALETTE_HELPER);
        for (int mode = 0; mode < MODES.length; mode++) patchMode(rom, rows, mode);
    }

    private static void patchMode(byte[] rom, int[][] rows, int mode) {
        int[] locations = MODES[mode];
        int live = locations[0], scroll = locations[1], hook = locations[2];
        int trampoline = locations[3], free = locations[4];
        byte[][] pictures = new byte[4][0x1000];
        for (int i = 0; i < ART[mode].length; i++) {
            byte[] half = decode(rom, ART[mode][i]);
            System.arraycopy(half, 0, pictures[i / 2], (i % 2) * 0x800, half.length);
        }
        boolean[][] cropTiles = new boolean[STAGES.length][256];
        boolean[] slots = new boolean[256];
        int[] colors = new int[256];
        for (int tile = 0; tile < 256; tile++) colors[tile] = u(rom[PALETTE + tile]);
        for (int i = 0; i < EXTRA_COLORS.length; i++) {
            slots[EXTRA_START + i] = true;
            colors[EXTRA_START + i] = EXTRA_COLORS[i];
        }
        for (int crop = 0; crop < STAGES.length; crop++) {
            for (int i = 0; i < STAGES[crop][1]; i++) {
                int record = live + (STAGES[crop][0] + i) * 8;
                for (int j = 2; j < 6; j++) {
                    int tile = u(rom[record + j]);
                    if (tile == 0x02 || tile == 0x10 || tile == 0x11 || tile == 0x12) continue;
                    cropTiles[crop][tile] = slots[tile] = true;
                }
            }
        }

        int table = put(rom, free,
            0xfa, 0x84, 0xb8, 0x87, 0x4f, 0x06, 0, // bc = season * 2
            0x21, 0, 0, 0x09, 0x2a, 0x66, 0x6f,    // hl = row pointer
            0x2a, 0xb7, 0xc8,                      // loop: tile = [hl++]; ret on 0
            0xcb, 0x37, 0x5f, 0xe6, 0x0f, 0xee, 8, // swap tile; low nibble xor 8
            0xc6, 0x88, 0x57, 0x7b, 0xe6, 0xf0, 0x5f, // de = signed tile address in $8800–$97ff
            0x01, 0x10, 0, 0xcd, 0xff, 0x22,       // copy the 16 bitmap bytes from hl
            0x18, 0xe7);                           // jr loop
        pointer(rom, free + 8, cpu(table));
        int pos = table + 8;
        for (int season = 0; season < rows.length; season++) {
            pointer(rom, table + 2 * season, cpu(pos));
            int first = 255, second = 255;
            for (int crop : rows[season]) {
                if (crop >= STAGES.length) continue;
                if (crop < first) { second = first; first = crop; }
                else second = crop;
            }
            boolean[] occupied = new boolean[256];
            for (int tile = 0; tile < 256; tile++) occupied[tile] = cropTiles[first][tile] || cropTiles[second][tile];
            for (int crop : new int[]{first, second}) {
                int[] dest = new int[256];
                for (int tile = 0; tile < 256; tile++) {
                    dest[tile] = tile;
                    if (!cropTiles[crop][tile]) continue;
                    if (crop == second && cropTiles[first][tile]) {
                        int spare = 0;
                        while (!slots[spare] || occupied[spare] || colors[spare] != colors[tile]) spare++;
                        dest[tile] = spare;
                        occupied[spare] = true;
                    }
                    rom[pos++] = (byte) dest[tile];
                    System.arraycopy(pictures[crop / 2], tile * TILE_BYTES, rom, pos, TILE_BYTES);
                    pos += TILE_BYTES;
                }
                for (int i = 0; i < STAGES[crop][1]; i++) {
                    int record = live + (STAGES[crop][0] + i) * 8;
                    int mapRecord = scroll + u(rom[record]) * 4;
                    for (int j = 0; j < 4; j++) {
                        byte subtile = (byte) dest[u(rom[record + 2 + j])];
                        rom[record + 2 + j] = rom[mapRecord + j] = subtile;
                    }
                }
            }
            rom[pos++] = 0;
        }
        call(rom, hook, cpu(trampoline));
        int addr = cpu(free);
        put(rom, trampoline, 0x21, addr & 0xff, addr >>> 8, 0x3e, free / 0x4000,
            0xcd, 0x7f, 0x21,  // far-call overlay after the vanilla farm graphics have loaded
            0xfa, 0xea, 0xb8, 0xc9); // displaced ld a,[$b8ea]; ret
    }

    /** Decode the game's $31A0 LZ format (LSB-first flags, 1 = literal). */
    private static byte[] decode(byte[] rom, int pos) {
        int size = u(rom[pos]) | (u(rom[pos + 1]) << 8);
        byte[] output = new byte[size];
        int end = 0;
        pos += 2;
        while (end < size) {
            int flags = u(rom[pos++]);
            for (int bit = 0; bit < 8 && end < size; bit++) {
                if ((flags & (1 << bit)) != 0) {
                    output[end++] = rom[pos++];
                } else {
                    int length = (rom[pos] & 0xf) + 3;
                    int distance = (u(rom[pos]) >>> 4) | (u(rom[pos + 1]) << 4);
                    pos += 2;
                    if (distance == 0 || distance > end) end += length;
                    else for (int i = 0; i < length; i++) {
                        output[end] = output[end - distance];
                        end++;
                    }
                }
            }
        }
        return output;
    }

    private static int u(byte b) { return b & 0xff; }
    private static int cpu(int offset) { return 0x4000 + offset % 0x4000; }
    private static int put(byte[] rom, int pos, int... data) {
        for (int b : data) rom[pos++] = (byte) b;
        return pos;
    }
    private static void pointer(byte[] rom, int pos, int addr) { put(rom, pos, addr & 0xff, addr >>> 8); }
    private static void call(byte[] rom, int pos, int addr) { put(rom, pos, 0xcd, addr & 0xff, addr >>> 8); }
}
