package com.kiwi.randomizer.core;

/**
 * Game Boy / Game Boy Color cartridge checksum utilities.
 * Compatible with Java 8+.
 */
public final class GbChecksum {

    public static final int HEADER_CHECKSUM_OFFSET = 0x014D;
    public static final int GLOBAL_CHECKSUM_HI_OFFSET = 0x014E;
    public static final int GLOBAL_CHECKSUM_LO_OFFSET = 0x014F;

    private GbChecksum() {
    }

    /**
     * Computes the 8-bit Nintendo header checksum across 0x0134..0x014C.
     */
    public static int computeHeaderChecksum(byte[] rom) {
        if (rom == null || rom.length < 0x0150) {
            throw new IllegalArgumentException("ROM is too small to contain a Game Boy header.");
        }
        int checksum = 0;
        for (int i = 0x0134; i <= 0x014C; i++) {
            checksum = (checksum - (rom[i] & 0xFF) - 1) & 0xFF;
        }
        return checksum;
    }

    /**
     * Computes the 16-bit big-endian global checksum across all ROM bytes except 0x014E..0x014F.
     */
    public static int computeGlobalChecksum(byte[] rom) {
        if (rom == null || rom.length < 0x0150) {
            throw new IllegalArgumentException("ROM is too small to contain a Game Boy header.");
        }
        long sum = 0L;
        for (int i = 0; i < rom.length; i++) {
            if (i == GLOBAL_CHECKSUM_HI_OFFSET || i == GLOBAL_CHECKSUM_LO_OFFSET) {
                continue;
            }
            sum += (rom[i] & 0xFF);
        }
        return (int) (sum & 0xFFFF);
    }

    public static int readHeaderChecksum(byte[] rom) {
        return rom[HEADER_CHECKSUM_OFFSET] & 0xFF;
    }

    public static int readGlobalChecksum(byte[] rom) {
        return ((rom[GLOBAL_CHECKSUM_HI_OFFSET] & 0xFF) << 8)
                | (rom[GLOBAL_CHECKSUM_LO_OFFSET] & 0xFF);
    }

    /**
     * Recalculates and writes both header (0x014D) and global (0x014E..0x014F) checksums in-place.
     */
    public static void updateChecksums(byte[] rom) {
        int header = computeHeaderChecksum(rom);
        rom[HEADER_CHECKSUM_OFFSET] = (byte) header;

        int global = computeGlobalChecksum(rom);
        rom[GLOBAL_CHECKSUM_HI_OFFSET] = (byte) ((global >>> 8) & 0xFF);
        rom[GLOBAL_CHECKSUM_LO_OFFSET] = (byte) (global & 0xFF);
    }
}
