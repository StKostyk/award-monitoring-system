package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.gdpr.repository.ErasureRequests;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;

class ErasureGuardTest {

    private static final long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    private final LegalHolds holds = mock(LegalHolds.class);
    private final ErasureRequests requests = mock(ErasureRequests.class);
    private final ErasureGuard guard = new ErasureGuard(holds, requests);

    @Test
    void anAccountWithoutHoldThatIsNotTheOnlyAdministratorIsErasable() {
        assertThat(guard.blocker(USER_ID, TODAY)).isEmpty();
    }

    @Test
    void anActiveLegalHoldBlocksBeforeTheAdministratorCheck() {
        when(holds.isActive(USER_ID)).thenReturn(true);
        when(requests.isOnlyAdministrator(USER_ID, TODAY)).thenReturn(true);

        assertThat(guard.blocker(USER_ID, TODAY)).contains(ErasureBlocker.LEGAL_HOLD);
        verify(requests, never()).isOnlyAdministrator(USER_ID, TODAY);
    }

    @Test
    void theOnlySystemAdministratorIsBlocked() {
        when(requests.isOnlyAdministrator(USER_ID, TODAY)).thenReturn(true);

        assertThat(guard.blocker(USER_ID, TODAY)).contains(ErasureBlocker.LAST_ADMINISTRATOR);
    }

    @Test
    void onlyALegalHoldNotifiesThePersonOfThePostponement() {
        assertThat(ErasureBlocker.LEGAL_HOLD.notifies()).isTrue();
        assertThat(ErasureBlocker.LAST_ADMINISTRATOR.notifies()).isFalse();
    }

    @Test
    void aBlockerBecomesAConflictWithItsCode() {
        ApiProblemException problem = ErasureBlocker.LAST_ADMINISTRATOR.problem();

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(problem.getType()).isEqualTo("last-administrator");
        assertThat(ErasureBlocker.LEGAL_HOLD.code()).isEqualTo("legal-hold");
    }
}
