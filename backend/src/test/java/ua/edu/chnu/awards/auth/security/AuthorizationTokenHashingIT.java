package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.testcontainers.containers.PostgreSQLContainer;

import ua.edu.chnu.awards.common.HashUtils;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class AuthorizationTokenHashingIT extends AbstractIntegrationTest {

    private static final String PRINCIPAL = "it.hashing@chnu.edu.ua";
    private static final String VALUE_COLUMNS = "authorization_code_value, access_token_value, oidc_id_token_value, "
        + "refresh_token_value, user_code_value, device_code_value";
    private static final String MIGRATION_CLIENT = "migration-client";
    private static final int PLANNER_ROWS = 2000;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private RegisteredClientRepository clients;

    @Autowired
    private AuthorizationRevoker revoker;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PostgreSQLContainer<?> postgres;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from oauth2_authorization where principal_name = ?", PRINCIPAL);
    }

    @Test
    void ac1_1_savedRowHoldsOnlyPrefixedHashes() {
        String id = save("code", "access", "id", "refresh");

        Map<String, Object> row = jdbc.queryForMap(
            "select " + VALUE_COLUMNS + " from oauth2_authorization where id = ?", id);

        assertThat(row.get("authorization_code_value")).isEqualTo("sha256:" + HashUtils.sha256Hex("code-" + id));
        assertThat(row.get("access_token_value")).isEqualTo("sha256:" + HashUtils.sha256Hex("access-" + id));
        assertThat(row.get("oidc_id_token_value")).isEqualTo("sha256:" + HashUtils.sha256Hex("id-" + id));
        assertThat(row.get("refresh_token_value")).isEqualTo("sha256:" + HashUtils.sha256Hex("refresh-" + id));
        assertThat(row.get("user_code_value")).isNull();
        assertThat(row.values()).noneMatch(value -> value != null && value.toString().endsWith(id));
    }

    @Test
    void ac1_2_savingAReadAuthorizationAgainDoesNotHashTwice() {
        String id = save("code", "access", "id", "refresh");
        OAuth2Authorization read = authorizationService.findById(id);
        OAuth2Authorization invalidated = OAuth2Authorization.from(read)
            .invalidate(read.getToken(OAuth2AuthorizationCode.class).getToken()).build();

        authorizationService.save(invalidated);

        Map<String, Object> row = jdbc.queryForMap("select authorization_code_value, authorization_code_metadata, "
            + "refresh_token_value from oauth2_authorization where id = ?", id);
        assertThat(row.get("authorization_code_value")).isEqualTo("sha256:" + HashUtils.sha256Hex("code-" + id));
        assertThat(row.get("refresh_token_value")).isEqualTo("sha256:" + HashUtils.sha256Hex("refresh-" + id));
        assertThat(row.get("authorization_code_metadata").toString()).contains("invalidated\":true");
    }

    @Test
    void ac1_3_everyTokenTypeIsFoundByItsIssuedValue() {
        String id = save("code", "access", "id", "refresh");

        assertThat(authorizationService.findByToken("code-" + id, new OAuth2TokenType("code")).getId()).isEqualTo(id);
        assertThat(authorizationService.findByToken("access-" + id, OAuth2TokenType.ACCESS_TOKEN).getId())
            .isEqualTo(id);
        assertThat(authorizationService.findByToken("id-" + id, new OAuth2TokenType("id_token")).getId())
            .isEqualTo(id);
        assertThat(authorizationService.findByToken("refresh-" + id, OAuth2TokenType.REFRESH_TOKEN).getId())
            .isEqualTo(id);
        assertThat(authorizationService.findByToken("refresh-" + id, null).getId()).isEqualTo(id);
        assertThat(authorizationService.findByToken("refresh-" + id, null).getRefreshToken().isActive()).isTrue();
    }

    @Test
    void ac1_3_theStoredHashFindsNothing() {
        String id = save("code", "access", "id", "refresh");
        String stored = jdbc.queryForObject("select refresh_token_value from oauth2_authorization where id = ?",
            String.class, id);

        assertThat(authorizationService.findByToken(stored, OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(authorizationService.findByToken(stored, null)).isNull();
    }

    @Test
    void ac1_5_signOutEverywhereRemovesHashedRows() {
        save("code", "access", "id", "refresh");
        save(null, "access", null, "refresh");

        assertThat(revoker.revokeAll(-1L, PRINCIPAL)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization where principal_name = ?",
            Integer.class, PRINCIPAL)).isZero();
    }

    @Test
    void ac1_6_v050ConvertsClearRowsInPlaceLikeTheApplicationAndIsIdempotent() {
        withDatabaseAt049(old -> {
            insertRow(old, "auth-1", "refresh-clear", "access-clear");
            insertRow(old, "auth-2", HashingAuthorizationService.hash("already"), null);
            migrate(old);

            Map<String, Object> converted = old.queryForMap("select " + VALUE_COLUMNS
                + " from oauth2_authorization where id = 'auth-1'");
            assertThat(converted.get("refresh_token_value"))
                .isEqualTo(HashingAuthorizationService.hash("refresh-clear"));
            assertThat(converted.get("access_token_value")).isEqualTo(HashingAuthorizationService.hash("access-clear"));
            assertThat(converted.get("authorization_code_value")).isNull();
            assertThat(old.queryForObject("select refresh_token_value from oauth2_authorization where id = 'auth-2'",
                String.class)).isEqualTo(HashingAuthorizationService.hash("already"));
            assertThat(old.queryForObject("select obj_description('oauth2_authorization'::regclass)", String.class))
                .contains("sha256:");
        });
    }

    @Test
    void ac1_7_v050IndexesTheLookupColumnsAndTheRefreshLookupUsesItsIndex() {
        withDatabaseAt049(old -> {
            migrate(old);
            List<String> indexes = old.queryForList("select indexdef from pg_indexes "
                + "where tablename = 'oauth2_authorization'", String.class);
            assertThat(indexes).anyMatch(def -> def.endsWith("(authorization_code_value)"))
                .anyMatch(def -> def.endsWith("(access_token_value)"))
                .anyMatch(def -> def.endsWith("(oidc_id_token_value)"))
                .anyMatch(def -> def.endsWith("(refresh_token_value)"));

            old.update("insert into oauth2_authorization (id, registered_client_id, principal_name, "
                + "authorization_grant_type, refresh_token_value) select 'bulk-' || n, ?, ?, 'authorization_code', "
                + "'sha256:' || encode(sha256(convert_to('bulk-' || n, 'UTF8')), 'hex') "
                + "from generate_series(1, ?) n", MIGRATION_CLIENT, PRINCIPAL, PLANNER_ROWS);
            old.execute("analyze oauth2_authorization");
            String plan = String.join("\n", old.queryForList("explain select id from oauth2_authorization "
                + "where refresh_token_value = '" + HashingAuthorizationService.hash("bulk-7") + "'", String.class));
            assertThat(plan).contains("idx_oauth2_authorization_refresh_value");
        });
    }

    private String save(String code, String access, String idToken, String refresh) {
        String id = UUID.randomUUID().toString();
        Instant issued = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expires = issued.plusSeconds(900);
        RegisteredClient client = clients.findById(clientId(jdbc));
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client).id(id)
            .principalName(PRINCIPAL).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid"));
        if (code != null) {
            builder.token(new OAuth2AuthorizationCode(code + "-" + id, issued, expires));
        }
        if (access != null) {
            builder.token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, access + "-" + id, issued, expires,
                Set.of("openid")), metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims()));
        }
        if (idToken != null) {
            builder.token(new OidcIdToken(idToken + "-" + id, issued, expires, claims()),
                metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims()));
        }
        if (refresh != null) {
            builder.refreshToken(new OAuth2RefreshToken(refresh + "-" + id, issued, expires));
        }
        authorizationService.save(builder.build());
        return id;
    }

    private static Map<String, Object> claims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", PRINCIPAL);
        return claims;
    }

    private static String clientId(JdbcTemplate template) {
        return template.queryForObject("select id from oauth2_registered_client order by id limit 1", String.class);
    }

    private static void insertRow(JdbcTemplate template, String id, String refresh, String access) {
        template.update("insert into oauth2_authorization (id, registered_client_id, principal_name, "
            + "authorization_grant_type, refresh_token_value, access_token_value) values (?, ?, ?, 'refresh_token', "
            + "?, ?)", id, MIGRATION_CLIENT, PRINCIPAL, refresh, access);
    }

    private static void migrate(JdbcTemplate template) {
        Flyway.configure().dataSource(template.getDataSource()).locations("classpath:db/migration").load().migrate();
    }

    private void withDatabaseAt049(Consumer<JdbcTemplate> check) {
        String database = "v050_hash_" + System.nanoTime();
        jdbc.execute("create database " + database);
        try {
            String url = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
            DriverManagerDataSource target = new DriverManagerDataSource(url, postgres.getUsername(),
                postgres.getPassword());
            Flyway.configure().dataSource(target).locations("classpath:db/migration").target("049").load().migrate();
            JdbcTemplate old = new JdbcTemplate(target);
            old.update("insert into oauth2_registered_client (id, client_id, client_name, "
                + "client_authentication_methods, authorization_grant_types, scopes, client_settings, token_settings) "
                + "values (?, ?, 'migration', 'none', 'refresh_token', 'openid', '{}', '{}')", MIGRATION_CLIENT,
                MIGRATION_CLIENT);
            check.accept(old);
        } finally {
            jdbc.execute("drop database " + database + " with (force)");
        }
    }
}
