package com.pmplatform.platform.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import javax.crypto.SecretKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChunkedAeadTest {

    private final SealingKeys keys = new SealingKeys(Base64.getEncoder().encodeToString(SealingKeys.randomBytes(32)));
    private final SecretKey key = keys.unwrap(keys.newWrappedDataKey());

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 1000, ChunkedAead.CHUNK - 1, ChunkedAead.CHUNK, ChunkedAead.CHUNK + 1,
            3 * ChunkedAead.CHUNK + 12345})
    void roundTripsAndDetectsTampering(int size) throws IOException {
        byte[] plain = new byte[size];
        new Random(size).nextBytes(plain);
        byte[] nonce = SealingKeys.randomBytes(ChunkedAead.FILE_NONCE_BYTES);
        byte[] sealed = ChunkedAead.encrypt(new ByteArrayInputStream(plain), size, key, nonce).readAllBytes();
        assertThat(sealed).hasSize((int) ChunkedAead.cipherLength(size));
        assertThat(ChunkedAead.decrypt(new ByteArrayInputStream(sealed), sealed.length, key, nonce).readAllBytes())
                .isEqualTo(plain);
        if (size > 0) {
            byte[] tampered = sealed.clone();
            tampered[tampered.length / 2] ^= 1;
            assertThatThrownBy(() -> ChunkedAead.decrypt(new ByteArrayInputStream(tampered), tampered.length, key,
                    nonce).readAllBytes()).isInstanceOf(IOException.class);
        }
    }

    @org.junit.jupiter.api.Test
    void detectsTruncationAtAChunkBoundary() throws IOException {
        int size = 3 * ChunkedAead.CHUNK + 5;
        byte[] nonce = SealingKeys.randomBytes(ChunkedAead.FILE_NONCE_BYTES);
        byte[] sealed = ChunkedAead.encrypt(new ByteArrayInputStream(new byte[size]), size, key, nonce).readAllBytes();
        byte[] truncated = Arrays.copyOf(sealed, 3 * (ChunkedAead.CHUNK + ChunkedAead.TAG_BYTES));
        assertThatThrownBy(() -> ChunkedAead.decrypt(new ByteArrayInputStream(truncated), truncated.length, key,
                nonce).readAllBytes()).isInstanceOf(IOException.class);
    }

    @org.junit.jupiter.api.Test
    void aDifferentMasterKeyCannotUnwrapTheTenderKey() {
        byte[] wrapped = keys.newWrappedDataKey();
        SealingKeys other = new SealingKeys(Base64.getEncoder().encodeToString(SealingKeys.randomBytes(32)));
        assertThatThrownBy(() -> other.unwrap(wrapped)).isInstanceOf(IllegalStateException.class);
    }
}
