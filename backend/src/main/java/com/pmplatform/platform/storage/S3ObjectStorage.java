package com.pmplatform.platform.storage;

import java.io.InputStream;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3-compatible storage (SeaweedFS on the VPS servers). */
@Component
@ConditionalOnProperty(name = "pmp.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3;
    private final String bucket;
    private volatile boolean bucketChecked;

    public S3ObjectStorage(@Value("${pmp.storage.endpoint}") String endpoint,
            @Value("${pmp.storage.bucket}") String bucket,
            @Value("${pmp.storage.access-key}") String accessKey,
            @Value("${pmp.storage.secret-key}") String secretKey) {
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1) // ignored by SeaweedFS, required by the SDK
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                // Newer SDKs add checksums some S3-compatible stores reject; send them only when required.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Override
    public void put(String key, InputStream content, long length, String contentType) {
        ensureBucket();
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                .contentLength(length).build(), RequestBody.fromInputStream(content, length));
    }

    @Override
    public InputStream get(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public long size(String key) {
        return s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength();
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** Creates the bucket on first use if the storage-init job has not. */
    private void ensureBucket() {
        if (bucketChecked) {
            return;
        }
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            log.warn("Bucket {} missing; creating it", bucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            log.warn("Bucket {} missing; creating it", bucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
        bucketChecked = true;
    }
}
