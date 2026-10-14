package com.pmplatform.platform.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps files in memory. For tests ({@code pmp.storage.type=memory}); never for real data. */
@Component
@ConditionalOnProperty(name = "pmp.storage.type", havingValue = "memory")
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, InputStream content, long length, String contentType) {
        try {
            byte[] bytes = content.readAllBytes();
            if (bytes.length != length) {
                throw new IllegalArgumentException("Expected " + length + " bytes, got " + bytes.length);
            }
            objects.put(key, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream get(String key) {
        byte[] b = objects.get(key);
        if (b == null) {
            throw new IllegalArgumentException("No object " + key);
        }
        return new ByteArrayInputStream(b);
    }

    @Override
    public long size(String key) {
        return get(key) instanceof ByteArrayInputStream s ? s.available() : 0;
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
