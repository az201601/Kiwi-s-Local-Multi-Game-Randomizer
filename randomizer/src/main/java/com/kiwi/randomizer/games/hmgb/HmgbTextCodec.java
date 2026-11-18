package com.kiwi.randomizer.games.hmgb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Verified text encoder, decoder, and dialogue box formatter for
 * Harvest Moon GB (USA) (SGB Enhanced).gbc.
 *
 * Verified against:
 *  - Font 2bpp tile table at Bank 0x11:$4001 (file offset 0x44001)
 *  - Dialogue engine in Bank 0x00:$36F4..$3A60
 *  - Dynamic RAM token table in Bank 0x17:$7739 (file offset 0x5F739)
 *  - Live CGB and DMG emulation of Dialogue ID 0xC9 (Grandpa's Spirit)
 */
public final class HmgbTextCodec {

    public static final int LINE_WIDTH = 16;
    public static final int CONT_LINE2_WIDTH = 15; // Col 15 holds 0xE9 next-page arrow
    public static final int FINAL_LINE2_WIDTH = 16;

    public static final int BYTE_SPACE = 0xEF;
    public static final int BYTE_NEXT_ARROW = 0xE9;
    public static final int BYTE_HYPHEN = 0x44;
    public static final int BYTE_APOSTROPHE = 0x4B;
    public static final int BYTE_APOSTROPHE_S = 0x53;
    public static final int BYTE_PERIOD = 0x4C;
    public static final int BYTE_COMMA = 0x8E;
    public static final int BYTE_EXCLAMATION = 0x40;
    public static final int BYTE_QUESTION = 0x41;
    public static final int BYTE_HEART = 0x42;
    public static final int BYTE_ELLIPSIS = 0x43;

    public static final int TERM_CONTINUE = 0xFE;
    public static final int TERM_END = 0xFF;

    public static final int[] PLAYER_NAME_BYTES = new int[] { 0xA0, 0xA1, 0xA2, 0xA3 };

    private HmgbTextCodec() {
    }

    /**
     * Represents one 2x16 in-game dialogue box (32 tile bytes + 1 terminator byte).
     */
    public static final class DialogueBox {
        private final byte[] line1; // length 16
        private final byte[] line2; // length 16 (col 15 is 0xE9 if continuation)
        private final int terminator; // 0xFE or 0xFF

        public DialogueBox(byte[] line1, byte[] line2, int terminator) {
            if (line1.length != LINE_WIDTH || line2.length != LINE_WIDTH) {
                throw new IllegalArgumentException("Dialogue box lines must each be 16 tiles.");
            }
            this.line1 = Arrays.copyOf(line1, LINE_WIDTH);
            this.line2 = Arrays.copyOf(line2, LINE_WIDTH);
            this.terminator = terminator & 0xFF;
        }

        public byte[] getLine1() {
            return Arrays.copyOf(line1, LINE_WIDTH);
        }

        public byte[] getLine2() {
            return Arrays.copyOf(line2, LINE_WIDTH);
        }

        public int getTerminator() {
            return terminator;
        }

        public boolean isContinuation() {
            return terminator == TERM_CONTINUE;
        }

        public String formatDisplayLine1(String samplePlayerName) {
            return decodeTilesForDisplay(line1, samplePlayerName);
        }

        public String formatDisplayLine2(String samplePlayerName) {
            return decodeTilesForDisplay(line2, samplePlayerName);
        }
    }

    /**
     * An atomic unit during tokenization (either a single glyph byte or the 4-byte {PLAYER} sequence).
     */
    private static final class GlyphAtom {
        final int[] bytes;
        final boolean isSpace;

        GlyphAtom(int singleByte) {
            this.bytes = new int[] { singleByte & 0xFF };
            this.isSpace = (singleByte & 0xFF) == BYTE_SPACE;
        }

        GlyphAtom(int[] multiBytes) {
            this.bytes = Arrays.copyOf(multiBytes, multiBytes.length);
            this.isSpace = false;
        }

        int width() {
            return bytes.length;
        }
    }

    /**
     * Encodes a single character into its Harvest Moon GB/GBC font byte, or -1 if unsupported.
     */
    public static int encodeSingleChar(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return ch - 'A'; // 0x00..0x19
        }
        if (ch >= 'a' && ch <= 'z') {
            return 0x1A + (ch - 'a'); // 0x1A..0x33
        }
        if (ch >= '0' && ch <= '9') {
            return 0x34 + (ch - '0'); // 0x34..0x3D
        }
        switch (ch) {
            case ' ':
                return BYTE_SPACE;
            case '[':
                return 0x3E;
            case ']':
                return 0x3F;
            case '!':
                return BYTE_EXCLAMATION;
            case '?':
                return BYTE_QUESTION;
            case '\u2665': // ♥
            case '\u2764': // ❤
                return BYTE_HEART;
            case '\u2026': // …
                return BYTE_ELLIPSIS;
            case '-':
            case '\u2013': // –
            case '\u2014': // —
                return BYTE_HYPHEN;
            case '~':
                return 0x45;
            case '_':
                return 0x46;
            case '\u2191': // ↑
                return 0x47;
            case '\u2190': // ←
                return 0x48;
            case '\u2193': // ↓
                return 0x49;
            case '\u2192': // →
                return 0x4A;
            case '\'':
            case '\u2018': // ‘
            case '\u2019': // ’
            case '`':
                return BYTE_APOSTROPHE;
            case '.':
                return BYTE_PERIOD;
            case '(':
                return 0x50;
            case ')':
                return 0x51;
            case '\u00D7': // ×
                return 0x52;
            case ',':
                return BYTE_COMMA;
            default:
                return -1;
        }
    }

    /**
     * Tokenizes a single line of text into GlyphAtoms.
     * Supports placeholders: {PLAYER}, {HEART}, {...}, {UP}, {LEFT}, {DOWN}, {RIGHT},
     * plus automatic vanilla 1-tile "'s" ligature (0x53) when usePossessiveLigature is true.
     */
    private static List<GlyphAtom> tokenizeLine(String line, int lineNumber, boolean usePossessiveLigature) {
        List<GlyphAtom> atoms = new ArrayList<GlyphAtom>();
        int i = 0;
        int n = line.length();
        while (i < n) {
            char ch = line.charAt(i);
            if (ch == '\t') {
                atoms.add(new GlyphAtom(BYTE_SPACE));
                i++;
                continue;
            }
            if (ch == '{') {
                int close = line.indexOf('}', i + 1);
                if (close < 0) {
                    throw new IllegalArgumentException(
                            "Line " + lineNumber + ": Unclosed '{'. Use {PLAYER}, {HEART}, or {...}.");
                }
                String tag = line.substring(i + 1, close).trim().toUpperCase();
                if ("PLAYER".equals(tag)) {
                    atoms.add(new GlyphAtom(PLAYER_NAME_BYTES));
                } else if ("HEART".equals(tag)) {
                    atoms.add(new GlyphAtom(BYTE_HEART));
                } else if ("...".equals(tag) || "ELLIPSIS".equals(tag)) {
                    atoms.add(new GlyphAtom(BYTE_ELLIPSIS));
                } else if ("UP".equals(tag)) {
                    atoms.add(new GlyphAtom(0x47));
                } else if ("LEFT".equals(tag)) {
                    atoms.add(new GlyphAtom(0x48));
                } else if ("DOWN".equals(tag)) {
                    atoms.add(new GlyphAtom(0x49));
                } else if ("RIGHT".equals(tag)) {
                    atoms.add(new GlyphAtom(0x4A));
                } else {
                    throw new IllegalArgumentException(
                            "Line " + lineNumber + ": Unknown tag '{" + tag
                                    + "}'. Supported tags: {PLAYER}, {HEART}, {...}, {UP}, {DOWN}, {LEFT}, {RIGHT}.");
                }
                i = close + 1;
                continue;
            }

            // Use the game's built-in 1-tile "'s" glyph (0x53), just like vanilla "grandpa's" and "It's"
            if (usePossessiveLigature
                    && (ch == '\'' || ch == '\u2019' || ch == '\u2018')
                    && (i + 1 < n)
                    && line.charAt(i + 1) == 's') {
                char after = (i + 2 < n) ? line.charAt(i + 2) : ' ';
                if (!Character.isLetterOrDigit(after)) {
                    atoms.add(new GlyphAtom(BYTE_APOSTROPHE_S));
                    i += 2;
                    continue;
                }
            }

            int code = encodeSingleChar(ch);
            if (code < 0) {
                String hint = "";
                if (ch == ':' || ch == ';') {
                    hint = " (Harvest Moon GBC's font has no '" + ch + "' glyph — try '-', '.', ',', or '!' instead.)";
                } else if (ch == '"') {
                    hint = " (Harvest Moon GBC's font has no double-quote glyph — try single quotes ' or [brackets].)";
                } else if (ch == '/') {
                    hint = " (Harvest Moon GBC's font has no '/' glyph — try '-' or 'or' instead.)";
                }
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": Unsupported character '" + ch + "' (U+"
                                + String.format("%04X", Integer.valueOf((int) ch)) + ")." + hint);
            }
            atoms.add(new GlyphAtom(code));
            i++;
        }
        return atoms;
    }

    /**
     * Converts free-form script text into properly wrapped 2x16 dialogue boxes.
     *
     * Rules:
     *  - Blank lines (one or more empty lines) start a new dialogue box (page break).
     *  - A line equal to "---" also forces a new dialogue box.
     *  - Single newlines start a new line within the current/next dialogue box.
     *  - Long lines automatically word-wrap to 16 tiles (line 1) and 15 tiles (line 2, leaving
     *    column 15 for the next-page arrow '▼'). On the final line of the entire speech,
     *    line 2 can use all 16 tiles because no '▼' arrow is shown.
     *  - {PLAYER} counts as 4 atomic tiles (the game's 4-character player name).
     */
    public static List<DialogueBox> encodeAutoWrapped(String script) {
        if (script == null) {
            throw new IllegalArgumentException("Dialogue text cannot be null.");
        }
        String normalized = script.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);

        // Group lines into page blocks separated by blank lines or "---"
        List<List<List<GlyphAtom>>> pageBlocks = new ArrayList<List<List<GlyphAtom>>>();
        List<List<GlyphAtom>> currentBlock = new ArrayList<List<GlyphAtom>>();

        for (int idx = 0; idx < rawLines.length; idx++) {
            String raw = rawLines[idx];
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || "---".equals(trimmed)) {
                if (!currentBlock.isEmpty()) {
                    pageBlocks.add(currentBlock);
                    currentBlock = new ArrayList<List<GlyphAtom>>();
                }
                continue;
            }
            List<GlyphAtom> atoms = tokenizeLine(trimmed, idx + 1, true);
            if (!atoms.isEmpty()) {
                currentBlock.add(atoms);
            }
        }
        if (!currentBlock.isEmpty()) {
            pageBlocks.add(currentBlock);
        }

        if (pageBlocks.isEmpty()) {
            throw new IllegalArgumentException("Grandpa's dialogue cannot be empty.");
        }

        // Lay out each block onto paired box lines (even index = line 1 [max 16], odd index = line 2 [max 15, or 16 if very last])
        List<byte[]> rawBoxLines = new ArrayList<byte[]>();

        for (int b = 0; b < pageBlocks.size(); b++) {
            boolean isLastBlock = (b == pageBlocks.size() - 1);
            List<List<GlyphAtom>> block = pageBlocks.get(b);

            // Start each paragraph block on Line 1 of a fresh box
            if ((rawBoxLines.size() % 2) == 1) {
                rawBoxLines.add(emptyLine());
            }

            for (int l = 0; l < block.size(); l++) {
                boolean isLastParagraphLine = isLastBlock && (l == block.size() - 1);
                wrapAtomsIntoLines(block.get(l), rawBoxLines, isLastParagraphLine);
            }
        }

        // Pad to full 2-line boxes
        if ((rawBoxLines.size() % 2) == 1) {
            rawBoxLines.add(emptyLine());
        }

        return buildBoxesFromLinePairs(rawBoxLines);
    }

    /**
     * Manual mode: every 2 lines equal 1 dialogue box (blank lines or "---" separate boxes cleanly).
     * Errors if any line exceeds 16 tiles (line 1) or 15 tiles (continuation line 2) / 16 tiles (final line 2).
     */
    public static List<DialogueBox> encodeManualBoxes(String script) {
        if (script == null) {
            throw new IllegalArgumentException("Dialogue text cannot be null.");
        }
        String normalized = script.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);

        // Trim leading and trailing completely blank lines
        int start = 0;
        int end = rawLines.length;
        while (start < end && rawLines[start].trim().isEmpty()) {
            start++;
        }
        while (end > start && rawLines[end - 1].trim().isEmpty()) {
            end--;
        }
        if (start >= end) {
            throw new IllegalArgumentException("Grandpa's dialogue cannot be empty.");
        }

        List<int[]> lineTileLists = new ArrayList<int[]>();
        List<Integer> sourceLineNums = new ArrayList<Integer>();

        for (int i = start; i < end; i++) {
            String raw = rawLines[i];
            if ("---".equals(raw.trim())) {
                if ((lineTileLists.size() % 2) == 1) {
                    lineTileLists.add(new int[0]);
                    sourceLineNums.add(Integer.valueOf(i + 1));
                }
                continue;
            }
            if (raw.trim().isEmpty()) {
                // If we just finished line 1, treat blank line as empty line 2; if between boxes, treat as separator
                if ((lineTileLists.size() % 2) == 1) {
                    lineTileLists.add(new int[0]);
                    sourceLineNums.add(Integer.valueOf(i + 1));
                }
                continue;
            }
            // Strip trailing spaces only (preserve intentional leading indentation)
            int rEnd = raw.length();
            while (rEnd > 0 && raw.charAt(rEnd - 1) == ' ') {
                rEnd--;
            }
            List<GlyphAtom> atoms = tokenizeLine(raw.substring(0, rEnd), i + 1, true);
            lineTileLists.add(flattenAtoms(atoms));
            sourceLineNums.add(Integer.valueOf(i + 1));
        }

        if ((lineTileLists.size() % 2) == 1) {
            lineTileLists.add(new int[0]);
            sourceLineNums.add(Integer.valueOf(end));
        }

        int boxCount = lineTileLists.size() / 2;
        List<byte[]> rawBoxLines = new ArrayList<byte[]>(lineTileLists.size());
        for (int b = 0; b < boxCount; b++) {
            boolean isLast = (b == boxCount - 1);
            int[] l1 = lineTileLists.get(b * 2);
            int[] l2 = lineTileLists.get(b * 2 + 1);
            int ln1 = sourceLineNums.get(b * 2).intValue();
            int ln2 = sourceLineNums.get(b * 2 + 1).intValue();

            if (l1.length > LINE_WIDTH) {
                throw new IllegalArgumentException(
                        "Line " + ln1 + " (Box " + (b + 1) + ", Line 1) is " + l1.length
                                + " tiles wide; maximum is " + LINE_WIDTH + " tiles.");
            }
            int maxL2 = isLast ? FINAL_LINE2_WIDTH : CONT_LINE2_WIDTH;
            if (l2.length > maxL2) {
                throw new IllegalArgumentException(
                        "Line " + ln2 + " (Box " + (b + 1) + ", Line 2) is " + l2.length
                                + " tiles wide; maximum is " + maxL2 + " tiles"
                                + (isLast ? "." : " (column 16 holds the '▼' arrow)."));
            }
            rawBoxLines.add(toPaddedLine(l1));
            rawBoxLines.add(toPaddedLine(l2));
        }

        return buildBoxesFromLinePairs(rawBoxLines);
    }

    private static void wrapAtomsIntoLines(
            List<GlyphAtom> atoms,
            List<byte[]> outLines,
            boolean isVeryLastTextOfSpeech) {
        // Split atoms into words (sequences of non-space atoms)
        List<List<GlyphAtom>> words = new ArrayList<List<GlyphAtom>>();
        List<GlyphAtom> curWord = new ArrayList<GlyphAtom>();
        for (int i = 0; i < atoms.size(); i++) {
            GlyphAtom a = atoms.get(i);
            if (a.isSpace) {
                if (!curWord.isEmpty()) {
                    words.add(curWord);
                    curWord = new ArrayList<GlyphAtom>();
                }
            } else {
                curWord.add(a);
            }
        }
        if (!curWord.isEmpty()) {
            words.add(curWord);
        }
        if (words.isEmpty()) {
            return;
        }

        List<GlyphAtom> curLine = new ArrayList<GlyphAtom>();
        int curWidth = 0;

        int wi = 0;
        while (wi < words.size()) {
            List<GlyphAtom> word = words.get(wi);
            int wLen = wordWidth(word);
            boolean onLine2 = ((outLines.size() % 2) == 1);
            boolean isLastWord = isVeryLastTextOfSpeech && (wi == words.size() - 1);

            int maxWidth;
            if (!onLine2) {
                maxWidth = LINE_WIDTH;
            } else if (isLastWord) {
                // On the very last word of the entire dialogue, line 2 has all 16 columns (no '▼')
                maxWidth = FINAL_LINE2_WIDTH;
            } else {
                // Check if ALL remaining words fit on this very last line 2 within 16 tiles!
                if (isVeryLastTextOfSpeech && fitsRemaining(curWidth, words, wi, FINAL_LINE2_WIDTH)) {
                    maxWidth = FINAL_LINE2_WIDTH;
                } else {
                    maxWidth = CONT_LINE2_WIDTH;
                }
            }

            int needed = (curWidth == 0) ? wLen : (1 + wLen);
            if (curWidth + needed <= maxWidth) {
                if (curWidth > 0) {
                    curLine.add(new GlyphAtom(BYTE_SPACE));
                    curWidth += 1;
                }
                curLine.addAll(word);
                curWidth += wLen;
                wi++;
            } else if (curWidth > 0) {
                // Flush current line and retry this word on the next line
                outLines.add(atomsToPaddedLine(curLine));
                curLine.clear();
                curWidth = 0;
            } else {
                // Single word exceeds the entire line width: split cleanly with a hyphen if possible
                List<GlyphAtom> head = new ArrayList<GlyphAtom>();
                List<GlyphAtom> tail = new ArrayList<GlyphAtom>();
                int headW = 0;
                int limitForHyphen = maxWidth - 1;
                boolean splitDone = false;
                for (int k = 0; k < word.size(); k++) {
                    GlyphAtom ga = word.get(k);
                    if (!splitDone && headW + ga.width() <= limitForHyphen) {
                        head.add(ga);
                        headW += ga.width();
                    } else {
                        splitDone = true;
                        tail.add(ga);
                    }
                }
                if (head.isEmpty()) {
                    // Fallback if first atom somehow filled limit
                    head.add(word.get(0));
                    tail.clear();
                    for (int k = 1; k < word.size(); k++) {
                        tail.add(word.get(k));
                    }
                } else if (!tail.isEmpty() && headW + 1 <= maxWidth) {
                    head.add(new GlyphAtom(BYTE_HYPHEN));
                }
                outLines.add(atomsToPaddedLine(head));
                words.set(wi, tail);
            }
        }

        if (!curLine.isEmpty()) {
            outLines.add(atomsToPaddedLine(curLine));
        }
    }

    private static boolean fitsRemaining(int curWidth, List<List<GlyphAtom>> words, int startIdx, int limit) {
        int w = curWidth;
        for (int i = startIdx; i < words.size(); i++) {
            int wl = wordWidth(words.get(i));
            w += (w == 0) ? wl : (1 + wl);
            if (w > limit) {
                return false;
            }
        }
        return true;
    }

    private static int wordWidth(List<GlyphAtom> word) {
        int w = 0;
        for (int i = 0; i < word.size(); i++) {
            w += word.get(i).width();
        }
        return w;
    }

    private static int[] flattenAtoms(List<GlyphAtom> atoms) {
        int total = wordWidth(atoms);
        int[] out = new int[total];
        int p = 0;
        for (int i = 0; i < atoms.size(); i++) {
            int[] bs = atoms.get(i).bytes;
            for (int j = 0; j < bs.length; j++) {
                out[p++] = bs[j];
            }
        }
        return out;
    }

    private static byte[] atomsToPaddedLine(List<GlyphAtom> atoms) {
        return toPaddedLine(flattenAtoms(atoms));
    }

    private static byte[] toPaddedLine(int[] tiles) {
        byte[] line = emptyLine();
        int copy = Math.min(tiles.length, LINE_WIDTH);
        for (int i = 0; i < copy; i++) {
            line[i] = (byte) (tiles[i] & 0xFF);
        }
        return line;
    }

    public static byte[] emptyLine() {
        byte[] line = new byte[LINE_WIDTH];
        Arrays.fill(line, (byte) BYTE_SPACE);
        return line;
    }

    private static List<DialogueBox> buildBoxesFromLinePairs(List<byte[]> rawBoxLines) {
        int boxCount = rawBoxLines.size() / 2;
        List<DialogueBox> boxes = new ArrayList<DialogueBox>(boxCount);
        for (int b = 0; b < boxCount; b++) {
            boolean isLast = (b == boxCount - 1);
            byte[] l1 = Arrays.copyOf(rawBoxLines.get(b * 2), LINE_WIDTH);
            byte[] l2 = Arrays.copyOf(rawBoxLines.get(b * 2 + 1), LINE_WIDTH);
            if (!isLast) {
                l2[LINE_WIDTH - 1] = (byte) BYTE_NEXT_ARROW;
                boxes.add(new DialogueBox(l1, l2, TERM_CONTINUE));
            } else {
                boxes.add(new DialogueBox(l1, l2, TERM_END));
            }
        }
        return Collections.unmodifiableList(boxes);
    }

    /**
     * Expands dynamic tokens (like 0xA0..0xA3 = {PLAYER}) into concrete font glyph indices
     * so the GUI preview can render exact 8x8 ROM tiles for any 1-4 character test name.
     */
    public static int[] resolveLineTilesForPreview(byte[] line, String samplePlayerName) {
        int[] playerTiles = encodePlayerName(samplePlayerName);
        int[] out = new int[LINE_WIDTH];
        for (int i = 0; i < LINE_WIDTH; i++) {
            int b = line[i] & 0xFF;
            if (b >= 0xA0 && b <= 0xA3) {
                out[i] = playerTiles[b - 0xA0];
            } else {
                out[i] = b;
            }
        }
        return out;
    }

    /**
     * Encodes a 1-4 character sample player name into the 4 SRAM bytes ($B8F4..$B8F7),
     * right-padded with 0xEF spaces just like Harvest Moon GBC's Name? screen.
     */
    public static int[] encodePlayerName(String name) {
        int[] res = new int[] { BYTE_SPACE, BYTE_SPACE, BYTE_SPACE, BYTE_SPACE };
        if (name == null) {
            return res;
        }
        int p = 0;
        for (int i = 0; i < name.length() && p < 4; i++) {
            int code = encodeSingleChar(name.charAt(i));
            if (code >= 0) {
                res[p++] = code;
            }
        }
        return res;
    }

    /**
     * Decodes a 16-byte ROM tile line into readable Unicode text, substituting samplePlayerName
     * if non-null, or "{PLAYER}" when samplePlayerName is null.
     */
    public static String decodeTilesForDisplay(byte[] tiles, String samplePlayerName) {
        StringBuilder sb = new StringBuilder();
        int[] playerTiles = (samplePlayerName != null) ? encodePlayerName(samplePlayerName) : null;
        int i = 0;
        while (i < tiles.length) {
            int b = tiles[i] & 0xFF;
            if (samplePlayerName == null
                    && b == 0xA0
                    && i + 3 < tiles.length
                    && (tiles[i + 1] & 0xFF) == 0xA1
                    && (tiles[i + 2] & 0xFF) == 0xA2
                    && (tiles[i + 3] & 0xFF) == 0xA3) {
                sb.append("{PLAYER}");
                i += 4;
                continue;
            }
            if (playerTiles != null && b >= 0xA0 && b <= 0xA3) {
                b = playerTiles[b - 0xA0];
            }
            sb.append(decodeSingleTile(b));
            i++;
        }
        return sb.toString();
    }

    public static String decodeSingleTile(int b) {
        b &= 0xFF;
        if (b <= 0x19) {
            return String.valueOf((char) ('A' + b));
        }
        if (b <= 0x33) {
            return String.valueOf((char) ('a' + (b - 0x1A)));
        }
        if (b <= 0x3D) {
            return String.valueOf((char) ('0' + (b - 0x34)));
        }
        switch (b) {
            case 0x3E:
                return "[";
            case 0x3F:
                return "]";
            case 0x40:
                return "!";
            case 0x41:
                return "?";
            case 0x42:
                return "\u2665";
            case 0x43:
                return "\u2026";
            case 0x44:
                return "-";
            case 0x45:
                return "~";
            case 0x46:
                return "_";
            case 0x47:
                return "\u2191";
            case 0x48:
                return "\u2190";
            case 0x49:
                return "\u2193";
            case 0x4A:
                return "\u2192";
            case 0x4B:
                return "'";
            case 0x4C:
                return ".";
            case 0x50:
                return "(";
            case 0x51:
                return ")";
            case 0x52:
                return "\u00D7";
            case 0x53:
                return "'s";
            case 0x8E:
                return ",";
            case 0x8F:
                return "'v";
            case 0xE9:
                return "\u25BC";
            case 0xEA:
                return "\u25BA";
            case 0xEF:
                return " ";
            default:
                return String.format("<%02X>", Integer.valueOf(b));
        }
    }

    /**
     * Converts decoded dialogue boxes into clean editable manual-mode text (2 lines per box, blank line between boxes).
     */
    public static String boxesToEditableText(List<DialogueBox> boxes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < boxes.size(); i++) {
            DialogueBox box = boxes.get(i);
            byte[] l1 = box.getLine1();
            byte[] l2 = box.getLine2();
            if (box.isContinuation() && (l2[LINE_WIDTH - 1] & 0xFF) == BYTE_NEXT_ARROW) {
                l2[LINE_WIDTH - 1] = (byte) BYTE_SPACE;
            }
            String s1 = rtrim(decodeTilesForDisplay(l1, null));
            String s2 = rtrim(decodeTilesForDisplay(l2, null));
            sb.append(s1).append('\n');
            if (!s2.isEmpty()) {
                sb.append(s2).append('\n');
            }
            if (i + 1 < boxes.size()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
