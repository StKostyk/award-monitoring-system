package ua.edu.chnu.awards.audit.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;

import lombok.RequiredArgsConstructor;

/**
 * Writes audit trail rows as a CSV file that spreadsheet programs open with Ukrainian text intact: UTF-8 with a
 * byte order mark, {@code ;} as separator, CRLF line ends, and cells that would start a formula prefixed with
 * {@code '}.
 */
@Component
@RequiredArgsConstructor
public class AuditTrailCsv {

    private static final String BOM = "﻿";
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";
    private static final String HEADER = String.join(SEPARATOR, "time", "actor_id", "actor_email", "action",
        "entity_type", "entity_id", "changed_fields", "old_values", "new_values", "ip_address", "correlation_id");
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private final ObjectMapper objectMapper;

    /**
     * The file with a header line and one line per row, in the given order.
     *
     * @param rows the rows
     * @return the file content
     */
    public String write(List<AuditTrailEntry> rows) {
        return rows.stream().map(this::line)
            .collect(Collectors.joining(LINE_END, BOM + HEADER + LINE_END, rows.isEmpty() ? "" : LINE_END));
    }

    /**
     * One cell: formula-leading text prefixed with {@code '}, and quoted when it holds a separator, a quote or a
     * line break.
     *
     * @param value the value, may be null
     * @return the cell text, empty for null
     */
    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean quoted = Stream.of(SEPARATOR, "\"", "\n", "\r").anyMatch(text::contains);
        return quoted ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private String line(AuditTrailEntry row) {
        return Stream.of(row.createdAt(), row.actorId(), row.actorEmail(), row.action(), row.entityType(),
                row.entityId(), String.join(",", row.changedFields()), json(row.oldValues()), json(row.newValues()),
                row.ipAddress(), row.correlationId())
            .map(value -> cell(Objects.toString(value, null)))
            .collect(Collectors.joining(SEPARATOR));
    }

    private String json(Map<String, Object> values) {
        if (values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writer().with(new CommaEscapes()).writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit values are not serialisable", e);
        }
    }

    /**
     * Writes commas inside JSON strings as {@code ,}, so a spreadsheet that splits the file on commas cannot
     * cut a user-entered text into a cell that starts a formula.
     */
    private static final class CommaEscapes extends CharacterEscapes {

        private static final long serialVersionUID = 1L;

        private final int[] codes;

        CommaEscapes() {
            codes = standardAsciiEscapesForJSON();
            codes[','] = ESCAPE_STANDARD;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return codes.clone();
        }

        @Override
        public SerializableString getEscapeSequence(int ch) {
            return null;
        }
    }
}
