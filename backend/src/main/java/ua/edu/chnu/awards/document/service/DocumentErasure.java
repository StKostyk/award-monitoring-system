package ua.edu.chnu.awards.document.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.erasure.ErasureOrderConstants;
import ua.edu.chnu.awards.document.event.ObjectsReleased;

import lombok.RequiredArgsConstructor;

/**
 * Erasure of the documents of the person's awards, kept or not: the rows go in the transaction, the stored
 * objects once it commits ({@link StoredObjectCleanup}, with {@link DocumentSweeper} for what that misses).
 */
@Component
@RequiredArgsConstructor
public class DocumentErasure implements ErasureContributor {

    private static final String DELETE = """
        delete from documents
         where award_id in (select award_id from awards where user_id = ?)
            or request_id in (select r.request_id from award_requests r
                                join awards a on a.award_id = r.award_id
                               where a.user_id = ?)
        returning document_id, storage_key
        """;

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    @Override
    public int order() {
        return ErasureOrderConstants.DOCUMENTS;
    }

    @Override
    public void erase(ErasureContext context) {
        List<Long> ids = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        jdbc.query(DELETE, row -> {
            ids.add(row.getLong("document_id"));
            keys.add(row.getString("storage_key"));
        }, context.userId(), context.userId());
        context.erased("documents", ids);
        context.count("documentsDeleted", ids.size());
        if (!keys.isEmpty()) {
            events.publishEvent(new ObjectsReleased(keys));
        }
    }
}
