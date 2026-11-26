package ua.edu.chnu.awards.award.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ExcelReaderException;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Row;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * Reads the first sheet of an {@code .xlsx} workbook as text; a number in a date format becomes
 * {@code yyyy-MM-dd}, any other number its plain digits.
 */
@Component
public class XlsxTables {

    private static final Pattern DATE_FORMAT = Pattern.compile("(?i).*(d|yy|m{3,}).*");
    private static final long MAX_INFLATED_BYTES = 64L * 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;

    /**
     * Whether the content is a workbook.
     *
     * @param content the content
     * @return true for the zip header of an Office Open XML file
     */
    public boolean isWorkbook(byte[] content) {
        return ReadableWorkbook.isOOXMLZipHeader(content);
    }

    /**
     * The rows of the first sheet, empty rows kept as empty lists.
     *
     * @param content the content
     * @param limit   the most rows to read
     * @return the cells of each row, at most {@code limit}
     * @throws ApiProblemException 422 {@code file-unreadable}, or {@code file-too-large} when the workbook unpacks
     *                             beyond 64 MB
     */
    public List<List<String>> read(byte[] content, int limit) {
        requireInflatedWithin(content);
        try (ReadableWorkbook book = new ReadableWorkbook(new ByteArrayInputStream(content),
            new ReadingOptions(true, false));
             Stream<Row> stream = book.getFirstSheet().openStream()) {
            List<List<String>> table = new ArrayList<>();
            Iterator<Row> rows = stream.limit(limit).iterator();
            while (rows.hasNext() && table.size() < limit) {
                Row row = rows.next();
                while (table.size() < Math.min(row.getRowNum() - 1, limit)) {
                    table.add(List.of());
                }
                if (table.size() >= limit) {
                    break;
                }
                List<String> cells = new ArrayList<>();
                for (int column = 0; column < row.getCellCount(); column++) {
                    cells.add(text(row.getCell(column)));
                }
                table.add(cells);
            }
            return table;
        } catch (IOException | ExcelReaderException | UncheckedIOException | IllegalStateException exception) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "file-unreadable",
                "The workbook cannot be read", exception);
        }
    }

    private static void requireInflatedWithin(byte[] content) {
        long inflated = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new ByteArrayInputStream(content),
            StandardCharsets.UTF_8.name(), true, true)) {
            while (zip.getNextEntry() != null) {
                for (int read = zip.read(buffer); read > 0; read = zip.read(buffer)) {
                    inflated += read;
                    if (inflated > MAX_INFLATED_BYTES) {
                        throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "file-too-large",
                            "The workbook unpacks beyond the limit");
                    }
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "file-unreadable",
                "The workbook cannot be read", exception);
        }
    }

    private static String text(Cell cell) {
        if (cell == null || cell.getType() == CellType.EMPTY) {
            return "";
        }
        if (cell.getType() == CellType.NUMBER) {
            String format = cell.getDataFormatString();
            return format != null && DATE_FORMAT.matcher(format).matches()
                ? cell.asDate().toLocalDate().toString()
                : cell.asNumber().stripTrailingZeros().toPlainString();
        }
        String value = cell.getText();
        return value == null ? "" : value;
    }
}
