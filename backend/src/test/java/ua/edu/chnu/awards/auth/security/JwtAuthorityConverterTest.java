package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthorityConverterTest {

    private final JwtAuthorityConverter converter = new JwtAuthorityConverter();

    @Test
    void ac14_rolesBecomeRoleAuthoritiesAndPermissionsStayPlain() {
        Jwt jwt = jwt(Map.of(
            "sub", "7",
            "email", "dean.fmi@chnu.edu.ua",
            "roles", List.of("DEAN"),
            "permissions", List.of("award:approve:level2")));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getName()).isEqualTo("dean.fmi@chnu.edu.ua");
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_DEAN", "award:approve:level2");
    }

    @Test
    void tokenWithoutClaimsHasNoAuthorities() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("sub", "7")));

        assertThat(token.getAuthorities()).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }
}
