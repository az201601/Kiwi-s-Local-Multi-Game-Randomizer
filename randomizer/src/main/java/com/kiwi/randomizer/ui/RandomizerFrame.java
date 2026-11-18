package com.kiwi.randomizer.ui;

import com.kiwi.randomizer.core.RomValidation;
import com.kiwi.randomizer.games.hmgb.HmgbDialoguePatcher;
import com.kiwi.randomizer.games.hmgb.HmgbFontRenderer;
import com.kiwi.randomizer.games.hmgb.HmgbOptions;
import com.kiwi.randomizer.games.hmgb.HmgbRandomizer;
import com.kiwi.randomizer.games.hmgb.HmgbRandomizer.PatchResult;
import com.kiwi.randomizer.games.hmgb.HmgbTextCodec;
import com.kiwi.randomizer.games.hmgb.HmgbTextCodec.DialogueBox;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Main desktop GUI window for Kiwi's Local Multi-Game Randomizer.
 * Strictly Java 8 compatible (Swing / AWT, zero external libraries).
 */
public final class RandomizerFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    public static final String APP_TITLE = "Kiwi's Local Multi-Game Randomizer";
    public static final String APP_VERSION = "v0.1.0";

    private static final Color HEADER_BG = new Color(0x23, 0x57, 0x2B);
    private static final Color HEADER_FG = new Color(0xFA, 0xF8, 0xEE);
    private static final Color HEADER_SUB_FG = new Color(0xD2, 0xEC, 0xB8);
    private static final Color ACCENT_GREEN = new Color(0x1F, 0x6E, 0x2C);
    private static final Color ACCENT_BLUE = new Color(0x1B, 0x52, 0x8A);
    private static final Color ACCENT_RED = new Color(0xA6, 0x1E, 0x1E);
    private static final Color MUTED_FG = new Color(0x50, 0x55, 0x50);
    private static final Color CARD_BORDER = new Color(0xC8, 0xCE, 0xC4);

    // State
    private File loadedRomFile;
    private byte[] loadedRomBytes;
    private List<DialogueBox> previewBoxes = Collections.emptyList();
    private int currentBoxIndex = 0;
    private boolean suppressListEvents = false;

    // ROM bar controls
    private final JComboBox<String> gameSelector;
    private final JTextField inputRomField;
    private final JTextField outputRomField;
    private final JLabel romStatusLabel;

    // Grandpa editor controls
    private final JCheckBox enableGrandpaCheck;
    private final JRadioButton autoWrapRadio;
    private final JRadioButton manualModeRadio;
    private final JTextArea scriptArea;
    private final JButton readRomTextButton;

    // Live preview controls
    private final DialoguePreviewPanel dialoguePreviewPanel;
    private final JButton prevBoxButton;
    private final JButton nextBoxButton;
    private final JLabel pageCounterLabel;
    private final JTextField sampleNameField;
    private final JLabel boxStatusLabel;
    private final DefaultListModel<String> boxListModel;
    private final JList<String> boxList;

    // Footer controls
    private final JTextArea logArea;
    private final JButton generateButton;

    public RandomizerFrame() {
        super(APP_TITLE + " " + APP_VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 680));
        setPreferredSize(new Dimension(1020, 740));

        JPanel root = new JPanel(new BorderLayout(0, 0));
        setContentPane(root);

        // 1. Header Banner
        root.add(buildHeaderBanner(), BorderLayout.NORTH);

        // 2. Main Body (ROM Picker + Tabs + Footer)
        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBorder(new EmptyBorder(10, 12, 10, 12));
        root.add(body, BorderLayout.CENTER);

        // Initialize fields
        gameSelector = new JComboBox<String>(new String[] { HmgbRandomizer.GAME_DISPLAY_NAME });
        inputRomField = new JTextField();
        inputRomField.setEditable(false);
        outputRomField = new JTextField();
        romStatusLabel = new JLabel("Select your clean Harvest Moon GB (USA) (SGB Enhanced).gbc ROM to begin.");
        romStatusLabel.setForeground(MUTED_FG);

        enableGrandpaCheck = new JCheckBox("Replace Grandpa's Spirit opening text", true);
        enableGrandpaCheck.setFont(enableGrandpaCheck.getFont().deriveFont(Font.BOLD));
        autoWrapRadio = new JRadioButton("Auto word-wrap", true);
        manualModeRadio = new JRadioButton("Manual (2 lines/box)", false);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(autoWrapRadio);
        modeGroup.add(manualModeRadio);

        scriptArea = new JTextArea(HmgbOptions.DEFAULT_GRANDPA_WELCOME, 14, 34);
        scriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        scriptArea.setLineWrap(true);
        scriptArea.setWrapStyleWord(true);
        scriptArea.setMargin(new Insets(6, 8, 6, 8));

        readRomTextButton = new JButton("Load From ROM");
        readRomTextButton.setEnabled(false);

        dialoguePreviewPanel = new DialoguePreviewPanel();
        prevBoxButton = new JButton("\u25C0 Prev");
        nextBoxButton = new JButton("Next \u25B6");
        pageCounterLabel = new JLabel("Box 1 / 1");
        pageCounterLabel.setFont(pageCounterLabel.getFont().deriveFont(Font.BOLD));
        sampleNameField = new JTextField("Kiwi", 5);
        sampleNameField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));

        boxStatusLabel = new JLabel(" ");
        boxListModel = new DefaultListModel<String>();
        boxList = new JList<String>(boxListModel);
        boxList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        boxList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        logArea = new JTextArea(3, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setMargin(new Insets(4, 6, 4, 6));

        generateButton = new JButton("Generate Randomized ROM");
        generateButton.setFont(generateButton.getFont().deriveFont(Font.BOLD, 14f));
        String lafName = UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getName() : "";
        if ("Nimbus".equalsIgnoreCase(lafName) || "Metal".equalsIgnoreCase(lafName)) {
            generateButton.setBackground(new Color(0x2A, 0x7B, 0x34));
            generateButton.setForeground(Color.WHITE);
        }
        generateButton.setFocusPainted(false);
        generateButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        generateButton.setPreferredSize(new Dimension(240, 42));
        generateButton.setEnabled(false);

        body.add(buildRomSection(), BorderLayout.NORTH);
        body.add(buildCenterTabs(), BorderLayout.CENTER);
        body.add(buildFooterSection(), BorderLayout.SOUTH);

        wireEvents();
        refreshPreview(true);
        autoDetectNearbyRom();

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildHeaderBanner() {
        JPanel banner = new JPanel(new BorderLayout(12, 2));
        banner.setBackground(HEADER_BG);
        banner.setBorder(new EmptyBorder(10, 16, 10, 16));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(APP_TITLE);
        title.setForeground(HEADER_FG);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JLabel sub = new JLabel("Harvest Moon GBC Randomizer  \u2022  Step 1: Grandpa's Spirit Welcome Speech");
        sub.setForeground(HEADER_SUB_FG);
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 12f));

        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(sub);

        JLabel badge = new JLabel(APP_VERSION + "  (Java 8+)");
        badge.setForeground(HEADER_SUB_FG);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 12f));

        banner.add(titles, BorderLayout.WEST);
        banner.add(badge, BorderLayout.EAST);
        return banner;
    }

    private JPanel buildRomSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(createSectionBorder("ROM Selection"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Game + Base ROM
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Game:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        panel.add(gameSelector, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Base ROM:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 1.0;
        panel.add(inputRomField, gbc);

        JButton browseInputBtn = new JButton("Browse ROM...");
        browseInputBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseInputRom();
            }
        });
        gbc.gridx = 4;
        gbc.weightx = 0;
        panel.add(browseInputBtn, gbc);

        // Row 1: ROM validation status
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 5;
        gbc.weightx = 1.0;
        panel.add(romStatusLabel, gbc);
        gbc.gridwidth = 1;

        // Row 2: Output ROM path
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Output:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        panel.add(outputRomField, gbc);
        gbc.gridwidth = 1;

        JButton browseOutputBtn = new JButton("Save As...");
        browseOutputBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseOutputRom();
            }
        });
        gbc.gridx = 4;
        gbc.weightx = 0;
        panel.add(browseOutputBtn, gbc);

        return panel;
    }

    private JTabbedPane buildCenterTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Grandpa's Spirit Intro", buildGrandpaTab());
        return tabs;
    }

    private JPanel buildGrandpaTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));
        tab.setBorder(new EmptyBorder(6, 6, 6, 6));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildEditorPane(), buildPreviewPane());
        split.setResizeWeight(0.50);
        split.setContinuousLayout(true);
        split.setBorder(null);
        tab.add(split, BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildEditorPane() {
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBorder(createSectionBorder("Grandpa's Spirit Dialogue Editor"));

        // Top controls: checkbox + wrap mode + preset buttons
        JPanel topControls = new JPanel();
        topControls.setLayout(new BoxLayout(topControls, BoxLayout.Y_AXIS));

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modeRow.add(enableGrandpaCheck);
        modeRow.add(Box.createHorizontalStrut(8));
        modeRow.add(autoWrapRadio);
        modeRow.add(manualModeRadio);

        JPanel presetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton defaultBtn = new JButton("Default Welcome");
        JButton shortBtn = new JButton("Short Preset");
        JButton insertPlayerBtn = new JButton("+ {PLAYER}");
        JButton insertHeartBtn = new JButton("+ \u2665");
        JButton loadTxtBtn = new JButton("Import .txt");
        JButton saveTxtBtn = new JButton("Export .txt");

        defaultBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                autoWrapRadio.setSelected(true);
                scriptArea.setText(HmgbOptions.DEFAULT_GRANDPA_WELCOME);
                scriptArea.setCaretPosition(0);
            }
        });
        shortBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                autoWrapRadio.setSelected(true);
                scriptArea.setText(HmgbOptions.SHORT_GRANDPA_WELCOME);
                scriptArea.setCaretPosition(0);
            }
        });
        readRomTextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadDialogueFromCurrentRom();
            }
        });
        insertPlayerBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scriptArea.replaceSelection("{PLAYER}");
                scriptArea.requestFocusInWindow();
            }
        });
        insertHeartBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scriptArea.replaceSelection("{HEART}");
                scriptArea.requestFocusInWindow();
            }
        });
        loadTxtBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                importScriptFile();
            }
        });
        saveTxtBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportScriptFile();
            }
        });

        presetRow.add(defaultBtn);
        presetRow.add(shortBtn);
        presetRow.add(readRomTextButton);
        presetRow.add(insertPlayerBtn);
        presetRow.add(insertHeartBtn);
        presetRow.add(loadTxtBtn);
        presetRow.add(saveTxtBtn);

        topControls.add(modeRow);
        topControls.add(presetRow);
        left.add(topControls, BorderLayout.NORTH);

        JScrollPane editorScroll = new JScrollPane(scriptArea);
        left.add(editorScroll, BorderLayout.CENTER);

        JLabel helpLabel = new JLabel(
                "<html><span style='color:#4a524a;'>Tags: <b>{PLAYER}</b> (4-char player name), "
                        + "<b>{HEART}</b> (\u2665), <b>{...}</b> (\u2026). "
                        + "Blank line starts a new box. Supports A-Z, a-z, 0-9, . , ! ? ' - ( ) [ ] ~</span></html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.PLAIN, 11f));
        left.add(helpLabel, BorderLayout.SOUTH);

        return left;
    }

    private JPanel buildPreviewPane() {
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setBorder(createSectionBorder("Live In-Game Box Preview (16x2 Tiles)"));

        JPanel topBox = new JPanel(new BorderLayout(4, 4));
        topBox.add(dialoguePreviewPanel, BorderLayout.CENTER);

        JPanel navRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        navRow.add(prevBoxButton);
        navRow.add(pageCounterLabel);
        navRow.add(nextBoxButton);
        navRow.add(Box.createHorizontalStrut(10));
        navRow.add(new JLabel("Test Name:"));
        navRow.add(sampleNameField);
        topBox.add(navRow, BorderLayout.SOUTH);

        right.add(topBox, BorderLayout.NORTH);

        JPanel listPanel = new JPanel(new BorderLayout(4, 4));
        boxStatusLabel.setFont(boxStatusLabel.getFont().deriveFont(Font.BOLD, 11.5f));
        listPanel.add(boxStatusLabel, BorderLayout.NORTH);

        JScrollPane listScroll = new JScrollPane(boxList);
        listPanel.add(listScroll, BorderLayout.CENTER);
        right.add(listPanel, BorderLayout.CENTER);

        return right;
    }

    private JPanel buildFooterSection() {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setBorder(createSectionBorder("Patcher Log & Build"));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(500, 62));
        footer.add(logScroll, BorderLayout.CENTER);

        JPanel btnHolder = new JPanel(new GridBagLayout());
        btnHolder.add(generateButton);
        footer.add(btnHolder, BorderLayout.EAST);

        appendLog("Ready. Load 'Harvest Moon GB (USA) (SGB Enhanced).gbc' and click Generate Randomized ROM.");
        return footer;
    }

    private static CompoundBorder createSectionBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(new LineBorder(CARD_BORDER, 1, true), title);
        tb.setTitleFont(tb.getTitleFont().deriveFont(Font.BOLD, 12f));
        return BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 6, 6, 6));
    }

    private void wireEvents() {
        DocumentListener liveDoc = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshPreview(false);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshPreview(false);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshPreview(false);
            }
        };
        scriptArea.getDocument().addDocumentListener(liveDoc);
        sampleNameField.getDocument().addDocumentListener(liveDoc);

        ActionListener modeListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scriptArea.setEnabled(enableGrandpaCheck.isSelected());
                scriptArea.setLineWrap(autoWrapRadio.isSelected());
                refreshPreview(false);
            }
        };
        enableGrandpaCheck.addActionListener(modeListener);
        autoWrapRadio.addActionListener(modeListener);
        manualModeRadio.addActionListener(modeListener);

        prevBoxButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentBoxIndex > 0) {
                    currentBoxIndex--;
                    updateSelectedBoxView();
                }
            }
        });
        nextBoxButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentBoxIndex + 1 < previewBoxes.size()) {
                    currentBoxIndex++;
                    updateSelectedBoxView();
                }
            }
        });

        boxList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!suppressListEvents && !e.getValueIsAdjusting()) {
                    int idx = boxList.getSelectedIndex();
                    if (idx >= 0 && idx < previewBoxes.size()) {
                        currentBoxIndex = idx;
                        updateSelectedBoxView();
                    }
                }
            }
        });

        generateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generatePatchedRom();
            }
        });
    }

    private void autoDetectNearbyRom() {
        String defaultRomName = "Harvest Moon GB (USA) (SGB Enhanced).gbc";
        File cwdCandidate = new File(defaultRomName);
        if (cwdCandidate.isFile()) {
            loadRomFile(cwdCandidate, false);
            return;
        }
        try {
            File jarFile = new File(
                    RandomizerFrame.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File jarDir = jarFile.isFile() ? jarFile.getParentFile() : jarFile;
            if (jarDir != null) {
                File sibling = new File(jarDir, defaultRomName);
                if (sibling.isFile()) {
                    loadRomFile(sibling, false);
                }
            }
        } catch (URISyntaxException ignored) {
            // Ignore and let user pick manually
        }
    }

    private void chooseInputRom() {
        JFileChooser chooser = new JFileChooser(getStartingDirectory());
        chooser.setDialogTitle("Select Harvest Moon GBC ROM");
        chooser.setFileFilter(new FileNameExtensionFilter("Game Boy Color ROMs (*.gbc, *.gb)", "gbc", "gb"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadRomFile(chooser.getSelectedFile(), true);
        }
    }

    private void chooseOutputRom() {
        JFileChooser chooser = new JFileChooser(getStartingDirectory());
        chooser.setDialogTitle("Save Randomized ROM As");
        chooser.setFileFilter(new FileNameExtensionFilter("Game Boy Color ROM (*.gbc)", "gbc"));
        String currentOut = outputRomField.getText().trim();
        if (!currentOut.isEmpty()) {
            chooser.setSelectedFile(new File(currentOut));
        }
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = chooser.getSelectedFile();
            if (!out.getName().toLowerCase().endsWith(".gbc") && !out.getName().toLowerCase().endsWith(".gb")) {
                out = new File(out.getParentFile(), out.getName() + ".gbc");
            }
            outputRomField.setText(out.getAbsolutePath());
        }
    }

    private File getStartingDirectory() {
        if (loadedRomFile != null && loadedRomFile.getParentFile() != null) {
            return loadedRomFile.getParentFile();
        }
        return new File(".").getAbsoluteFile();
    }

    public void loadRomFile(File file, boolean showDialogOnError) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            RomValidation v = HmgbRandomizer.validateRom(bytes);
            if (!v.isValid()) {
                romStatusLabel.setForeground(ACCENT_RED);
                romStatusLabel.setText("\u2716 " + v.getMessage());
                appendLog("Rejected ROM '" + file.getName() + "': " + v.getMessage());
                if (showDialogOnError) {
                    JOptionPane.showMessageDialog(this, v.getMessage(), "Incompatible ROM", JOptionPane.ERROR_MESSAGE);
                }
                return;
            }

            loadedRomFile = file;
            loadedRomBytes = bytes;
            inputRomField.setText(file.getAbsolutePath());

            File suggestedOut = suggestOutputFile(file);
            outputRomField.setText(suggestedOut.getAbsolutePath());

            romStatusLabel.setForeground(v.isKnownVanillaHash() ? ACCENT_GREEN : ACCENT_BLUE);
            romStatusLabel.setText("\u2714 " + v.getMessage() + "  [SHA-1: " + v.getSha1().substring(0, 10) + "...]");

            // Load actual 8x8 2bpp font tiles from ROM Bank 0x11 for pixel-exact live preview!
            dialoguePreviewPanel.setFontRenderer(new HmgbFontRenderer(bytes));
            readRomTextButton.setEnabled(true);
            updateGenerateButtonState(null);

            appendLog("Loaded ROM: " + file.getName() + " (real ROM font active in live preview).");
        } catch (IOException ex) {
            romStatusLabel.setForeground(ACCENT_RED);
            romStatusLabel.setText("\u2716 Failed to read ROM: " + ex.getMessage());
            if (showDialogOnError) {
                JOptionPane.showMessageDialog(this, "Could not read file:\n" + ex.getMessage(),
                        "Read Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static File suggestOutputFile(File inputFile) {
        String name = inputFile.getName();
        int dot = name.lastIndexOf('.');
        String stem = (dot > 0) ? name.substring(0, dot) : name;
        String ext = (dot > 0) ? name.substring(dot) : ".gbc";
        if (!stem.endsWith(" Randomizer")) {
            stem = stem + " Randomizer";
        } else {
            stem = stem + " (Patched)";
        }
        return new File(inputFile.getParentFile(), stem + ext);
    }

    private void loadDialogueFromCurrentRom() {
        if (loadedRomBytes == null) {
            return;
        }
        try {
            List<DialogueBox> romBoxes = HmgbDialoguePatcher.readGrandpaDialogue(loadedRomBytes);
            manualModeRadio.setSelected(true);
            scriptArea.setLineWrap(false);
            scriptArea.setText(HmgbTextCodec.boxesToEditableText(romBoxes));
            scriptArea.setCaretPosition(0);
            refreshPreview(true);
            appendLog("Loaded Grandpa's current dialogue from ROM (" + romBoxes.size() + " boxes).");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to read dialogue from ROM:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importScriptFile() {
        JFileChooser chooser = new JFileChooser(getStartingDirectory());
        chooser.setDialogTitle("Import Grandpa Dialogue (.txt)");
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try {
                String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                scriptArea.setText(text);
                scriptArea.setCaretPosition(0);
                appendLog("Imported text from " + f.getName() + ".");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not read file:\n" + ex.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportScriptFile() {
        JFileChooser chooser = new JFileChooser(getStartingDirectory());
        chooser.setDialogTitle("Export Grandpa Dialogue (.txt)");
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        chooser.setSelectedFile(new File(getStartingDirectory(), "grandpa_welcome.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".txt")) {
                f = new File(f.getParentFile(), f.getName() + ".txt");
            }
            try {
                Files.write(f.toPath(), scriptArea.getText().getBytes(StandardCharsets.UTF_8));
                appendLog("Exported text to " + f.getName() + ".");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not save file:\n" + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshPreview(boolean resetPage) {
        String sampleName = getSanitizedSampleName();
        try {
            List<DialogueBox> boxes = manualModeRadio.isSelected()
                    ? HmgbTextCodec.encodeManualBoxes(scriptArea.getText())
                    : HmgbTextCodec.encodeAutoWrapped(scriptArea.getText());

            if (boxes.size() > HmgbDialoguePatcher.MAX_REPOINT_BOXES) {
                throw new IllegalArgumentException(
                        "Dialogue is " + boxes.size() + " boxes long (maximum is "
                                + HmgbDialoguePatcher.MAX_REPOINT_BOXES + " boxes).");
            }

            previewBoxes = boxes;
            if (resetPage || currentBoxIndex >= previewBoxes.size()) {
                currentBoxIndex = 0;
            }

            suppressListEvents = true;
            try {
                boxListModel.clear();
                for (int i = 0; i < boxes.size(); i++) {
                    DialogueBox b = boxes.get(i);
                    String l1 = b.formatDisplayLine1(sampleName);
                    String l2 = b.formatDisplayLine2(sampleName);
                    boxListModel.addElement(String.format("[%02d] %s | %s",
                            Integer.valueOf(i + 1), l1, l2));
                }
            } finally {
                suppressListEvents = false;
            }

            int n = boxes.size();
            if (!enableGrandpaCheck.isSelected()) {
                boxStatusLabel.setForeground(MUTED_FG);
                boxStatusLabel.setText("Grandpa's intro text patch is disabled (ROM text will stay unchanged).");
            } else if (n <= HmgbDialoguePatcher.VANILLA_BOX_CAPACITY) {
                boxStatusLabel.setForeground(ACCENT_GREEN);
                boxStatusLabel.setText("\u2714 " + n + " / " + HmgbDialoguePatcher.VANILLA_BOX_CAPACITY
                        + " boxes — patches cleanly in-place at Bank 0x0A:$510A");
            } else {
                boxStatusLabel.setForeground(ACCENT_BLUE);
                boxStatusLabel.setText("\u2714 " + n + " / " + HmgbDialoguePatcher.MAX_REPOINT_BOXES
                        + " boxes — auto-repoints ID 0xC9 to free space at Bank 0x09:$75A1");
            }

            updateSelectedBoxView();
            updateGenerateButtonState(null);
        } catch (IllegalArgumentException ex) {
            previewBoxes = Collections.emptyList();
            boxListModel.clear();
            pageCounterLabel.setText("Box 0 / 0");
            prevBoxButton.setEnabled(false);
            nextBoxButton.setEnabled(false);
            dialoguePreviewPanel.setPreview(null, sampleName, ex.getMessage());

            if (enableGrandpaCheck.isSelected()) {
                boxStatusLabel.setForeground(ACCENT_RED);
                boxStatusLabel.setText("\u2716 " + ex.getMessage());
                updateGenerateButtonState(ex.getMessage());
            } else {
                boxStatusLabel.setForeground(MUTED_FG);
                boxStatusLabel.setText("Grandpa's intro text patch is disabled.");
                updateGenerateButtonState(null);
            }
        }
    }

    private void updateSelectedBoxView() {
        if (previewBoxes.isEmpty()) {
            return;
        }
        String sampleName = getSanitizedSampleName();
        DialogueBox box = previewBoxes.get(currentBoxIndex);
        dialoguePreviewPanel.setPreview(box, sampleName, null);
        pageCounterLabel.setText("Box " + (currentBoxIndex + 1) + " / " + previewBoxes.size());
        prevBoxButton.setEnabled(currentBoxIndex > 0);
        nextBoxButton.setEnabled(currentBoxIndex + 1 < previewBoxes.size());

        suppressListEvents = true;
        try {
            boxList.setSelectedIndex(currentBoxIndex);
            boxList.ensureIndexIsVisible(currentBoxIndex);
        } finally {
            suppressListEvents = false;
        }
    }

    private String getSanitizedSampleName() {
        String s = sampleNameField.getText();
        if (s == null || s.isEmpty()) {
            return "Kiwi";
        }
        return s.length() > 4 ? s.substring(0, 4) : s;
    }

    private void updateGenerateButtonState(String validationError) {
        generateButton.setEnabled(loadedRomBytes != null && validationError == null);
    }

    private void generatePatchedRom() {
        if (loadedRomBytes == null || loadedRomFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a base Harvest Moon GBC ROM first.",
                    "No ROM Loaded", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String outText = outputRomField.getText().trim();
        if (outText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please choose an output ROM file path.",
                    "No Output Path", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File outFile = new File(outText);
        try {
            if (outFile.getCanonicalFile().equals(loadedRomFile.getCanonicalFile())) {
                JOptionPane.showMessageDialog(this,
                        "To keep your original ROM safe, please save the randomized ROM to a different filename.",
                        "Cannot Overwrite Base ROM", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (IOException ignored) {
            // Proceed if canonicalization fails
        }

        try {
            HmgbOptions opts = new HmgbOptions();
            opts.setPatchGrandpaText(enableGrandpaCheck.isSelected());
            opts.setManualBoxMode(manualModeRadio.isSelected());
            opts.setGrandpaText(scriptArea.getText());

            PatchResult result = HmgbRandomizer.apply(loadedRomBytes, opts);
            File parent = outFile.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(outFile.toPath(), result.getPatchedRom());

            List<String> lines = result.getLogLines();
            for (int i = 0; i < lines.size(); i++) {
                appendLog(lines.get(i));
            }
            appendLog("Saved randomized ROM -> " + outFile.getAbsolutePath());

            JOptionPane.showMessageDialog(this,
                    "Randomized ROM generated successfully!\n\n"
                            + "Saved to:\n" + outFile.getAbsolutePath() + "\n\n"
                            + String.format("Checksums: Header 0x%02X | Global 0x%04X",
                            Integer.valueOf(result.getHeaderChecksum()),
                            Integer.valueOf(result.getGlobalChecksum())),
                    "Success!", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            appendLog("ERROR: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to generate ROM:\n" + ex.getMessage(),
                    "Patch Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void appendLog(String line) {
        if (logArea.getDocument().getLength() > 0) {
            logArea.append("\n");
        }
        logArea.append(line);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
