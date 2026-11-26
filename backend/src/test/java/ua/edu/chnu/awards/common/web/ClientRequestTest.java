package ua.edu.chnu.awards.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ClientRequestTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ac43_usesTheSocketPeerAndIgnoresClientHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.9");
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        ClientRequest client = ClientRequest.from(request);

        assertThat(client.ip()).isEqualTo("198.51.100.9");
        assertThat(client.address().getHostAddress()).isEqualTo("198.51.100.9");
    }

    @Test
    void ac43_readsAgentAndCorrelationFromTheCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "x".repeat(600));
        request.addHeader("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8");
        UUID id = UUID.randomUUID();
        request.setAttribute(ClientRequest.CORRELATION_ATTRIBUTE, id);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ClientRequest client = ClientRequest.current();

        assertThat(client.userAgent()).hasSize(500);
        assertThat(client.acceptLanguage()).isEqualTo("uk-UA,uk;q=0.9,en;q=0.8");
        assertThat(client.correlationId()).isEqualTo(id);
        assertThat(client.ip()).isEqualTo("127.0.0.1");
    }

    @Test
    void outsideARequestEverythingIsNullAndNamesAreNeverResolved() {
        ClientRequest client = ClientRequest.current();

        assertThat(client.ip()).isNull();
        assertThat(client.address()).isNull();
        assertThat(client.correlationId()).isNull();
        assertThat(new ClientRequest("attacker.example", null, null, null).address()).isNull();
        assertThat(new ClientRequest("::1", null, null, null).address().isLoopbackAddress()).isTrue();
    }
}
