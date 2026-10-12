package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
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
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.ReviewDecisionEntry;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.DecisionRows;
import ua.edu.chnu.awards.support.RequestRows;
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
        dean = TestUsers.user(DEAN, department);
        dean.setFirstName("Петро");
        dean.setLastName("Мартинюк");
        dean = userRepository.save(dean);
        admin = TestUsers.user(ADMIN, department);
        admin.setFirstName("Оксана");
        admin.setLastName("Коваль");
        admin = userRepository.save(admin);
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
            assertThat(delegation.otherParty()).isEqualTo("Петро Мартинюк");
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

    @Test
    void ac1_ac2_ac3_decisionsOnOwnAwardsAndAsReviewerUnderADelegationAreExported() throws JsonProcessingException {
        seedSections();
        Instant returned = Instant.parse("2026-10-01T09:00:00Z");
        long ownAward = AwardRows.award(jdbc, user.getId()).title("Gratitude").titleUk("Подяка").status("PENDING")
            .insert();
        long ownRequest = RequestRows.request(jdbc, ownAward, user.getId()).status("IN_REVIEW").level("DEAN")
            .insert();
        DecisionRows.decision(jdbc, ownRequest, admin.getId()).comments("Додайте наказ").decidedAt(returned)
            .insert();
        DecisionRows.decision(jdbc, ownRequest, admin.getId()).type("ESCALATED")
            .decidedAt(returned.plusSeconds(3600)).insert();
        long othersAward = AwardRows.award(jdbc, admin.getId()).title("Diploma").titleUk("Диплом").status("PENDING")
            .insert();
        long othersRequest = RequestRows.request(jdbc, othersAward, admin.getId()).status("IN_REVIEW").level("DEAN")
            .insert();
        DecisionRows.decision(jdbc, othersRequest, user.getId()).type("APPROVED").level("DEAN").comments("Погоджено")
            .delegator(dean.getId()).decidedAt(returned).insert();

        PersonalDataFile file = exportService.export(user.getId()).content();

        assertThat(file.reviewDecisions()).extracting(ReviewDecisionEntry::awardId, ReviewDecisionEntry::awardTitleUk,
                entry -> entry.decision().name(), entry -> entry.level().name(), ReviewDecisionEntry::comments,
                ReviewDecisionEntry::reviewer, ReviewDecisionEntry::delegator)
            .containsExactly(
                tuple(ownAward, "Подяка", "RETURNED", "FACULTY_SECRETARY", "Додайте наказ", "Оксана Коваль", null),
                tuple(ownAward, "Подяка", "ESCALATED", "FACULTY_SECRETARY", null, "Оксана Коваль", null));
        assertThat(file.reviewDecisions().get(0).decidedAt()).isEqualTo(returned);
        assertThat(file.decisionsMade()).singleElement().satisfies(entry -> {
            assertThat(entry.awardId()).isEqualTo(othersAward);
            assertThat(entry.awardTitle()).isEqualTo("Diploma");
            assertThat(entry.awardTitleUk()).isEqualTo("Диплом");
            assertThat(entry.decision().name()).isEqualTo("APPROVED");
            assertThat(entry.level().name()).isEqualTo("DEAN");
            assertThat(entry.decidedAt()).isEqualTo(returned);
            assertThat(entry.comments()).isEqualTo("Погоджено");
            assertThat(entry.reviewer()).isEqualTo("Test User");
            assertThat(entry.delegator()).isEqualTo("Петро Мартинюк");
        });
        assertThat(file.sectionCounts()).containsEntry("review_decisions", 2).containsEntry("decisions_made", 1);

        String json = objectMapper.writeValueAsString(file);
        assertThat(json).contains("\"review_decisions\"", "\"decisions_made\"", "\"award_title_uk\"")
            .doesNotContain(DEAN, ADMIN, "reviewer_id", "delegator_id", "submitter");
    }

    @Test
    void ac2_decisionsADelegateMadeUnderTheLentRoleAreExportedWithTitleFallback() {
        long othersAward = AwardRows.award(jdbc, admin.getId()).title(null).titleUk("Грамота").status("PENDING")
            .insert();
        long othersRequest = RequestRows.request(jdbc, othersAward, admin.getId()).insert();
        DecisionRows.decision(jdbc, othersRequest, dean.getId()).type("APPROVED").delegator(user.getId()).insert();

        PersonalDataFile file = exportService.export(user.getId()).content();

        assertThat(file.reviewDecisions()).isEmpty();
        assertThat(file.decisionsMade()).singleElement().satisfies(entry -> {
            assertThat(entry.awardTitle()).isEqualTo("Грамота");
            assertThat(entry.awardTitleUk()).isEqualTo("Грамота");
            assertThat(entry.reviewer()).isEqualTo("Петро Мартинюк");
            assertThat(entry.delegator()).isEqualTo("Test User");
        });
    }

    @Test
    void ac3_aPersonWithoutDecisionsGetsEmptyDecisionSections() {
        long othersAward = AwardRows.award(jdbc, admin.getId()).status("PENDING").insert();
        long othersRequest = RequestRows.request(jdbc, othersAward, admin.getId()).insert();
        DecisionRows.decision(jdbc, othersRequest, dean.getId()).type("APPROVED").delegator(admin.getId()).insert();

        PersonalDataFile file = exportService.export(user.getId()).content();

        assertThat(file.reviewDecisions()).isEmpty();
        assertThat(file.decisionsMade()).isEmpty();
        assertThat(file.sectionCounts()).containsEntry("review_decisions", 0).containsEntry("decisions_made", 0);
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
