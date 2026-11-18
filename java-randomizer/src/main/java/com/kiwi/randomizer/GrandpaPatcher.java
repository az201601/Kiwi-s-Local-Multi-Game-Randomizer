package com.kiwi.randomizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Patches the grandpa opening letter in Harvest Moon GB.
 *
 * <p>The letter starts with "{PLAYER} WAKE UP {PLAYER} ..." and spans 22 fixed-size
 * entries (each: 4-byte header BC 54 00 00 + 31 bytes text + 2 bytes terminator +
 * 6 bytes 00 padding = 43 bytes). The same letter exists twice in the 1 MB ROM
 * (Bank 10 at file offset 0x29110 and Bank 42 at 0xA9110); both copies are patched
 * so the game shows the new text regardless of which bank is active.
 *
 * <p>Patching is length-preserving: only the 31-byte text areas are overwritten.
 * Headers, terminators and padding are left untouched, so no pointer fixups are needed.
 * After patching, the GB header checksum (0x14D) and global checksum (0x14E-0x14F)
 * are recomputed.
 *
 * <p>Java 8 compatible.
 */
public final class GrandpaPatcher {

    public static final byte[] ENTRY_HEADER = {(byte) 0xBC, (byte) 0x54, (byte) 0x00, (byte) 0x00};
    public static final int ENTRY_TEXT_SIZE = HarvestMoonCodec.ENTRY_TEXT_SIZE; // 31
    public static final int ENTRY_TOTAL_SIZE = 43; // 4 header + 31 text + 2 terminator + 6 padding
    public static final int LETTER_ENTRY_COUNT = 22; // entries 81..102 in the text bank

    // "WAKE" in mixed font encoding: W(alt)=0x16, A=0x1A, K=0x24, E=0x1E (original ROM)
    private static final byte[] WAKE_PATTERN_ALT = {(byte) 0x16, (byte) 0x1A, (byte) 0x24, (byte) 0x1E};
    // "WAKE" in normal font encoding: W=0x30, A=0x1A, K=0x24, E=0x1E (patched ROM, encoder uses normal font)
    private static final byte[] WAKE_PATTERN_NORM = {(byte) 0x30, (byte) 0x1A, (byte) 0x24, (byte) 0x1E};
    private static final byte[] PLAYER_PATTERN = {(byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3};

    private GrandpaPatcher() {
    }

    /** One located letter copy: file offset of its first entry header. */
    public static final class LetterLocation {
        public final int headerOffset;
        public final int entryCount;

        public LetterLocation(int headerOffset, int entryCount) {
            this.headerOffset = headerOffset;
            this.entryCount = entryCount;
        }

        @Override
        public String toString() {
            return String.format("letter @ 0x%06X (%d entries)", headerOffset, entryCount);
        }
    }

    /**
     * Find all copies of the grandpa letter (entries whose text contains WAKE + {PLAYER}).
     * Returns one location per copy (expected: 2 for the stock USA ROM).
     */
    public static List<LetterLocation> findLetterCopies(byte[] rom) {
        List<Integer> headers = findAllHeaders(rom);
        List<LetterLocation> result = new ArrayList<LetterLocation>();
        for (int h : headers) {
            if (h + 4 + ENTRY_TEXT_SIZE > rom.length) {
                continue;
            }
            byte[] text = Arrays.copyOfRange(rom, h + 4, h + 4 + ENTRY_TEXT_SIZE);
            boolean hasWake = contains(text, WAKE_PATTERN_ALT) || contains(text, WAKE_PATTERN_NORM);
            if (hasWake && contains(text, PLAYER_PATTERN)) {
                // Verify the following entries are contiguous (each 43 bytes apart with header)
                int count = 1;
                for (int n = 1; n < LETTER_ENTRY_COUNT; n++) {
                    int next = h + n * ENTRY_TOTAL_SIZE;
                    if (next + 4 > rom.length || !matchesAt(rom, next, ENTRY_HEADER)) {
                        break;
                    }
                    count++;
                }
                result.add(new LetterLocation(h, count));
            }
        }
        return result;
    }

    /**
     * Dump the decoded letter (one line per entry) for inspection.
     */
    public static List<String> dumpLetter(byte[] rom, LetterLocation loc) {
        List<String> lines = new ArrayList<String>();
        for (int n = 0; n < loc.entryCount; n++) {
            int textOff = loc.headerOffset + n * ENTRY_TOTAL_SIZE + 4;
            byte[] text = Arrays.copyOfRange(rom, textOff, textOff + ENTRY_TEXT_SIZE);
            lines.add(HarvestMoonCodec.decodeEntry(text));
        }
        return lines;
    }

    /**
     * Patch the letter with new plain-text lines (one line per entry).
     * Only the first {@code lines.size()} entries are overwritten; remaining entries
     * are left untouched. Both ROM copies are patched identically.
     *
     * @param rom   ROM bytes (modified in place)
     * @param lines new entry texts (each must encode to &lt;= 31 bytes)
     * @return number of entries patched per copy
     */
    public static int patchLetter(byte[] rom, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        List<LetterLocation> copies = findLetterCopies(rom);
        if (copies.isEmpty()) {
            throw new IllegalStateException(
                    "Grandpa letter not found. Is this Harvest Moon GB (USA)? "
                            + "Expected an entry containing {PLAYER} WAKE UP {PLAYER}.");
        }
        // Validate all lines first (fail before touching the ROM)
        List<byte[]> encoded = new ArrayList<byte[]>();
        for (int i = 0; i < lines.size(); i++) {
            try {
                encoded.add(HarvestMoonCodec.encodeEntry(lines.get(i)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        for (LetterLocation loc : copies) {
            if (lines.size() > loc.entryCount) {
                throw new IllegalArgumentException(
                        "Too many lines: " + lines.size() + " lines but letter has only "
                                + loc.entryCount + " entries at 0x" + Integer.toHexString(loc.headerOffset).toUpperCase() + ".");
            }
            for (int n = 0; n < encoded.size(); n++) {
                int textOff = loc.headerOffset + n * ENTRY_TOTAL_SIZE + 4;
                System.arraycopy(encoded.get(n), 0, rom, textOff, ENTRY_TEXT_SIZE);
            }
        }
        return encoded.size();
    }

    /** Recompute GB header checksum (0x14D) and global checksum (0x14E-0x14F). */
    public static void fixChecksums(byte[] rom) {
        if (rom.length < 0x150) {
            throw new IllegalArgumentException("ROM too small to contain a GB header");
        }
        // Header checksum: (-0x19 - sum(0x134..0x14C)) & 0xFF
        int sum = 0;
        for (int i = 0x134; i <= 0x14C; i++) {
            sum += rom[i] & 0xFF;
        }
        rom[0x14D] = (byte) ((-0x19 - sum) & 0xFF);
        // Global checksum: sum of all bytes with 0x14E/0x14F zeroed, & 0xFFFF, big-endian
        rom[0x14E] = 0;
        rom[0x14F] = 0;
        int total = 0;
        for (byte b : rom) {
            total += b & 0xFF;
        }
        total &= 0xFFFF;
        rom[0x14E] = (byte) ((total >> 8) & 0xFF);
        rom[0x14F] = (byte) (total & 0xFF);
    }

    /** Read ROM, patch, fix checksums, write output. Returns patched ROM bytes. */
    public static byte[] patchRom(Path romPath, Path outPath, List<String> lines) throws IOException {
        byte[] rom = Files.readAllBytes(romPath);
        verifyRomHeader(rom);
        List<LetterLocation> copies = findLetterCopies(rom);
        System.out.println("Found " + copies.size() + " letter copy/copies:");
        for (LetterLocation loc : copies) {
            System.out.println("  " + loc);
        }
        int patched = patchLetter(rom, lines);
        System.out.println("Patched " + patched + " entries per copy (" + copies.size() + " copies).");
        fixChecksums(rom);
        System.out.printf("Header checksum 0x14D = %02X, global checksum = %02X%02X%n",
                rom[0x14D] & 0xFF, rom[0x14E] & 0xFF, rom[0x14F] & 0xFF);
        Files.write(outPath, rom);
        System.out.println("Wrote: " + outPath);
        return rom;
    }

    public static void verifyRomHeader(byte[] rom) {
        if (rom.length != 1048576) {
            System.out.println("Warning: ROM size is " + rom.length + " bytes (expected 1048576 for this USA dump).");
        }
        if (rom.length >= 0x144) {
            String title = new String(rom, 0x134, 0x10, java.nio.charset.StandardCharsets.US_ASCII);
            System.out.println("ROM title: \"" + title.trim() + "\"");
            if (!title.startsWith("HARVEST-MOON")) {
                System.out.println("Warning: title does not look like Harvest Moon GB. Continuing anyway.");
            }
        }
    }

    private static List<Integer> findAllHeaders(byte[] rom) {
        List<Integer> headers = new ArrayList<Integer>();
        for (int i = 0; i + 4 <= rom.length; i++) {
            if (rom[i] == ENTRY_HEADER[0] && rom[i + 1] == ENTRY_HEADER[1]
                    && rom[i + 2] == ENTRY_HEADER[2] && rom[i + 3] == ENTRY_HEADER[3]) {
                headers.add(i);
            }
        }
        return headers;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean matchesAt(byte[] rom, int offset, byte[] pattern) {
        if (offset + pattern.length > rom.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (rom[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }
}
