package ua.edu.chnu.awards.award.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.config.AwardImportProperties;
import ua.edu.chnu.awards.common.web.ApiProblemException;

import lombok.RequiredArgsConstructor;

/**
 * Reads an uploaded {@code .xlsx} workbook or {@code .csv} file into text cells within the size and row limits;
 * the type is checked by the content as well as by the name, and empty rows are left out.
 */
@Component
@RequiredArgsConstructor
public class SpreadsheetReader {

    private static final int HEADER_ROW = 1;
    private static final int SCAN_FACTOR = 10;

    private final AwardImportProperties properties;
    private final XlsxTables workbooks;
    private final CsvTables csv;

    /**
     * Reads the file.
     *
     * @param name    the file name as sent
     * @param content the content
     * @return the cells
     * @throws ApiProblemException 422 {@code file-too-large}, {@code unsupported-type}, {@code file-empty},
     *                             {@code too-many-rows} or {@code file-unreadable}
     */
    public ImportSheet read(String name, byte[] content) {
        if (content.length > properties.maxSize().toBytes()) {
            throw refusal("file-too-large", "The file is larger than the limit",
                Map.of("maxBytes", properties.maxSize().toBytes()));
        }
        int limit = properties.maxRows() * SCAN_FACTOR;
        List<List<String>> table = table(name == null ? "" : name.toLowerCase(Locale.ROOT), content, limit);
        if (table.size() >= limit) {
            throw tooMany();
        }
        return sheet(table);
    }

    private List<List<String>> table(String name, byte[] content, int limit) {
        if (name.endsWith(".xlsx") && workbooks.isWorkbook(content)) {
            return workbooks.read(content, limit);
        }
        if (name.endsWith(".csv") && csv.isText(content)) {
            return csv.read(content, limit);
        }
        throw refusal("unsupported-type", "Only an .xlsx workbook or a .csv file can be imported", Map.of());
    }

    private ImportSheet sheet(List<List<String>> table) {
        if (table.isEmpty()) {
            throw refusal("file-empty", "The file has no header row", Map.of());
        }
        List<ImportSheet.Line> rows = new ArrayList<>();
        for (int index = 1; index < table.size(); index++) {
            List<String> cells = table.get(index);
            if (cells.stream().anyMatch(cell -> !cell.isBlank())) {
                rows.add(new ImportSheet.Line(index + HEADER_ROW, cells));
            }
        }
        if (rows.isEmpty()) {
            throw refusal("file-empty", "The file has no rows below the header", Map.of());
        }
        if (rows.size() > properties.maxRows()) {
            throw tooMany();
        }
        return new ImportSheet(table.get(0).stream().map(String::strip).toList(), rows);
    }

    private ApiProblemException tooMany() {
        return refusal("too-many-rows", "The file has more rows than the limit",
            Map.of("maxRows", properties.maxRows()));
    }

    private static ApiProblemException refusal(String type, String detail, Map<String, Object> details) {
        return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, type, detail, details);
    }
}
