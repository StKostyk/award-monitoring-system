package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import ua.edu.chnu.awards.common.HashUtils;

class HashingAuthorizationServiceTest {

    private static final Instant ISSUED = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant EXPIRES = ISSUED.plusSeconds(900);
    private static final String STORED_PATTERN = "sha256:[0-9a-f]{64}";

    private final OAuth2AuthorizationService delegate = mock(OAuth2AuthorizationService.class);
    private final HashingAuthorizationService service = new HashingAuthorizationService(delegate);

    @Test
    void ac1_1_everyIssuedValueIsSavedAsItsPrefixedHash() {
        OAuth2Authorization issued = authorization("code-1", "access-1", "id-1", "refresh-1", "user-1", "device-1");

        OAuth2Authorization saved = save(issued);

        assertThat(saved.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue())
            .isEqualTo("sha256:" + HashUtils.sha256Hex("code-1"));
        assertThat(saved.getAccessToken().getToken().getTokenValue()).isEqualTo(hash("access-1"));
        assertThat(saved.getToken(OidcIdToken.class).getToken().getTokenValue()).isEqualTo(hash("id-1"));
        assertThat(saved.getRefreshToken().getToken().getTokenValue()).isEqualTo(hash("refresh-1"));
        assertThat(saved.getToken(OAuth2UserCode.class).getToken().getTokenValue()).isEqualTo(hash("user-1"));
        assertThat(saved.getToken(OAuth2DeviceCode.class).getToken().getTokenValue()).isEqualTo(hash("device-1"));
        assertThat(saved.getAccessToken().getToken().getTokenValue()).matches(STORED_PATTERN);
    }

    @Test
    void ac1_1_tokenAttributesAndMetadataAreKept() {
        OAuth2Authorization issued = authorization("code-1", "access-1", "id-1", "refresh-1", null, null);

        OAuth2Authorization saved = save(issued);

        OAuth2AccessToken access = saved.getAccessToken().getToken();
        assertThat(access.getScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(access.getIssuedAt()).isEqualTo(ISSUED);
        assertThat(access.getExpiresAt()).isEqualTo(EXPIRES);
        assertThat(saved.getAccessToken().getClaims()).containsEntry("sub", "dean@chnu.edu.ua");
        assertThat(saved.getToken(OidcIdToken.class).getToken().getClaims()).containsEntry("sid", "session-1");
        assertThat(saved.getToken(OAuth2AuthorizationCode.class).isInvalidated()).isTrue();
        assertThat(saved.getId()).isEqualTo(issued.getId());
        assertThat(saved.getPrincipalName()).isEqualTo("dean@chnu.edu.ua");
        assertThat(saved.getToken(OAuth2UserCode.class)).isNull();
    }

    @Test
    void ac1_1_callersAuthorizationKeepsTheIssuedValues() {
        OAuth2Authorization issued = authorization("code-1", "access-1", "id-1", "refresh-1", null, null);

        save(issued);

        assertThat(issued.getRefreshToken().getToken().getTokenValue()).isEqualTo("refresh-1");
        assertThat(issued.getAccessToken().getToken().getTokenValue()).isEqualTo("access-1");
    }

    @Test
    void ac1_2_valuesAlreadyHashedAreSavedUnchanged() {
        OAuth2Authorization read = authorization(hash("code-1"), hash("access-1"), hash("id-1"), "refresh-2", null,
            null);

        OAuth2Authorization saved = save(read);

        assertThat(saved.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue()).isEqualTo(hash("code-1"));
        assertThat(saved.getAccessToken().getToken().getTokenValue()).isEqualTo(hash("access-1"));
        assertThat(saved.getToken(OidcIdToken.class).getToken().getTokenValue()).isEqualTo(hash("id-1"));
        assertThat(saved.getRefreshToken().getToken().getTokenValue()).isEqualTo(hash("refresh-2"));
    }

    @Test
    void ac1_3_lookupHashesThePresentedValue() {
        OAuth2Authorization found = authorization(hash("code-1"), hash("access-1"), null, hash("refresh-1"), null,
            null);
        when(delegate.findByToken(hash("refresh-1"), OAuth2TokenType.REFRESH_TOKEN)).thenReturn(found);

        OAuth2Authorization result = service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN);

        assertThat(result.getId()).isEqualTo("auth-1");
        assertThat(result.getToken("refresh-1")).isNotNull();
        assertThat(result.getRefreshToken().getToken().getTokenValue()).isEqualTo("refresh-1");
        assertThat(result.getAccessToken().getToken().getTokenValue()).isEqualTo(hash("access-1"));
        assertThat(result.getToken(OAuth2AuthorizationCode.class).isInvalidated()).isTrue();
    }

    @Test
    void ac1_2_savingAFoundAuthorizationStoresTheSameHashes() {
        OAuth2Authorization found = authorization(hash("code-1"), hash("access-1"), hash("id-1"), hash("refresh-1"),
            null, null);
        when(delegate.findByToken(hash("refresh-1"), null)).thenReturn(found);

        OAuth2Authorization saved = save(service.findByToken("refresh-1", null));

        assertThat(saved.getRefreshToken().getToken().getTokenValue()).isEqualTo(hash("refresh-1"));
        assertThat(saved.getToken(OidcIdToken.class).getToken().getTokenValue()).isEqualTo(hash("id-1"));
    }

    @Test
    void ac1_3_lookupWithoutTypeHashesThePresentedValue() {
        OAuth2Authorization found = authorization(null, hash("access-1"), null, null, null, null);
        when(delegate.findByToken(hash("access-1"), null)).thenReturn(found);

        assertThat(service.findByToken("access-1", null).getToken("access-1")).isNotNull();
    }

    @Test
    void ac1_3_presentedHashIsHashedAgainAndFindsNothing() {
        OAuth2Authorization found = authorization(null, null, null, hash("refresh-1"), null, null);
        when(delegate.findByToken(hash("refresh-1"), OAuth2TokenType.REFRESH_TOKEN)).thenReturn(found);

        assertThat(service.findByToken(hash("refresh-1"), OAuth2TokenType.REFRESH_TOKEN)).isNull();
        verify(delegate).findByToken(hash(hash("refresh-1")), OAuth2TokenType.REFRESH_TOKEN);
    }

    @Test
    void stateIsLookedUpAsStored() {
        OAuth2TokenType state = new OAuth2TokenType("state");

        service.findByToken("state-1", state);

        verify(delegate).findByToken("state-1", state);
    }

    @Test
    void findByIdAndRemovePassThrough() {
        OAuth2Authorization stored = authorization(null, null, null, hash("refresh-1"), null, null);
        when(delegate.findById("auth-1")).thenReturn(stored);

        assertThat(service.findById("auth-1")).isSameAs(stored);
        service.remove(stored);

        verify(delegate).remove(stored);
    }

    private OAuth2Authorization save(OAuth2Authorization authorization) {
        service.save(authorization);
        ArgumentCaptor<OAuth2Authorization> saved = ArgumentCaptor.forClass(OAuth2Authorization.class);
        verify(delegate).save(saved.capture());
        return saved.getValue();
    }

    private static String hash(String value) {
        return HashingAuthorizationService.hash(value);
    }

    private static OAuth2Authorization authorization(String code, String access, String id, String refresh,
                                                     String user, String device) {
        RegisteredClient client = RegisteredClient.withId("client-1").clientId("award-spa")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/cb")
            .build();
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client).id("auth-1")
            .principalName("dean@chnu.edu.ua").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"));
        if (code != null) {
            builder.token(new OAuth2AuthorizationCode(code, ISSUED, EXPIRES),
                metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));
        }
        if (access != null) {
            builder.token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, access, ISSUED, EXPIRES,
                Set.of("openid", "profile")), metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                Map.of("sub", "dean@chnu.edu.ua")));
        }
        if (id != null) {
            builder.token(new OidcIdToken(id, ISSUED, EXPIRES, Map.of("sub", "dean@chnu.edu.ua", "sid", "session-1")));
        }
        if (refresh != null) {
            builder.refreshToken(new OAuth2RefreshToken(refresh, ISSUED, EXPIRES));
        }
        if (user != null) {
            builder.token(new OAuth2UserCode(user, ISSUED, EXPIRES));
        }
        if (device != null) {
            builder.token(new OAuth2DeviceCode(device, ISSUED, EXPIRES));
        }
        return builder.build();
    }
}
