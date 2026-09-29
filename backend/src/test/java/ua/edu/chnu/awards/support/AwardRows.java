package ua.edu.chnu.awards.support;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts an award row directly, below the service rules, for schema, repository and query tests. Unset columns
 * are a draft in the DAI department for the Ministry category, awarded by «МОН» on 1 May 2025.
 */
public final class AwardRows {

    private static final String INSERT = "insert into awards (user_id, organization_id, title, title_uk, status, "
        + "category_id, awarding_organization, award_date) values (?, ?, ?, ?, ?, ?, ?, ?) returning award_id";
    private static final long MINISTRY_CATEGORY = 13L;

    private final JdbcTemplate jdbc;
    private final long userId;
    private String title = "Letter";
    private String titleUk;
    private String status = "DRAFT";
    private Long categoryId = MINISTRY_CATEGORY;
    private String awardingOrganization = "МОН";
    private LocalDate awardDate = LocalDate.of(2025, 5, 1);

    private AwardRows(JdbcTemplate jdbc, long userId) {
        this.jdbc = jdbc;
        this.userId = userId;
    }

    /**
     * Starts a row owned by a user.
     *
     * @param jdbc   template of the test database
     * @param userId owner of the award
     * @return the row with its defaults
     */
    public static AwardRows award(JdbcTemplate jdbc, long userId) {
        return new AwardRows(jdbc, userId);
    }

    /**
     * Sets the English title.
     *
     * @param value the title
     * @return this row
     */
    public AwardRows title(String value) {
        title = value;
        return this;
    }

    /**
     * Sets the Ukrainian title.
     *
     * @param value the title
     * @return this row
     */
    public AwardRows titleUk(String value) {
        titleUk = value;
        return this;
    }

    /**
     * Sets the status.
     *
     * @param value status name, such as {@code PENDING}
     * @return this row
     */
    public AwardRows status(String value) {
        status = value;
        return this;
    }

    /**
     * Sets the category.
     *
     * @param value category id, null for none
     * @return this row
     */
    public AwardRows category(Long value) {
        categoryId = value;
        return this;
    }

    /**
     * Sets the awarding organization.
     *
     * @param value organization name
     * @return this row
     */
    public AwardRows awardingOrganization(String value) {
        awardingOrganization = value;
        return this;
    }

    /**
     * Sets the award date.
     *
     * @param value the date
     * @return this row
     */
    public AwardRows awardDate(LocalDate value) {
        awardDate = value;
        return this;
    }

    /**
     * Inserts the row.
     *
     * @return id of the new award
     */
    public long insert() {
        return jdbc.queryForObject(INSERT, Long.class, userId, TestUsers.DAI_DEPARTMENT_ID, title, titleUk, status,
            categoryId, awardingOrganization, awardDate);
    }
}
