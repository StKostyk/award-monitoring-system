package ua.edu.chnu.awards.award.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.ImportScreening;
import ua.edu.chnu.awards.award.dto.AwardImportPreview;
import ua.edu.chnu.awards.award.dto.AwardImportResult;
import ua.edu.chnu.awards.award.dto.ImportMapping;
import ua.edu.chnu.awards.award.dto.ImportRow;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

import lombok.RequiredArgsConstructor;

/**
 * The spreadsheet import of people and past awards for a faculty secretary or dean: the file is scanned and
 * read, its columns mapped (the caller's mapping, or a guess from the headers), and every row previewed;
 * the import stores the rows of an unchanged preview without blocking problems in one transaction.
 */
@Service
@RequiredArgsConstructor
public class AwardImport {

    private static final int FILE_NAME_MAX = 255;
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}\\\\/]");

    private final SpreadsheetReader reader;
    private final ImportColumns columns;
    private final ImportPreviewer previewer;
    private final ImportStore store;
    private final PersonRules rules;
    private final ImportScreening screening;
    private final AccessScope access;

    /**
     * The preview of a file; nothing is stored.
     *
     * @param file    the spreadsheet
     * @param mapping the mapping, null to guess it from the headers
     * @return the preview
     * @throws ApiProblemException 422 for a refused file or mapping
     */
    @Transactional(readOnly = true)
    public AwardImportPreview preview(MultipartFile file, ImportMapping mapping) {
        rules.requireRole();
        byte[] content = content(file);
        ImportSheet sheet = read(file, content);
        ImportMapping used = mapping == null ? columns.guess(sheet.headers()) : check(mapping, sheet);
        return new AwardImportPreview(checksum(content, used), sheet.headers(), used,
            previewer.rows(sheet, used));
    }

    /**
     * Imports a previewed file.
     *
     * @param file     the spreadsheet
     * @param mapping  the mapping of the preview
     * @param checksum the checksum of the preview
     * @return the counts
     * @throws ApiProblemException 409 {@code preview-stale} when the file or the mapping changed, 422
     *                             {@code rows-blocked} naming the rows with blocking problems, 422 for a refused
     *                             file or mapping
     */
    @Transactional
    public AwardImportResult run(MultipartFile file, ImportMapping mapping, String checksum) {
        rules.requireRole();
        if (mapping == null) {
            throw ApiProblemException.validationFailed("The mapping is required",
                List.of(new FieldViolation("mapping", "required", "The mapping of the preview is required")));
        }
        byte[] content = content(file);
        ImportSheet sheet = read(file, content);
        ImportMapping used = check(mapping, sheet);
        if (!checksum(content, used).equals(checksum)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "preview-stale",
                "The file or the mapping differs from the previewed one");
        }
        List<ImportRow> rows = previewer.rows(sheet, used);
        List<Integer> blocked = rows.stream().filter(ImportRow::blocked).map(ImportRow::row).toList();
        if (!blocked.isEmpty()) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "rows-blocked",
                "Some rows have problems that block the import", Map.of("rows", blocked));
        }
        return store.store(fileName(file), rows);
    }

    private ImportSheet read(MultipartFile file, byte[] content) {
        screening.checkImport(access.callerId(), content);
        return reader.read(file.getOriginalFilename(), content);
    }

    private ImportMapping check(ImportMapping mapping, ImportSheet sheet) {
        List<FieldViolation> errors = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        mapping.columns().forEach((field, column) -> {
            if (column == null || column < 0 || column >= sheet.headers().size()) {
                errors.add(new FieldViolation("mapping.columns." + field, "unknown-column",
                    "The file has no such column"));
            } else if (!used.add(column)) {
                errors.add(new FieldViolation("mapping.columns." + field, "column-reused",
                    "A column can fill one field only"));
            }
        });
        if (mapping.departmentId() != null && !rules.covers(mapping.departmentId())) {
            errors.add(new FieldViolation("mapping.departmentId", "out-of-scope",
                "Only an active department of your faculty secretary or dean role can be chosen"));
        }
        if (!errors.isEmpty()) {
            throw ApiProblemException.validationFailed("The mapping has invalid fields", errors);
        }
        return mapping;
    }

    private static byte[] content(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String checksum(byte[] content, ImportMapping mapping) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(content);
            digest.update(mapping.canonical().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String fileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        String name = original == null ? "" : original;
        String plain = UNSAFE.matcher(name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1))
            .replaceAll("");
        return plain.length() > FILE_NAME_MAX ? plain.substring(0, FILE_NAME_MAX) : plain;
    }
}
