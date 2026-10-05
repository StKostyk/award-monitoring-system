package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.DocumentProperties;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

class UploadLimitsTest {

    private static final long USER_ID = 21L;
    private static final int RATE = 20;
    private static final long QUOTA = DataSize.ofMegabytes(50).toBytes();
    private static final long MEGABYTE = DataSize.ofMegabytes(1).toBytes();
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:45Z");
    private static final long WINDOW = NOW.getEpochSecond() / 60;
    private static final long SECONDS_LEFT = 15L;

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final UploadLimits limits = new UploadLimits(redis, documents,
        new DocumentProperties("award-documents", DataSize.ofMegabytes(10), 10, Duration.ofHours(24),
            DataSize.ofMegabytes(50), RATE, null, null),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void ac3_6_uploadsAreCountedPerUserAndMinuteAndTheTwentyFirstIsRefused() {
        String key = UploadLimits.RATE_KEY_PREFIX + USER_ID + ":" + WINDOW;
        when(values.increment(key)).thenReturn(1L, (long) RATE, RATE + 1L);

        limits.checkRate(USER_ID);
        verify(redis).expire(key, Duration.ofMinutes(2));
        limits.checkRate(USER_ID);
        assertThatThrownBy(() -> limits.checkRate(USER_ID)).isInstanceOfSatisfying(ApiProblemException.class,
            problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(problem.getType()).isEqualTo("too-many-requests");
                assertThat(problem.getProperties()).containsEntry("retryAfter", SECONDS_LEFT);
            });
    }

    @Test
    void ac3_6_withoutRedisUploadsAreNotLimited() {
        when(values.increment(anyString())).thenThrow(new QueryTimeoutException("down"));

        assertThatCode(() -> limits.checkRate(USER_ID)).doesNotThrowAnyException();
        verify(redis, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void ac3_5_theLockedCheckTakesTheUsersUploadLockFirst() {
        when(documents.totalSizeUploadedBy(USER_ID)).thenReturn(QUOTA);

        assertThatThrownBy(() -> limits.checkQuotaLocked(USER_ID, 1)).isInstanceOf(ApiProblemException.class);
        var order = inOrder(documents);
        order.verify(documents).lockUploadsOf((0x444F_4351L << Integer.SIZE) | USER_ID);
        order.verify(documents).totalSizeUploadedBy(USER_ID);
    }

    @Test
    void ac3_5_aFileThatTakesTheUserOverTheQuotaIsRefused() {
        when(documents.totalSizeUploadedBy(USER_ID)).thenReturn(QUOTA - MEGABYTE);

        assertThatCode(() -> limits.checkQuota(USER_ID, MEGABYTE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limits.checkQuota(USER_ID, MEGABYTE + 1))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.getType()).isEqualTo("storage-quota");
                assertThat(problem.getProperties()).containsEntry("quota", QUOTA)
                    .containsEntry("used", QUOTA - MEGABYTE);
            });
    }
}
