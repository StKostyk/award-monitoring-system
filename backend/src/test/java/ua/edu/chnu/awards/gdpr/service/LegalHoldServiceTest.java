package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.common.web.NotFoundException;
import ua.edu.chnu.awards.gdpr.dto.LegalHold;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldOverview;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;

class LegalHoldServiceTest {

    private static final long USER_ID = 42L;
    private static final long OFFICER_ID = 7L;
    private static final long HOLD_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    private static final String REASON = "Pending court case 12/2026";
    private static final UserRef OFFICER = new UserRef(OFFICER_ID, "Galyna Officer", "gdpr@chnu.edu.ua");

    private final LegalHolds holds = mock(LegalHolds.class);
    private final AuditService audit = mock(AuditService.class);
    private final LegalHoldService service = new LegalHoldService(holds, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void ac3_2_aHoldIsPlacedAndAuditedWithTheReasonLengthOnly() {
        LegalHold placed = active();
        when(holds.lockAccount(USER_ID)).thenReturn(Optional.of("ACTIVE"));
        when(holds.place(USER_ID, REASON, OFFICER_ID, NOW)).thenReturn(HOLD_ID);
        when(holds.find(USER_ID, HOLD_ID)).thenReturn(Optional.of(placed));

        assertThat(service.place(USER_ID, "  " + REASON + " ", OFFICER_ID)).isEqualTo(placed);

        verify(audit).record(AuditAction.LEGAL_HOLD_PLACED, AuditEntityConstants.GDPR, OFFICER_ID, USER_ID,
            Map.of("holdId", HOLD_ID, "reasonLength", REASON.length()));
    }

    @Test
    void ac3_2_anActiveHoldIsRefused() {
        when(holds.lockAccount(USER_ID)).thenReturn(Optional.of("ACTIVE"));
        when(holds.isActive(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.place(USER_ID, REASON, OFFICER_ID))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.getType()).isEqualTo("legal-hold-active");
            });
        verify(holds, never()).place(anyLong(), anyString(), anyLong(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void ac3_2_anErasedAccountIsRefused() {
        when(holds.lockAccount(USER_ID)).thenReturn(Optional.of("DELETED"));

        assertThatThrownBy(() -> service.place(USER_ID, REASON, OFFICER_ID))
            .isInstanceOfSatisfying(ApiProblemException.class,
                problem -> assertThat(problem.getType()).isEqualTo("account-erased"));
        verifyNoInteractions(audit);
    }

    @Test
    void ac3_2_anUnknownAccountIsNotFound() {
        when(holds.lockAccount(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.place(USER_ID, REASON, OFFICER_ID)).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL, required", "'   ', required", "too short, too-short"}, nullValues = "NULL")
    void ac3_2_aMissingOrShortReasonIsRefused(String reason, String code) {
        assertThatThrownBy(() -> service.place(USER_ID, reason, OFFICER_ID))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(problem.getProperties()).containsKey("errors");
                assertThat((List<?>) problem.getProperties().get("errors")).singleElement()
                    .extracting(error -> ((FieldViolation) error).code()).isEqualTo(code);
            });
        verifyNoInteractions(holds);
    }

    @Test
    void ac3_2_aReasonOfFiveHundredCharactersIsAcceptedAndOneMoreIsNot() {
        when(holds.lockAccount(USER_ID)).thenReturn(Optional.of("ACTIVE"));
        when(holds.place(anyLong(), anyString(), anyLong(), any())).thenReturn(HOLD_ID);
        when(holds.find(USER_ID, HOLD_ID)).thenReturn(Optional.of(active()));

        service.place(USER_ID, "x".repeat(LegalHoldService.REASON_MAX), OFFICER_ID);

        assertThatThrownBy(() -> service.place(USER_ID, "x".repeat(LegalHoldService.REASON_MAX + 1), OFFICER_ID))
            .isInstanceOfSatisfying(ApiProblemException.class,
                problem -> assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void ac3_3_anActiveHoldIsLiftedAndAudited() {
        LegalHold lifted = new LegalHold(HOLD_ID, REASON, NOW, OFFICER, NOW, OFFICER, "Court case closed");
        when(holds.find(USER_ID, HOLD_ID)).thenReturn(Optional.of(active()), Optional.of(lifted));
        when(holds.lift(HOLD_ID, "Court case closed", OFFICER_ID, NOW)).thenReturn(true);

        assertThat(service.lift(USER_ID, HOLD_ID, "Court case closed", OFFICER_ID)).isEqualTo(lifted);

        verify(holds).lockAccount(USER_ID);
        verify(audit).record(AuditAction.LEGAL_HOLD_LIFTED, AuditEntityConstants.GDPR, OFFICER_ID, USER_ID,
            Map.of("holdId", HOLD_ID, "reasonLength", "Court case closed".length()));
    }

    @Test
    void ac3_3_aLiftedHoldIsRefused() {
        when(holds.find(USER_ID, HOLD_ID)).thenReturn(Optional.of(
            new LegalHold(HOLD_ID, REASON, NOW, OFFICER, NOW, OFFICER, "Court case closed")));

        assertThatThrownBy(() -> service.lift(USER_ID, HOLD_ID, "Court case closed", OFFICER_ID))
            .isInstanceOfSatisfying(ApiProblemException.class,
                problem -> assertThat(problem.getType()).isEqualTo("legal-hold-lifted"));
        verifyNoInteractions(audit);
    }

    @Test
    void ac3_3_aHoldOfAnotherAccountIsNotFound() {
        when(holds.find(USER_ID, HOLD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lift(USER_ID, HOLD_ID, "Court case closed", OFFICER_ID))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(problem.getType()).isEqualTo("legal-hold-not-found");
            });
    }

    @Test
    void ac3_4_theOverviewListsTheHoldsWithoutAnErasureDate() {
        when(holds.of(USER_ID)).thenReturn(List.of(active()));

        assertThat(service.overview(USER_ID)).isEqualTo(new LegalHoldOverview(List.of(active()), null));
    }

    @Test
    void ac3_4_anUnknownAccountHasNoOverview() {
        when(holds.of(USER_ID)).thenReturn(List.of());
        when(holds.accountExists(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.overview(USER_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void ac3_4_anAccountWithoutHoldsHasAnEmptyOverview() {
        when(holds.of(USER_ID)).thenReturn(List.of());
        when(holds.accountExists(USER_ID)).thenReturn(true);

        assertThat(service.overview(USER_ID).holds()).isEmpty();
    }

    private static LegalHold active() {
        return new LegalHold(HOLD_ID, REASON, NOW, OFFICER, null, null, null);
    }
}
