package com.pmplatform.procurement.tender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The fingerprint printed on a bidder's receipt: SHA-256 over the tender, supplier,
 * receipt time and each sealed file's envelope, size and SHA-256. Anyone holding the
 * receipt can later check that the bid opened is the bid that was received.
 */
public final class ReceiptHash {

    private ReceiptHash() {
    }

    public record Part(Envelope envelope, long size, String sha256) {
    }

    public static String of(UUID tenderId, UUID supplierId, String receiptNo, Instant receivedAt, List<Part> parts) {
        StringBuilder s = new StringBuilder("pmp-bid-receipt-v1\n")
                .append(tenderId).append('\n').append(supplierId).append('\n')
                .append(receiptNo).append('\n').append(receivedAt).append('\n');
        parts.stream()
                .sorted(Comparator.comparing(Part::envelope).thenComparing(Part::sha256).thenComparingLong(Part::size))
                .forEach(p -> s.append(p.envelope()).append(' ').append(p.size()).append(' ').append(p.sha256())
                        .append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(s.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
