package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.service.EmailChangeService;
import ua.edu.chnu.awards.auth.service.OneTimeTokenService;
import ua.edu.chnu.awards.auth.service.PasswordResetService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ProfileChangeIT extends AbstractIntegrationTest {

    private static final String EMAIL = "it.profile@chnu.edu.ua";
    private static final String PASSWORD = "Passw0rd-demo";
    private static final List<String> OTHERS = List.of("it.profile.a@chnu.edu.ua", "it.profile.b@chnu.edu.ua",
        "it.profile.race@chnu.edu.ua", "it.profile.moved@chnu.edu.ua");

    @Autowired
    private UserProfileService profileService;

    @Autowired
    private EmailChangeService emailChangeService;

    @Autowired
    private OneTimeTokenService tokens;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private Organization department;

    @BeforeEach
    void createUser() {
        department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        user = userRepository.save(TestUsers.user(EMAIL, department));
        redis.delete("auth:email-change:" + user.getId());
    }

    @AfterEach
    void deleteUsers() {
        userRepository.findById(user.getId()).ifPresent(userRepository::delete);
        OTHERS.forEach(email -> userRepository.findByEmailAddressIgnoreCase(email)
            .ifPresent(userRepository::delete));
    }

    @Test
    void ac13_ac18_aNameChangeIsAuditedWithTheChangedFieldAndNoHashInTheTriggerRow() {
        profileService.update(user.getId(), new UserUpdateRequest("Test", " Петренко-Коваль "));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastName()).isEqualTo("Петренко-Коваль");
        Map<String, Object> audit = jdbc.queryForMap("""
            select entity_type, entity_id, old_values->>'last_name' as old_name,
                   new_values->>'last_name' as new_name,
                   jsonb_exists(new_values, 'first_name') as first_name_logged,
                   array_to_string(changed_fields, ',') as changed
            from audit_logs where action_type = 'PROFILE_UPDATED' and user_id = ?
            """, user.getId());
        assertThat(audit).containsEntry("entity_type", "USER").containsEntry("entity_id", user.getId())
            .containsEntry("old_name", "User").containsEntry("new_name", "Петренко-Коваль")
            .containsEntry("first_name_logged", false).containsEntry("changed", "last_name");
        Map<String, Object> trigger = jdbc.queryForMap("""
            select jsonb_exists(old_values, 'password_hash') as old_hash,
                   jsonb_exists(new_values, 'password_hash') as new_hash,
                   new_values->>'last_name' as new_name
            from audit_logs where entity_type = 'users' and action_type = 'UPDATE' and entity_id = ?
            order by created_at desc, log_id desc limit 1
            """, user.getId());
        assertThat(trigger).containsEntry("old_hash", false).containsEntry("new_hash", false)
            .containsEntry("new_name", "Петренко-Коваль");
        assertThat(jdbc.queryForObject("""
            select count(*) from audit_logs where entity_type = 'users' and action_type = 'INSERT'
              and entity_id = ? and jsonb_exists(new_values, 'password_hash')
            """, Long.class, user.getId())).isZero();
    }

    @Test
    void ac12_aChangeOfNothingWritesNoAuditRow() {
        profileService.update(user.getId(), new UserUpdateRequest("Test", "User"));

        assertThat(jdbc.queryForObject("select count(*) from audit_logs where user_id = ? "
            + "and action_type = 'PROFILE_UPDATED'", Long.class, user.getId())).isZero();
    }

    @Test
    void ac14_aNewRequestCancelsTheOlderLink() {
        emailChangeService.request(user.getId(), OTHERS.get(0), PASSWORD);
        redis.delete("auth:email-change:" + user.getId());
        emailChangeService.request(user.getId(), OTHERS.get(1), PASSWORD);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            select new_email_address, used_at is not null as used
            from one_time_tokens where user_id = ? and purpose = 'EMAIL_CHANGE' order by id
            """, user.getId());
        assertThat(rows).extracting(row -> row.get("new_email_address")).containsExactly(OTHERS.get(0),
            OTHERS.get(1));
        assertThat(rows).extracting(row -> row.get("used")).containsExactly(true, false);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where user_id = ? "
            + "and action_type = 'EMAIL_CHANGE_REQUESTED'", Long.class, user.getId())).isEqualTo(2);
    }

    @Test
    void ac14_onlyAnAddressChangeTokenCarriesAnAddress() {
        assertThatThrownBy(() -> jdbc.update("""
            insert into one_time_tokens (token_hash, user_id, purpose, expires_at)
            values ('x', ?, 'EMAIL_CHANGE', now() + interval '1 hour')
            """, user.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
            insert into one_time_tokens (token_hash, user_id, purpose, expires_at, new_email_address)
            values ('y', ?, 'PASSWORD_RESET', now() + interval '1 hour', 'a@chnu.edu.ua')
            """, user.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ac15_ac16_theLinkMovesTheAccountAndCancelsItsResetLinks() {
        String reset = tokens.issue(user, TokenPurpose.PASSWORD_RESET, Duration.ofHours(1));
        String raw = tokens.issue(user, TokenPurpose.EMAIL_CHANGE, Duration.ofHours(1), OTHERS.get(3));

        assertThat(emailChangeService.confirm(raw)).isEqualTo(OTHERS.get(3));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmailAddress()).isEqualTo(OTHERS.get(3));
        assertThat(tokens.peek(reset, TokenPurpose.PASSWORD_RESET)).isEmpty();
        assertThat(jdbc.queryForMap("""
            select new_values->>'oldEmail' as old_email, new_values->>'newEmail' as new_email
            from audit_logs where user_id = ? and action_type = 'EMAIL_CHANGED'
            """, user.getId())).containsEntry("old_email", EMAIL).containsEntry("new_email", OTHERS.get(3));
    }

    @Test
    void edge_aPasswordResetCancelsAPendingAddressChange() {
        String change = tokens.issue(user, TokenPurpose.EMAIL_CHANGE, Duration.ofHours(1), OTHERS.get(3));
        String reset = tokens.issue(user, TokenPurpose.PASSWORD_RESET, Duration.ofHours(1));

        passwordResetService.confirm(reset, "new-horse-battery-staple");
        assertThat(jdbc.queryForObject("""
            select coalesce(array_to_string(changed_fields, ','), '') from audit_logs
            where entity_type = 'users' and action_type = 'UPDATE' and entity_id = ?
            order by created_at desc, log_id desc limit 1
            """, String.class, user.getId())).doesNotContain("password_hash");

        assertThatThrownBy(() -> emailChangeService.confirm(change))
            .isInstanceOfSatisfying(ApiProblemException.class,
                problem -> assertThat(problem.getType()).isEqualTo("token-invalid"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmailAddress()).isEqualTo(EMAIL);
    }

    @Test
    void edge_anAddressRegisteredBeforeTheConfirmationKeepsTheAccountWhereItWas() {
        String raw = tokens.issue(user, TokenPurpose.EMAIL_CHANGE, Duration.ofHours(1), OTHERS.get(2));
        userRepository.save(TestUsers.user(OTHERS.get(2), department));

        assertThatThrownBy(() -> emailChangeService.confirm(raw))
            .isInstanceOfSatisfying(ApiProblemException.class,
                problem -> assertThat(problem.getType()).isEqualTo("email-taken"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmailAddress()).isEqualTo(EMAIL);
    }
}
