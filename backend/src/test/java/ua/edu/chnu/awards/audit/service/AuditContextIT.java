package ua.edu.chnu.awards.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AuditContextIT extends AbstractIntegrationTest {

    private static final String EMAIL = "it.audit.context@chnu.edu.ua";
    private static final String TOUCH = "update users set last_name = last_name where user_id = ?";
    private static final String SETTING = "select current_setting('app.current_user_id', true)";

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private UUID correlation;

    @BeforeEach
    void setUp() {
        user = userRepository.save(TestUsers.user(EMAIL,
            organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow()));
        correlation = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ClientRequest.CORRELATION_ATTRIBUTE, correlation);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        userRepository.findById(user.getId()).ifPresent(userRepository::delete);
    }

    @Test
    void ac1_6_theApplicationUsesTheAuditingTransactionManager() {
        assertThat(transactionManager).isInstanceOf(AuditingTransactionManager.class);
    }

    @Test
    void ac1_6_aReadWriteTransactionOfASignedInCallerNamesThemInTriggerRows() {
        signIn();

        template(false, TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status ->
            jdbc.update(TOUCH, user.getId()));

        assertThat(lastTriggerRow()).containsEntry("user_id", user.getId()).containsEntry("correlation_id",
            correlation);
    }

    @Test
    void ac1_6_aRequiresNewTransactionIsBoundToo() {
        signIn();

        template(true, TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(outer ->
            template(false, TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(inner ->
                jdbc.update(TOUCH, user.getId())));

        assertThat(lastTriggerRow()).containsEntry("user_id", user.getId());
    }

    @Test
    void ac1_6_readOnlyTransactionsAreNotBound() {
        signIn();

        String setting = template(true, TransactionDefinition.PROPAGATION_REQUIRED).execute(status ->
            jdbc.queryForObject(SETTING, String.class));

        assertThat(setting).isNullOrEmpty();
    }

    @Test
    void ac1_6_changesWithoutASignedInCallerKeepTheActorEmptyAndNothingLeaksFromAnEarlierTransaction() {
        signIn();
        template(false, TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status ->
            jdbc.update(TOUCH, user.getId()));
        SecurityContextHolder.clearContext();

        String setting = template(false, TransactionDefinition.PROPAGATION_REQUIRED).execute(status -> {
            jdbc.update(TOUCH, user.getId());
            return jdbc.queryForObject(SETTING, String.class);
        });

        assertThat(setting).isNullOrEmpty();
        assertThat(lastTriggerRow().get("user_id")).isNull();
    }

    private void signIn() {
        TestUsers.signInAs(user);
    }

    private TransactionTemplate template(boolean readOnly, int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(readOnly);
        template.setPropagationBehavior(propagation);
        return template;
    }

    private Map<String, Object> lastTriggerRow() {
        return jdbc.queryForMap("select user_id, correlation_id from audit_logs where entity_type = 'users' "
            + "and entity_id = ? and action_type = 'UPDATE' order by log_id desc limit 1", user.getId());
    }
}
