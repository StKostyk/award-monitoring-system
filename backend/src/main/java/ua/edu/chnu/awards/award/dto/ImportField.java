package ua.edu.chnu.awards.award.dto;

/**
 * A field that a column of an imported spreadsheet can fill.
 */
public enum ImportField {
    /** Surname, first name and an optional patronymic in one cell. */
    FULL_NAME,
    /** Surname. */
    LAST_NAME,
    /** First name. */
    FIRST_NAME,
    /** University address, used only to find an account. */
    EMAIL,
    /** Department name or code. */
    DEPARTMENT,
    /** Award title. */
    TITLE,
    /** Award date: a date, a month and year, or a year. */
    DATE,
    /** Recognition level. */
    LEVEL,
    /** Who granted the award. */
    AWARDING_ORGANIZATION,
    /** Notes, stored as the description. */
    NOTES
}
