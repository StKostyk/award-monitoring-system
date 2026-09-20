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
 * Recognises a public client presenting only its {@code client_id} on a refresh or revocation request.
 * The library handles the PKCE code exchange itself; this covers the two other calls a browser client makes.
 */
public final class PublicClientRefreshAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod()) || request.getHeader("Authorization") != null) {
            return null;
        }
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId)
            || StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))
            || StringUtils.hasText(request.getParameter(PkceParameterNames.CODE_VERIFIER))) {
            return null;
        }
        boolean refresh = AuthorizationGrantType.REFRESH_TOKEN.getValue()
            .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
        boolean revocation = StringUtils.hasText(request.getParameter(OAuth2ParameterNames.TOKEN));
        if (!refresh && !revocation) {
            return null;
        }
        Map<String, Object> additional = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!OAuth2ParameterNames.CLIENT_ID.equals(key)) {
                additional.put(key, values.length == 1 ? values[0] : values);
            }
        });
        return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, additional);
    }
}
