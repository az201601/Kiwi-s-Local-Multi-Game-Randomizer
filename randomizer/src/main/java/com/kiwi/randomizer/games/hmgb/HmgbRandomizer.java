package com.kiwi.randomizer.games.hmgb;

import com.kiwi.randomizer.core.GbChecksum;
import com.kiwi.randomizer.core.RomValidation;
import com.kiwi.randomizer.games.hmgb.HmgbDialoguePatcher.PatchReport;
import com.kiwi.randomizer.games.hmgb.HmgbTextCodec.DialogueBox;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Main Harvest Moon GB / GBC (USA, SGB Enhanced) ROM validator and patcher.
 */
public final class HmgbRandomizer {

    public static final String GAME_DISPLAY_NAME = "Harvest Moon GBC (USA)";
    public static final int EXPECTED_ROM_SIZE = 1048576; // 1 MB (64 x 16 KB banks)
    public static final String EXPECTED_ROM_TITLE = "HARVEST-MOON GB";
    public static final String VANILLA_USA_SHA1 = "d407b9c20c5381f46ec460858539a5b6f559e04f";

    private HmgbRandomizer() {
    }

    public static final class PatchResult {
        private final byte[] patchedRom;
        private final List<String> logLines;
        private final int headerChecksum;
        private final int globalChecksum;

        public PatchResult(byte[] patchedRom, List<String> logLines, int headerChecksum, int globalChecksum) {
            this.patchedRom = patchedRom;
            this.logLines = Collections.unmodifiableList(new ArrayList<String>(logLines));
            this.headerChecksum = headerChecksum;
            this.globalChecksum = globalChecksum;
        }

        public byte[] getPatchedRom() {
            return patchedRom;
        }

        public List<String> getLogLines() {
            return logLines;
        }

        public int getHeaderChecksum() {
            return headerChecksum;
        }

        public int getGlobalChecksum() {
            return globalChecksum;
        }
    }

    /**
     * Validates that the supplied bytes are a compatible Harvest Moon GB (USA) (SGB Enhanced).gbc ROM.
     */
    public static RomValidation validateRom(byte[] rom) {
        if (rom == null || rom.length == 0) {
            return new RomValidation(false, GAME_DISPLAY_NAME, "", 0, 0, "", false, false, false,
                    "ROM file is empty.");
        }
        String sha1 = RomValidation.sha1Hex(rom);
        if (rom.length != EXPECTED_ROM_SIZE) {
            return new RomValidation(
                    false, GAME_DISPLAY_NAME, readTitle(rom), rom.length, rom.length / 0x4000,
                    sha1, false, false, false,
                    String.format("Expected a 1,048,576-byte (1 MB) GBC ROM, got %,d bytes.",
                            Integer.valueOf(rom.length)));
        }

        String title = readTitle(rom);
        int cgbFlag = rom[0x0143] & 0xFF;
        boolean hdrOk = GbChecksum.readHeaderChecksum(rom) == GbChecksum.computeHeaderChecksum(rom);
        boolean glbOk = GbChecksum.readGlobalChecksum(rom) == GbChecksum.computeGlobalChecksum(rom);
        boolean exactVanilla = VANILLA_USA_SHA1.equalsIgnoreCase(sha1);

        if (!EXPECTED_ROM_TITLE.equals(title)) {
            return new RomValidation(
                    false, GAME_DISPLAY_NAME, title, rom.length, 64, sha1, false, hdrOk, glbOk,
                    "Unexpected ROM header title '" + title + "' (expected '" + EXPECTED_ROM_TITLE + "').");
        }
        if (cgbFlag != 0x80 && cgbFlag != 0xC0) {
            return new RomValidation(
                    false, GAME_DISPLAY_NAME, title, rom.length, 64, sha1, false, hdrOk, glbOk,
                    String.format("Unexpected CGB flag 0x%02X (expected 0x80 dual DMG/CGB).", Integer.valueOf(cgbFlag)));
        }
        if (!HmgbDialoguePatcher.hasValidGrandpaTable(rom)) {
            return new RomValidation(
                    false, GAME_DISPLAY_NAME, title, rom.length, 64, sha1, exactVanilla, hdrOk, glbOk,
                    "Master Dialogue Table at 0x0367C did not match Harvest Moon GB (USA).");
        }

        String status = exactVanilla
                ? "Verified clean vanilla ROM — Harvest Moon GB (USA) (SGB Enhanced).gbc"
                : "Compatible Harvest Moon GB (USA) GBC ROM (pre-modified / non-vanilla SHA-1)";
        return new RomValidation(
                true, GAME_DISPLAY_NAME, title, rom.length, 64, sha1, exactVanilla, hdrOk, glbOk, status);
    }

    public static String readTitle(byte[] rom) {
        if (rom == null || rom.length < 0x0143) {
            return "";
        }
        int end = 0x0143;
        while (end > 0x0134 && rom[end - 1] == 0x00) {
            end--;
        }
        return new String(rom, 0x0134, end - 0x0134, StandardCharsets.US_ASCII).trim();
    }

    /**
     * Applies selected options to a fresh copy of the base ROM and updates header + global checksums.
     */
    public static PatchResult apply(byte[] baseRom, HmgbOptions options) {
        RomValidation validation = validateRom(baseRom);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (options == null) {
            throw new IllegalArgumentException("Options cannot be null.");
        }

        byte[] out = Arrays.copyOf(baseRom, baseRom.length);
        List<String> logs = new ArrayList<String>();
        logs.add("Loaded base ROM: " + validation.getRomTitle() + " (SHA-1: " + validation.getSha1().substring(0, 12) + "...)");

        if (options.isPatchGrandpaText()) {
            List<DialogueBox> boxes = options.isManualBoxMode()
                    ? HmgbTextCodec.encodeManualBoxes(options.getGrandpaText())
                    : HmgbTextCodec.encodeAutoWrapped(options.getGrandpaText());
            PatchReport report = HmgbDialoguePatcher.patchGrandpaDialogue(out, boxes);
            logs.add(report.describe());
        } else {
            logs.add("Grandpa's Spirit intro text left unchanged.");
        }

        GbChecksum.updateChecksums(out);
        int hdr = GbChecksum.readHeaderChecksum(out);
        int glb = GbChecksum.readGlobalChecksum(out);
        logs.add(String.format("Updated checksums: Header (0x014D) = 0x%02X, Global (0x014E-0x014F) = 0x%04X.",
                Integer.valueOf(hdr), Integer.valueOf(glb)));
        logs.add("Output ROM ready!");

        return new PatchResult(out, logs, hdr, glb);
    }
}
