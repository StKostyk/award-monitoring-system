package ua.edu.chnu.awards.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;

class AuditTrailCsvTest {

    private static final String HEADER = "time;actor_id;actor_email;action;entity_type;entity_id;changed_fields;"
        + "old_values;new_values;ip_address;correlation_id";

    private final AuditTrailCsv csv = new AuditTrailCsv(new ObjectMapper());

    @Test
    void ac2_6_theFileStartsWithABomAndTheHeaderAndHasOneLinePerRow() {
        UUID correlation = UUID.fromString("0b5d5c0e-6a52-4b8e-9f43-1c4b4f0a9a11");
        AuditTrailEntry update = new AuditTrailEntry(9L, Instant.parse("2026-09-30T08:00:00Z"), 21L, "Анастасія",
            "employee.fmi@chnu.edu.ua", "UPDATE", "awards", 5L, List.of("title", "category_id"),
            Map.of("title", "Лист"), Map.of("title", "Грамота"), "10.0.0.1", correlation);
        AuditTrailEntry system = new AuditTrailEntry(8L, Instant.parse("2026-09-29T08:00:00Z"), null, null, null,
            "INSERT", "awards", 5L, List.of(), null, null, null, null);

        String file = csv.write(List.of(update, system));

        assertThat(file).startsWith("﻿" + HEADER + "\r\n");
        assertThat(file.split("\r\n")).containsExactly("﻿" + HEADER,
            "2026-09-30T08:00:00Z;21;employee.fmi@chnu.edu.ua;UPDATE;awards;5;title,category_id;"
                + "\"{\"\"title\"\":\"\"Лист\"\"}\";\"{\"\"title\"\":\"\"Грамота\"\"}\";10.0.0.1;" + correlation,
            "2026-09-29T08:00:00Z;;;INSERT;awards;5;;;;;");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"x\")", "+1", "-1", "@SUM(A1)", "\tcmd", "\rcmd"})
    void ac2_7_aCellThatStartsLikeAFormulaIsPrefixedWithAQuote(String value) {
        assertThat(AuditTrailCsv.cell(value)).matches("(?s)\"?'.*").contains(value.replace("\"", "\"\""));
    }

    @Test
    void ac2_7_commasInValuesCannotStartAFormulaWhenTheFileIsSplitOnCommas() {
        AuditTrailEntry row = new AuditTrailEntry(9L, Instant.parse("2026-09-30T08:00:00Z"), 21L, null, null,
            "INSERT", "awards", 5L, List.of(), null, Map.of("title", "x,=cmd|' /C calc'!A0,"), null, null);

        String line = csv.write(List.of(row)).split("\r\n")[1];

        assertThat(line).doesNotContain(",=").contains("x\\u002C=cmd|' /C calc'!A0\\u002C");
    }

    @Test
    void ac2_7_separatorsQuotesAndLineBreaksAreQuoted() {
        assertThat(AuditTrailCsv.cell("a;b")).isEqualTo("\"a;b\"");
        assertThat(AuditTrailCsv.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(AuditTrailCsv.cell("line\nbreak")).isEqualTo("\"line\nbreak\"");
        assertThat(AuditTrailCsv.cell("plain")).isEqualTo("plain");
        assertThat(AuditTrailCsv.cell(null)).isEmpty();
    }
}
