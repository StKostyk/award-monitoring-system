package ua.edu.chnu.awards.analytics.export;

import java.util.List;

import org.springframework.context.MessageSource;

import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * The {@code report.*} messages in one report language.
 *
 * @param messages the message source
 * @param language the report language
 */
record ReportLabels(MessageSource messages, ReportLanguage language) {

    static final String APPROVED = "approved";
    static final String AWARDED_PEOPLE = "awardedPeople";
    static final String PERSONAL = "personal";
    static final String UNIT_AWARDS = "unitAwards";
    static final String MEMBERS = "members";
    static final String PER_HUNDRED = "perHundredMembers";
    static final String VERIFIED = "verified";

    String text(String key, Object... args) {
        return messages.getMessage("report." + key, args.length == 0 ? null : args, language.locale());
    }

    String figure(String key) {
        return text("figure." + key);
    }

    String level(Enum<?> level) {
        return text("level." + level.name());
    }

    String name(OrganizationRef unit) {
        return language.name(unit.name(), unit.nameUk());
    }

    UnitReport.Field field(String key, String value) {
        return new UnitReport.Field(key, text("field." + key), value);
    }

    UnitReport.Section section(String key, List<UnitReport.Row> rows) {
        return new UnitReport.Section(key, text("section." + key), rows);
    }
}
