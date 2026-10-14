package com.pmplatform.platform.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Time-ordered UUID version 7 (RFC 9562). Keys sort by creation time, which keeps
 * B-tree indexes compact compared with random v4 UUIDs.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(System.currentTimeMillis());
    }

    static UUID generate(long epochMillis) {
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);

        long msb = (epochMillis & 0xFFFF_FFFF_FFFFL) << 16;      // 48-bit timestamp
        msb |= 0x7000L;                                           // version 7
        msb |= ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL);     // 12 random bits

        long lsb = 0x8000_0000_0000_0000L;                        // IETF variant (10xx)
        lsb |= (rand[2] & 0x3FL) << 56;
        for (int i = 3; i < 10; i++) {
            lsb |= (rand[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}
