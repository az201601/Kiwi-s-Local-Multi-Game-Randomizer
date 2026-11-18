package com.kiwi.randomizer;

/**
 * Harvest Moon GB (USA) text codec.
 *
 * <p>Reverse-engineered from "Harvest Moon GB (USA) (SGB Enhanced).gbc" (1 MB, 64 banks).
 * Text entries in the grandpa letter use a custom font encoding, NOT plain ASCII.
 *
 * <h2>Encoding summary</h2>
 * <ul>
 *   <li>A-Z (normal font): 0x1A - 0x33  (0x1A='A', 0x1B='B', ..., 0x33='Z')</li>
 *   <li>A-Z (alt font):    0x00 - 0x19  (0x00='A', 0x01='B', ..., 0x19='Z') — same glyphs, different VRAM bank.
 *       The original ROM mixes both (often first letter alt, rest normal). This codec always
 *       <em>encodes</em> with the normal font, which the game displays correctly.</li>
 *   <li>Space / word separator: 0xEF</li>
 *   <li>Line break inside an entry: 0x40 (decoded as space for dump readability)</li>
 *   <li>Mid-word line break (invisible): 0x44 (decoded as empty)</li>
 *   <li>'.' (period): 0x4C</li>
 *   <li>'!' (exclamation): 0x41</li>
 *   <li>',' (comma): 0x8E</li>
 *   <li>''' (apostrophe): 0x4B, "'S" (apostrophe+S as one tile): 0x53</li>
 *   <li>'0'-'9': 0x34 - 0x3D (0x34='0', 0x35='1', ..., 0x3D='9')</li>
 *   <li>'(' : 0x3E, ')' : 0x3F (best guess from "[3E]GIVE AND TAKE[3F]" context)</li>
 *   <li>{PLAYER} (player name insertion): 0xA0 0xA1 0xA2 0xA3 (4 bytes)</li>
 *   <li>Entry terminators (NOT part of text, preserved by patcher): 0xE9 0xFE or 0xEF 0xFF,
 *       followed by 0x00 padding.</li>
 * </ul>
 *
 * <p>Each grandpa-letter entry holds exactly 31 bytes of text (padded with 0xEF).
 * New text must fit in 31 encoded bytes per entry.
 *
 * <p>Java 8 compatible (no newer language features).
 */
public final class HarvestMoonCodec {

    public static final int ENTRY_TEXT_SIZE = 31;

    // Font bytes
    private static final int NORM_A = 0x1A;
    private static final int ALT_A = 0x00;
    private static final int SPACE = 0xEF;
    private static final int LINE_BREAK = 0x40;
    private static final int MID_WORD_BREAK = 0x44;
    private static final int PERIOD = 0x4C;
    private static final int EXCLAMATION = 0x41;
    private static final int COMMA = 0x8E;
    private static final int APOSTROPHE = 0x4B;
    private static final int APOSTROPHE_S = 0x53;
    private static final int DIGIT_0 = 0x34;
    private static final int LPAREN = 0x3E;
    private static final int RPAREN = 0x3F;
    private static final byte[] PLAYER_CODE = {(byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3};
    public static final String PLAYER_TOKEN = "{PLAYER}";

    private HarvestMoonCodec() {
    }

    /**
     * Encode one entry of plain text (one line) to exactly 31 bytes (padded with 0xEF).
     *
     * <p>Supported characters: A-Z, a-z (converted to upper), 0-9, space, '.', ',', '\'',
     * "'S", '(', ')', '!', and the token {PLAYER}. Newlines inside the string are encoded
     * as 0x40 (line break).
     *
     * @param text plain text for one entry (must not be null)
     * @return 31-byte array
     * @throws IllegalArgumentException if text contains unsupported characters or exceeds 31 encoded bytes
     */
    public static byte[] encodeEntry(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        // Upper-case for letters (game font is all-caps)
        String s = text.toUpperCase(java.util.Locale.ROOT);
        java.util.List<Byte> out = new java.util.ArrayList<Byte>(ENTRY_TEXT_SIZE);
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith(PLAYER_TOKEN, i)) {
                for (byte b : PLAYER_CODE) {
                    out.add(b);
                }
                i += PLAYER_TOKEN.length();
            } else if (s.startsWith("'S", i)) {
                out.add((byte) APOSTROPHE_S);
                i += 2;
            } else {
                char c = s.charAt(i);
                if (c >= 'A' && c <= 'Z') {
                    out.add((byte) (NORM_A + (c - 'A')));
                } else if (c >= '0' && c <= '9') {
                    out.add((byte) (DIGIT_0 + (c - '0')));
                } else if (c == ' ') {
                    out.add((byte) SPACE);
                } else if (c == '\n' || c == '\r') {
                    out.add((byte) LINE_BREAK);
                } else if (c == '.') {
                    out.add((byte) PERIOD);
                } else if (c == ',') {
                    out.add((byte) COMMA);
                } else if (c == '\'') {
                    out.add((byte) APOSTROPHE);
                } else if (c == '!') {
                    out.add((byte) EXCLAMATION);
                } else if (c == '(') {
                    out.add((byte) LPAREN);
                } else if (c == ')') {
                    out.add((byte) RPAREN);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported character '" + c + "' (U+" + String.format("%04X", (int) c)
                                    + ") in entry text: \"" + text + "\". "
                                    + "Supported: A-Z, 0-9, space, '.', ',', '\\'', \"'S\", '(', ')', '!', {PLAYER}.");
                }
                i++;
            }
        }
        if (out.size() > ENTRY_TEXT_SIZE) {
            throw new IllegalArgumentException(
                    "Entry too long: encoded length " + out.size() + " bytes exceeds "
                            + ENTRY_TEXT_SIZE + " bytes. Text: \"" + text + "\". "
                            + "Shorten it (each letter/space/punct = 1 byte, {PLAYER} = 4 bytes).");
        }
        while (out.size() < ENTRY_TEXT_SIZE) {
            out.add((byte) SPACE);
        }
        byte[] result = new byte[ENTRY_TEXT_SIZE];
        for (int k = 0; k < ENTRY_TEXT_SIZE; k++) {
            result[k] = out.get(k);
        }
        return result;
    }

    /**
     * Encoded length of plain text (without padding). Useful for validation messages.
     */
    public static int encodedLength(String text) {
        if (text == null) {
            return 0;
        }
        String s = text.toUpperCase(java.util.Locale.ROOT);
        int total = 0;
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith(PLAYER_TOKEN, i)) {
                total += 4;
                i += PLAYER_TOKEN.length();
            } else if (s.startsWith("'S", i)) {
                total += 1;
                i += 2;
            } else {
                total += 1;
                i++;
            }
        }
        return total;
    }

    /**
     * Decode 31 bytes of entry text back to plain text (trimmed of trailing padding spaces).
     * Unknown bytes are rendered as [XX] so dumps stay readable.
     */
    public static String decodeEntry(byte[] raw) {
        if (raw == null || raw.length != ENTRY_TEXT_SIZE) {
            throw new IllegalArgumentException("raw must be exactly " + ENTRY_TEXT_SIZE + " bytes");
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < raw.length) {
            int b = raw[i] & 0xFF;
            // Player name code?
            if (i + 3 < raw.length
                    && (raw[i] & 0xFF) == 0xA0 && (raw[i + 1] & 0xFF) == 0xA1
                    && (raw[i + 2] & 0xFF) == 0xA2 && (raw[i + 3] & 0xFF) == 0xA3) {
                sb.append(PLAYER_TOKEN);
                i += 4;
                continue;
            }
            if (b >= 0x00 && b <= 0x19) {
                sb.append((char) ('A' + b));
            } else if (b >= 0x1A && b <= 0x33) {
                sb.append((char) ('A' + (b - 0x1A)));
            } else if (b == SPACE) {
                sb.append(' ');
            } else if (b == LINE_BREAK) {
                sb.append(' ');
            } else if (b == MID_WORD_BREAK) {
                // invisible mid-word break: no output
            } else if (b == PERIOD) {
                sb.append('.');
            } else if (b == EXCLAMATION) {
                sb.append('!');
            } else if (b == COMMA) {
                sb.append(',');
            } else if (b == APOSTROPHE) {
                sb.append('\'');
            } else if (b == APOSTROPHE_S) {
                sb.append("'S");
            } else if (b >= 0x34 && b <= 0x3D) {
                sb.append((char) ('0' + (b - 0x34)));
            } else if (b == LPAREN) {
                sb.append('(');
            } else if (b == RPAREN) {
                sb.append(')');
            } else if (b == 0x00) {
                sb.append(' ');
            } else {
                sb.append(String.format("[%02X]", b));
            }
            i++;
        }
        // Trim trailing padding spaces (0xEF padding decodes as spaces)
        String s = sb.toString();
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
