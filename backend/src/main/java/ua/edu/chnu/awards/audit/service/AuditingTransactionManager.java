package ua.edu.chnu.awards.audit.service;

import java.sql.SQLException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.hibernate.Session;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;

/**
 * The JPA transaction manager that binds the signed-in caller to every read-write transaction it begins,
 * including {@code REQUIRES_NEW} ones and JDBC work that joins them, so every table trigger row names its actor.
 * Read-only transactions write nothing and are skipped.
 */
public class AuditingTransactionManager extends JpaTransactionManager {

    private final transient AuditContextBinder binder;

    /**
     * Creates the manager; the entity manager factory is set before the container initialises it.
     *
     * @param binder writes the caller to the transaction
     */
    public AuditingTransactionManager(AuditContextBinder binder) {
        super();
        this.binder = binder;
    }

    /**
     * Takes the settings of the entity manager factory, then replaces its dialect by one that binds the caller.
     */
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        setJpaDialect(new BindingDialect(binder));
    }

    private static final class BindingDialect extends HibernateJpaDialect {

        private final transient AuditContextBinder binder;

        private BindingDialect(AuditContextBinder binder) {
            this.binder = binder;
        }

        @Override
        public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
                throws SQLException {
            Object data = super.beginTransaction(entityManager, definition);
            if (!definition.isReadOnly()) {
                try {
                    entityManager.unwrap(Session.class).doWork(binder::bind);
                } catch (PersistenceException e) {
                    entityManager.getTransaction().rollback();
                    throw e;
                }
            }
            return data;
        }
    }
}
