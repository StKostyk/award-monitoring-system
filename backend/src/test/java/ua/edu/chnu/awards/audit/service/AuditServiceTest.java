package ua.edu.chnu.awards.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.repository.AuditLogRepository;
import ua.edu.chnu.awards.common.web.ClientRequest;

class AuditServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditService service = new AuditService(repository);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ac43_storesTheEventWithTheClientContextOfTheRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "Firefox");
        UUID correlation = UUID.randomUUID();
        request.setAttribute(ClientRequest.CORRELATION_ATTRIBUTE, correlation);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.record(AuditAction.LOGIN_FAILED, 7L, Map.of("reason", "bad_credentials"));

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        AuditLog row = saved.getValue();
        assertThat(row.getActionType()).isEqualTo("LOGIN_FAILED");
        assertThat(row.getEntityType()).isEqualTo("AUTHENTICATION");
        assertThat(row.getUserId()).isEqualTo(7L);
        assertThat(row.getIpAddress().getHostAddress()).isEqualTo("203.0.113.7");
        assertThat(row.getUserAgent()).isEqualTo("Firefox");
        assertThat(row.getCorrelationId()).isEqualTo(correlation);
        assertThat(row.getDetails()).containsEntry("reason", "bad_credentials");
    }

    @Test
    void ac43_eventsOutsideARequestAreStoredWithoutClientContext() {
        service.record(AuditAction.LOGOUT, null);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getIpAddress()).isNull();
        assertThat(saved.getValue().getUserId()).isNull();
    }
}
