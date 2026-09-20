-- V014__create_auth_tables.sql
-- Description: Authorization server persistence, one-time tokens and known devices
-- Author: Stefan Kostyk
-- Date: 2026-09-21

-- ============================================================================
-- SPRING AUTHORIZATION SERVER TABLES
-- ============================================================================
-- Reference schema of Spring Authorization Server 1.5 with the PostgreSQL
-- adjustments it recommends: blob -> text, timestamp -> timestamptz.
-- ============================================================================

CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMP WITH TIME ZONE,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_oauth2_registered_client PRIMARY KEY (id),
    CONSTRAINT uk_oauth2_registered_client_id UNIQUE (client_id)
);

CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMP WITH TIME ZONE,
    authorization_code_expires_at TIMESTAMP WITH TIME ZONE,
    authorization_code_metadata TEXT,
    access_token_value TEXT,
    access_token_issued_at TIMESTAMP WITH TIME ZONE,
    access_token_expires_at TIMESTAMP WITH TIME ZONE,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMP WITH TIME ZONE,
    oidc_id_token_expires_at TIMESTAMP WITH TIME ZONE,
    oidc_id_token_metadata TEXT,
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMP WITH TIME ZONE,
    refresh_token_expires_at TIMESTAMP WITH TIME ZONE,
    refresh_token_metadata TEXT,
    user_code_value TEXT,
    user_code_issued_at TIMESTAMP WITH TIME ZONE,
    user_code_expires_at TIMESTAMP WITH TIME ZONE,
    user_code_metadata TEXT,
    device_code_value TEXT,
    device_code_issued_at TIMESTAMP WITH TIME ZONE,
    device_code_expires_at TIMESTAMP WITH TIME ZONE,
    device_code_metadata TEXT,
    CONSTRAINT pk_oauth2_authorization PRIMARY KEY (id),
    CONSTRAINT fk_oauth2_authorization_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client(id) ON DELETE CASCADE
);

CREATE INDEX idx_oauth2_authorization_principal ON oauth2_authorization(principal_name);
CREATE INDEX idx_oauth2_authorization_refresh_expires ON oauth2_authorization(refresh_token_expires_at);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_oauth2_authorization_consent PRIMARY KEY (registered_client_id, principal_name),
    CONSTRAINT fk_oauth2_authorization_consent_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client(id) ON DELETE CASCADE
);

COMMENT ON TABLE oauth2_registered_client IS 'OAuth2 clients known to the authorization server';
COMMENT ON TABLE oauth2_authorization IS 'Issued authorizations: codes, access, refresh and id tokens (values are hashed by the server)';
COMMENT ON TABLE oauth2_authorization_consent IS 'Scopes a principal consented to per client';

-- ============================================================================
-- ONE-TIME TOKENS
-- ============================================================================
-- Links sent by email: address verification, password reset, security revoke.
-- Only the SHA-256 hash of the token is stored.
-- ============================================================================

CREATE TABLE one_time_tokens (
    id BIGSERIAL,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_one_time_tokens PRIMARY KEY (id),
    CONSTRAINT uk_one_time_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_one_time_tokens_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT ck_one_time_tokens_purpose CHECK (
        purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'SECURITY_REVOKE')
    )
);

CREATE INDEX idx_one_time_tokens_user ON one_time_tokens(user_id);
CREATE INDEX idx_one_time_tokens_expires ON one_time_tokens(expires_at);

COMMENT ON TABLE one_time_tokens IS 'Single-use tokens sent by email; the raw token never touches the database';
COMMENT ON COLUMN one_time_tokens.token_hash IS 'SHA-256 hex digest of the token';
COMMENT ON COLUMN one_time_tokens.purpose IS 'EMAIL_VERIFICATION (24 h), PASSWORD_RESET (1 h), SECURITY_REVOKE (24 h)';
COMMENT ON COLUMN one_time_tokens.used_at IS 'Set on first use; a used token is rejected';

-- ============================================================================
-- USER DEVICES
-- ============================================================================
-- Browsers a user has signed in from; a sign-in from an unknown fingerprint
-- triggers a notification email.
-- ============================================================================

CREATE TABLE user_devices (
    id BIGSERIAL,
    user_id BIGINT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    browser VARCHAR(100),
    operating_system VARCHAR(100),
    last_ip_address VARCHAR(45),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_devices PRIMARY KEY (id),
    CONSTRAINT uk_user_devices_fingerprint UNIQUE (user_id, fingerprint),
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_devices_last_used ON user_devices(user_id, last_used_at DESC);

COMMENT ON TABLE user_devices IS 'Known browsers per user for new-device detection';
COMMENT ON COLUMN user_devices.fingerprint IS 'SHA-256 hex digest of browser family, OS family and accept-language';
COMMENT ON COLUMN user_devices.last_ip_address IS 'GDPR: Technical ID - IPv4 or IPv6 address of the last sign-in';
