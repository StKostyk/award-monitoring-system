package ua.edu.chnu.awards.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API description shown in Swagger UI, with the authorization-server login flow wired to the Authorize button.
 */
@Configuration
public class OpenApiConfig {

    static final String OAUTH_SCHEME = "oauth2";
    static final String BEARER_SCHEME = "bearer";

    @Bean
    OpenAPI awardsOpenApi(AuthProperties properties) {
        String issuer = properties.issuer();
        SecurityScheme oauth = new SecurityScheme()
            .type(SecurityScheme.Type.OAUTH2)
            .description("Sign in through the authorization server (authorization code with PKCE)")
            .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                .authorizationUrl(issuer + "/oauth2/authorize")
                .tokenUrl(issuer + "/oauth2/token")
                .scopes(new Scopes().addString("openid", "identity").addString("profile", "profile"))));
        SecurityScheme bearer = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Paste an access token obtained elsewhere");
        return new OpenAPI()
            .info(new Info().title("Award Monitoring & Tracking System API").version("1.0.0"))
            .components(new Components()
                .addSecuritySchemes(OAUTH_SCHEME, oauth)
                .addSecuritySchemes(BEARER_SCHEME, bearer))
            .addSecurityItem(new SecurityRequirement().addList(OAUTH_SCHEME))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
