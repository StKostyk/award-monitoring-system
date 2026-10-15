package ua.edu.chnu.awards.notification.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.notification.entity.DeliveryStatus;
import ua.edu.chnu.awards.notification.entity.Notification;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;

/**
 * Notification rows.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Inserts a row unless one with the same dedupe key exists.
     *
     * @param row the values
     * @return 1 when written, 0 when the key existed
     */
    @Modifying
    @Query(value = "INSERT INTO notifications (user_id, type, channel, payload, dedupe_key, reference_type, "
        + "reference_id, sent_at, expires_at) VALUES (:#{#row.userId()}, :#{#row.type()}, :#{#row.channel()}, "
        + "CAST(:#{#row.payload()} AS jsonb), :#{#row.dedupeKey()}, :#{#row.referenceType()}, "
        + ":#{#row.referenceId()}, :#{#row.sentAt()}, :#{#row.expiresAt()}) ON CONFLICT (dedupe_key) DO NOTHING",
        nativeQuery = true)
    int insertOnce(@Param("row") NewNotification row);

    /**
     * Claims the delivery of a mail: inserts a {@code SENDING} row, or takes over the row of the same key when it
     * failed or was claimed before {@code staleBefore}. One statement, so two concurrent claims cannot both win.
     *
     * @param row         the values, channel {@code EMAIL}
     * @param staleBefore claims older than this are taken over
     * @return 1 when claimed, 0 when the mail was sent or is being sent, or the recipient no longer exists
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO notifications (user_id, type, channel, payload, dedupe_key, reference_type, "
        + "reference_id, sent_at, expires_at, delivery_status) SELECT u.user_id, :#{#row.type()}, "
        + ":#{#row.channel()}, CAST(:#{#row.payload()} AS jsonb), :#{#row.dedupeKey()}, :#{#row.referenceType()}, "
        + ":#{#row.referenceId()}, :#{#row.sentAt()}, :#{#row.expiresAt()}, 'SENDING' FROM users u "
        + "WHERE u.user_id = :#{#row.userId()} "
        + "ON CONFLICT (dedupe_key) DO UPDATE SET delivery_status = 'SENDING', sent_at = EXCLUDED.sent_at, "
        + "delivered_at = NULL WHERE notifications.delivery_status = 'FAILED' "
        + "OR (notifications.delivery_status = 'SENDING' AND notifications.sent_at < :staleBefore)",
        nativeQuery = true)
    int claim(@Param("row") NewNotification row, @Param("staleBefore") Instant staleBefore);

    /**
     * Records the outcome of a claimed mail.
     *
     * @param dedupeKey   the key of the claimed row
     * @param status      sent or failed
     * @param deliveredAt when the mail server accepted it, null when it did not
     * @return 1 when recorded
     */
    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.deliveryStatus = :status, n.deliveredAt = :deliveredAt "
        + "WHERE n.dedupeKey = :dedupeKey")
    int finish(@Param("dedupeKey") String dedupeKey, @Param("status") DeliveryStatus status,
               @Param("deliveredAt") Instant deliveredAt);

    /**
     * Every row of one person, both channels, newest first.
     *
     * @param userId the person
     * @return the rows
     */
    List<Notification> findByUserIdOrderBySentAtDesc(long userId);

    /**
     * One person's rows of a channel.
     *
     * @param userId   the person
     * @param channel  the channel
     * @param pageable page and order
     * @return the page
     */
    Page<Notification> findByUserIdAndChannel(long userId, NotificationChannel channel, Pageable pageable);

    /**
     * One person's unread rows of a channel.
     *
     * @param userId   the person
     * @param channel  the channel
     * @param pageable page and order
     * @return the page
     */
    Page<Notification> findByUserIdAndChannelAndReadFalse(long userId, NotificationChannel channel,
                                                          Pageable pageable);

    /**
     * How many rows of a channel one person has not read.
     *
     * @param userId  the person
     * @param channel the channel
     * @return the count
     */
    long countByUserIdAndChannelAndReadFalse(long userId, NotificationChannel channel);

    /**
     * Whether a row of a channel belongs to a person.
     *
     * @param id      the row
     * @param userId  the person
     * @param channel the channel
     * @return true when it does
     */
    boolean existsByIdAndUserIdAndChannel(long id, long userId, NotificationChannel channel);

    /**
     * Marks one unread row of a person read.
     *
     * @param id      the row
     * @param userId  the person
     * @param channel the channel
     * @param at      when
     * @return 1 when marked, 0 when it was read already or is not the person's
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :at WHERE n.id = :id AND n.userId = :userId "
        + "AND n.channel = :channel AND n.read = false")
    int markRead(@Param("id") long id, @Param("userId") long userId, @Param("channel") NotificationChannel channel,
                 @Param("at") Instant at);

    /**
     * Marks every unread row of a person's channel read.
     *
     * @param userId  the person
     * @param channel the channel
     * @param at      when
     * @return how many were marked
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :at WHERE n.userId = :userId "
        + "AND n.channel = :channel AND n.read = false")
    int markAllRead(@Param("userId") long userId, @Param("channel") NotificationChannel channel,
                    @Param("at") Instant at);
}
