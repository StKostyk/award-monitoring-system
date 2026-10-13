package ua.edu.chnu.awards.authz;

/**
 * Names of the claims that access and id tokens carry.
 */
@SuppressWarnings("PMD.DataClass")
public final class TokenClaimConstants {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_ROLE_SCOPES = "role_scopes";
    public static final String CLAIM_HELD_SCOPES = "held_scopes";
    public static final String CLAIM_DELEGATIONS = "delegations";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_ORG_ID = "org_id";
    public static final String CLAIM_ORG_TYPE = "org_type";
    public static final String CLAIM_TOKEN_USE = "token_use";
    /**
     * Issue instant of an access token in epoch milliseconds, as a string like the other scalar claims because
     * the stored authorization accepts no other scalar type; {@code iat} has second precision only.
     */
    public static final String CLAIM_ISSUED_AT_MILLIS = "iat_ms";
    public static final String TOKEN_USE_ACCESS = "access";

    private TokenClaimConstants() {
    }
}
