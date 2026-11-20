package kiwi;

/** Nina's four seasonal inventories for the verified Harvest Moon GB ROM. */
final class SeedShop {
    private static final int GRASS = 8;
    private static final int EMPTY = 0xff;
    private static final int[][] VANILLA = {{0, 1, GRASS}, {2, 3, GRASS}, {4, 5, GRASS}, {6, 7, EMPTY}};
    private static final String SEED_PREFIX = "harvest-moon-gbc/seed-shop/";
    private static final int[] INIT_OFFSETS = {0x2491d, 0xa4900};
    private static final int[] SETUP = {0x4b09, 0x4aec};
    private static final int[] APPEND = {0x49ae, 0x4991};
    private static final int RECORD_SIZE = 9;

    // Overnight season ($B884) and weather ($B8A0) comparison operands,
    // by hardware mode and then by shop catalog index.
    private static final int[][][] GROWTH_CHECKS = {
        {{0xab43, 0xab6e}, {0xab86, 0xabb1}, {0xa9e2, 0xaa0d}, {0xaad8, 0xab03},
         {0xabc9, 0xabf4}, {0xac0c, 0xac37}, {0xac4f, 0xac7a}, {0xac92, 0xacbd}},
        {{0x8aaa3, 0x8aace}, {0x8aae6, 0x8ab11}, {0x8a942, 0x8a96d}, {0x8aa38, 0x8aa63},
         {0x8ab29, 0x8ab54}, {0x8ab6c, 0x8ab97}, {0x8abaf, 0x8abda}, {0x8abf2, 0x8ac1d}}
    };

    private SeedShop() {}

    static int[][] inventory(String seed, boolean randomizeStock, boolean shuffleSlots) {
        int[] crops = {0, 1, 2, 3, 4, 5, 6, 7};
        if (randomizeStock) shuffle(crops, crops.length, SEED_PREFIX + "stock:" + seed);
        int[][] rows = new int[VANILLA.length][];
        for (int season = 0; season < rows.length; season++) {
            int[] row = VANILLA[season].clone();
            if (randomizeStock) {
                // Put crops in original catalog order until the slot option is enabled.
                row[0] = Math.min(crops[season * 2], crops[season * 2 + 1]);
                row[1] = Math.max(crops[season * 2], crops[season * 2 + 1]);
            }
            if (shuffleSlots) shuffle(row, row[2] == EMPTY ? 2 : 3, SEED_PREFIX + "slots:" + season + ":" + seed);
            rows[season] = row;
        }
        return rows;
    }

    private static void shuffle(int[] values, int count, String key) {
        PythonSeedRandom rng = new PythonSeedRandom(key);
        for (int i = count - 1; i > 0; i--) {
            int j = rng.oneTo(i + 1) - 1;
            int temp = values[i];
            values[i] = values[j];
            values[j] = temp;
        }
    }

    static void patch(byte[] rom, String seed, boolean randomizeStock, boolean shuffleSlots) {
        if (!randomizeStock && !shuffleSlots) return;
        int[][] rows = inventory(seed, randomizeStock, shuffleSlots);
        for (int mode = 0; mode < INIT_OFFSETS.length; mode++) {
            int start = INIT_OFFSETS[mode];
            int origin = 0x4000 + (start & 0x3fff);
            int[] code = dispatcher(SETUP[mode], origin + 52, origin + 64, APPEND[mode]);
            int pos = start;
            for (int op : code) rom[pos++] = (byte) op;
            for (int[] row : rows) for (int entry : row) rom[pos++] = (byte) entry;
            if (randomizeStock) {
                // The per-season table now controls availability. Accept every season
                // and name type for crop seeds; don't change Grass's vanilla filters.
                for (int crop = 0; crop < GRASS; crop++) {
                    rom[pos + crop * RECORD_SIZE + 2] = 0x0f;
                    rom[pos + crop * RECORD_SIZE + 3] = 0x1f;
                }
                // Use the same rows for sales and overnight survival. Winter uses snow
                // for natural watering instead of rain. Leave storm logic unchanged.
                for (int season = 0; season < rows.length; season++) {
                    for (int crop : rows[season]) {
                        if (crop >= GRASS) continue;
                        int[] checks = GROWTH_CHECKS[mode][crop];
                        rom[checks[0]] = (byte) season;
                        rom[checks[1]] = (byte) (season == 3 ? 2 : 1);
                    }
                }
            }
        }
    }

    private static int[] dispatcher(int setup, int table, int records, int append) {
        // Replaces 64 bytes of unrolled append calls with 52 bytes of code and
        // four 3-byte rows. $B884 is the season and $CD33 is the visible count.
        return new int[]{
            0xc5, 0xcd, setup & 0xff, setup >>> 8,          // push bc; call setup
            0xaf, 0xea, 0x33, 0xcd,                        // xor a; ld [$CD33], a
            0xfa, 0x84, 0xb8, 0x4f, 0x87, 0x81, 0x4f,      // c = season * 3
            0x06, 0x00, 0x21, table & 0xff, table >>> 8,     // b = 0; hl = table
            0x09, 0x06, 0x03,                              // hl += bc; b = row length
            0x2a, 0xfe, 0xff, 0x28, 0x13,                  // loop: load [hl++]; skip empty
            0xc5, 0xe5, 0x4f, 0x87, 0x87, 0x87, 0x81,      // save bc,hl; a = record id * 9
            0x5f, 0x16, 0x00, 0x21, records & 0xff, records >>> 8,
            0x19, 0xcd, append & 0xff, append >>> 8,        // hl += de; call vanilla append
            0xe1, 0xc1, 0x05, 0x20, 0xe5, 0xe1, 0xc9       // next, then original pop hl / ret
        };
    }
}
