package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.DeviceFingerprint;
import ua.edu.chnu.awards.auth.service.RequestThrottle;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.gdpr.dto.DataExport;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.event.DataExported;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Exports a person's own data (GDPR Article 20): at most once a minute, audited, and announced by email.
 */
@Service
@RequiredArgsConstructor
public class DataExportService {

    /** Redis key prefix of the one-export-a-minute marker, followed by the user id. */
    public static final String THROTTLE_KEY_PREFIX = "gdpr:export:";
    static final Duration INTERVAL = Duration.ofMinutes(1);
    static final String FILE_PREFIX = "award-monitoring-export-";

    private final UserRepository userRepository;
    private final PersonalDataAssembler assembler;
    private final RequestThrottle throttle;
    private final AuditService audit;
    private final DeviceFingerprint fingerprint;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Builds the export of the caller.
     *
     * @param userId the caller
     * @return the file and its attachment name
     * @throws ApiProblemException 429 {@code too-many-requests} within a minute of the previous export
     */
    @Transactional
    public DataExport export(long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!throttle.claimForTransaction(THROTTLE_KEY_PREFIX + userId, INTERVAL)) {
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
                "Your data was exported a moment ago; try again in a minute");
        }
        PersonalDataFile file = assembler.assemble(user);
        audit.record(AuditAction.DATA_EXPORT, AuditEntityConstants.GDPR, userId, userId, file.sectionCounts());
        ClientRequest client = ClientRequest.current();
        events.publishEvent(new DataExported(user.getEmailAddress(), user.getFirstName(),
            file.exportMetadata().exportDate(), client.ip(),
            fingerprint.of(client.userAgent(), client.acceptLanguage()).browser()));
        LocalDate day = LocalDate.ofInstant(file.exportMetadata().exportDate(), clock.getZone());
        return new DataExport(FILE_PREFIX + day + ".json", file);
    }
}
