package ec.deportecuador.web.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.Base64;

/** Secrets stay in the private files volume, never in logs or the public API. */
@Component
public class AdminSecrets {
    private final byte[] key;
    private final String setupToken;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AdminSecrets(@Value("${app.storage.root}") String storageRoot) throws Exception {
        Path directory = Path.of(storageRoot).resolve(".admin-secrets");
        Files.createDirectories(directory);
        restrict(directory, "rwx------");
        key = Base64.getDecoder().decode(readOrCreate(directory.resolve("encryption.key"), token()));
        if (key.length != 32) throw new IllegalStateException("Invalid admin encryption key");
        setupToken = readOrCreate(directory.resolve("setup.token"), token());
    }

    private static String readOrCreate(Path path, String value) throws Exception {
        try { Files.writeString(path, value, StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException ignored) { /* Preserve existing secrets. */ }
        restrict(path, "rw-------");
        return Files.readString(path).trim();
    }

    private static void restrict(Path path, String permissions) throws Exception {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }

    public static String token() {
        byte[] value = new byte[32]; RANDOM.nextBytes(value);
        return Base64.getEncoder().encodeToString(value);
    }

    public boolean matchesSetup(String candidate) {
        return MessageDigest.isEqual(setupToken.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    public static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Unable to encrypt MFA secret", e); }
    }

    public String decrypt(String value) {
        try {
            ByteBuffer data = ByteBuffer.wrap(Base64.getDecoder().decode(value));
            byte[] nonce = new byte[12]; data.get(nonce);
            byte[] encrypted = new byte[data.remaining()]; data.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) { throw new IllegalStateException("Unable to decrypt MFA secret", e); }
    }
}
