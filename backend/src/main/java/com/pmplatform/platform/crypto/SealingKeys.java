package com.pmplatform.platform.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Key hierarchy for sealed bids: one random data key per tender, stored only in
 * wrapped (encrypted) form, under a master key supplied from outside the database
 * ({@code PMP_SEALING_MASTER_KEY}, 32 bytes, base64). A copy of the database alone
 * therefore cannot read any bid.
 */
@Component
public class SealingKeys {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] WRAP_AAD = "pmp-dek-v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final SecretKey master;

    public SealingKeys(@Value("${pmp.sealing.master-key:}") String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new IllegalStateException("pmp.sealing.master-key (PMP_SEALING_MASTER_KEY) is not set. "
                    + "Generate one with: openssl rand -base64 32");
        }
        byte[] raw = Base64.getDecoder().decode(masterKeyBase64.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("pmp.sealing.master-key must be 32 bytes (base64 of 32 random bytes)");
        }
        this.master = new SecretKeySpec(raw, "AES");
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    /** A new random data key, returned wrapped for storage. */
    public byte[] newWrappedDataKey() {
        byte[] dek = randomBytes(32);
        try {
            return wrap(dek);
        } finally {
            java.util.Arrays.fill(dek, (byte) 0);
        }
    }

    public SecretKey unwrap(byte[] wrapped) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(wrapped);
            byte[] nonce = new byte[12];
            buf.get(nonce);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, master, new GCMParameterSpec(128, nonce));
            c.updateAAD(WRAP_AAD);
            return new SecretKeySpec(c.doFinal(ct), "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not unwrap the tender key; is the master key correct?", e);
        }
    }

    private byte[] wrap(byte[] dek) {
        try {
            byte[] nonce = randomBytes(12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, master, new GCMParameterSpec(128, nonce));
            c.updateAAD(WRAP_AAD);
            byte[] ct = c.doFinal(dek);
            return ByteBuffer.allocate(nonce.length + ct.length).put(nonce).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not wrap the tender key", e);
        }
    }
}
