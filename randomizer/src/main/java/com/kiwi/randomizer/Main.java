package com.kiwi.randomizer;

import com.kiwi.randomizer.games.hmgb.HmgbDialoguePatcher;
import com.kiwi.randomizer.games.hmgb.HmgbOptions;
import com.kiwi.randomizer.games.hmgb.HmgbRandomizer;
import com.kiwi.randomizer.games.hmgb.HmgbRandomizer.PatchResult;
import com.kiwi.randomizer.games.hmgb.HmgbTextCodec.DialogueBox;
import com.kiwi.randomizer.ui.RandomizerFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Entry point for Kiwi's Local Multi-Game Randomizer (Java 8+).
 *
 * Double-click the JAR or run `java -jar KiwisMultiGameRandomizer.jar` to open the desktop GUI.
 * Also supports `--cli` flags for automated ROM verification and headless testing:
 *   java -jar KiwisMultiGameRandomizer.jar --cli --rom input.gbc --out output.gbc [--text welcome.txt] [--manual] [--dump]
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            int code = runCli(args);
            if (code != 0) {
                System.exit(code);
            }
            return;
        }

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(RandomizerFrame.APP_TITLE + " " + RandomizerFrame.APP_VERSION);
            System.err.println("No graphical display detected. Run on a desktop OS to launch the GUI,");
            System.err.println("or pass --help for headless CLI options.");
            System.exit(1);
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                installLookAndFeel();
                RandomizerFrame frame = new RandomizerFrame();
                frame.setVisible(true);
            }
        });
    }

    private static void installLookAndFeel() {
        try {
            // Prefer Nimbus if available for clean cross-platform button colors on Java 8+, else System L&F
            UIManager.LookAndFeelInfo[] installed = UIManager.getInstalledLookAndFeels();
            for (int i = 0; i < installed.length; i++) {
                if ("Nimbus".equals(installed[i].getName())) {
                    UIManager.setLookAndFeel(installed[i].getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Keep default Swing Look & Feel
        }
    }

    private static int runCli(String[] args) {
        String romPath = null;
        String outPath = null;
        String textPath = null;
        boolean manual = false;
        boolean dump = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--cli".equals(a)) {
                continue;
            } else if ("--help".equals(a) || "-h".equals(a)) {
                printUsage();
                return 0;
            } else if ("--rom".equals(a) && i + 1 < args.length) {
                romPath = args[++i];
            } else if ("--out".equals(a) && i + 1 < args.length) {
                outPath = args[++i];
            } else if ("--text".equals(a) && i + 1 < args.length) {
                textPath = args[++i];
            } else if ("--manual".equals(a)) {
                manual = true;
            } else if ("--dump".equals(a)) {
                dump = true;
            } else {
                System.err.println("Unknown argument: " + a);
                printUsage();
                return 2;
            }
        }

        if (romPath == null) {
            System.err.println("Missing required --rom <path>.");
            printUsage();
            return 2;
        }

        try {
            File inFile = new File(romPath);
            byte[] romBytes = Files.readAllBytes(inFile.toPath());

            if (dump) {
                List<DialogueBox> boxes = HmgbDialoguePatcher.readGrandpaDialogue(romBytes);
                System.out.println("Grandpa's Spirit dialogue (" + boxes.size() + " boxes):");
                for (int i = 0; i < boxes.size(); i++) {
                    DialogueBox b = boxes.get(i);
                    System.out.printf("  [%02d] |%s|%s| (0x%02X)%n",
                            Integer.valueOf(i + 1),
                            b.formatDisplayLine1("Kiwi"),
                            b.formatDisplayLine2("Kiwi"),
                            Integer.valueOf(b.getTerminator()));
                }
                if (outPath == null) {
                    return 0;
                }
            }

            if (outPath == null) {
                System.err.println("Missing required --out <path>.");
                return 2;
            }

            File outFile = new File(outPath);
            if (outFile.getCanonicalFile().equals(inFile.getCanonicalFile())) {
                System.err.println("Refusing to overwrite input ROM. Choose a different --out path.");
                return 2;
            }

            HmgbOptions opts = new HmgbOptions();
            opts.setPatchGrandpaText(true);
            opts.setManualBoxMode(manual);
            if (textPath != null) {
                String customText = new String(Files.readAllBytes(new File(textPath).toPath()), StandardCharsets.UTF_8);
                opts.setGrandpaText(customText);
            }

            PatchResult res = HmgbRandomizer.apply(romBytes, opts);
            File parent = outFile.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(outFile.toPath(), res.getPatchedRom());

            List<String> logs = res.getLogLines();
            for (int i = 0; i < logs.size(); i++) {
                System.out.println(logs.get(i));
            }
            System.out.println("Wrote: " + outFile.getAbsolutePath());
            return 0;
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            return 1;
        }
    }

    private static void printUsage() {
        System.out.println(RandomizerFrame.APP_TITLE + " " + RandomizerFrame.APP_VERSION);
        System.out.println("GUI mode:  java -jar KiwisMultiGameRandomizer.jar");
        System.out.println("CLI mode:  java -jar KiwisMultiGameRandomizer.jar --cli --rom <in.gbc> --out <out.gbc> [--text <file.txt>] [--manual] [--dump]");
    }
}
