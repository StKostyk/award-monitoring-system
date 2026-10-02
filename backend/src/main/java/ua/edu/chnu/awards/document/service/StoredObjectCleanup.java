package ua.edu.chnu.awards.document.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.document.event.ObjectStored;
import ua.edu.chnu.awards.document.event.ObjectsReleased;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the storage in step with the {@code documents} rows once a transaction ends: objects of deleted rows go
 * after the commit, objects of rows that were never committed after the rollback. A failure here leaves an
 * orphan for the daily sweep.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoredObjectCleanup {

    private final ObjectStorage storage;

    /**
     * Removes the objects of committed deletions.
     *
     * @param released the keys
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void released(ObjectsReleased released) {
        remove(released.keys());
    }

    /**
     * Removes the object of an upload whose row was rolled back.
     *
     * @param stored the key
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void abandoned(ObjectStored stored) {
        remove(List.of(stored.key()));
    }

    private void remove(List<String> keys) {
        try {
            storage.delete(keys);
        } catch (StorageUnavailableException exception) {
            log.warn("Could not remove {} document objects, left for the sweep", keys.size(), exception);
        }
    }
}
