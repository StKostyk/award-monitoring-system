package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.web.session.SessionInformationExpiredEvent;

class RetryRequestSessionExpiredStrategyTest {

    private final RetryRequestSessionExpiredStrategy strategy = new RetryRequestSessionExpiredStrategy();

    @Test
    void ac32_redirectsToTheSameUrlWithItsQuery() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("client_id=award-web&state=abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        strategy.onExpiredSessionDetected(new SessionInformationExpiredEvent(
            new SessionInformation("p", "s1", new java.util.Date()), request, response));

        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorize?client_id=award-web&state=abc");
    }

    @Test
    void ac32_redirectsWithoutAQueryWhenThereIsNone() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        strategy.onExpiredSessionDetected(new SessionInformationExpiredEvent(
            new SessionInformation("p", "s1", new java.util.Date()), request, response));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
    }
}
