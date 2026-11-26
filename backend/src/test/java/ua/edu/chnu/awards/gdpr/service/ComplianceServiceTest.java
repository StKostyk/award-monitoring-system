package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestEntry;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestStatus;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionPolicyEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionRunEntry;
import ua.edu.chnu.awards.gdpr.repository.ComplianceQueries;

class ComplianceServiceTest {

    private final ComplianceQueries queries = mock(ComplianceQueries.class);
    private final List<String> calls = new ArrayList<>();

    @Test
    void ac2_1_policiesAreListedInRunOrderWithTheirActionCode() {
        ComplianceService service = new ComplianceService(List.of(
            new FakeRetentionPolicy("later", 20, context -> null, calls),
            new FakeRetentionPolicy("earlier", 10, context -> null, calls)), queries);

        assertThat(service.policies()).containsExactly(
            new RetentionPolicyEntry("earlier", "P1D", "delete", 10),
            new RetentionPolicyEntry("later", "P1D", "delete", 20));
        assertThat(calls).isEmpty();
    }

    @Test
    void ac2_2_ac2_3_ac2_4_theListsComeFromTheQueries() {
        PageResponse<RetentionRunEntry> runs = page();
        PageResponse<ErasureRequestEntry> requests = page();
        PageResponse<LegalHoldEntry> holds = page();
        when(queries.runs(1, 5)).thenReturn(runs);
        when(queries.erasureRequests(ErasureRequestStatus.PENDING, 0, 20)).thenReturn(requests);
        when(queries.legalHolds(true, 2, 10)).thenReturn(holds);
        ComplianceService service = new ComplianceService(List.of(), queries);

        assertThat(service.runs(1, 5)).isSameAs(runs);
        assertThat(service.erasureRequests(ErasureRequestStatus.PENDING, 0, 20)).isSameAs(requests);
        assertThat(service.legalHolds(true, 2, 10)).isSameAs(holds);
    }

    private static <T> PageResponse<T> page() {
        return new PageResponse<>(List.of(), 0, 0, 20, 0, true, true);
    }
}
