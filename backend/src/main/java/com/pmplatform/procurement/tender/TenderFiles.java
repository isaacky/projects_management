package com.pmplatform.procurement.tender;

import com.pmplatform.platform.crypto.ChunkedAead;
import com.pmplatform.platform.crypto.SealingKeys;
import com.pmplatform.platform.storage.ObjectStorage;
import com.pmplatform.platform.web.BusinessRuleException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores tender files. Tender documents are stored as they are; bid files are
 * encrypted with the tender's key before they leave the application, and their
 * SHA-256 is taken from the original bytes so the bidder's receipt can be checked later.
 */
@Component
public class TenderFiles {

    private final ObjectStorage storage;
    private final SealingKeys keys;

    public TenderFiles(ObjectStorage storage, SealingKeys keys) {
        this.storage = storage;
        this.keys = keys;
    }

    public StoredFile storePlain(String key, MultipartFile file) {
        MessageDigest sha = sha256();
        try (InputStream in = new DigestInputStream(file.getInputStream(), sha)) {
            storage.put(key, in, file.getSize(), contentType(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new StoredFile(key, fileName(file), contentType(file), file.getSize(), hex(sha), new byte[0]);
    }

    public StoredFile storeSealed(String key, MultipartFile file, byte[] wrappedTenderKey) {
        SecretKey k = keys.unwrap(wrappedTenderKey);
        byte[] nonce = SealingKeys.randomBytes(ChunkedAead.FILE_NONCE_BYTES);
        MessageDigest sha = sha256();
        long size = file.getSize();
        try (InputStream plain = new DigestInputStream(file.getInputStream(), sha);
                InputStream sealed = ChunkedAead.encrypt(plain, size, k, nonce)) {
            storage.put(key, sealed, ChunkedAead.cipherLength(size), "application/octet-stream");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new StoredFile(key, fileName(file), contentType(file), size, hex(sha), nonce);
    }

    public InputStream openPlain(String key) {
        return storage.get(key);
    }

    /** Decrypting stream of a sealed file; fails if the stored bytes were altered. */
    public InputStream openSealed(BidFile f, byte[] wrappedTenderKey) {
        SecretKey k = keys.unwrap(wrappedTenderKey);
        return ChunkedAead.decrypt(storage.get(f.getStorageKey()), ChunkedAead.cipherLength(f.getSizeBytes()), k,
                f.getFileNonce());
    }

    /** Best-effort clean-up of files whose database record was not saved. */
    public void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ignored) {
            // an orphaned object is harmless; it is never referenced
        }
    }

    public static String newKey(UUID tenantId, UUID tenderId, String kind) {
        return "tenders/" + tenantId + "/" + tenderId + "/" + kind + "/" + UUID.randomUUID();
    }

    static String fileName(MultipartFile f) {
        String n = f.getOriginalFilename();
        if (n == null || n.isBlank()) {
            return "file";
        }
        n = n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "_").trim();
        if (n.isEmpty()) {
            return "file";
        }
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    static String contentType(MultipartFile f) {
        String t = f.getContentType();
        return t == null || t.isBlank() || t.length() > 127 ? "application/octet-stream" : t;
    }

    static void requireNotEmpty(MultipartFile f) {
        if (f.isEmpty()) {
            throw new BusinessRuleException("The file " + fileName(f) + " is empty");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(MessageDigest d) {
        return HexFormat.of().formatHex(d.digest());
    }
}
