package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.gdpr.dto.DataExport;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.ActivityEntry;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class DataExportIT extends AbstractIntegrationTest {

    private static final String EMAIL = "it.export@chnu.edu.ua";
    private static final String DEAN = "it.export.dean@chnu.edu.ua";
    private static final String ADMIN = "it.export.admin@chnu.edu.ua";
    private static final int MANY_AWARDS = 200;
    private static final Duration TARGET = Duration.ofSeconds(2);

    @Autowired
    private DataExportService exportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private User dean;
    private User admin;

    @BeforeEach
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        user = userRepository.save(TestUsers.user(EMAIL, department));
        dean = userRepository.save(TestUsers.user(DEAN, department));
        admin = userRepository.save(TestUsers.user(ADMIN, department));
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + user.getId());
    }

    @AfterEach
    void deleteUsers() {
        List<Long> ids = List.of(user.getId(), dean.getId(), admin.getId());
        ids.forEach(id -> {
            jdbc.update("delete from documents where uploaded_by = ?", id);
            jdbc.update("delete from consent_records where user_id = ?", id);
            jdbc.update("delete from awards where user_id = ?", id);
        });
        ids.forEach(userRepository::deleteById);
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + user.getId());
    }

    @Test
    void ac32_ac33_theExportCarriesEverySectionAndNothingSecret() throws JsonProcessingException {
        seedSections();

        DataExport export = exportService.export(user.getId());
        PersonalDataFile file = export.content();

        assertThat(file.personalData().profile().faculty().id()).isEqualTo(TestUsers.FMI_FACULTY_ID);
        assertThat(file.roles()).singleElement().satisfies(role -> assertThat(role.current()).isTrue());
        assertThat(file.delegations()).singleElement().satisfies(delegation -> {
            assertThat(delegation.direction()).isEqualTo("RECEIVED");
            assertThat(delegation.otherParty()).isEqualTo("Test User");
        });
        assertThat(file.awards()).hasSize(2).extracting(award -> award.status().name())
            .containsExactlyInAnyOrder("DRAFT", "PENDING");
        assertThat(file.documents()).singleElement().satisfies(document ->
            assertThat(document.apiPath()).isEqualTo("/api/v1/documents/" + document.documentId()));
        assertThat(file.consentHistory()).singleElement()
            .satisfies(consent -> assertThat(consent.ipAddress()).isEqualTo("10.0.0.7"));
        assertThat(file.devices()).singleElement()
            .satisfies(device -> assertThat(device.lastIpAddress()).isEqualTo("10.0.0.7"));
        assertThat(file.activityLog()).extracting(ActivityEntry::action, ActivityEntry::ipAddress)
            .containsExactlyInAnyOrder(
                tuple("LOGIN_SUCCESS", "10.0.0.7"),
                tuple("PROFILE_UPDATED", "10.0.0.7"),
                tuple("ROLE_ASSIGNED", null),
                tuple("LOGIN_FAILED", null));

        String json = objectMapper.writeValueAsString(file);
        assertThat(json).doesNotContain("password", "$2a$", "token", DEAN, ADMIN, "10.9.9.9", "fingerprint",
            "storage_key", "old_values");
    }

    @Test
    void ac34_theExportIsAuditedWithItsSectionCounts() {
        seedSections();

        exportService.export(user.getId());

        Map<String, Object> row = jdbc.queryForMap("""
            select entity_type, entity_id, (new_values->>'awards')::int as awards,
                   (new_values->>'activity_log')::int as activity
              from audit_logs where action_type = 'DATA_EXPORT' and user_id = ?
            """, user.getId());
        assertThat(row).containsEntry("entity_type", "GDPR").containsEntry("entity_id", user.getId())
            .containsEntry("awards", 2).containsEntry("activity", 4);
    }

    @Test
    void ac36_twoHundredAwardsExportWithinTwoSeconds() {
        for (int i = 0; i < MANY_AWARDS; i++) {
            AwardRows.award(jdbc, user.getId()).title("Award " + i).insert();
        }

        long started = System.nanoTime();
        PersonalDataFile file = exportService.export(user.getId()).content();
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(file.awards()).hasSize(MANY_AWARDS);
        assertThat(took).isLessThan(TARGET);
    }

    private void seedSections() {
        jdbc.update("insert into user_roles (user_id, role_type, organization_id, valid_from) values (?, 'EMPLOYEE',"
            + " ?, ?)", user.getId(), TestUsers.DAI_DEPARTMENT_ID, LocalDate.now().minusDays(10));
        jdbc.update("insert into role_delegations (delegator_id, delegate_id, role_type, organization_id, valid_from,"
            + " valid_to, reason) values (?, ?, 'DEAN', ?, ?, ?, 'Відрядження')", dean.getId(), user.getId(),
            TestUsers.FMI_FACULTY_ID, LocalDate.now(), LocalDate.now().plusDays(5));
        long draft = AwardRows.award(jdbc, user.getId()).insert();
        AwardRows.award(jdbc, user.getId()).title("Submitted").status("PENDING").insert();
        AwardRows.award(jdbc, dean.getId()).title("Dean's own").insert();
        jdbc.update("insert into documents (award_id, file_name, file_type, file_size, storage_bucket, storage_key,"
            + " uploaded_by) values (?, 'letter.pdf', 'PDF', 1024, 'awards', 'secret/key.pdf', ?)",
            draft, user.getId());
        jdbc.update("insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at,"
            + " ip_address) values (?, 'DATA_PROCESSING', '1.0', true, now(), '10.0.0.7')", user.getId());
        jdbc.update("insert into user_devices (user_id, fingerprint, browser, operating_system, last_ip_address,"
            + " first_seen_at, last_used_at) values (?, ?, 'Firefox', 'Windows', '10.0.0.7', now(), now())",
            user.getId(), "f".repeat(64));
        audit("LOGIN_SUCCESS", "AUTHENTICATION", "{}", "10.0.0.7");
        audit("PROFILE_UPDATED", "USER", "{\"last_name\":\"User\"}", "10.0.0.7");
        audit("ROLE_ASSIGNED", "AUTHORIZATION", "{\"actorId\":" + admin.getId() + "}", "10.9.9.9");
        audit("LOGIN_FAILED", "AUTHENTICATION", "{\"reason\":\"BAD_CREDENTIALS\"}", "10.9.9.9");
        audit("UPDATE", "users", "{\"email_address\":\"" + ADMIN + "\"}", "10.9.9.9");
    }

    private void audit(String action, String entityType, String details, String ip) {
        jdbc.update("insert into audit_logs (user_id, action_type, entity_type, entity_id, new_values, ip_address)"
            + " values (?, ?, ?, ?, ?::jsonb, ?::inet)", user.getId(), action, entityType, user.getId(), details, ip);
    }
}
