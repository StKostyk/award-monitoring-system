package ua.edu.chnu.awards.auth.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Maps the {@code roles} claim to {@code ROLE_*} authorities and the {@code permissions} claim to plain authorities.
 */
@Component
public class JwtAuthorityConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList(TokenClaimsCustomizer.CLAIM_ROLES);
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
        }
        List<String> permissions = jwt.getClaimAsStringList(TokenClaimsCustomizer.CLAIM_PERMISSIONS);
        if (permissions != null) {
            permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString(TokenClaimsCustomizer.CLAIM_EMAIL));
    }
}
