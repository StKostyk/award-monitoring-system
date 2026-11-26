package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditRedaction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.gdpr.event.ErasureCompleted;
import ua.edu.chnu.awards.gdpr.event.ErasurePostponed;
import ua.edu.chnu.awards.gdpr.repository.ErasureRequests;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Carries out due erasures, one transaction per request: every contributor in its order, the audit row with their
 * counts, the redaction of the trail, the request marked completed and the confirmation mail after the commit. A
 * request whose account is under a legal hold, or whose holder is the only system administrator, is postponed; a
 * failure rolls the whole erasure back and the next run tries again. A request filed by the retention job sends no
 * mail and is cancelled when its account no longer meets the retention condition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErasureRunner {

    private static final String REQUEST_ID = "requestId";
    private static final String ORIGIN = "origin";

    private final ErasureRequests requests;
    private final ErasureGuard guard;
    private final UserAccounts users;
    private final List<ErasureContributor> contributors;
    private final AuditService audit;
    private final AuditRedaction redaction;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final ErasureMetrics metrics;
    private final Clock clock;

    /**
     * Erases every account whose request is due.
     *
     * @param now the current moment
     * @return how many accounts were erased
     */
    public int runDue(Instant now) {
        int erased = 0;
        for (Long requestId : requests.due(now)) {
            try {
                Outcome outcome = Objects.requireNonNull(transactions.execute(status -> erase(requestId, now)));
                if (outcome == Outcome.COMPLETED) {
                    metrics.completed();
                    erased++;
                }
            } catch (Exception exception) {
                metrics.failed();
                log.error("Erasure request {} failed and stays pending for the next run", requestId, exception);
            }
        }
        return erased;
    }

    private Outcome erase(long requestId, Instant now) {
        requests.serialize();
        Optional<ErasureRequests.Locked> due = requests.lockDue(requestId, now);
        if (due.isEmpty()) {
            return Outcome.SKIPPED;
        }
        ErasureRequests.Locked request = due.get();
        User user = users.findByIdForUpdate(request.userId())
            .orElseThrow(() -> new IllegalStateException("Erasure request " + requestId + " has no account"));
        if (guard.cancelIfLapsed(request, now)) {
            return Outcome.CANCELLED;
        }
        LocalDate day = LocalDate.now(clock);
        return guard.blocker(request.userId(), day)
            .map(reason -> postpone(request, user, reason, now))
            .orElseGet(() -> complete(request, user, now, day));
    }

    private Outcome complete(ErasureRequests.Locked request, User user, Instant now, LocalDate day) {
        long userId = request.userId();
        final String email = user.getEmailAddress();
        final String firstName = user.getFirstName();
        Set<String> names = new LinkedHashSet<>();
        names.add(user.getFullName());
        names.addAll(redaction.formerNames(userId));
        ErasureContext context = new ErasureContext(userId, email, names, now, day);
        contributors.stream()
            .sorted(Comparator.comparingInt(ErasureContributor::order))
            .forEach(contributor -> contributor.erase(context));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(REQUEST_ID, request.requestId());
        details.put(ORIGIN, request.origin().code());
        details.putAll(context.counts());
        audit.record(AuditAction.ERASURE_COMPLETED, AuditEntityConstants.GDPR, userId, userId, details);
        redaction.redactSubject(userId, context.names(), context.erasedRecords());
        requests.markCompleted(request.requestId(), now);
        if (request.byPerson()) {
            events.publishEvent(new ErasureCompleted(email, firstName));
        }
        log.info("Erased account {} (request {})", userId, request.requestId());
        return Outcome.COMPLETED;
    }

    private Outcome postpone(ErasureRequests.Locked request, User user, ErasureBlocker reason, Instant now) {
        if (requests.markPostponed(request.requestId(), now)) {
            audit.record(AuditAction.ERASURE_POSTPONED, AuditEntityConstants.GDPR, request.userId(),
                request.userId(), Map.of(REQUEST_ID, request.requestId(), "reason", reason.code(), ORIGIN,
                    request.origin().code()));
            if (reason.notifies() && request.byPerson()) {
                events.publishEvent(new ErasurePostponed(user.getEmailAddress(), user.getFirstName()));
            }
        }
        return Outcome.POSTPONED;
    }

    private enum Outcome {
        COMPLETED,
        POSTPONED,
        CANCELLED,
        SKIPPED
    }
}
