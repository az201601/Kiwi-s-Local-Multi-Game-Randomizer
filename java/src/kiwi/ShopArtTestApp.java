package kiwi;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.GraphicsEnvironment;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

/** A separate test launcher for the fixed mixed-shop artwork prototype. */
public final class ShopArtTestApp {
    private static final String OUTPUT_NAME = "harvest-moon-mixed-shop-test.gbc";
    private static final String TITLE = "Harvest Moon GB / GBC — Mixed Shop Test";

    private ShopArtTestApp() {}

    public static void main(String[] args) {
        if (args.length == 2) {
            try {
                System.out.println("Wrote " + generate(Paths.get(args[0]), Paths.get(args[1])));
            } catch (IOException | IllegalArgumentException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        } else if (args.length == 0 && !GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(ShopArtTestApp::chooseFiles);
        } else {
            System.err.println("Usage: java -jar kiwi-shop-art-test-java8.jar ORIGINAL.gbc OUTPUT.gbc");
            System.exit(2);
        }
    }

    private static void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Game Boy Color ROM (*.gbc)", "gbc"));
        chooser.setDialogTitle("Choose the original Harvest Moon GB (USA) ROM");
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File source = chooser.getSelectedFile();
        chooser.setDialogTitle("Save the mixed-shop test ROM as a new file");
        chooser.setSelectedFile(new File(source.getParentFile(), OUTPUT_NAME));
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path output = generate(source.toPath(), chooser.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(null, "Open this ROM in your emulator:\n" + output
                    + "\n\nIn Spring, visit Eve at 6 PM to see Turnip and Potato seeds."
                    + "\nApple Juice and Milk move to Nina and keep their unlocks.",
                    TITLE, JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Path generate(Path input, Path output) throws IOException {
        if (Files.size(input) != 1_048_576) {
            throw new IOException("Select the unmodified " + HarvestMoon.ROM_NAME + " file.");
        }
        byte[] rom = Files.readAllBytes(input);
        if (!HarvestMoon.ROM_HASH.equals(HarvestMoon.hex(HarvestMoon.digest("SHA-256", rom)))) {
            throw new IOException("Select the unmodified " + HarvestMoon.ROM_NAME + " file.");
        }
        try (InputStream resource = ShopArtTestApp.class.getResourceAsStream("shop-art.dat")) {
            if (resource == null) throw new IOException("This JAR is missing the prototype patch.");
            DataInputStream patch = new DataInputStream(resource);
            int count = patch.readUnsignedShort();
            int lastEnd = 0;
            for (int i = 0; i < count; i++) {
                int offset = patch.readInt();
                int length = patch.readUnsignedShort();
                if (offset < lastEnd || length == 0 || offset > rom.length - length) {
                    throw new IOException("Invalid prototype patch data.");
                }
                patch.readFully(rom, offset, length);
                lastEnd = offset + length;
            }
            if (patch.read() != -1) throw new IOException("Invalid trailing prototype patch data.");
        }
        Path target = output.toAbsolutePath().normalize();
        if (Files.exists(target)) throw new IOException("Output already exists. Choose a new filename: " + target);
        return Files.write(target, rom, CREATE_NEW, WRITE);
    }
}
