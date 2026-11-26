package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

class PublicClientRefreshAuthenticationTest {

    private final PublicClientRefreshAuthenticationConverter converter =
        new PublicClientRefreshAuthenticationConverter("/oauth2/token", "/oauth2/revoke");
    private final RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
    private final PublicClientRefreshAuthenticationProvider provider =
        new PublicClientRefreshAuthenticationProvider(repository);

    @Test
    void ac15_refreshRequestWithClientIdOnlyIsConvertedToAPublicClientToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter("grant_type", "refresh_token");
        request.setParameter("refresh_token", "r");
        request.setParameter("client_id", "award-web");

        Authentication result = converter.convert(request);

        assertThat(result).isInstanceOf(OAuth2ClientAuthenticationToken.class);
        OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) result;
        assertThat(token.getPrincipal()).isEqualTo("award-web");
        assertThat(token.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        assertThat(token.getAdditionalParameters()).containsEntry("refresh_token", "r").doesNotContainKey("client_id")
            .containsEntry(PublicClientRefreshAuthenticationConverter.MARKER, Boolean.TRUE);
    }

    @Test
    void ac18_revocationRequestIsConvertedToo() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/revoke");
        request.setParameter("token", "r");
        request.setParameter("client_id", "award-web");

        assertThat(converter.convert(request)).isNotNull();
    }

    @Test
    void otherRequestsAreLeftToTheLibrary() {
        MockHttpServletRequest pkce = new MockHttpServletRequest("POST", "/oauth2/token");
        pkce.setParameter("grant_type", "authorization_code");
        pkce.setParameter("client_id", "award-web");
        pkce.setParameter("code_verifier", "v");
        assertThat(converter.convert(pkce)).isNull();

        MockHttpServletRequest secret = new MockHttpServletRequest("POST", "/oauth2/token");
        secret.setParameter("grant_type", "refresh_token");
        secret.setParameter("client_id", "c");
        secret.setParameter("client_secret", "s");
        assertThat(converter.convert(secret)).isNull();

        MockHttpServletRequest basic = new MockHttpServletRequest("POST", "/oauth2/token");
        basic.setParameter("grant_type", "refresh_token");
        basic.setParameter("client_id", "c");
        basic.addHeader("Authorization", "Basic abc");
        assertThat(converter.convert(basic)).isNull();

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/oauth2/token");
        get.setParameter("grant_type", "refresh_token");
        get.setParameter("client_id", "c");
        assertThat(converter.convert(get)).isNull();

        MockHttpServletRequest code = new MockHttpServletRequest("POST", "/oauth2/token");
        code.setParameter("grant_type", "client_credentials");
        code.setParameter("client_id", "c");
        assertThat(converter.convert(code)).isNull();

    }

    @Test
    void ac12_codeExchangeAndIntrospectionAreNeverConverted() {
        MockHttpServletRequest strayToken = new MockHttpServletRequest("POST", "/oauth2/token");
        strayToken.setParameter("grant_type", "authorization_code");
        strayToken.setParameter("code", "abc");
        strayToken.setParameter("client_id", "award-web");
        strayToken.setParameter("token", "x");
        assertThat(converter.convert(strayToken)).isNull();

        MockHttpServletRequest introspect = new MockHttpServletRequest("POST", "/oauth2/introspect");
        introspect.setParameter("token", "x");
        introspect.setParameter("client_id", "award-web");
        assertThat(converter.convert(introspect)).isNull();
    }

    @Test
    void ac15_registeredPublicClientIsAuthenticated() {
        RegisteredClient client = RegisteredClient.withId("award-web").clientId("award-web")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).build();
        when(repository.findByClientId("award-web")).thenReturn(client);

        Authentication result = provider.authenticate(new OAuth2ClientAuthenticationToken("award-web",
            ClientAuthenticationMethod.NONE, null, Map.of(PublicClientRefreshAuthenticationConverter.MARKER, true)));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(((OAuth2ClientAuthenticationToken) result).getRegisteredClient()).isSameAs(client);
        assertThat(provider.supports(OAuth2ClientAuthenticationToken.class)).isTrue();
    }

    @Test
    void unknownOrConfidentialClientIsRejected() {
        when(repository.findByClientId("ghost")).thenReturn(null);
        RegisteredClient confidential = RegisteredClient.withId("c").clientId("c")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).build();
        when(repository.findByClientId("c")).thenReturn(confidential);

        Map<String, Object> marked = Map.of(PublicClientRefreshAuthenticationConverter.MARKER, true);
        assertThatThrownBy(() -> provider.authenticate(
            new OAuth2ClientAuthenticationToken("ghost", ClientAuthenticationMethod.NONE, null, marked)))
            .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> provider.authenticate(
            new OAuth2ClientAuthenticationToken("c", ClientAuthenticationMethod.NONE, null, marked)))
            .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(provider.authenticate(
            new OAuth2ClientAuthenticationToken("c", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "s", null)))
            .isNull();
    }

    @Test
    void ac12_tokensFromTheLibraryConverterAreLeftToTheLibrary() {
        RegisteredClient client = RegisteredClient.withId("award-web").clientId("award-web")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:4200/callback").build();
        when(repository.findByClientId("award-web")).thenReturn(client);

        assertThat(provider.authenticate(new OAuth2ClientAuthenticationToken("award-web",
            ClientAuthenticationMethod.NONE, null, Map.of("code_verifier", "v", "code", "c")))).isNull();
        assertThat(provider.authenticate(
            new OAuth2ClientAuthenticationToken("award-web", ClientAuthenticationMethod.NONE, null, null))).isNull();
    }
}
