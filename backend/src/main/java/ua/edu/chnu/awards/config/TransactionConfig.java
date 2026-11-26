package ua.edu.chnu.awards.config;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

import ua.edu.chnu.awards.audit.service.AuditContextBinder;
import ua.edu.chnu.awards.audit.service.AuditingTransactionManager;

/**
 * The application's transaction manager: Spring Boot's JPA one, binding the signed-in caller for the audit
 * triggers.
 */
@Configuration(proxyBeanMethods = false)
public class TransactionConfig {

    /**
     * Replaces the auto-configured JPA transaction manager.
     *
     * @param entityManagerFactory the entity manager factory
     * @param binder               writes the caller to each read-write transaction
     * @param customizers          Spring Boot's {@code spring.transaction.*} settings
     * @return the transaction manager
     */
    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory, AuditContextBinder binder,
                                             ObjectProvider<TransactionManagerCustomizers> customizers) {
        AuditingTransactionManager manager = new AuditingTransactionManager(binder);
        manager.setEntityManagerFactory(entityManagerFactory);
        customizers.ifAvailable(customizer -> customizer.customize(manager));
        return manager;
    }
}
