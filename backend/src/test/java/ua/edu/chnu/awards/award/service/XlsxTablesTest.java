package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.common.web.ApiProblemException;

class XlsxTablesTest {

    private static final int LIMIT = 10;
    private static final int FAR_ROW = 1_000_000;
    private static final int INFLATED_MB = 65;

    private final XlsxTables tables = new XlsxTables();

    @Test
    void ac5_4_a_row_far_below_the_header_stops_reading_at_the_limit() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(out, "test", "1.0");
        Worksheet sheet = workbook.newWorksheet("Аркуш");
        sheet.value(0, 0, "ПІБ");
        sheet.value(FAR_ROW, 0, "Коваль Анна");
        workbook.finish();

        assertThat(tables.read(out.toByteArray(), LIMIT)).hasSize(LIMIT);
    }

    @Test
    void ac5_4_a_workbook_that_unpacks_beyond_the_cap_is_refused() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            byte[] block = new byte[1024 * 1024];
            for (int megabyte = 0; megabyte < INFLATED_MB; megabyte++) {
                zip.write(block);
            }
            zip.closeEntry();
        }

        assertThatThrownBy(() -> tables.read(out.toByteArray(), LIMIT)).isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).toProblem().getType().toString())
            .asString().endsWith("file-too-large");
    }
}
