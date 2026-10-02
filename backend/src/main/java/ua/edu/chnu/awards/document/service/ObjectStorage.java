package ua.edu.chnu.awards.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.config.DocumentProperties;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * The private bucket of document contents in the S3-compatible storage. Every object is encrypted by the
 * storage; the bucket is created on first use without any access policy.
 */
@Component
@Slf4j
public class ObjectStorage {

    private final S3Client s3;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    /**
     * Binds the storage to the configured bucket.
     *
     * @param s3         the storage client
     * @param properties the bucket name
     */
    public ObjectStorage(S3Client s3, DocumentProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    /**
     * Prepares the bucket when the application has started; a storage that is down is only logged, the first
     * upload tries again.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void prepareBucket() {
        try {
            ensureBucket();
        } catch (StorageUnavailableException exception) {
            log.warn("Document storage not ready at start: {}", exception.getCause().getMessage());
        }
    }

    /**
     * Creates the bucket when it is missing, with server-side encryption as its default. An existing bucket is
     * left as it is: in Compose it is created by {@code minio-init}, and the backend's account may not change it.
     *
     * @throws StorageUnavailableException when the storage cannot be reached or refuses
     */
    public void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        call(() -> {
            if (!exists()) {
                create();
            }
            return null;
        });
        bucketReady.set(true);
    }

    /**
     * Writes an object, encrypted at rest.
     *
     * @param key         the object key
     * @param content     the content, opened again if the call is retried
     * @param size        content length in bytes
     * @param contentType media type of the content
     * @throws StorageUnavailableException when the storage cannot be reached or refuses
     */
    public void put(String key, InputStreamSource content, long size, String contentType) {
        ensureBucket();
        call(() -> s3.putObject(request -> request.bucket(bucket).key(key).contentType(contentType)
                .contentLength(size).serverSideEncryption(ServerSideEncryption.AES256),
            RequestBody.fromContentProvider(() -> open(content), size, contentType)));
    }

    /**
     * Opens an object for reading.
     *
     * @param key the object key
     * @return the content stream, empty when there is no such object
     * @throws StorageUnavailableException when the storage cannot be reached or refuses
     */
    public Optional<InputStream> get(String key) {
        try {
            return Optional.of(call(() -> s3.getObject(request -> request.bucket(bucket).key(key))));
        } catch (StorageUnavailableException exception) {
            if (exception.getCause() instanceof NoSuchKeyException) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    /**
     * Removes objects; a missing object is not an error.
     *
     * @param keys the object keys
     * @throws StorageUnavailableException when the storage cannot be reached or refuses
     */
    public void delete(Collection<String> keys) {
        keys.forEach(key -> call(() -> s3.deleteObject(request -> request.bucket(bucket).key(key))));
    }

    /**
     * Keys under a prefix whose objects were last written before a moment.
     *
     * @param prefix key prefix
     * @param cutoff the moment
     * @return the keys
     * @throws StorageUnavailableException when the storage cannot be reached or refuses
     */
    public List<String> keysOlderThan(String prefix, Instant cutoff) {
        return call(() -> s3.listObjectsV2Paginator(request -> request.bucket(bucket).prefix(prefix)).contents()
            .stream()
            .filter(object -> object.lastModified().isBefore(cutoff))
            .map(S3Object::key)
            .toList());
    }

    /**
     * The bucket all documents are kept in.
     *
     * @return the bucket name
     */
    public String bucket() {
        return bucket;
    }

    private boolean exists() {
        try {
            s3.headBucket(request -> request.bucket(bucket));
            return true;
        } catch (NoSuchBucketException missing) {
            return false;
        }
    }

    private void create() {
        try {
            s3.createBucket(request -> request.bucket(bucket));
            log.info("Created document bucket {}", bucket);
        } catch (BucketAlreadyOwnedByYouException created) {
            log.debug("Document bucket {} created concurrently", bucket);
        }
        s3.putBucketEncryption(request -> request.bucket(bucket).serverSideEncryptionConfiguration(config ->
            config.rules(rule -> rule.applyServerSideEncryptionByDefault(sse ->
                sse.sseAlgorithm(ServerSideEncryption.AES256)))));
    }

    private static InputStream open(InputStreamSource content) {
        try {
            return content.getInputStream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SdkException exception) {
            throw new StorageUnavailableException(exception);
        }
    }
}
