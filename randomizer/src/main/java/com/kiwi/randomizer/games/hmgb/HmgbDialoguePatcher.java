package com.kiwi.randomizer.games.hmgb;

import com.kiwi.randomizer.games.hmgb.HmgbTextCodec.DialogueBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reads and patches Harvest Moon GB/GBC dialogue entries.
 *
 * Verified Dialogue Engine Layout (Bank 0x00:$36F4..$3A60):
 *  - Master Dialogue Table at 0x03421 (241 entries x 3 bytes: addr_lo, addr_hi, bank)
 *  - Grandpa's Spirit intro speech is Dialogue ID 0xC9 (entry at 0x0367C):
 *      - Triggered in CGB cutscene Bank 0x10:$5568 and DMG cutscene Bank 0x30:$54D3
 *      - Vanilla target: Bank 0x0A:$510A (file offset 0x2910A), 19 boxes (817 bytes, 0x2910A..0x2943A)
 *      - Next dialogue (ID 0xEE, "Thank you.") begins immediately at 0x2943B — never overwritten!
 *  - Each dialogue box record is exactly 43 (0x2B) bytes:
 *      [0..5]   6 bytes: choice flags/targets/cursor offsets (all 0x00 for normal dialogue)
 *      [6..7]   2 bytes: little-endian callback address in the same bank (vanilla $54BC -> 0xC9 RET)
 *      [8..9]   2 bytes: reserved (0x00, 0x00)
 *      [10..25] 16 bytes: Line 1 tile codes (VRAM $80..$8F, Window $9C22..$9C31)
 *      [26..41] 16 bytes: Line 2 tile codes (VRAM $90..$9F, Window $9C62..$9C71; col 15 = 0xE9 on continuation)
 *      [42]     1 byte:  terminator (0xFE = wait for A & advance to next 43-byte box; 0xFF = end dialogue)
 *
 * Patching Strategy:
 *  - 1..19 boxes:  Patched in-place at Bank 0x0A:$510A (0x2910A..0x2943A). Unused trailing slots up to 19
 *                  are cleanly filled with blank 0xFF-terminated boxes.
 *  - 20..61 boxes: Automatically repointed via Master Dialogue Table entry 0x0367C to verified unused
 *                  0xFF free space at Bank 0x09:$75A1 (file offset 0x275A1), with a 0xC9 RET stub at $75A0.
 */
public final class HmgbDialoguePatcher {

    public static final int BANK_SIZE = 0x4000;

    public static final int MASTER_TABLE_OFFSET = 0x03421;
    public static final int GRANDPA_DIALOGUE_ID = 0xC9; // 201
    public static final int GRANDPA_PTR_OFFSET = MASTER_TABLE_OFFSET + GRANDPA_DIALOGUE_ID * 3; // 0x0367C

    public static final int BOX_SIZE = 43; // 0x2B
    public static final int HEADER_SIZE = 10;

    // Vanilla Grandpa slot in Bank 0x0A
    public static final int VANILLA_BANK = 0x0A;
    public static final int VANILLA_ADDR = 0x510A;
    public static final int VANILLA_FILE_OFFSET = VANILLA_BANK * BANK_SIZE + (VANILLA_ADDR - 0x4000); // 0x2910A
    public static final int VANILLA_BOX_CAPACITY = 19; // 0x2910A..0x2943A (0x2943B = ID 0xEE)
    public static final int VANILLA_CALLBACK_ADDR = 0x54BC;
    public static final int VANILLA_CALLBACK_FILE_OFFSET = VANILLA_BANK * BANK_SIZE + (VANILLA_CALLBACK_ADDR - 0x4000); // 0x294BC

    // Unused leftover DMG-build duplicate slot in Bank 0x2A (mirrored cleanly on in-place patches)
    public static final int MIRROR_BANK = 0x2A;
    public static final int MIRROR_FILE_OFFSET = MIRROR_BANK * BANK_SIZE + (VANILLA_ADDR - 0x4000); // 0xA910A
    public static final int MIRROR_CALLBACK_FILE_OFFSET = MIRROR_BANK * BANK_SIZE + (VANILLA_CALLBACK_ADDR - 0x4000); // 0xA94BC

    // Free-space repoint region in Bank 0x09 (0x275A0..0x27FFF = 2,656 bytes of unused 0xFF)
    public static final int REPOINT_BANK = 0x09;
    public static final int REPOINT_CALLBACK_ADDR = 0x75A0;
    public static final int REPOINT_DATA_ADDR = 0x75A1;
    public static final int REPOINT_CALLBACK_FILE_OFFSET = REPOINT_BANK * BANK_SIZE + (REPOINT_CALLBACK_ADDR - 0x4000); // 0x275A0
    public static final int REPOINT_DATA_FILE_OFFSET = REPOINT_BANK * BANK_SIZE + (REPOINT_DATA_ADDR - 0x4000); // 0x275A1
    public static final int REPOINT_REGION_END = (REPOINT_BANK + 1) * BANK_SIZE; // 0x28000
    public static final int MAX_REPOINT_BOXES = (REPOINT_REGION_END - REPOINT_DATA_FILE_OFFSET) / BOX_SIZE; // 61

    public static final int OPCODE_RET = 0xC9;

    private HmgbDialoguePatcher() {
    }

    public static final class PatchReport {
        private final int boxCount;
        private final boolean repointed;
        private final int targetBank;
        private final int targetBankAddr;
        private final int targetFileOffset;

        public PatchReport(int boxCount, boolean repointed, int targetBank, int targetBankAddr, int targetFileOffset) {
            this.boxCount = boxCount;
            this.repointed = repointed;
            this.targetBank = targetBank;
            this.targetBankAddr = targetBankAddr;
            this.targetFileOffset = targetFileOffset;
        }

        public int getBoxCount() {
            return boxCount;
        }

        public boolean isRepointed() {
            return repointed;
        }

        public int getTargetBank() {
            return targetBank;
        }

        public int getTargetBankAddr() {
            return targetBankAddr;
        }

        public int getTargetFileOffset() {
            return targetFileOffset;
        }

        public String describe() {
            String mode = repointed
                    ? "repointed via 0x0367C to free space"
                    : "patched in-place (vanilla slot holds up to " + VANILLA_BOX_CAPACITY + " boxes)";
            return String.format(
                    "Grandpa's Spirit (ID 0x%02X): wrote %d box%s (%d bytes) at Bank 0x%02X:$%04X (file 0x%05X) — %s.",
                    Integer.valueOf(GRANDPA_DIALOGUE_ID),
                    Integer.valueOf(boxCount),
                    boxCount == 1 ? "" : "es",
                    Integer.valueOf(boxCount * BOX_SIZE),
                    Integer.valueOf(targetBank),
                    Integer.valueOf(targetBankAddr),
                    Integer.valueOf(targetFileOffset),
                    mode);
        }
    }

    public static int toFileOffset(int bank, int bankAddr) {
        if (bank == 0) {
            return bankAddr;
        }
        if (bankAddr < 0x4000 || bankAddr >= 0x8000) {
            throw new IllegalArgumentException(String.format("Invalid banked address $%04X", Integer.valueOf(bankAddr)));
        }
        return bank * BANK_SIZE + (bankAddr - 0x4000);
    }

    /**
     * Checks that the ROM contains a valid Grandpa dialogue entry in the Master Dialogue Table.
     */
    public static boolean hasValidGrandpaTable(byte[] rom) {
        if (rom == null || rom.length != 64 * BANK_SIZE) {
            return false;
        }
        int addr = (rom[GRANDPA_PTR_OFFSET] & 0xFF) | ((rom[GRANDPA_PTR_OFFSET + 1] & 0xFF) << 8);
        int bank = rom[GRANDPA_PTR_OFFSET + 2] & 0xFF;
        boolean vanillaPtr = (bank == VANILLA_BANK && addr == VANILLA_ADDR);
        boolean repointPtr = (bank == REPOINT_BANK && addr == REPOINT_DATA_ADDR);
        if (!vanillaPtr && !repointPtr) {
            return false;
        }
        return (rom[VANILLA_CALLBACK_FILE_OFFSET] & 0xFF) == OPCODE_RET;
    }

    /**
     * Reads Grandpa's current dialogue boxes from the ROM by following Master Dialogue Table ID 0xC9.
     */
    public static List<DialogueBox> readGrandpaDialogue(byte[] rom) {
        if (!hasValidGrandpaTable(rom)) {
            throw new IllegalArgumentException("ROM does not have a recognized Grandpa dialogue pointer at 0x0367C.");
        }
        int addr = (rom[GRANDPA_PTR_OFFSET] & 0xFF) | ((rom[GRANDPA_PTR_OFFSET + 1] & 0xFF) << 8);
        int bank = rom[GRANDPA_PTR_OFFSET + 2] & 0xFF;
        int offset = toFileOffset(bank, addr);

        List<DialogueBox> boxes = new ArrayList<DialogueBox>();
        for (int i = 0; i < MAX_REPOINT_BOXES; i++) {
            int boxOff = offset + i * BOX_SIZE;
            if (boxOff + BOX_SIZE > rom.length) {
                break;
            }
            byte[] l1 = Arrays.copyOfRange(rom, boxOff + 10, boxOff + 26);
            byte[] l2 = Arrays.copyOfRange(rom, boxOff + 26, boxOff + 42);
            int term = rom[boxOff + 42] & 0xFF;
            if (term != HmgbTextCodec.TERM_CONTINUE && term != HmgbTextCodec.TERM_END) {
                throw new IllegalStateException(
                        String.format("Unexpected dialogue terminator 0x%02X at 0x%05X.",
                                Integer.valueOf(term), Integer.valueOf(boxOff + 42)));
            }
            boxes.add(new DialogueBox(l1, l2, term));
            if (term == HmgbTextCodec.TERM_END) {
                break;
            }
        }
        return Collections.unmodifiableList(boxes);
    }

    /**
     * Writes Grandpa's new dialogue boxes into a mutable copy of the ROM.
     */
    public static PatchReport patchGrandpaDialogue(byte[] rom, List<DialogueBox> boxes) {
        if (!hasValidGrandpaTable(rom)) {
            throw new IllegalArgumentException("Cannot patch Grandpa dialogue: unrecognized ROM dialogue table.");
        }
        if (boxes == null || boxes.isEmpty()) {
            throw new IllegalArgumentException("Grandpa's dialogue must contain at least 1 box.");
        }
        if (boxes.size() > MAX_REPOINT_BOXES) {
            throw new IllegalArgumentException(
                    "Grandpa's dialogue is " + boxes.size() + " boxes long; maximum supported is "
                            + MAX_REPOINT_BOXES + " boxes.");
        }

        int currentBank = rom[GRANDPA_PTR_OFFSET + 2] & 0xFF;
        boolean wasRepointed = (currentBank == REPOINT_BANK);

        if (boxes.size() <= VANILLA_BOX_CAPACITY) {
            // If ROM was previously repointed, restore free space in Bank 0x09 back to 0xFF
            if (wasRepointed) {
                Arrays.fill(rom, REPOINT_CALLBACK_FILE_OFFSET, REPOINT_REGION_END, (byte) 0xFF);
            }

            // Write Master Dialogue Table entry 0x0367C -> Bank 0x0A:$510A
            writeMasterPointer(rom, GRANDPA_PTR_OFFSET, VANILLA_BANK, VANILLA_ADDR);

            // Write active boxes into Bank 0x0A:$510A
            writeBoxSequence(rom, VANILLA_FILE_OFFSET, VANILLA_CALLBACK_ADDR, boxes);

            // Cleanly clear any unused remaining boxes in the 19-box vanilla region (never touching 0x2943B = ID 0xEE)
            byte[] blankLine = HmgbTextCodec.emptyLine();
            DialogueBox fillerBox = new DialogueBox(blankLine, blankLine, HmgbTextCodec.TERM_END);
            for (int i = boxes.size(); i < VANILLA_BOX_CAPACITY; i++) {
                writeSingleBox(rom, VANILLA_FILE_OFFSET + i * BOX_SIZE, VANILLA_CALLBACK_ADDR, fillerBox);
            }

            // Mirror cleanly to Bank 0x2A's unused copy if verified intact
            if ((rom[MIRROR_CALLBACK_FILE_OFFSET] & 0xFF) == OPCODE_RET) {
                System.arraycopy(
                        rom, VANILLA_FILE_OFFSET,
                        rom, MIRROR_FILE_OFFSET,
                        VANILLA_BOX_CAPACITY * BOX_SIZE);
            }

            return new PatchReport(boxes.size(), false, VANILLA_BANK, VANILLA_ADDR, VANILLA_FILE_OFFSET);
        } else {
            // Verify Bank 0x09 bank-ID header and free space before repointing
            if ((rom[REPOINT_BANK * BANK_SIZE] & 0xFF) != REPOINT_BANK) {
                throw new IllegalStateException("Bank 0x09 self-ID byte mismatch; refusing to repoint.");
            }
            if (!wasRepointed) {
                for (int off = REPOINT_CALLBACK_FILE_OFFSET; off < REPOINT_REGION_END; off++) {
                    if ((rom[off] & 0xFF) != 0xFF) {
                        throw new IllegalStateException(
                                String.format("Free space at 0x%05X is already in use (0x%02X); cannot repoint.",
                                        Integer.valueOf(off), Integer.valueOf(rom[off] & 0xFF)));
                    }
                }
            } else {
                Arrays.fill(rom, REPOINT_CALLBACK_FILE_OFFSET, REPOINT_REGION_END, (byte) 0xFF);
            }

            // Place 1-byte RET callback at Bank 0x09:$75A0 and dialogue boxes right after at $75A1
            rom[REPOINT_CALLBACK_FILE_OFFSET] = (byte) OPCODE_RET;
            writeBoxSequence(rom, REPOINT_DATA_FILE_OFFSET, REPOINT_CALLBACK_ADDR, boxes);
            writeMasterPointer(rom, GRANDPA_PTR_OFFSET, REPOINT_BANK, REPOINT_DATA_ADDR);

            return new PatchReport(boxes.size(), true, REPOINT_BANK, REPOINT_DATA_ADDR, REPOINT_DATA_FILE_OFFSET);
        }
    }

    private static void writeMasterPointer(byte[] rom, int ptrOffset, int bank, int addr) {
        rom[ptrOffset] = (byte) (addr & 0xFF);
        rom[ptrOffset + 1] = (byte) ((addr >>> 8) & 0xFF);
        rom[ptrOffset + 2] = (byte) (bank & 0xFF);
    }

    private static void writeBoxSequence(byte[] rom, int baseOffset, int callbackAddr, List<DialogueBox> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            boolean isLast = (i == boxes.size() - 1);
            DialogueBox box = boxes.get(i);
            byte[] l1 = box.getLine1();
            byte[] l2 = box.getLine2();
            int term = isLast ? HmgbTextCodec.TERM_END : HmgbTextCodec.TERM_CONTINUE;
            if (!isLast) {
                l2[HmgbTextCodec.LINE_WIDTH - 1] = (byte) HmgbTextCodec.BYTE_NEXT_ARROW;
            }
            writeSingleBox(rom, baseOffset + i * BOX_SIZE, callbackAddr, new DialogueBox(l1, l2, term));
        }
    }

    private static void writeSingleBox(byte[] rom, int offset, int callbackAddr, DialogueBox box) {
        // 10-byte header: [0..5]=0x00, [6..7]=callback LE, [8..9]=0x00
        for (int i = 0; i < HEADER_SIZE; i++) {
            rom[offset + i] = 0x00;
        }
        rom[offset + 6] = (byte) (callbackAddr & 0xFF);
        rom[offset + 7] = (byte) ((callbackAddr >>> 8) & 0xFF);

        // 16-byte line 1 + 16-byte line 2
        byte[] l1 = box.getLine1();
        byte[] l2 = box.getLine2();
        System.arraycopy(l1, 0, rom, offset + 10, HmgbTextCodec.LINE_WIDTH);
        System.arraycopy(l2, 0, rom, offset + 26, HmgbTextCodec.LINE_WIDTH);

        // 1-byte terminator (0xFE continue / 0xFF end)
        rom[offset + 42] = (byte) (box.getTerminator() & 0xFF);
    }
}
