package ua.edu.chnu.awards.notification.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.notification.dto.NotificationPreferenceItem;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationPreference;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.repository.NotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;

/**
 * Which channels a person wants per notification type. A type never changed is on for both channels, every type
 * keeps at least one channel, and an effective change is audited with the old and new values.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private static final String LIST = "preferences";
    private static final String REQUIRED = "required";

    private final NotificationPreferenceRepository repository;
    private final AuditService audit;

    /**
     * Every type with its channels.
     *
     * @param userId the person
     * @return one entry per type, in declaration order
     */
    @Transactional(readOnly = true)
    public List<NotificationPreferenceItem> preferences(long userId) {
        return items(stored(userId));
    }

    /**
     * Stores the listed types; types not listed keep their state.
     *
     * @param userId  the person
     * @param changes the types to store
     * @return every type after the change
     */
    @Transactional
    public List<NotificationPreferenceItem> update(long userId, List<NotificationPreferenceItem> changes) {
        List<Choice> choices = validate(changes);
        Map<NotificationType, Choice> current = stored(userId);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (Choice choice : choices) {
            Choice old = current.get(choice.type());
            if (!old.equals(choice)) {
                repository.upsert(userId, choice.type().name(), choice.email(), choice.inApp());
                before.put(choice.type().name(), old.channels());
                after.put(choice.type().name(), choice.channels());
                current.put(choice.type(), choice);
            }
        }
        if (!after.isEmpty()) {
            audit.recordChange(AuditAction.NOTIFICATION_PREFERENCES_CHANGED, AuditEntityConstants.USER, userId,
                userId, before, after);
        }
        return items(current);
    }

    /**
     * Whether a notification of a type may go to a person through a channel, read when the notification is sent.
     *
     * @param userId  the person; without one there is no preference to honour
     * @param type    the notification type
     * @param channel the channel
     * @return false only if the person switched the channel off for the type
     */
    @Transactional(readOnly = true)
    public boolean allows(Long userId, NotificationType type, NotificationChannel channel) {
        return userId == null || repository.findByUserIdAndType(userId, type)
            .map(row -> channel == NotificationChannel.EMAIL ? row.isEmailEnabled() : row.isInAppEnabled())
            .orElse(true);
    }

    private Map<NotificationType, Choice> stored(long userId) {
        Map<NotificationType, Choice> choices = new EnumMap<>(NotificationType.class);
        Arrays.stream(NotificationType.values()).forEach(type -> choices.put(type, new Choice(type, true, true)));
        for (NotificationPreference row : repository.findByUserId(userId)) {
            choices.put(row.getType(), new Choice(row.getType(), row.isEmailEnabled(), row.isInAppEnabled()));
        }
        return choices;
    }

    private static List<NotificationPreferenceItem> items(Map<NotificationType, Choice> choices) {
        return choices.values().stream()
            .map(choice -> new NotificationPreferenceItem(choice.type().name(), choice.email(), choice.inApp()))
            .toList();
    }

    private static List<Choice> validate(List<NotificationPreferenceItem> changes) {
        if (changes == null) {
            throw invalid(List.of(new FieldViolation(LIST, REQUIRED, "The list is required")));
        }
        List<FieldViolation> errors = new ArrayList<>();
        List<Choice> choices = new ArrayList<>();
        Set<NotificationType> seen = EnumSet.noneOf(NotificationType.class);
        for (int i = 0; i < changes.size(); i++) {
            String field = LIST + "[" + i + "]";
            NotificationPreferenceItem item = changes.get(i);
            NotificationType type = item == null ? null : typeOf(item.type());
            Optional.ofNullable(typeViolation(field, item, type, seen)).ifPresent(errors::add);
            FieldViolation channels = channelViolation(field, item);
            if (channels != null) {
                errors.add(channels);
            } else if (type != null) {
                choices.add(new Choice(type, item.email(), item.inApp()));
            }
        }
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        return choices;
    }

    private static ApiProblemException invalid(List<FieldViolation> errors) {
        return ApiProblemException.validationFailed("The notification preferences are not valid", errors);
    }

    private static FieldViolation typeViolation(String field, NotificationPreferenceItem item, NotificationType type,
                                                Set<NotificationType> seen) {
        if (item != null && type == null) {
            return new FieldViolation(field + ".type", "invalid", "Unknown notification type");
        }
        if (type != null && !seen.add(type)) {
            return new FieldViolation(field + ".type", "duplicate", "The type is listed twice");
        }
        return null;
    }

    private static FieldViolation channelViolation(String field, NotificationPreferenceItem item) {
        if (item == null || item.email() == null || item.inApp() == null) {
            return new FieldViolation(field, REQUIRED, "Both channels are required");
        }
        if (!item.email() && !item.inApp()) {
            return new FieldViolation(field, "both-off", "Each type keeps at least one channel");
        }
        return null;
    }

    private static NotificationType typeOf(String name) {
        return Arrays.stream(NotificationType.values())
            .filter(type -> type.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    private record Choice(NotificationType type, boolean email, boolean inApp) {

        Map<String, Object> channels() {
            return Map.of("email", email, "inApp", inApp);
        }
    }
}
