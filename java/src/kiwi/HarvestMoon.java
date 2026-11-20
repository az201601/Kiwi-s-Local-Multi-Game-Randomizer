package kiwi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

/** ROM patching for the supported Harvest Moon GB (USA) revision. */
public final class HarvestMoon {
    public static final String ROM_NAME = "Harvest Moon GB (USA) (SGB Enhanced).gbc";
    private static final String ROM_HASH = "54fe95af5ded33631db84b6ba4ecc4a86f9f7528c9c56ca678150a3246d49623";
    private static final int ROM_SIZE = 1_048_576;
    public static final int VANILLA_STARTING_MONEY = 500;
    // Immediate bytes of LD A,imm instructions writing the new game's balance
    // to SRAM $B8EF/$B8F0; the adjacent high byte is already cleared by the game.
    private static final int[][] STARTING_MONEY_OFFSETS = {{0x752C4, 0x752C9}, {0xF527F, 0xF5284}};
    private static final int[] PAYOUT_OFFSETS = {0x99D7, 0x89944};
    private static final int[] RECEIPT_OFFSETS = {0x1C718, 0x9C6B5};

    private HarvestMoon() {}

    public static final class Result {
        private final Path output;
        private final String seed;
        private final int amount;
        private final int startingMoney;

        private Result(Path output, String seed, int amount, int startingMoney) {
            this.output = output;
            this.seed = seed;
            this.amount = amount;
            this.startingMoney = startingMoney;
        }

        public Path output() { return output; }
        public String seed() { return seed; }
        public int amount() { return amount; }
        public int startingMoney() { return startingMoney; }
    }

    public static Result generate(Path input, Path output, String seed, boolean randomizeStartingMoney)
            throws IOException {
        if (seed.isEmpty()) {
            byte[] token = new byte[16];
            new SecureRandom().nextBytes(token);
            seed = hex(token);
        }
        if (!Files.isRegularFile(input) || Files.size(input) != ROM_SIZE) {
            throw new IOException("Select the unmodified 1 MiB " + ROM_NAME + " file.");
        }
        int amount = roll(seed);
        int startingMoney = randomizeStartingMoney ? rollStartingMoney(seed) : VANILLA_STARTING_MONEY;
        byte[] patched = patch(Files.readAllBytes(input), amount, startingMoney);
        Path target = output.toAbsolutePath().normalize();
        if (target.getParent() == null) throw new IOException("Choose a new output filename.");
        Files.createDirectories(target.getParent());
        Files.write(target, patched, WRITE, CREATE_NEW);
        return new Result(target, seed, amount, startingMoney);
    }

    static int roll(String seed) {
        return new PythonSeedRandom(seed).oneTo(1000);
    }

    static int rollStartingMoney(String seed) {
        return new PythonSeedRandom("harvest-moon-gbc/starting-money:" + seed).oneTo(500);
    }

    static byte[] patch(byte[] source, int amount) {
        return patch(source, amount, VANILLA_STARTING_MONEY);
    }

    static byte[] patch(byte[] source, int amount, int startingMoney) {
        if (startingMoney < 1 || startingMoney > VANILLA_STARTING_MONEY) {
            throw new IllegalArgumentException("Starting money must be 1 to 500G.");
        }
        if (amount < 1 || amount > 1000) {
            throw new IllegalArgumentException("The payout must be 1 to 1,000G.");
        }
        if (source.length != ROM_SIZE || !hex(digest("SHA-256", source)).equals(ROM_HASH)) {
            throw new IllegalArgumentException("Unsupported ROM. Select the unmodified " + ROM_NAME + " file.");
        }
        byte[] patched = source.clone();
        for (int offset : PAYOUT_OFFSETS) {
            patched[offset] = (byte) amount;
            patched[offset + 1] = (byte) (amount >>> 8);
        }
        String receipt = (amount + "G!!       ").substring(0, 7);
        for (int offset : RECEIPT_OFFSETS) {
            for (int i = 0; i < receipt.length(); i++) {
                char c = receipt.charAt(i);
                patched[offset + i] = (byte) receiptGlyph(c);
            }
        }
        for (int[] offsets : STARTING_MONEY_OFFSETS) {
            patched[offsets[0]] = (byte) startingMoney;
            patched[offsets[1]] = (byte) (startingMoney >>> 8);
        }
        patched[0x14E] = patched[0x14F] = 0;
        int checksum = 0;
        for (byte b : patched) checksum += Byte.toUnsignedInt(b);
        patched[0x14E] = (byte) (checksum >>> 8);
        patched[0x14F] = (byte) checksum;
        return patched;
    }

    private static int receiptGlyph(char c) {
        switch (c) {
            case 'G': return 0x06;
            case '!': return 0x40;
            case ' ': return 0xEF;
            default: return 0x34 + c - '0';
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value & 0xff) >>> 4, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    static byte[] digest(String algorithm, byte[] input) {
        try {
            return MessageDigest.getInstance(algorithm).digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " missing from the Java runtime", e);
        }
    }
}
