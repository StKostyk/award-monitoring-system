package ua.edu.chnu.awards.common.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    private static final Object EVENT = "reset requested";

    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AfterCommit afterCommit = new AfterCommit(events);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void ac1_7_withoutATransactionTheEventIsPublishedAtOnce() {
        afterCommit.publish(EVENT);

        verify(events).publishEvent(EVENT);
    }

    @Test
    void ac1_7_insideATransactionTheEventWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        afterCommit.publish(EVENT);
        verifyNoInteractions(events);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(events).publishEvent(EVENT);
    }

    @Test
    void ac1_7_nothingIsPublishedAfterARollback() {
        TransactionSynchronizationManager.initSynchronization();

        afterCommit.publish(EVENT);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(events);
    }
}
