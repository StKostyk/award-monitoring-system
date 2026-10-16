package ua.edu.chnu.awards.gdpr.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.gdpr.dto.ActivityEntry;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class PrivacyQueriesIT extends AbstractIntegrationTest {

    private static final String EMAIL = "it.privacy@chnu.edu.ua";
    private static final String ADMIN = "it.privacy.admin@chnu.edu.ua";

    @Autowired
    private PrivacySummaryQueries summaryQueries;

    @Autowired
    private ActivityQueries activityQueries;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private User admin;

    @BeforeEach
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        user = userRepository.save(TestUsers.user(EMAIL, department));
        admin = userRepository.save(TestUsers.user(ADMIN, department));
    }

    @AfterEach
    void deleteUsers() {
        List.of(user.getId(), admin.getId()).forEach(id -> {
            jdbc.update("delete from documents where uploaded_by = ?", id);
            jdbc.update("delete from awards where user_id = ?", id);
            jdbc.update("delete from notification_preferences where user_id = ?", id);
            userRepository.deleteById(id);
        });
    }

    @Test
    void ac21_onlyApprovedPersonalAwardsAndSharedDocumentsAreCounted() {
        approved("UNIVERSITY");
        approved("PUBLIC");
        approved("PRIVATE");
        AwardRows.award(jdbc, user.getId()).title("Pending").status("PENDING").insert();
        AwardRows.award(jdbc, user.getId()).title("Unit").status("APPROVED").unit(TestUsers.FMI_FACULTY_ID).insert();
        AwardRows.award(jdbc, admin.getId()).title("Someone else's").status("APPROVED").insert();
        long shared = approved("UNIVERSITY");
        document(shared, true);
        document(shared, false);
        jdbc.update("insert into notification_preferences (user_id, event_type, email_enabled, in_app_enabled)"
            + " values (?, 'AWARD_RETURNED', false, true), (?, 'AWARD_APPROVED', true, false)",
            user.getId(), user.getId());

        assertThat(summaryQueries.awardsByVisibility(user.getId()))
            .containsOnly(entry("UNIVERSITY", 2L), entry("PUBLIC", 1L), entry("PRIVATE", 1L));
        assertThat(summaryQueries.documentsShown(user.getId())).isEqualTo(1L);
        assertThat(summaryQueries.emailOffTypes(user.getId())).containsExactly("AWARD_RETURNED");
    }

    @Test
    void ac21_theLastExportIsNullUntilTheFirstOne() {
        assertThat(summaryQueries.lastExportAt(user.getId())).isNull();

        audit(user.getId(), "DATA_EXPORT", "GDPR", user.getId(), "{}", 3);
        audit(user.getId(), "DATA_EXPORT", "GDPR", user.getId(), "{}", 1);

        assertThat(summaryQueries.lastExportAt(user.getId())).isNotNull()
            .isAfter(Instant.now().minusSeconds(150));
    }

    @Test
    void ac22_eventsByAndAboutThePersonComeNewestFirstWithoutTriggerRows() {
        audit(user.getId(), "LOGIN_SUCCESS", "AUTHENTICATION", user.getId(), "{}", 6);
        audit(user.getId(), "ROLE_ASSIGNED", "AUTHORIZATION", user.getId(), "{\"actorId\":" + admin.getId() + "}", 5);
        audit(admin.getId(), "ACCOUNT_LOCKED", "USER", user.getId(), "{}", 4);
        audit(user.getId(), "UPDATE", "users", user.getId(), "{\"first_name\":\"X\"}", 3);
        audit(admin.getId(), "AWARD_SUBMITTED", "awards", user.getId(), "{}", 2);
        audit(user.getId(), "AWARD_SUBMITTED", "awards", 99L, "{}", 1);

        Page<ActivityEntry> page = activityQueries.activity(user.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(ActivityEntry::action, ActivityEntry::entityType,
                ActivityEntry::entityId, ActivityEntry::byMe)
            .containsExactly(
                tuple("AWARD_SUBMITTED", "awards", 99L, true),
                tuple("ACCOUNT_LOCKED", "USER", user.getId(), false),
                tuple("ROLE_ASSIGNED", "AUTHORIZATION", user.getId(), false),
                tuple("LOGIN_SUCCESS", "AUTHENTICATION", user.getId(), true));
        assertThat(activityQueries.activity(user.getId(), PageRequest.of(1, 3)).getContent())
            .extracting(ActivityEntry::action).containsExactly("LOGIN_SUCCESS");
    }

    private long approved(String visibility) {
        long award = AwardRows.award(jdbc, user.getId()).status("APPROVED").insert();
        jdbc.update("update awards set visibility = ? where award_id = ?", visibility, award);
        return award;
    }

    private void document(long award, boolean shown) {
        jdbc.update("insert into documents (award_id, file_name, file_type, file_size, storage_bucket, storage_key,"
            + " uploaded_by, shown_to_colleagues) values (?, 'letter.pdf', 'PDF', 1024, 'awards', ?, ?, ?)",
            award, "privacy/" + award + "/" + shown, user.getId(), shown);
    }

    private void audit(long userId, String action, String entityType, long entityId, String details,
                       int minutesAgo) {
        jdbc.update("insert into audit_logs (user_id, action_type, entity_type, entity_id, new_values, ip_address,"
            + " created_at) values (?, ?, ?, ?, ?::jsonb, '10.1.1.1'::inet, now() - make_interval(mins => ?))",
            userId, action, entityType, entityId, details, minutesAgo);
    }
}
