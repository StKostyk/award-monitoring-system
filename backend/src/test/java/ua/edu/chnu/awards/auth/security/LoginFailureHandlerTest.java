package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import ua.edu.chnu.awards.user.entity.AccountStatus;

class LoginFailureHandlerTest {

    private final LoginFailureHandler handler = new LoginFailureHandler();

    @Test
    void ac16_statusRefusalRedirectsWithTheStatusCode() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
            new AccountStatusRefusedException(AccountStatus.SUSPENDED));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=SUSPENDED");
    }

    @Test
    void wrongPasswordRedirectsWithGenericCode() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
            new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=BAD_CREDENTIALS");
    }
}
