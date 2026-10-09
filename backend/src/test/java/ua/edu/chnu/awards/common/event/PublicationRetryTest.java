package ua.edu.chnu.awards.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PublicationRetryTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Duration RETRY_AFTER = Duration.ofMinutes(10);
    private static final Duration GIVE_UP_AFTER = Duration.ofHours(24);
    private static final Duration KEEP_FAILED = Duration.ofDays(30);

    private final IncompleteEventPublications publications = mock(IncompleteEventPublications.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final PublicationRetry retry = new PublicationRetry(publications, jdbc,
        new EventProperties(Duration.ofMinutes(1), Duration.ofMinutes(10), RETRY_AFTER, GIVE_UP_AFTER, KEEP_FAILED),
        Clock.fixed(NOW, ZoneOffset.UTC), registry);

    @Test
    void ac1_4_aPublicationIsRetriedFromTenMinutesOn() {
        assertThat(retry.inRetryWindow(publishedAgo(Duration.ofMinutes(9)), NOW)).isFalse();
        assertThat(retry.inRetryWindow(publishedAgo(RETRY_AFTER), NOW)).isTrue();
        assertThat(retry.inRetryWindow(publishedAgo(Duration.ofHours(23)), NOW)).isTrue();
    }

    @Test
    void ac1_5_aPublicationOlderThanADayIsNoLongerRetried() {
        assertThat(retry.inRetryWindow(publishedAgo(GIVE_UP_AFTER), NOW)).isFalse();
        assertThat(retry.inRetryWindow(publishedAgo(Duration.ofDays(2)), NOW)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac1_4_eachRunResubmitsOnlyTheRetryWindow() {
        retry.run();

        ArgumentCaptor<Predicate<EventPublication>> filter = ArgumentCaptor.forClass(Predicate.class);
        verify(publications).resubmitIncompletePublications(filter.capture());
        assertThat(filter.getValue().test(publishedAgo(Duration.ofMinutes(15)))).isTrue();
        assertThat(filter.getValue().test(publishedAgo(Duration.ofMinutes(5)))).isFalse();
        assertThat(filter.getValue().test(publishedAgo(Duration.ofHours(25)))).isFalse();
    }

    @Test
    void ac1_5_eachRunDeletesWhatIsOlderThanThirtyDays() {
        retry.run();

        verify(jdbc).update(anyString(), eq(Timestamp.from(NOW.minus(KEEP_FAILED))));
    }

    @Test
    void ac1_6_theGaugesReportIncompleteAndAbandonedPublications() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(1L);

        assertThat(registry.get(PublicationRetry.INCOMPLETE).gauge().value()).isEqualTo(3.0);
        assertThat(registry.get(PublicationRetry.ABANDONED).gauge().value()).isEqualTo(1.0);
    }

    @Test
    void ac1_6_anEmptyCountReadsAsZero() {
        assertThat(registry.get(PublicationRetry.INCOMPLETE).gauge().value()).isZero();
        assertThat(registry.get(PublicationRetry.ABANDONED).gauge().value()).isZero();
    }

    private static EventPublication publishedAgo(Duration age) {
        EventPublication publication = mock(EventPublication.class);
        when(publication.getPublicationDate()).thenReturn(NOW.minus(age));
        return publication;
    }
}
