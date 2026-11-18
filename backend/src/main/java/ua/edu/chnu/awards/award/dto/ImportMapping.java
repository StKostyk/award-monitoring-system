package ua.edu.chnu.awards.award.dto;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Which column fills which field, and the values for rows that leave a field empty.
 *
 * @param columns      the 0-based column of each mapped field
 * @param departmentId the department of rows without one, null for none
 * @param defaultLevel the level of rows without one, null for none
 * @param defaultAwardingOrganization who granted the awards of rows without one, empty for none
 */
@SuppressWarnings("PMD.CommentSize")
public record ImportMapping(Map<ImportField, Integer> columns, Long departmentId, RecognitionLevel defaultLevel,
                            String defaultAwardingOrganization) {

    /**
     * Copies the columns.
     *
     * @param columns      the 0-based column of each mapped field
     * @param departmentId the department of rows without one, null for none
     * @param defaultLevel the level of rows without one, null for none
     * @param defaultAwardingOrganization who granted the awards of rows without one, null or empty for none
     */
    public ImportMapping {
        columns = Map.copyOf(withoutNulls(columns));
        defaultAwardingOrganization = Objects.toString(defaultAwardingOrganization, "").strip();
    }

    private static Map<ImportField, Integer> withoutNulls(Map<ImportField, Integer> columns) {
        Map<ImportField, Integer> kept = new EnumMap<>(ImportField.class);
        if (columns != null) {
            columns.forEach((field, column) -> {
                if (field != null && column != null) {
                    kept.put(field, column);
                }
            });
        }
        return kept;
    }

    /**
     * Whether a column is mapped to the field.
     *
     * @param field the field
     * @return true when mapped
     */
    public boolean has(ImportField field) {
        return columns.containsKey(field);
    }

    /**
     * A stable text of the mapping, part of the preview checksum.
     *
     * @return the text
     */
    public String canonical() {
        StringBuilder text = new StringBuilder();
        for (ImportField field : ImportField.values()) {
            text.append(field.name()).append('=').append(columns.get(field)).append(';');
        }
        return text.append(departmentId).append(';').append(defaultLevel).append(';')
            .append(defaultAwardingOrganization).toString();
    }
}
