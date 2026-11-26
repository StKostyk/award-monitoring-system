package ua.edu.chnu.awards.gdpr.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.erasure.ErasureOrderConstants;

import lombok.RequiredArgsConstructor;

/**
 * Erasure of the technical identifiers in the person's consent history; the rows stay as proof of what was
 * acknowledged, the only change the consent guard allows.
 */
@Component
@RequiredArgsConstructor
public class ConsentErasure implements ErasureContributor {

    private static final String CLEAR = """
        update consent_records set ip_address = null, user_agent = null
         where user_id = ? and (ip_address is not null or user_agent is not null)
        """;

    private final JdbcTemplate jdbc;

    @Override
    public int order() {
        return ErasureOrderConstants.CONSENTS;
    }

    @Override
    public void erase(ErasureContext context) {
        context.count("consentsCleared", jdbc.update(CLEAR, context.userId()));
    }
}
