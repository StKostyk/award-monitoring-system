package ua.edu.chnu.awards.analytics.export;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes the CV as plain text: the heading, then one block per year with one line per award.
 */
final class CvTextHelper {

    private static final String LINE = "\n";
    private static final String SEPARATOR = " — ";

    private CvTextHelper() {
    }

    static byte[] write(CvReport report) {
        List<String> lines = new ArrayList<>(List.of(report.title(), report.generated()));
        for (CvReport.Year year : report.years()) {
            lines.add("");
            lines.add(String.valueOf(year.year()));
            year.entries().forEach(entry -> lines.add("- " + line(entry)));
        }
        return (String.join(LINE, lines) + LINE).getBytes(StandardCharsets.UTF_8);
    }

    private static String line(CvReport.Entry entry) {
        return String.join(SEPARATOR, Stream.of(entry.title(), entry.level(), entry.issuer(), entry.date())
            .filter(part -> part != null && !part.isBlank())
            .toList());
    }
}
