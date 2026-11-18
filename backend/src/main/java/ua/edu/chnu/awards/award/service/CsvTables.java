package ua.edu.chnu.awards.award.service;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * Reads a {@code .csv} file: UTF-8, or Windows-1251 when it is not valid UTF-8, with the delimiter its header
 * line uses most (semicolon, comma or tab). A file with binary content is not text.
 */
@Component
public class CsvTables {

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final char BOM = '﻿';
    private static final int LAST_CONTROL = 0x1F;
    private static final byte[] PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);
    private static final Set<Byte> TEXT_CONTROLS = Set.of((byte) '\t', (byte) '\n', (byte) '\r');
    private static final List<Character> DELIMITERS = List.of(';', ',', '\t');

    /**
     * Whether the content is text.
     *
     * @param content the content
     * @return false for a PDF or content with control bytes other than tabs and line breaks
     */
    public boolean isText(byte[] content) {
        if (content.length >= PDF.length && Arrays.equals(content, 0, PDF.length, PDF, 0, PDF.length)) {
            return false;
        }
        for (byte value : content) {
            if (value >= 0 && value <= LAST_CONTROL && !TEXT_CONTROLS.contains(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The rows of the file.
     *
     * @param content the content
     * @param limit   the most rows to read
     * @return the cells of each row
     * @throws ApiProblemException 422 {@code file-unreadable}
     */
    public List<List<String>> read(byte[] content, int limit) {
        String text = decode(content);
        String body = !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter(body))
            .setIgnoreEmptyLines(false).get();
        List<List<String>> table = new ArrayList<>();
        try (CSVParser parser = format.parse(new StringReader(body))) {
            for (CSVRecord row : parser) {
                if (table.size() >= limit) {
                    break;
                }
                table.add(row.toList());
            }
            return table;
        } catch (IOException | UncheckedIOException | IllegalStateException exception) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "file-unreadable",
                "The file cannot be read as CSV", exception);
        }
    }

    private static char delimiter(String body) {
        int end = body.indexOf('\n');
        String header = end < 0 ? body : body.substring(0, end);
        char best = DELIMITERS.get(0);
        long most = -1;
        for (char candidate : DELIMITERS) {
            long count = header.chars().filter(character -> character == candidate).count();
            if (count > most) {
                most = count;
                best = candidate;
            }
        }
        return best;
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString();
        } catch (CharacterCodingException exception) {
            return new String(content, WINDOWS_1251);
        }
    }
}
