package com.kiwi.randomizer.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Summary of inspecting a candidate ROM image.
 */
public final class RomValidation {

    private final boolean valid;
    private final String gameName;
    private final String romTitle;
    private final int sizeBytes;
    private final int bankCount;
    private final String sha1;
    private final boolean knownVanillaHash;
    private final boolean headerChecksumOk;
    private final boolean globalChecksumOk;
    private final String message;

    public RomValidation(
            boolean valid,
            String gameName,
            String romTitle,
            int sizeBytes,
            int bankCount,
            String sha1,
            boolean knownVanillaHash,
            boolean headerChecksumOk,
            boolean globalChecksumOk,
            String message) {
        this.valid = valid;
        this.gameName = gameName;
        this.romTitle = romTitle;
        this.sizeBytes = sizeBytes;
        this.bankCount = bankCount;
        this.sha1 = sha1;
        this.knownVanillaHash = knownVanillaHash;
        this.headerChecksumOk = headerChecksumOk;
        this.globalChecksumOk = globalChecksumOk;
        this.message = message;
    }

    public boolean isValid() {
        return valid;
    }

    public String getGameName() {
        return gameName;
    }

    public String getRomTitle() {
        return romTitle;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public int getBankCount() {
        return bankCount;
    }

    public String getSha1() {
        return sha1;
    }

    public boolean isKnownVanillaHash() {
        return knownVanillaHash;
    }

    public boolean isHeaderChecksumOk() {
        return headerChecksumOk;
    }

    public boolean isGlobalChecksumOk() {
        return globalChecksumOk;
    }

    public String getMessage() {
        return message;
    }

    public static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (int i = 0; i < digest.length; i++) {
                int v = digest[i] & 0xFF;
                if (v < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }
}
