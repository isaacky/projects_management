package com.pmplatform.platform.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void hasVersion7AndIetfVariant() {
        UUID id = UuidV7.generate();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void laterTimestampsSortLater() {
        UUID earlier = UuidV7.generate(1_700_000_000_000L);
        UUID later = UuidV7.generate(1_700_000_000_001L);
        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    void doesNotRepeat() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(UuidV7.generate())).isTrue();
        }
    }
}
