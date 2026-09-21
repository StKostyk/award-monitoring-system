package ua.edu.chnu.awards.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;

class AuditLogRepositoryIT extends AbstractJpaSliceTest {

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ac43_ac46_rowLandsInTheMonthPartitionWithJsonAndInetColumns() throws UnknownHostException {
        UUID correlation = UUID.randomUUID();
        AuditLog saved = repository.saveAndFlush(AuditLog.builder()
            .actionType("LOGIN_FAILED")
            .entityType(AuditLog.AUTHENTICATION)
            .details(Map.of("reason", "bad_credentials", "email", "x@chnu.edu.ua"))
            .ipAddress(InetAddress.getByName("203.0.113.7"))
            .userAgent("Firefox")
            .correlationId(correlation)
            .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        String partition = jdbc.queryForObject(
            "select tableoid::regclass::text from audit_logs where log_id = ?", String.class, saved.getId());
        assertThat(partition).startsWith("audit_logs_20").doesNotContain("default");
        assertThat(jdbc.queryForObject("select new_values->>'reason' from audit_logs where log_id = ?",
            String.class, saved.getId())).isEqualTo("bad_credentials");
        assertThat(jdbc.queryForObject("select host(ip_address) from audit_logs where log_id = ?",
            String.class, saved.getId())).isEqualTo("203.0.113.7");
    }
}
