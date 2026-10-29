-- V050__hash_authorization_tokens.sql
-- Description: Token values of issued authorizations are kept as SHA-256 hashes (story 9.1.1). Rows written before
--              this version are converted in place, so signed-in users keep their sessions; values that already
--              carry the sha256: prefix are left as they are.

UPDATE oauth2_authorization
SET authorization_code_value = 'sha256:' || encode(sha256(convert_to(authorization_code_value, 'UTF8')), 'hex')
WHERE authorization_code_value IS NOT NULL AND authorization_code_value NOT LIKE 'sha256:%';

UPDATE oauth2_authorization
SET access_token_value = 'sha256:' || encode(sha256(convert_to(access_token_value, 'UTF8')), 'hex')
WHERE access_token_value IS NOT NULL AND access_token_value NOT LIKE 'sha256:%';

UPDATE oauth2_authorization
SET oidc_id_token_value = 'sha256:' || encode(sha256(convert_to(oidc_id_token_value, 'UTF8')), 'hex')
WHERE oidc_id_token_value IS NOT NULL AND oidc_id_token_value NOT LIKE 'sha256:%';

UPDATE oauth2_authorization
SET refresh_token_value = 'sha256:' || encode(sha256(convert_to(refresh_token_value, 'UTF8')), 'hex')
WHERE refresh_token_value IS NOT NULL AND refresh_token_value NOT LIKE 'sha256:%';

UPDATE oauth2_authorization
SET user_code_value = 'sha256:' || encode(sha256(convert_to(user_code_value, 'UTF8')), 'hex')
WHERE user_code_value IS NOT NULL AND user_code_value NOT LIKE 'sha256:%';

UPDATE oauth2_authorization
SET device_code_value = 'sha256:' || encode(sha256(convert_to(device_code_value, 'UTF8')), 'hex')
WHERE device_code_value IS NOT NULL AND device_code_value NOT LIKE 'sha256:%';

CREATE INDEX idx_oauth2_authorization_code_value ON oauth2_authorization (authorization_code_value);
CREATE INDEX idx_oauth2_authorization_access_value ON oauth2_authorization (access_token_value);
CREATE INDEX idx_oauth2_authorization_id_token_value ON oauth2_authorization (oidc_id_token_value);
CREATE INDEX idx_oauth2_authorization_refresh_value ON oauth2_authorization (refresh_token_value);

COMMENT ON TABLE oauth2_authorization IS 'Issued authorizations: codes, access, refresh and id tokens; token values '
    'are stored as sha256: followed by the hex SHA-256 of the issued value';
