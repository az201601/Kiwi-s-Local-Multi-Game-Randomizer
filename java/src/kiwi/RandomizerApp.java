package kiwi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/** Standalone desktop entry point. No Python or emulator needed to patch a ROM. */
public final class RandomizerApp {
    private static final Color INK = new Color(0x203B32);
    private static final Color GREEN = new Color(0x235844);
    private static final Color CREAM = new Color(0xF3F5ED);
    private static final Color BORDER = new Color(0xD3DED1);
    private static final Color ERROR_INK = new Color(0x992F23);
    private static final String DEFAULT_FILENAME = "harvest-moon-randomized.gbc";

    private RandomizerApp() {}

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The Kiwi randomizer GUI needs a desktop display. See README.md for the Python CLI.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kiwi’s Local Multi-Game Randomizer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new Form());
            frame.pack();
            frame.setMinimumSize(frame.getSize());
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    static final class Form extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JTextField source = new JTextField(36);
        private final JTextField destination = new JTextField(Paths.get("output", DEFAULT_FILENAME).toString(), 36);
        private final JTextField seed = new JTextField(36);
        private final JCheckBox startingMoney = new JCheckBox("Randomize starting money (1–500G)");
        private final JButton open = new JButton("Browse…");
        private final JButton save = new JButton("Choose…");
        private final JButton generate = new JButton("Generate patched ROM");
        private final JTextArea result = new JTextArea(5, 35);

        Form() {
            super(new BorderLayout(0, 18));
            setBackground(CREAM);
            setBorder(new EmptyBorder(24, 28, 24, 28));
            add(header(), BorderLayout.NORTH);
            add(settings(), BorderLayout.CENTER);
            JPanel footer = new JPanel(new BorderLayout(0, 10));
            footer.setOpaque(false);
            generate.setBackground(GREEN);
            generate.setForeground(Color.WHITE);
            generate.setOpaque(true);
            generate.setFocusPainted(true);
            generate.addActionListener(e -> generate());
            footer.add(generate, BorderLayout.NORTH);
            result.setEditable(false);
            result.setLineWrap(true);
            result.setWrapStyleWord(true);
            result.setFont(UIManager.getFont("Label.font"));
            result.setBorder(new EmptyBorder(9, 12, 9, 12));
            result.getAccessibleContext().setAccessibleName("Generation result");
            JScrollPane report = new JScrollPane(result);
            report.setBorder(BorderFactory.createLineBorder(BORDER));
            footer.add(report, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);
            if (Files.isRegularFile(Paths.get(HarvestMoon.ROM_NAME))) source.setText(HarvestMoon.ROM_NAME);
            status("Ready. Generate a separate ROM, then open it in your emulator.", false);
        }

        @Override public void addNotify() {
            super.addNotify();
            JRootPane root = SwingUtilities.getRootPane(this);
            if (root != null) root.setDefaultButton(generate);
        }

        private JPanel header() {
            JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
            header.setBackground(GREEN);
            header.setBorder(new EmptyBorder(16, 18, 16, 18));
            JLabel title = new JLabel("Kiwi’s Local Multi-Game Randomizer");
            title.setForeground(Color.WHITE);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
            header.add(title);
            JLabel game = new JLabel("HARVEST MOON GB / GBC");
            game.setForeground(new Color(0xDCF1D5));
            header.add(game);
            return header;
        }

        private JPanel settings() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(12, 14, 12, 14)));
            JLabel feature = new JLabel("Grandpa’s Rainy Day Money   ·   1–1,000G");
            feature.setFont(feature.getFont().deriveFont(Font.BOLD, 16f));
            feature.setForeground(INK);
            panel.add(feature, across(0));
            addField(panel, 1, "Original ROM", source, open);
            addField(panel, 2, "Output ROM", destination, save);
            addField(panel, 3, "Seed (optional)", seed, null);
            startingMoney.setOpaque(false);
            startingMoney.setToolTipText("Changes the money you start with in a new game. Default: 500G.");
            panel.add(startingMoney, across(4));
            panel.add(new JLabel("Leave the seed blank for a fresh roll. Use the same options to repeat a seed."), across(5));
            open.addActionListener(e -> choose(false));
            save.addActionListener(e -> choose(true));
            return panel;
        }

        private static GridBagConstraints across(int row) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.gridwidth = 3;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(4, 3, 8, 3);
            return c;
        }

        private static void addField(JPanel panel, int row, String name, JTextField field, JButton browse) {
            GridBagConstraints c = across(row);
            c.gridwidth = 1;
            c.insets = new Insets(4, 3, 4, 3);
            JLabel label = new JLabel(name);
            label.setLabelFor(field);
            panel.add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            c.gridwidth = browse == null ? 2 : 1;
            panel.add(field, c);
            if (browse != null) {
                c.gridx = 2;
                c.weightx = 0;
                browse.getAccessibleContext().setAccessibleName("Choose " + name.toLowerCase(Locale.ROOT));
                panel.add(browse, c);
            }
        }

        private void choose(boolean output) {
            JFileChooser picker = new JFileChooser();
            picker.setFileFilter(new FileNameExtensionFilter(output ? "Game Boy Color ROM (*.gbc)" :
                    "Game Boy ROM (*.gb, *.gbc)", output ? new String[]{"gbc"} : new String[]{"gb", "gbc"}));
            JTextField field = output ? destination : source;
            if (!field.getText().trim().isEmpty()) {
                try {
                    Path existing = Paths.get(field.getText().trim()).toAbsolutePath();
                    if (existing.getParent() != null && Files.isDirectory(existing.getParent())) picker.setSelectedFile(existing.toFile());
                    else if (output) picker.setSelectedFile(Paths.get(DEFAULT_FILENAME).toFile());
                } catch (InvalidPathException ignored) {
                    // Let the picker replace an invalid typed path.
                }
            }
            int choice = output ? picker.showSaveDialog(this) : picker.showOpenDialog(this);
            if (choice == JFileChooser.APPROVE_OPTION) {
                String path = picker.getSelectedFile().getAbsolutePath();
                if (output && !path.toLowerCase(Locale.ROOT).endsWith(".gbc")) path += ".gbc";
                field.setText(path);
            }
        }

        private void generate() {
            if (source.getText().trim().isEmpty() || destination.getText().trim().isEmpty()) {
                status("Choose both the original ROM and a new output file.", true);
                return;
            }
            final Path input;
            final Path output;
            try {
                input = Paths.get(source.getText().trim());
                output = Paths.get(destination.getText().trim());
                if (!output.toString().toLowerCase(Locale.ROOT).endsWith(".gbc")) {
                    status("The output filename should end with .gbc.", true);
                    return;
                }
            } catch (InvalidPathException e) {
                status("Enter a valid path for the original ROM and for the output file.", true);
                return;
            }
            String chosenSeed = seed.getText();
            boolean randomizeStartingMoney = startingMoney.isSelected();
            setBusy(true);
            status("Validating and patching the ROM…", false);
            new SwingWorker<HarvestMoon.Result, Void>() {
                @Override protected HarvestMoon.Result doInBackground() throws Exception {
                    return HarvestMoon.generate(input, output, chosenSeed, randomizeStartingMoney);
                }

                @Override protected void done() {
                    try {
                        HarvestMoon.Result saved = get();
                        status("Grandpa’s Rainy Day Money: " + saved.amount() + "G"
                                + "\nStarting money: " + saved.startingMoney() + "G"
                                + (randomizeStartingMoney ? " (randomized)" : " (unchanged)")
                                + "\nSeed: " + saved.seed() + "\nSaved to: " + saved.output(), false);
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        status(cause instanceof FileAlreadyExistsException
                                ? "The output already exists. Choose a new filename; no file was overwritten."
                                : "Could not generate the ROM: " + cause.getMessage(), true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        status("Generation was interrupted.", true);
                    } finally {
                        setBusy(false);
                    }
                }
            }.execute();
        }

        private void setBusy(boolean busy) {
            for (JComponent field : new JComponent[]{source, destination, seed, startingMoney, open, save, generate}) {
                field.setEnabled(!busy);
            }
        }

        private void status(String message, boolean error) {
            result.setForeground(error ? ERROR_INK : INK);
            result.setText(message);
            result.setCaretPosition(0);
        }
    }
}
