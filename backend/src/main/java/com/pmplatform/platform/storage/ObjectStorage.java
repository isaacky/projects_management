package com.pmplatform.platform.storage;

import java.io.InputStream;

/** Where files live: SeaweedFS (S3 API) on the servers, memory in tests. */
public interface ObjectStorage {

    void put(String key, InputStream content, long length, String contentType);

    /** The stored bytes. The caller closes the stream. */
    InputStream get(String key);

    long size(String key);

    void delete(String key);
}
