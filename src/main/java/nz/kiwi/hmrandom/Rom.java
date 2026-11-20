package nz.kiwi.hmrandom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** A Harvest Moon GB ROM image, with just enough smarts to patch it. */
public final class Rom {

    private final byte[] data;
    private final String sourcePath;

    public Rom(String path) throws IOException {
        this.sourcePath = path;
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            throw new IOException("ROM not found: " + path);
        }
        byte[] loaded = Files.readAllBytes(p);
        if (loaded.length != RomData.ROM_SIZE) {
            throw new IOException("Unexpected ROM size " + loaded.length + " (expected " + RomData.ROM_SIZE + ")");
        }
        data = loaded;
    }

    public Rom(byte[] data, String sourcePath) {
        if (data.length != RomData.ROM_SIZE) {
            throw new IllegalArgumentException("Unexpected ROM size " + data.length);
        }
        this.data = data;
        this.sourcePath = sourcePath;
    }

    public byte[] data() {
        return data;
    }

    public String sourcePath() {
        return sourcePath;
    }

    public int readByte(int offset) {
        return data[offset] & 0xff;
    }

    public int readWordLE(int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    public void writeByte(int offset, int value) {
        data[offset] = (byte) value;
    }

    public void writeWordLE(int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >> 8) & 0xff);
    }

    public void writeBytes(int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            data[offset + i] = bytes[i];
        }
    }

    /** Sanity-checks that this is the ROM we know how to randomize. */
    public boolean isSupportedBaseRom() {
        // 0x134..0x143 is the 15-char title; 0x143 is the CGB flag ($80).
        String title = readAscii(0x134, 15);
        if (!title.equals("HARVEST-MOON GB")) {
            return false;
        }
        if (readByte(0x143) != 0x80) {
            return false;
        }
        // The dresser routine must be byte-for-byte what we expect.
        int[] expected = {
            0xFA, 0xFF, 0xB8, 0xB7, 0x20, 0x15, 0x3E, 0x01, 0xEA, 0xFF, 0xB8,
            0x21, 0xEF, 0xB8, 0x01, 0xE8, 0x03, 0xCD, 0xBB, 0x0C
        };
        for (int i = 0; i < expected.length; i++) {
            if (readByte(RomData.DRESSER_ROUTINE + i) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /** Reads a plain-ASCII region (the header is not charmap-encoded). */
    public String readAscii(int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) (data[offset + i] & 0xff));
        }
        return sb.toString();
    }

    /** Decodes a game text block starting at {@code offset} until 0xFF. */
    public String readText(int offset) {
        int end = offset;
        while (end < data.length && (data[end] & 0xff) != 0xff) {
            end++;
        }
        return TextCodec.decode(data, offset, end - offset);
    }

    /** Writes a text line (space padded) and returns the number of bytes written. */
    public int writeTextLine(int offset, String text, int width) {
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) {
            sb.append(' ');
        }
        byte[] encoded = TextCodec.encode(sb.toString());
        writeBytes(offset, encoded);
        return encoded.length;
    }

    /** Writes the (unpatched) header checksum so emulators accept the ROM. */
    public void fixHeaderChecksum() {
        int checksum = 0;
        for (int i = 0x134; i <= 0x14C; i++) {
            checksum = (checksum - (data[i] & 0xff) - 1) & 0xff;
        }
        data[0x14D] = (byte) checksum;
    }

    public void save(String path) throws IOException {
        Files.write(Paths.get(path), data);
    }

    @Override
    public String toString() {
        return "Rom(" + sourcePath + ", " + data.length + " bytes)";
    }
}
