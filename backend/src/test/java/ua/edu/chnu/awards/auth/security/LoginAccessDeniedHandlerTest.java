package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class LoginAccessDeniedHandlerTest {

    private final LoginAccessDeniedHandler handler = new LoginAccessDeniedHandler();

    @Test
    void ac62_staleCsrfTokenOnTheLoginFormReturnsToTheFormWithAnExplanation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new MissingCsrfTokenException("x"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=EXPIRED");
    }

    @Test
    void ac62_invalidCsrfTokenIsTreatedTheSameWay() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsrfToken token = new DefaultCsrfToken("X-CSRF", "_csrf", "t");

        handler.handle(request, response, new InvalidCsrfTokenException(token, "x"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=EXPIRED");
    }

    @Test
    void ac62_otherRefusalsAnswerForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getRedirectedUrl()).isNull();
    }
}
