package ua.edu.chnu.awards.document.service;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.limit.FixedWindowCounter;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.DocumentProperties;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Per-user bounds on uploads: a fixed one-minute window of upload attempts in Redis, and the total size of the
 * documents a user has uploaded. Without Redis uploads are not rate limited.
 */
@Component
@RequiredArgsConstructor
public class UploadLimits {

    /** Redis key prefix of the per-user upload windows. */
    public static final String RATE_KEY_PREFIX = "documents:rate:";

    private static final long QUOTA_LOCK_SPACE = 0x444F_4351L << Integer.SIZE;

    private final FixedWindowCounter counter;
    private final DocumentRepository documents;
    private final DocumentProperties properties;

    /**
     * Counts an upload attempt of the user in the current minute.
     *
     * @param userId the uploader
     * @throws ApiProblemException 429 {@code too-many-requests} with {@code retryAfter} in seconds when the user
     *                             started more uploads this minute than allowed
     */
    public void checkRate(long userId) {
        long wait = counter.secondsToWait(RATE_KEY_PREFIX, userId, properties.uploadRate());
        if (wait > 0) {
            throw ApiProblemException.tooManyRequests("Too many uploads; try again in a minute", wait);
        }
    }

    /**
     * Checks that a file of the given size fits into the user's quota.
     *
     * @param userId the uploader
     * @param size   the new file in bytes
     * @throws ApiProblemException 409 {@code storage-quota} with {@code quota} and {@code used} in bytes
     */
    public void checkQuota(long userId, long size) {
        long used = documents.totalSizeUploadedBy(userId);
        long quota = properties.userQuota().toBytes();
        if (used + size > quota) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "storage-quota",
                "The file does not fit into the storage quota", Map.of("quota", quota, "used", used));
        }
    }

    /**
     * Checks the quota while holding the user's upload lock until the end of the caller's transaction, so that
     * concurrent uploads to different awards cannot pass it together.
     *
     * @param userId the uploader
     * @param size   the new file in bytes
     * @throws ApiProblemException 409 {@code storage-quota} with {@code quota} and {@code used} in bytes
     */
    public void checkQuotaLocked(long userId, long size) {
        documents.lockUploadsOf(QUOTA_LOCK_SPACE | userId);
        checkQuota(userId, size);
    }
}
