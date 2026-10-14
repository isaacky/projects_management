package com.pmplatform.procurement.tender;

/** What was stored for one uploaded file. */
public record StoredFile(String storageKey, String fileName, String contentType, long sizeBytes, String sha256,
        byte[] fileNonce) {
}
