package ua.edu.chnu.awards.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final NotificationChannel IN_APP = NotificationChannel.IN_APP;
    private static final long CALLER = 7L;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repository, Clock.fixed(NOW,
        ZoneOffset.UTC));

    @Test
    void ac1_7_theListIsTheCallersInAppRowsNewestFirstWithAClampedPage() {
        when(repository.findByUserIdAndChannel(eq(CALLER), eq(IN_APP), any())).thenReturn(Page.empty());

        service.list(CALLER, false, List.of(), -1, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdAndChannel(eq(CALLER), eq(IN_APP), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getSort())
            .containsExactly(Sort.Order.desc("sentAt"), Sort.Order.desc("id"));
    }

    @Test
    void ac1_7_unreadReadsOnlyUnreadRows() {
        when(repository.findByUserIdAndChannelAndReadFalse(eq(CALLER), eq(IN_APP), any())).thenReturn(Page.empty());

        assertThat(service.list(CALLER, true, List.of(), 0, 20)).isEmpty();
        verify(repository, never()).findByUserIdAndChannel(anyLong(), any(), any());
    }

    @Test
    void ac1_8_theCountIsTheCallersUnreadInAppRows() {
        when(repository.countByUserIdAndChannelAndReadFalse(CALLER, IN_APP)).thenReturn(3L);

        assertThat(service.unreadCount(CALLER)).isEqualTo(3);
    }

    @Test
    void ac1_9_markingAnUnreadRowNeedsNoFurtherCheck() {
        when(repository.markRead(5L, CALLER, IN_APP, NOW)).thenReturn(1);

        service.markRead(CALLER, 5L);

        verify(repository, never()).existsByIdAndUserIdAndChannel(anyLong(), anyLong(), any());
    }

    @Test
    void ac1_9_markingARowReadAlreadyIsANoOp() {
        when(repository.markRead(5L, CALLER, IN_APP, NOW)).thenReturn(0);
        when(repository.existsByIdAndUserIdAndChannel(5L, CALLER, IN_APP)).thenReturn(true);

        service.markRead(CALLER, 5L);

        verify(repository).existsByIdAndUserIdAndChannel(5L, CALLER, IN_APP);
    }

    @Test
    void ac1_9_aRowThatIsNotTheCallersInAppRowIsNotFound() {
        when(repository.markRead(5L, CALLER, IN_APP, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.markRead(CALLER, 5L)).isInstanceOf(NotificationNotFoundException.class)
            .hasMessageContaining("5");
    }

    @Test
    void ac7_2_typesFilterTheListAndCombineWithUnread() {
        List<NotificationType> types = List.of(NotificationType.AWARD_APPROVED, NotificationType.AWARD_REJECTED);
        when(repository.findByUserIdAndChannelAndTypeIn(eq(CALLER), eq(IN_APP), eq(types), any()))
            .thenReturn(Page.empty());
        when(repository.findByUserIdAndChannelAndReadFalseAndTypeIn(eq(CALLER), eq(IN_APP), eq(types), any()))
            .thenReturn(Page.empty());

        service.list(CALLER, false, types, 0, 20);
        service.list(CALLER, true, types, 0, 20);

        verify(repository).findByUserIdAndChannelAndTypeIn(eq(CALLER), eq(IN_APP), eq(types), any());
        verify(repository).findByUserIdAndChannelAndReadFalseAndTypeIn(eq(CALLER), eq(IN_APP), eq(types), any());
        verify(repository, never()).findByUserIdAndChannel(anyLong(), any(), any());
        verify(repository, never()).findByUserIdAndChannelAndReadFalse(anyLong(), any(), any());
    }

    @Test
    void ac7_1_markingARowUnreadNeedsNoFurtherCheck() {
        when(repository.markUnread(5L, CALLER, IN_APP)).thenReturn(1);

        service.markUnread(CALLER, 5L);

        verify(repository, never()).existsByIdAndUserIdAndChannel(anyLong(), anyLong(), any());
    }

    @Test
    void ac7_1_markingAnUnreadRowUnreadIsANoOp() {
        when(repository.markUnread(5L, CALLER, IN_APP)).thenReturn(0);
        when(repository.existsByIdAndUserIdAndChannel(5L, CALLER, IN_APP)).thenReturn(true);

        service.markUnread(CALLER, 5L);

        verify(repository).existsByIdAndUserIdAndChannel(5L, CALLER, IN_APP);
    }

    @Test
    void ac7_1_unreadingARowThatIsNotTheCallersInAppRowIsNotFound() {
        when(repository.markUnread(5L, CALLER, IN_APP)).thenReturn(0);

        assertThatThrownBy(() -> service.markUnread(CALLER, 5L)).isInstanceOf(NotificationNotFoundException.class)
            .hasMessageContaining("5");
    }

    @Test
    void ac1_10_readAllReturnsHowManyWereMarked() {
        when(repository.markAllRead(CALLER, IN_APP, NOW)).thenReturn(2);

        assertThat(service.markAllRead(CALLER)).isEqualTo(2);
    }
}
