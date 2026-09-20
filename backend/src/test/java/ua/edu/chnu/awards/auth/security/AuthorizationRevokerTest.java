package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class AuthorizationRevokerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SessionRegistryImpl sessions = new SessionRegistryImpl();
    private final AuthorizationRevoker revoker = new AuthorizationRevoker(jdbc, sessions);

    @Test
    void ac32_deletesEveryAuthorizationAndExpiresEveryLoginSessionOfThePrincipal() {
        UserDetails olena = User.withUsername("olena@chnu.edu.ua").password("x").authorities("ROLE_X").build();
        UserDetails other = User.withUsername("other@chnu.edu.ua").password("x").authorities("ROLE_X").build();
        sessions.registerNewSession("s1", olena);
        sessions.registerNewSession("s2", olena);
        sessions.registerNewSession("s3", other);
        when(jdbc.update("delete from oauth2_authorization where principal_name = ?", "olena@chnu.edu.ua"))
            .thenReturn(3);

        assertThat(revoker.revokeAll("olena@chnu.edu.ua")).isEqualTo(3);

        assertThat(sessions.getSessionInformation("s1").isExpired()).isTrue();
        assertThat(sessions.getSessionInformation("s2").isExpired()).isTrue();
        assertThat(sessions.getSessionInformation("s3").isExpired()).isFalse();
    }
}
