package bate_papo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    @Value("${algorithm}")
    private String ALGORITHM;

    @Value("${encryption.secret.key}")
    private String ENCRYPTION_SECRET_KEY;

    @Value("${iv.length}")
    private int IV_LENGTH;

    private byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private SecretKey generateSecretKey(String secretKeyString) {
        try {
            byte[] keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length != 16) {
                throw new IllegalArgumentException("Invalid key length for AES. Key must be 16 bytes.");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Error generating secret key", e);
        }
    }

    public Mono<String> encryptMessage(String message) {
        return Mono.fromCallable(() -> {
            try {
                byte[] iv = generateIv();
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

                SecretKey secretKey = generateSecretKey(ENCRYPTION_SECRET_KEY);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

                byte[] encryptedBytes = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));

                byte[] ivWithEncryptedBytes = new byte[iv.length + encryptedBytes.length];
                System.arraycopy(iv, 0, ivWithEncryptedBytes, 0, iv.length);
                System.arraycopy(encryptedBytes, 0, ivWithEncryptedBytes, iv.length, encryptedBytes.length);

                return Base64.getEncoder().encodeToString(ivWithEncryptedBytes);
            } catch (Exception e) {
                throw new RuntimeException("Error while encrypting the message", e);
            }
        });
    }

    public Mono<String> decryptMessage(String encryptedMessage) {
        return Mono.fromCallable(() -> {
            try {
                byte[] ivWithEncryptedBytes = Base64.getDecoder().decode(encryptedMessage);

                if (ivWithEncryptedBytes.length < IV_LENGTH) {
                    throw new IllegalArgumentException("Invalid encrypted message: too short to contain IV and encrypted data.");
                }

                byte[] iv = new byte[IV_LENGTH];
                System.arraycopy(ivWithEncryptedBytes, 0, iv, 0, IV_LENGTH);
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

                byte[] encryptedBytes = new byte[ivWithEncryptedBytes.length - IV_LENGTH];
                System.arraycopy(ivWithEncryptedBytes, IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

                SecretKey secretKey = generateSecretKey(ENCRYPTION_SECRET_KEY);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

                byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Error while decrypting the message.", e);
            }
        });
    }
}