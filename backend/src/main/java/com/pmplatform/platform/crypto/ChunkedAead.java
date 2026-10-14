package com.pmplatform.platform.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Streaming authenticated encryption for large files: the plaintext is split into
 * 1 MiB chunks, each sealed with AES-256-GCM. Memory use stays at one chunk whatever
 * the file size, and any change, reordering or truncation of the stored bytes is
 * detected on decryption.
 *
 * <ul>
 *   <li>Nonce for chunk i = 8 random bytes chosen per file + i as a 4-byte counter.</li>
 *   <li>Additional data = 1 if this is the last chunk, else 0 (stops truncation).</li>
 *   <li>Stored size = plaintext size + 16 bytes per chunk; an empty file is one empty chunk.</li>
 * </ul>
 */
public final class ChunkedAead {

    public static final int CHUNK = 1024 * 1024;
    public static final int TAG_BYTES = 16;
    public static final int FILE_NONCE_BYTES = 8;

    private ChunkedAead() {
    }

    public static long chunkCount(long plainLength) {
        return Math.max(1, (plainLength + CHUNK - 1) / CHUNK);
    }

    public static long cipherLength(long plainLength) {
        return plainLength + TAG_BYTES * chunkCount(plainLength);
    }

    public static long plainLength(long cipherLength) {
        long chunks = Math.max(1, (cipherLength + CHUNK + TAG_BYTES - 1) / (CHUNK + TAG_BYTES));
        long plain = cipherLength - TAG_BYTES * chunks;
        if (plain < 0 || cipherLength(plain) != cipherLength) {
            throw new IllegalArgumentException("Not a valid sealed file length: " + cipherLength);
        }
        return plain;
    }

    /** Encrypts exactly {@code plainLength} bytes from {@code plain}; fails if the stream is shorter or longer. */
    public static InputStream encrypt(InputStream plain, long plainLength, SecretKey key, byte[] fileNonce) {
        return new Transform(plain, key, fileNonce, Cipher.ENCRYPT_MODE, chunkCount(plainLength), plainLength);
    }

    /** Decrypts a stored file of {@code cipherLength} bytes; throws IOException if it was altered. */
    public static InputStream decrypt(InputStream cipher, long cipherLength, SecretKey key, byte[] fileNonce) {
        return new Transform(cipher, key, fileNonce, Cipher.DECRYPT_MODE, chunkCount(plainLength(cipherLength)),
                cipherLength);
    }

    private static final class Transform extends InputStream {

        private final InputStream source;
        private final SecretKey key;
        private final byte[] fileNonce;
        private final int mode;
        private final long chunks;
        private final long sourceLength;
        private final int inChunk;

        private long chunkIndex;
        private long consumed;
        private byte[] out = new byte[0];
        private int pos;
        private boolean done;

        Transform(InputStream source, SecretKey key, byte[] fileNonce, int mode, long chunks, long sourceLength) {
            if (fileNonce.length != FILE_NONCE_BYTES) {
                throw new IllegalArgumentException("File nonce must be " + FILE_NONCE_BYTES + " bytes");
            }
            if (chunks > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("File too large");
            }
            this.source = source;
            this.key = key;
            this.fileNonce = fileNonce.clone();
            this.mode = mode;
            this.chunks = chunks;
            this.sourceLength = sourceLength;
            this.inChunk = mode == Cipher.ENCRYPT_MODE ? CHUNK : CHUNK + TAG_BYTES;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            while (pos >= out.length) {
                if (done) {
                    return -1;
                }
                nextChunk();
            }
            int n = Math.min(len, out.length - pos);
            System.arraycopy(out, pos, b, off, n);
            pos += n;
            return n;
        }

        private void nextChunk() throws IOException {
            boolean last = chunkIndex == chunks - 1;
            long remaining = sourceLength - consumed;
            int want = (int) Math.min(inChunk, remaining);
            byte[] in = source.readNBytes(want);
            if (in.length != want) {
                throw new IOException("Stream ended early: expected " + sourceLength + " bytes");
            }
            consumed += want;
            if (last && source.read() != -1) {
                throw new IOException("Stream is longer than the declared " + sourceLength + " bytes");
            }
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                byte[] nonce = ByteBuffer.allocate(12).put(fileNonce).putInt((int) chunkIndex).array();
                c.init(mode, key, new GCMParameterSpec(TAG_BYTES * 8, nonce));
                c.updateAAD(new byte[] {(byte) (last ? 1 : 0)});
                out = c.doFinal(in);
            } catch (GeneralSecurityException e) {
                throw new IOException("Sealed file failed integrity check at chunk " + chunkIndex, e);
            }
            Arrays.fill(in, (byte) 0);
            pos = 0;
            chunkIndex++;
            done = last;
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }
}
