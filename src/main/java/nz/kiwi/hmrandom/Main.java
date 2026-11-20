package nz.kiwi.hmrandom;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Command line entry point.
 *
 * <pre>
 *   java -jar hm-randomizer.jar generate --seed 12345 \
 *        --rom "Harvest Moon GB (USA) (SGB Enhanced).gbc" \
 *        --out output/patched.gbc
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                usage(System.out);
                return;
            }
            String command = args[0];
            if ("generate".equals(command)) {
                generate(args);
            } else if ("verify".equals(command)) {
                verify(args);
            } else if ("info".equals(command)) {
                info(args);
            } else {
                System.err.println("Unknown command: " + command);
                usage(System.err);
                exit(2);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e);
            exit(1);
        }
    }

    private static void generate(String[] args) throws Exception {
        Long seed = null;
        String romPath = null;
        String outPath = null;
        List<String> players = new ArrayList<String>();
        players.add("Player 1");

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--seed".equals(arg)) {
                seed = Long.parseLong(args[++i]);
            } else if ("--rom".equals(arg)) {
                romPath = args[++i];
            } else if ("--out".equals(arg)) {
                outPath = args[++i];
            } else if ("--players".equals(arg)) {
                players = new ArrayList<String>(Arrays.asList(args[++i].split(",")));
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (seed == null || romPath == null || outPath == null) {
            throw new IllegalArgumentException("generate needs --seed, --rom and --out");
        }

        Rom rom = new Rom(romPath);
        if (!rom.isSupportedBaseRom()) {
            throw new IllegalArgumentException(
                "This does not look like Harvest Moon GB (USA) (SGB Enhanced). Refusing to patch.");
        }

        World world = new HarvestMoonGb();
        Multiworld multiworld = new Multiworld(seed, players, Arrays.asList(world));
        Map<String, Item> assignment = multiworld.generate();
        List<Patch> patches = multiworld.patches(assignment);

        Patcher.apply(rom, patches);
        rom.save(outPath);

        String spoiler = multiworld.spoiler(assignment);
        System.out.println(spoiler);
        System.out.println("Patches applied:");
        for (Patch patch : patches) {
            System.out.println("  " + patch);
        }

        String logPath = outPath + ".spoiler.txt";
        PrintStream log = new PrintStream(Files.newOutputStream(Paths.get(logPath)));
        try {
            log.println(spoiler);
            log.println("Patches:");
            for (Patch patch : patches) {
                log.println("  " + patch);
            }
        } finally {
            log.close();
        }
        System.out.println("Wrote " + outPath + " and " + logPath);
    }

    private static void verify(String[] args) throws Exception {
        String romPath = null;
        for (int i = 1; i < args.length; i++) {
            if ("--rom".equals(args[i])) {
                romPath = args[++i];
            }
        }
        if (romPath == null) {
            throw new IllegalArgumentException("verify needs --rom");
        }
        Rom rom = new Rom(romPath);
        System.out.println("ROM: " + rom);
        System.out.println("Title: " + rom.readAscii(0x134, 15));
        int amount = rom.readWordLE(RomData.DRESSER_MONEY_IMMEDIATE);
        System.out.println("Dresser money immediate: " + amount);
        System.out.println("Dresser text: " + rom.readText(RomData.TEXT_RECEIVED));
        System.out.println("Dresser stub: " + hex(rom, RomData.DRESSER_REWARD_STUB, 10));
        System.out.println("give_item:   " + hex(rom, RomData.GIVE_ITEM_ROUTINE, RomData.GIVE_ITEM_ROUTINE_LEN));
    }

    private static void info(String[] args) {
        World world = new HarvestMoonGb();
        System.out.println("World: " + world.name());
        System.out.println("Items (" + world.items().size() + "):");
        for (Item item : world.items()) {
            System.out.println("  " + item.id() + " -> " + item.name() + " (" + item.kind() + ")");
        }
        System.out.println("Locations (" + world.locations().size() + "):");
        for (Location location : world.locations()) {
            System.out.println("  " + location.id() + " -> " + location.name() + " [" + location.region() + "]");
        }
    }

    private static String hex(Rom rom, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X ", rom.readByte(offset + i)));
        }
        return sb.toString().trim();
    }

    private static void exit(int code) {
        Runtime.getRuntime().exit(code);
    }

    private static void usage(PrintStream out) {
        out.println("Kiwi local multiworld randomizer - step 1: Harvest Moon GB");
        out.println();
        out.println("Commands:");
        out.println("  generate --seed <n> --rom <base.gbc> --out <patched.gbc> [--players a,b]");
        out.println("  verify   --rom <patched.gbc>");
        out.println("  info");
    }
}
