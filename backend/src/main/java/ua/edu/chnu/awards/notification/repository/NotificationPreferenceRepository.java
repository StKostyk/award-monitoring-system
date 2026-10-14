package ua.edu.chnu.awards.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.notification.entity.NotificationPreference;
import ua.edu.chnu.awards.notification.entity.NotificationType;

/**
 * Stored notification channels per person and type.
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    /**
     * The stored preferences of a person.
     *
     * @param userId the person
     * @return one row per changed type
     */
    List<NotificationPreference> findByUserId(Long userId);

    /**
     * The stored preference of a person for one type.
     *
     * @param userId the person
     * @param type   the notification type
     * @return the row, if the type was ever changed
     */
    Optional<NotificationPreference> findByUserIdAndType(Long userId, NotificationType type);

    /**
     * Stores the channels of one type, inserting the row or updating it; two concurrent first changes cannot
     * collide on the unique key.
     *
     * @param userId the person
     * @param type   the notification type name
     * @param email  whether mails are sent
     * @param inApp  whether in-app notifications are written
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        insert into notification_preferences (user_id, event_type, email_enabled, in_app_enabled)
        values (:userId, :type, :email, :inApp)
        on conflict (user_id, event_type)
        do update set email_enabled = excluded.email_enabled, in_app_enabled = excluded.in_app_enabled
        """, nativeQuery = true)
    void upsert(@Param("userId") long userId, @Param("type") String type, @Param("email") boolean email,
                @Param("inApp") boolean inApp);
}
