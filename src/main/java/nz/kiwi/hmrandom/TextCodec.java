package nz.kiwi.hmrandom;

/**
 * Harvest Moon GB text codec.
 *
 * The game stores dialogue as single bytes using a custom character map.
 * This class was derived by inspecting the ROM directly: the map below is the
 * one the game's own text engine uses to copy font tiles into VRAM.
 *
 * Control codes:
 *   0xFE = prompt (wait for A/B)
 *   0xFF = end of text block
 *   0xEF = space
 */
public final class TextCodec {

    private TextCodec() {
    }

    /** Returns the byte for a printable ASCII character, or -1 if unsupported. */
    public static int encodeChar(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';            // 0x00..0x19
        }
        if (c >= 'a' && c <= 'z') {
            return 0x1a + (c - 'a');   // 0x1a..0x33
        }
        if (c >= '0' && c <= '9') {
            return 0x34 + (c - '0');   // 0x34..0x3d
        }
        switch (c) {
            case ' ':  return 0xef;
            case '!':  return 0x40;
            case '?':  return 0x41;
            case '-':  return 0x44;
            case '.':  return 0x4c;
            case ',':  return 0x8e;
            case '[':  return 0x3e;
            case ']':  return 0x3f;
            case '\'': return 0x4b;
            default:   return -1;
        }
    }

    /** Encodes a string to game text bytes. Unsupported characters become spaces. */
    public static byte[] encode(String text) {
        byte[] out = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            int b = encodeChar(text.charAt(i));
            out[i] = (byte) (b < 0 ? 0xef : b);
        }
        return out;
    }

    /** Decodes game text bytes to a printable string (control codes become markers). */
    public static String decode(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(decodeChar(data[offset + i] & 0xff));
        }
        return sb.toString();
    }

    public static char decodeChar(int b) {
        if (b >= 0x00 && b <= 0x19) return (char) ('A' + b);
        if (b >= 0x1a && b <= 0x33) return (char) ('a' + (b - 0x1a));
        if (b >= 0x34 && b <= 0x3d) return (char) ('0' + (b - 0x34));
        switch (b) {
            case 0x3e: return '[';
            case 0x3f: return ']';
            case 0x40: return '!';
            case 0x41: return '?';
            case 0x43: return '~';
            case 0x44: return '-';
            case 0x4b: return '\'';
            case 0x4c: return '.';
            case 0x53: return '\'';
            case 0x8e: return ',';
            case 0xef: return ' ';
            case 0xfe: return '\n';
            case 0xff: return '\0';
            default:   return '?';
        }
    }
}
