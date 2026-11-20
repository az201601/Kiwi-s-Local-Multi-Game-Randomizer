package kiwi;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        byte[] invalid = original.clone();
        invalid[0] ^= 1;
        try {
            HarvestMoon.patch(invalid, 945);
            throw new AssertionError("modified input must be rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("Unsupported ROM"), "wrong ROM message");
        }
        Path temp = Files.createTempDirectory("kiwi-java-test");
        Path output = temp.resolve("test.gbc");
        Path randomized = temp.resolve("randomized.gbc");
        try {
            runFormTest(output, false);
            check(hash(Files.readAllBytes(output)).equals(
                    "fa517358a1fd2604a75d04ddc7a87a4b4026f4d435c1be0401d9575af10b732d"), "GUI output matches Python patch");
            runFormTest(randomized, true);
            check(hash(Files.readAllBytes(randomized)).equals(
                    "f68589c0a78337130ef72af2dfd261be5e39046e56ffcd278429cabe1f1b1c42"), "GUI starting money matches Python patch");
            check(hash(HarvestMoon.patch(original, 945, 1)).equals(
                    "098b0d239e7731cdf1a9fb17090fe18cfe0bfed4f61209f5be11119db8b00e13"), "1G starting money matches Python patch");
            check(hash(Files.readAllBytes(rom)).equals(originalDigest), "original untouched");
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(randomized);
            Files.deleteIfExists(temp);
        }
        System.out.println("Java seed, ROM patch, and Swing form tests passed.");
    }

    private static void runFormTest(Path output, boolean randomizeStartingMoney) throws Exception {
        AtomicReference<JButton> button = new AtomicReference<>();
        AtomicReference<JTextArea> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JPanel form = new RandomizerApp.Form();
            find(form, JLabel.class, l -> l.getText().equals("Output ROM"), JTextField.class).setText(output.toString());
            find(form, JLabel.class, l -> l.getText().equals("Seed (optional)"), JTextField.class).setText("kiwi-demo");
            JCheckBox checkbox = find(form, JCheckBox.class, b -> b.getText().startsWith("Randomize starting money"));
            check(!checkbox.isSelected(), "starting money defaults to off");
            if (randomizeStartingMoney) checkbox.doClick(0);
            result.set(find(form, JTextArea.class, text -> true));
            button.set(find(form, JButton.class, b -> b.getText().equals("Generate patched ROM")));
            button.get().doClick(0);
        });
        String first = waitForResult(button.get(), result.get());
        check(first.contains("945G") && first.contains("kiwi-demo"), "GUI shows seed and payout");
        check(first.contains(randomizeStartingMoney ? "Starting money: 377G (randomized)"
                : "Starting money: 500G (unchanged)"), "GUI shows starting money and whether it is randomized");
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
