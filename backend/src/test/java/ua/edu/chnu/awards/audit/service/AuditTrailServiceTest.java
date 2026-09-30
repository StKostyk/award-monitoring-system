package ua.edu.chnu.awards.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.audit.dto.AuditTrailExport;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.repository.AuditLogRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuditTrailServiceTest {

    private static final long AWARD = 5L;
    private static final long AUDITOR = 2L;

    @Mock
    private AuditLogRepository logs;

    @Mock
    private UserRepository users;

    @Mock
    private AuditService audit;

    private AuditTrailService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T22:30:00Z"), ZoneId.of("Europe/Kyiv"));
        service = new AuditTrailService(logs, users, audit, new AuditTrailCsv(new ObjectMapper()), clock);
    }

    @Test
    void ac2_6_theExportIsNamedAfterTheAwardAndTheKyivDayAndIsAudited() {
        when(logs.findNewestAboutAward(AWARD, AuditTrailService.MAX_EXPORT_ROWS + 1)).thenReturn(rows(3));

        AuditTrailExport export = service.exportAward(AWARD, AUDITOR).orElseThrow();

        assertThat(export.fileName()).isEqualTo("award-5-audit-2026-10-01.csv");
        assertThat(export.truncated()).isFalse();
        assertThat(export.content().split("\r\n")).hasSize(4);
        verify(audit).record(AuditAction.AUDIT_EXPORT, AuditEntityConstants.AWARDS, AUDITOR, AWARD,
            Map.of("rows", 3, "truncated", false));
    }

    @Test
    void ac2_7_moreRowsThanTheLimitKeepTheNewestAndAreMarkedAsTruncated() {
        when(logs.findNewestAboutAward(AWARD, AuditTrailService.MAX_EXPORT_ROWS + 1))
            .thenReturn(rows(AuditTrailService.MAX_EXPORT_ROWS + 1));

        AuditTrailExport export = service.exportAward(AWARD, AUDITOR).orElseThrow();

        assertThat(export.truncated()).isTrue();
        String[] lines = export.content().split("\r\n");
        assertThat(lines).hasSize(AuditTrailService.MAX_EXPORT_ROWS + 1);
        assertThat(lines[1]).contains(";awards;" + AWARD + ";");
        verify(audit).record(AuditAction.AUDIT_EXPORT, AuditEntityConstants.AWARDS, AUDITOR, AWARD,
            Map.of("rows", AuditTrailService.MAX_EXPORT_ROWS, "truncated", true));
    }

    @Test
    void ac2_6_anAwardNobodyLoggedIsNotFoundAndNotAudited() {
        when(logs.findNewestAboutAward(AWARD, AuditTrailService.MAX_EXPORT_ROWS + 1)).thenReturn(List.of());

        assertThat(service.exportAward(AWARD, AUDITOR)).isEmpty();
        verify(audit, never()).record(any(), any(), anyLong(), anyLong(), any());
    }

    private static List<AuditLog> rows(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> AuditLog.builder().id(id)
            .createdAt(Instant.parse("2026-09-30T08:00:00Z").minusSeconds(id)).actionType("UPDATE")
            .entityType(AuditEntityConstants.AWARDS).entityId(AWARD).build()).toList();
    }
}
