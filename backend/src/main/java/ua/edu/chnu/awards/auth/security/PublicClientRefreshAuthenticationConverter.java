package ua.edu.chnu.awards.auth.security;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

/**
 * Recognises a public client presenting only its {@code client_id} on a refresh-token request to the token endpoint or
 * on a revocation request. The PKCE code exchange is left to the library, which verifies the code verifier.
 */
public final class PublicClientRefreshAuthenticationConverter implements AuthenticationConverter {

    /** Marker placed in the additional parameters so the matching provider handles only these tokens. */
    static final String MARKER = PublicClientRefreshAuthenticationConverter.class.getName();

    private final String tokenEndpoint;
    private final String revocationEndpoint;

    public PublicClientRefreshAuthenticationConverter(String tokenEndpoint, String revocationEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
        this.revocationEndpoint = revocationEndpoint;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId) || !isPublicClientOnly(request) || !isRefreshOrRevocation(request)) {
            return null;
        }
        Map<String, Object> additional = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!OAuth2ParameterNames.CLIENT_ID.equals(key)) {
                additional.put(key, values.length == 1 ? values[0] : values);
            }
        });
        additional.put(MARKER, Boolean.TRUE);
        return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, additional);
    }

    private static boolean isPublicClientOnly(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
            && request.getHeader("Authorization") == null
            && !StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))
            && !StringUtils.hasText(request.getParameter(PkceParameterNames.CODE_VERIFIER))
            && !StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CODE));
    }

    private boolean isRefreshOrRevocation(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean refresh = path.equals(tokenEndpoint) && AuthorizationGrantType.REFRESH_TOKEN.getValue()
            .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
        return refresh || path.equals(revocationEndpoint);
    }
}
