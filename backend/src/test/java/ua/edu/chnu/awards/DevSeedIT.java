package ua.edu.chnu.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.support.ContainersConfiguration;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/seed/local")
@Import(ContainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class DevSeedIT {

    private static final Map<String, RoleType> EXPECTED_ROLES = Map.of(
        "admin@chnu.edu.ua", RoleType.SYSTEM_ADMIN,
        "rector@chnu.edu.ua", RoleType.RECTOR,
        "gdpr@chnu.edu.ua", RoleType.GDPR_OFFICER,
        "dean.fmi@chnu.edu.ua", RoleType.DEAN,
        "secretary.fmi@chnu.edu.ua", RoleType.FACULTY_SECRETARY,
        "secretary2.fmi@chnu.edu.ua", RoleType.FACULTY_SECRETARY,
        "rector.secretary@chnu.edu.ua", RoleType.RECTOR_SECRETARY,
        "employee.fmi@chnu.edu.ua", RoleType.EMPLOYEE,
        "pending@chnu.edu.ua", RoleType.EMPLOYEE);
    private static final List<String> ANALYTICS_DAI = List.of("lecturer.fmi", "dai.bondar", "dai.hnatiuk",
        "dai.kovalchuk", "dai.melnyk", "dai.savchuk", "dai.tkachuk", "dai.fedoriuk", "dai.shevchuk", "dai.lysiuk",
        "dai.romaniuk", "dai.danyliuk");
    private static final List<String> ANALYTICS_DMA = List.of("dma.ivaniuk", "dma.palamar", "dma.kushnir",
        "dma.vasylyk", "dma.hrytsiuk", "dma.kravets");
    private static final String DOMAIN = "@chnu.edu.ua";
    private static final String LECTURER = "lecturer.fmi" + DOMAIN;
    private static final String ANALYTICS_SEED = "db/seed/local/R__seed_users_dev_analytics.sql";
    private static final int MIN_YEARS = 5;
    private static final int MIN_LEVELS = 4;
    private static final int MIN_DAI_MEMBERS = 12;
    private static final int MIN_DAI_UNIT_AWARDS = 3;
    private static final int MIN_FACULTY_UNIT_AWARDS = 2;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ac05_seedUsersExistWithRolesAndStatuses() {
        List<User> users = userRepository.findAll().stream()
            .filter(user -> EXPECTED_ROLES.containsKey(user.getEmailAddress())).toList();

        assertThat(userRepository.findAll()).extracting(User::getEmailAddress)
            .containsExactlyInAnyOrderElementsOf(Stream.concat(EXPECTED_ROLES.keySet().stream(),
                analytics().stream()).toList());
        for (User user : users) {
            AccountStatus expected = "pending@chnu.edu.ua".equals(user.getEmailAddress())
                ? AccountStatus.PENDING : AccountStatus.ACTIVE;
            assertThat(user.getAccountStatus()).as(user.getEmailAddress()).isEqualTo(expected);
            List<UserRole> roles = userRoleRepository.findCurrentByUserId(user.getId(), LocalDate.now());
            assertThat(roles).extracting(UserRole::getRoleType)
                .as(user.getEmailAddress())
                .containsExactly(EXPECTED_ROLES.get(user.getEmailAddress()));
        }
    }

    @Test
    void ac1_9_theAnalyticsSeedFillsTwoDepartmentsOnceAndLeavesEmployeeFmiAlone() {
        Map<String, Object> before = seedCounts();

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(ANALYTICS_SEED));
            return null;
        });

        assertThat(seedCounts()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from users u join organizations o "
            + "on o.org_id = u.organization_id where o.code = 'DAI' and u.account_status = 'ACTIVE'", Integer.class))
            .isGreaterThanOrEqualTo(MIN_DAI_MEMBERS);
        Map<String, Object> lecturer = jdbc.queryForMap("select count(distinct extract(year from a.award_date)) "
            + "as years, count(distinct c.level) as levels, count(*) filter (where a.visibility = 'PUBLIC') as shown, "
            + "count(*) filter (where r.status = 'APPROVED') as decided, count(*) as awards from awards a "
            + "join award_categories c on c.category_id = a.category_id "
            + "left join award_requests r on r.award_id = a.award_id "
            + "join users u on u.user_id = a.user_id where u.email_address = ? and a.status = 'APPROVED'", LECTURER);
        assertThat(((Number) lecturer.get("years")).intValue()).isGreaterThanOrEqualTo(MIN_YEARS);
        assertThat(((Number) lecturer.get("levels")).intValue()).isGreaterThanOrEqualTo(MIN_LEVELS);
        assertThat(((Number) lecturer.get("shown")).intValue()).isEqualTo(2);
        assertThat(lecturer.get("decided")).isEqualTo(lecturer.get("awards"));
        assertThat(jdbc.queryForObject("select count(*) from awards where visibility <> 'PRIVATE' and user_id <> "
            + "(select user_id from users where email_address = ?)", Integer.class, LECTURER)).isZero();
        assertThat(jdbc.queryForObject("select bool_and(is_granted) from consent_records c join users u "
            + "on u.user_id = c.user_id where u.email_address = ? and c.consent_type = 'ANALYTICS'", Boolean.class,
            LECTURER)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from consent_records where consent_type = 'ANALYTICS'",
            Integer.class)).isBetween(2, analytics().size() - 1);
        assertThat(jdbc.queryForObject("select count(distinct extract(year from a.award_date)) from awards a "
            + "join users u on u.user_id = a.user_id where u.email_address like 'dma.%'", Integer.class))
            .isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from awards a join users u on u.user_id = a.user_id "
            + "where u.email_address = 'employee.fmi@chnu.edu.ua'", Integer.class)).isZero();
    }

    @Test
    void ac1_12_theSeedHoldsApprovedUnitAwardsAndOneSourceInSeveralSpellings() {
        Map<String, Object> before = seedCounts();

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(ANALYTICS_SEED));
            return null;
        });

        assertThat(seedCounts()).isEqualTo(before);
        Map<String, Object> units = jdbc.queryForMap("select count(*) filter (where o.code = 'DAI') as dai, "
            + "count(*) filter (where o.org_type = 'FACULTY') as faculty, "
            + "count(*) filter (where r.status is distinct from 'APPROVED') as undecided from awards a "
            + "join organizations o on o.org_id = a.recipient_org_id "
            + "left join award_requests r on r.award_id = a.award_id where a.status = 'APPROVED'");
        assertThat(((Number) units.get("dai")).intValue()).isGreaterThanOrEqualTo(MIN_DAI_UNIT_AWARDS);
        assertThat(((Number) units.get("faculty")).intValue()).isGreaterThanOrEqualTo(MIN_FACULTY_UNIT_AWARDS);
        assertThat(((Number) units.get("undecided")).intValue()).isZero();
        assertThat(jdbc.queryForObject("select count(distinct awarding_organization) from awards "
            + "where status = 'APPROVED' and lower(regexp_replace(btrim(awarding_organization), '\\s+', ' ', 'g')) "
            + "= 'міністерство освіти і науки україни'", Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void ac05_seedPasswordMatchesDemoPassword() {
        User admin = userRepository.findByEmailAddressIgnoreCase("admin@chnu.edu.ua").orElseThrow();

        assertThat(new BCryptPasswordEncoder().matches("Passw0rd-demo", admin.getPasswordHash())).isTrue();
    }

    private Map<String, Object> seedCounts() {
        return jdbc.queryForMap("select (select count(*) from users) as users, (select count(*) from user_roles) "
            + "as roles, (select count(*) from consent_records) as consents, (select count(*) from awards) as awards, "
            + "(select count(*) from award_requests) as requests");
    }

    private static List<String> analytics() {
        return Stream.concat(ANALYTICS_DAI.stream(), ANALYTICS_DMA.stream()).map(name -> name + DOMAIN).toList();
    }
}
