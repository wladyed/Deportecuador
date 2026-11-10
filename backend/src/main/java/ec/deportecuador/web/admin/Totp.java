package ec.deportecuador.web.admin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;

/** RFC 6238 / RFC 4226: SHA-1, 6 digits, 30 seconds. */
public final class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private Totp() {}

    public static String newSecret() {
        byte[] bytes = new byte[20]; new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder(); int buffer = 0, bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 255); bits += 8;
            while (bits >= 5) { bits -= 5; result.append(ALPHABET.charAt((buffer >>> bits) & 31)); }
        }
        return result.toString();
    }

    public static String code(String secret, long step) {
        try {
            byte[] bytes = new byte[secret.length() * 5 / 8]; int buffer = 0, bits = 0, index = 0;
            for (char c : secret.toCharArray()) {
                int value = ALPHABET.indexOf(c);
                if (value < 0) throw new IllegalArgumentException("Invalid TOTP secret");
                buffer = (buffer << 5) | value; bits += 5;
                if (bits >= 8) { bits -= 8; bytes[index++] = (byte)(buffer >>> bits); }
            }
            Mac mac = Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(bytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 15;
            int number = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            return String.format(java.util.Locale.ROOT, "%06d", number % 1_000_000);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public static long match(String secret, String supplied, Instant now, long lastUsedStep) {
        if (supplied == null || !supplied.matches("[0-9]{6}")) return -1;
        long step = now.getEpochSecond() / 30;
        for (long candidate = step - 1; candidate <= step + 1; candidate++) {
            if (candidate > lastUsedStep && MessageDigest.isEqual(code(secret, candidate).getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) return candidate;
        }
        return -1;
    }
}
