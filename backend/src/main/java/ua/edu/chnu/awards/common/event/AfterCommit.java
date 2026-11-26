package ua.edu.chnu.awards.common.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

/**
 * Publishes a transient event once the current transaction commits, or at once without a transaction. Transient
 * events carry one-time links and are never written to the event publication registry; their listeners are plain
 * {@code @EventListener}s.
 */
@Component
@RequiredArgsConstructor
public class AfterCommit {

    private final ApplicationEventPublisher events;

    /**
     * Publishes the event after the commit of the current transaction; nothing is published after a rollback.
     *
     * @param event the transient event
     */
    public void publish(Object event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            events.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                events.publishEvent(event);
            }
        });
    }
}
