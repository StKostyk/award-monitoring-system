package ua.edu.chnu.awards.auth.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.erasure.ErasureOrderConstants;

import lombok.RequiredArgsConstructor;

/**
 * Erasure of what the person signs in with: one-time tokens (including address changes), known devices and
 * authorizations; login sessions expire and access tokens stop working once the erasure commits.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationErasure implements ErasureContributor {

    private final JdbcTemplate jdbc;
    private final AuthorizationRevoker revoker;

    @Override
    public int order() {
        return ErasureOrderConstants.AUTHENTICATION;
    }

    @Override
    public void erase(ErasureContext context) {
        long userId = context.userId();
        context.count("tokensDeleted", jdbc.update("delete from one_time_tokens where user_id = ?", userId));
        context.count("devicesDeleted", jdbc.update("delete from user_devices where user_id = ?", userId));
        context.count("authorizationsDeleted", revoker.revokeAll(userId, context.emailAddress()));
    }
}
