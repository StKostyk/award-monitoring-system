package ua.edu.chnu.awards.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import ua.edu.chnu.awards.config.AuthProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Completes a logout the OpenID Connect endpoint refused although the browser's own user asked for it. The
 * library refuses an {@code id_token_hint} whose authorization was deleted by a sign-out everywhere, and one whose
 * {@code sid} names another login session of the same user (it takes the user's most recent session when the
 * token is issued). When the refusal is about the hint and the browser has no login session left, the login page
 * is shown. When the hint is an id token signed by this server for the web client and issued to the user of the
 * requesting session, that session is ended and the login page shown. Every other refusal (no hint, a hint of
 * somebody else, an unknown client or redirect address) keeps the library's 400 answer, so a forged link cannot
 * sign anybody out.
 */
@Component
@Slf4j
public class RefusedLogoutHandler implements AuthenticationFailureHandler {

    private final JwtDecoder idTokens;
    private final String clientId;
    private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

    public RefusedLogoutHandler(JWKSource<SecurityContext> jwkSource, AuthProperties properties) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        processor.setJWTClaimsSetVerifier((claims, context) -> { });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new JwtIssuerValidator(properties.issuer()));
        this.idTokens = decoder;
        this.clientId = properties.client().id();
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (hintRefused(exception) && !signedIn(current)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        if (hintRefused(exception) && ownIdToken(request.getParameter("id_token_hint"), current.getName())) {
            log.debug("Logout refused for the id token of the session user, ending the session: {}",
                exception.getMessage());
            sessionLogout.logout(request, response, current);
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        String error = exception instanceof OAuth2AuthenticationException oauth
            ? oauth.getError().toString() : exception.getMessage();
        response.sendError(HttpStatus.BAD_REQUEST.value(), error);
    }

    private static boolean hintRefused(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauth
            && OAuth2ErrorCodes.INVALID_TOKEN.equals(oauth.getError().getErrorCode());
    }

    private static boolean signedIn(Authentication current) {
        return current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken);
    }

    private boolean ownIdToken(String hint, String user) {
        if (hint == null || hint.isBlank()) {
            return false;
        }
        try {
            Jwt token = idTokens.decode(hint);
            return token.getAudience() != null && token.getAudience().contains(clientId)
                && !TokenClaimsCustomizer.TOKEN_USE_ACCESS.equals(
                    token.getClaimAsString(TokenClaimsCustomizer.CLAIM_TOKEN_USE))
                && user.equalsIgnoreCase(token.getClaimAsString(TokenClaimsCustomizer.CLAIM_EMAIL));
        } catch (JwtException e) {
            return false;
        }
    }
}
