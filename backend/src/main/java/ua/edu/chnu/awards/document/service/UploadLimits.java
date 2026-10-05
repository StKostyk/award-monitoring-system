package ua.edu.chnu.awards.document.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.web.ApiExceptionHandler;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.DocumentProperties;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Per-user bounds on uploads: a fixed one-minute window of upload attempts in Redis, and the total size of the
 * documents a user has uploaded. Without Redis uploads are not rate limited.
 */
@Component
@Slf4j
public class UploadLimits {

    /** Redis key prefix of the per-user upload windows. */
    public static final String RATE_KEY_PREFIX = "documents:rate:";

    private static final long QUOTA_LOCK_SPACE = 0x444F_4351L << Integer.SIZE;
    private static final long WINDOW_SECONDS = 60;
    private static final Duration KEY_TTL = Duration.ofSeconds(2 * WINDOW_SECONDS);

    private final StringRedisTemplate redis;
    private final DocumentRepository documents;
    private final DocumentProperties properties;
    private final Clock clock;

    /**
     * Creates the limits.
     *
     * @param redis      the window counters
     * @param documents  the uploaded documents, for the quota
     * @param properties the rate and quota
     * @param clock      the time of the window
     */
    public UploadLimits(StringRedisTemplate redis, DocumentRepository documents, DocumentProperties properties,
                        Clock clock) {
        this.redis = redis;
        this.documents = documents;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Counts an upload attempt of the user in the current minute.
     *
     * @param userId the uploader
     * @throws ApiProblemException 429 {@code too-many-requests} with {@code retryAfter} in seconds when the user
     *                             started more uploads this minute than allowed
     */
    public void checkRate(long userId) {
        long now = clock.instant().getEpochSecond();
        long window = now / WINDOW_SECONDS;
        if (countInWindow(RATE_KEY_PREFIX + userId + ":" + window) > properties.uploadRate()) {
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
                "Too many uploads; try again in a minute",
                Map.of(ApiExceptionHandler.RETRY_AFTER, Math.max(1, (window + 1) * WINDOW_SECONDS - now)));
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

    private long countInWindow(String key) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, KEY_TTL);
            }
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            log.error("Redis unavailable; uploads are not rate limited: {}", e.getMessage());
            return 0;
        }
    }
}
