package kiwi;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Dependency-free Java regression and Swing form smoke test. */
public final class RandomizerTest {
    public static void main(String[] args) throws Exception {
        check(HarvestMoon.roll("kiwi-demo") == 945, "demo seed parity");
        check(HarvestMoon.roll("Kākāpō 🌧") == 96, "UTF-8 seed parity");
        check(HarvestMoon.roll("reject-43") == 614, "seed parity when the first draw is rejected");
        check(HarvestMoon.roll(new String(new char[100]).replace('\0', 'a')) == 894, "long seed parity");
        check(HarvestMoon.rollStartingMoney("kiwi-demo") == 377, "starting money seed parity");
        for (int i = 0; i < 100; i++) {
            int start = HarvestMoon.rollStartingMoney("start-test-" + i);
            check(start >= 1 && start <= 500, "starting balance in range");
        }
        check(Arrays.deepEquals(SeedShop.inventory("nina-2", true, true),
                new int[][]{{3, 4, 8}, {0, 8, 5}, {8, 1, 7}, {2, 6, 255}}), "seed shop fixture order");
        Path rom = Paths.get(HarvestMoon.ROM_NAME);
        if (!Files.isRegularFile(rom)) {
            System.out.println("Java seed tests passed; ROM and GUI generation checks skipped (ROM not found).");
            return;
        }
        byte[] original = Files.readAllBytes(rom);
        String originalDigest = hash(original);
        check(hash(HarvestMoon.patch(original, 1)).equals(
                "2beef0e0ca7da2961c60902659ca372df5a16b864ba81648d98d14c7ac0f226b"), "minimum matches Python patch");
        check(hash(HarvestMoon.patch(original, 1000)).equals(originalDigest), "maximum is vanilla");
        check(hash(HarvestMoon.patch(original, 1000, 500, "nina-2", true, true)).equals(
                "f18c9fc6d48ec6a59a4e6c5f25cfdc3072ddb72ccb0a11e00ca793365c5dfb87"),
                "crop palette collision fix matches Python patch");
        byte[] invalid = original.clone();
        invalid[0] ^= 1;
        try {
            HarvestMoon.patch(invalid, 945);
            throw new AssertionError("modified input must be rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("Unsupported ROM"), "wrong ROM message");
        }
        Path temp = Files.createTempDirectory("kiwi-java-test");
        try {
            testOptionPair(temp, false, false, originalDigest);
            testOptionPair(temp, false, true,
                    "7e16173d4cfa7837cc598d734e4b4c4a536249c2d556c7ba9351993a0958e80f");
            testOptionPair(temp, true, false,
                    "fa517358a1fd2604a75d04ddc7a87a4b4026f4d435c1be0401d9575af10b732d");
            testOptionPair(temp, true, true,
                    "f68589c0a78337130ef72af2dfd261be5e39046e56ffcd278429cabe1f1b1c42");
            testOptions(temp, true, true, true, false,
                    "3a48013d4a5eb8541f5817ab0bdf4876af2c0e068dcc437b24582a346a619aa8");
            testOptions(temp, true, true, false, true,
                    "2a9b7cd1c91961d3ed3916452b482e5023c2ef591b02e038c15d161d4b529ad5");
            testOptions(temp, true, true, true, true,
                    "417bb4fb77df9a1822c2be9715c86d963c564c257b87ee7a6851020ed2ed99e6");
            check(hash(HarvestMoon.patch(original, 945, 1)).equals(
                    "098b0d239e7731cdf1a9fb17090fe18cfe0bfed4f61209f5be11119db8b00e13"), "1G starting money matches Python patch");
            check(hash(Files.readAllBytes(rom)).equals(originalDigest), "original untouched");
        } finally {
            Files.deleteIfExists(temp);
        }
        System.out.println("Java seed, ROM patch, and Swing form tests passed.");
    }

    private static void testOptionPair(Path temp, boolean rainyDayMoney, boolean startingMoney,
                                       String expectedDigest) throws Exception {
        testOptions(temp, rainyDayMoney, startingMoney, false, false, expectedDigest);
    }

    private static void testOptions(Path temp, boolean rainyDayMoney, boolean startingMoney,
                                    boolean seedStock, boolean seedSlots, String expectedDigest) throws Exception {
        Path output = temp.resolve("test.gbc");
        try {
            runFormTest(output, rainyDayMoney, startingMoney, seedStock, seedSlots);
            check(hash(Files.readAllBytes(output)).equals(expectedDigest), "GUI output matches Python patch");
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static void runFormTest(Path output, boolean randomizeRainyDayMoney, boolean randomizeStartingMoney,
                                    boolean randomizeSeedStock, boolean shuffleSeedSlots) throws Exception {
        AtomicReference<JButton> button = new AtomicReference<>();
        AtomicReference<JTextArea> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JPanel form = new RandomizerApp.Form();
            find(form, JLabel.class, l -> l.getText().equals("Output ROM"), JTextField.class).setText(output.toString());
            find(form, JLabel.class, l -> l.getText().equals("Seed (optional)"), JTextField.class).setText("kiwi-demo");
            JCheckBox rainyDay = find(form, JCheckBox.class, b -> b.getText().startsWith("Randomize Grandpa"));
            JCheckBox starting = find(form, JCheckBox.class, b -> b.getText().startsWith("Randomize starting money"));
            check(rainyDay.isSelected(), "drawer randomization defaults to on");
            check(!starting.isSelected(), "starting money randomization defaults to off");
            JCheckBox stock = find(form, JCheckBox.class, b -> b.getText().startsWith("Randomize seasonal seed"));
            JCheckBox slots = find(form, JCheckBox.class, b -> b.getText().startsWith("Shuffle seed shop"));
            check(!stock.isSelected() && !slots.isSelected(), "seed shop options default to off");
            if (randomizeSeedStock) stock.doClick(0);
            if (shuffleSeedSlots) slots.doClick(0);
            if (!randomizeRainyDayMoney) rainyDay.doClick(0);
            if (randomizeStartingMoney) starting.doClick(0);
            result.set(find(form, JTextArea.class, text -> true));
            button.set(find(form, JButton.class, b -> b.getText().equals("Generate patched ROM")));
            button.get().doClick(0);
        });
        String first = waitForResult(button.get(), result.get());
        check(first.contains("kiwi-demo"), "GUI shows seed");
        check(first.contains(randomizeRainyDayMoney ? "945G (randomized)" : "1000G (unchanged)"),
                "GUI shows drawer payout and whether it is randomized");
        check(first.contains(randomizeStartingMoney ? "Starting money: 377G (randomized)"
                : "Starting money: 500G (unchanged)"), "GUI shows starting money and whether it is randomized");
        check(first.contains(randomizeSeedStock ? "randomized stock" : "original stock"), "GUI shows stock setting");
        check(first.contains(shuffleSeedSlots ? "shuffled slots" : "original slot ordering"), "GUI shows slots setting");
        SwingUtilities.invokeAndWait(() -> button.get().doClick(0));
        check(waitForResult(button.get(), result.get()).contains("no file was overwritten"), "GUI reports duplicate output");
    }

    private static String waitForResult(JButton button, JTextArea result) throws Exception {
        AtomicReference<String> message = new AtomicReference<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (message.get() == null && System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> {
                if (button.isEnabled()) message.set(result.getText());
            });
            if (message.get() == null) Thread.sleep(20);
        }
        check(message.get() != null, "GUI generation timed out");
        return message.get();
    }

    private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> matches) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && matches.test(type.cast(child))) return type.cast(child);
            if (child instanceof Container) {
                T nested = find((Container) child, type, matches);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static <T extends Component> T find(Container root, Class<JLabel> type, Predicate<JLabel> matches,
                                                Class<T> fieldType) {
        return fieldType.cast(find(root, type, matches).getLabelFor());
    }

    private static String hash(byte[] bytes) {
        return HarvestMoon.hex(HarvestMoon.digest("SHA-256", bytes));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
