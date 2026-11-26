package ua.edu.chnu.awards.award.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

/**
 * The empty workbook a faculty can fill for the import: the columns the import recognises, and a second sheet
 * with the level names it reads.
 */
@Component
public class ImportTemplate {

    private static final String APPLICATION = "Award Monitoring";
    private static final String VERSION = "1.0";
    private static final List<String> COLUMNS = List.of("Прізвище, ім'я, по батькові", "Кафедра",
        "Назва нагороди", "Дата нагородження", "Рівень", "Ким видано", "Електронна адреса", "Примітки");
    private static final List<String> LEVELS = List.of("Міжнародний", "Всеукраїнський", "Регіональний",
        "Місцевий", "Університетський", "Факультетський", "Коледж", "Кафедральний", "Спеціальність");
    private static final double WIDE = 32;

    /**
     * The workbook.
     *
     * @return the {@code .xlsx} content
     */
    public byte[] workbook() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, APPLICATION, VERSION);
            columns(workbook.newWorksheet("Нагороди"));
            levels(workbook.newWorksheet("Рівні"));
            workbook.finish();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }

    private static void columns(Worksheet sheet) {
        for (int column = 0; column < COLUMNS.size(); column++) {
            sheet.value(0, column, COLUMNS.get(column));
            sheet.width(column, WIDE);
        }
        sheet.range(0, 0, 0, COLUMNS.size() - 1).style().bold().set();
    }

    private static void levels(Worksheet sheet) {
        sheet.value(0, 0, "Рівень");
        sheet.style(0, 0).bold().set();
        for (int row = 0; row < LEVELS.size(); row++) {
            sheet.value(row + 1, 0, LEVELS.get(row));
        }
        sheet.width(0, WIDE);
    }
}
