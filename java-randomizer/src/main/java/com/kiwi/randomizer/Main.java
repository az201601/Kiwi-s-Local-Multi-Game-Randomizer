package com.kiwi.randomizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line entry point for the Harvest Moon GB grandpa-letter patcher.
 *
 * <p>Usage:
 * <pre>
 *   java com.kiwi.randomizer.Main [--rom &lt;path&gt;] [--out &lt;path&gt;] [--text &lt;path&gt;] [--dump &lt;path&gt;]
 * </pre>
 *
 * <p>Defaults:
 * <ul>
 *   <li>--rom: "Harvest Moon GB (USA) (SGB Enhanced).gbc"</li>
 *   <li>--out: "Harvest Moon GB Randomizer.gbc"</li>
 *   <li>--text: built-in Randomizer welcome (same as grandpa_welcome.txt)</li>
 *   <li>--dump: if given, also write the decoded ORIGINAL letter to that file</li>
 * </ul>
 *
 * <p>The text file holds one entry per line (max 31 encoded bytes per line).
 * Use {PLAYER} for the player-name placeholder. See grandpa_welcome.txt.
 *
 * <p>Java 8 compatible.
 */
public final class Main {

    private static final String DEFAULT_ROM = "Harvest Moon GB (USA) (SGB Enhanced).gbc";
    private static final String DEFAULT_OUT = "Harvest Moon GB Randomizer.gbc";

    /** Built-in welcome text (identical to grandpa_welcome.txt). */
    private static final String[] DEFAULT_WELCOME = {
            "{PLAYER} WAKE UP {PLAYER} WELCOME TO",
            "HARVEST MOON RANDOMIZER EDITION",
            "EVERYTHING IS RANDOM EVERY RUN",
            "CROPS ANIMALS AND ITEMS CHANGE",
            "ITEMS MAY APPEAR IN OTHER GAMES",
            "ITEMS MAY COME FROM OTHER GAMES",
            "NO TWO FARMS ARE EVER THE SAME",
            "PLANT ANYTHING ANYWHERE ANYTIME",
            "MEET NEW FRIENDS NEW CHALLENGES",
            "THE HARVEST GODDESS WATCHES YOU",
            "GOOD LUCK FUTURE RANCH MASTER",
            "OH I FORGOT TO TELL YOU THAT",
            "THE SEASONS WILL SURPRISE YOU",
            "CHECK THE CALENDAR EVERY DAY",
            "IT IS ESSENTIAL FOR CROPS",
            "THANKS FOR PLAYING RANDOMIZER",
            "HAVE FUN AND ENJOY YOUR FARM",
            "GOOD LUCK AND HAPPY FARMING",
            "MAY YOUR HARVEST BE BOTH RANDOM",
            "AND BOUNTIFUL EVERY SINGLE DAY",
            "WELCOME TO YOUR NEW RANDOM FARM",
    };

    private Main() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        Path romPath = Paths.get(DEFAULT_ROM);
        Path outPath = Paths.get(DEFAULT_OUT);
        Path textPath = null;
        Path dumpPath = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--rom".equals(a) && i + 1 < args.length) {
                romPath = Paths.get(args[++i]);
            } else if ("--out".equals(a) && i + 1 < args.length) {
                outPath = Paths.get(args[++i]);
            } else if ("--text".equals(a) && i + 1 < args.length) {
                textPath = Paths.get(args[++i]);
            } else if ("--dump".equals(a) && i + 1 < args.length) {
                dumpPath = Paths.get(args[++i]);
            } else if ("--help".equals(a) || "-h".equals(a)) {
                printHelp();
                return;
            } else {
                System.err.println("Unknown argument: " + a);
                printHelp();
                System.exit(1);
            }
        }

        if (!Files.exists(romPath)) {
            System.err.println("ROM not found: " + romPath);
            System.err.println("Put the original .gbc next to the jar, or use --rom <path>.");
            System.exit(1);
        }

        List<String> lines;
        if (textPath != null) {
            lines = readTextFile(textPath);
            System.out.println("Loaded " + lines.size() + " lines from " + textPath);
        } else {
            lines = new ArrayList<String>(Arrays.asList(DEFAULT_WELCOME));
            System.out.println("Using built-in Randomizer welcome (" + lines.size() + " lines).");
        }
        for (int i = 0; i < lines.size(); i++) {
            int len = HarvestMoonCodec.encodedLength(lines.get(i));
            System.out.printf("  Entry %d (%d bytes): %s%n", 81 + i, len, lines.get(i));
            if (len > HarvestMoonCodec.ENTRY_TEXT_SIZE) {
                System.err.println("Error: line " + (i + 1) + " exceeds 31 encoded bytes.");
                System.exit(1);
            }
        }

        byte[] rom = Files.readAllBytes(romPath);
        GrandpaPatcher.verifyRomHeader(rom);

        if (dumpPath != null) {
            List<GrandpaPatcher.LetterLocation> copies = GrandpaPatcher.findLetterCopies(rom);
            if (!copies.isEmpty()) {
                List<String> original = GrandpaPatcher.dumpLetter(rom, copies.get(0));
                Files.write(dumpPath, String.join("\n", original).getBytes(StandardCharsets.UTF_8));
                System.out.println("Dumped original letter to " + dumpPath);
            }
        }

        GrandpaPatcher.patchRom(romPath, outPath, lines);

        // Verify: re-read output and decode
        byte[] patched = Files.readAllBytes(outPath);
        List<GrandpaPatcher.LetterLocation> after = GrandpaPatcher.findLetterCopies(patched);
        if (!after.isEmpty()) {
            System.out.println("Verify patched letter (first copy):");
            List<String> decoded = GrandpaPatcher.dumpLetter(patched, after.get(0));
            for (int i = 0; i < Math.min(lines.size(), decoded.size()); i++) {
                System.out.printf("  %d: %s%n", 81 + i, decoded.get(i));
            }
        }
        System.out.println("Done. Test the output ROM in an emulator before playing on hardware.");
    }

    private static List<String> readTextFile(Path path) throws IOException {
        List<String> raw = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<String>();
        for (String line : raw) {
            // Skip blank lines and comments starting with '#'
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            lines.add(t);
        }
        return lines;
    }

    private static void printHelp() {
        System.out.println("Harvest Moon GB Randomizer - Grandpa Letter Patcher");
        System.out.println("Usage: java com.kiwi.randomizer.Main [--rom <path>] [--out <path>] [--text <path>] [--dump <path>]");
        System.out.println("  --rom   original .gbc (default: " + DEFAULT_ROM + ")");
        System.out.println("  --out   patched .gbc (default: " + DEFAULT_OUT + ")");
        System.out.println("  --text  welcome text file, one entry per line, max 31 bytes/line (default: built-in)");
        System.out.println("  --dump  also dump decoded original letter to this file");
        System.out.println("Text file: use {PLAYER} for player name. Supported: A-Z, 0-9, space, '.', ',', '\\'', \"'S\", '(', ')', '!'.");
    }
}
