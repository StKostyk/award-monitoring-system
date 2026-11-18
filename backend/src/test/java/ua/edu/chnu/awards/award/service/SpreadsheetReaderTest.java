package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import ua.edu.chnu.awards.award.config.AwardImportProperties;
import ua.edu.chnu.awards.common.web.ApiProblemException;

class SpreadsheetReaderTest {

    private static final int MAX_ROWS = 3;
    private static final DataSize MAX_SIZE = DataSize.ofKilobytes(4);

    private final SpreadsheetReader reader = new SpreadsheetReader(new AwardImportProperties(MAX_SIZE, MAX_ROWS),
        new XlsxTables(), new CsvTables());

    @Test
    void ac5_1_a_utf8_csv_with_semicolons_and_a_bom_is_read_without_empty_rows() {
        byte[] content = "﻿ПІБ;Нагорода;Дата\nКоваль Анна;\"Грамота; МОН\";2024\n;;\n"
            .getBytes(StandardCharsets.UTF_8);

        ImportSheet sheet = reader.read("Нагороди.CSV", content);

        assertThat(sheet.headers()).containsExactly("ПІБ", "Нагорода", "Дата");
        assertThat(sheet.rows()).hasSize(1);
        assertThat(sheet.rows().get(0).number()).isEqualTo(2);
        assertThat(sheet.rows().get(0).cells()).containsExactly("Коваль Анна", "Грамота; МОН", "2024");
    }

    @Test
    void ac5_1_a_windows_1251_csv_with_commas_is_read() {
        byte[] content = "ПІБ,Нагорода\nКоваль Анна,Подяка\n".getBytes(Charset.forName("windows-1251"));

        ImportSheet sheet = reader.read("people.csv", content);

        assertThat(sheet.headers()).containsExactly("ПІБ", "Нагорода");
        assertThat(sheet.rows().get(0).cells()).containsExactly("Коваль Анна", "Подяка");
    }

    @Test
    void ac5_1_a_workbook_keeps_row_numbers_and_reads_dates() throws IOException {
        ImportSheet sheet = reader.read("awards.xlsx", workbook());

        assertThat(sheet.headers()).containsExactly("ПІБ", "Дата", "Рік");
        assertThat(sheet.rows()).hasSize(1);
        assertThat(sheet.rows().get(0).number()).isEqualTo(3);
        assertThat(sheet.rows().get(0).cells()).containsExactly("Коваль Анна", "2024-05-17", "2023");
    }

    @Test
    void ac5_4_a_pdf_renamed_csv_or_another_type_is_refused() {
        byte[] pdf = "%PDF-1.7\nstream".getBytes(StandardCharsets.US_ASCII);
        byte[] binary = {'a', ';', 0x01, '\n'};

        assertProblem(() -> reader.read("awards.csv", pdf), "unsupported-type");
        assertProblem(() -> reader.read("awards.csv", binary), "unsupported-type");
        assertProblem(() -> reader.read("awards.xlsx", "a;b\n".getBytes(StandardCharsets.UTF_8)),
            "unsupported-type");
        assertProblem(() -> reader.read("awards.txt", "a;b\n".getBytes(StandardCharsets.UTF_8)), "unsupported-type");
    }

    @Test
    void ac5_4_files_over_the_limits_or_without_rows_are_refused() {
        byte[] large = new byte[(int) MAX_SIZE.toBytes() + 1];
        Arrays.fill(large, (byte) 'a');
        String fourRows = "ПІБ\nА Б\nВ Г\nД Е\nЖ З\n";

        assertProblem(() -> reader.read("awards.csv", large), "file-too-large");
        assertProblem(() -> reader.read("awards.csv", fourRows.getBytes(StandardCharsets.UTF_8)), "too-many-rows");
        assertProblem(() -> reader.read("awards.csv", "ПІБ\n\n".getBytes(StandardCharsets.UTF_8)), "file-empty");
        assertProblem(() -> reader.read("awards.csv", new byte[0]), "file-empty");
    }

    @Test
    void ac5_4_many_empty_lines_do_not_hide_rows_over_the_limit() {
        String spaced = "ПІБ\n" + "\n".repeat(40) + "А Б\n";

        assertProblem(() -> reader.read("awards.csv", spaced.getBytes(StandardCharsets.UTF_8)), "too-many-rows");
    }

    private static byte[] workbook() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(out, "test", "1.0");
        fill(workbook.newWorksheet("Аркуш"));
        workbook.finish();
        return out.toByteArray();
    }

    private static void fill(Worksheet sheet) {
        List<String> headers = List.of("ПІБ", "Дата", "Рік");
        for (int column = 0; column < headers.size(); column++) {
            sheet.value(0, column, headers.get(column));
        }
        sheet.value(2, 0, "Коваль Анна");
        sheet.value(2, 1, LocalDate.of(2024, 5, 17));
        sheet.style(2, 1).format("dd.MM.yyyy").set();
        sheet.value(2, 2, 2023);
    }

    private static void assertProblem(ThrowingCallable call, String type) {
        assertThatThrownBy(call).isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).toProblem().getType().toString())
            .asString().endsWith(type);
    }
}
