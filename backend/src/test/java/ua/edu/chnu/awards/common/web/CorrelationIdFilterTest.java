package ua.edu.chnu.awards.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void ac43_keepsACallerSuppliedUuidAndEchoesItBack() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "123e4567-e89b-12d3-a456-426614174000");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(ClientRequest.CORRELATION_ATTRIBUTE))
            .isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void ac43_replacesAnythingThatIsNotAUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "<script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotEqualTo("<script>");
        assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).isNotNull();
    }
}
