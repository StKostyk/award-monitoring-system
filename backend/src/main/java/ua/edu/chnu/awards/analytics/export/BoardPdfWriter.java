package ua.edu.chnu.awards.analytics.export;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;

/**
 * Writes the board report as an A4 landscape PDF from the template {@code templates/report/board.html}.
 */
@Component
public final class BoardPdfWriter {

    static final String TEMPLATE = "report/board";

    private final PdfRenderer renderer;

    /**
     * Creates the writer and loads the bundled fonts.
     *
     * @param templates the template engine
     */
    public BoardPdfWriter(ITemplateEngine templates) {
        this.renderer = new PdfRenderer(templates);
    }

    /**
     * Renders the report.
     *
     * @param report the report
     * @return the PDF
     */
    public byte[] write(BoardReport report) {
        return renderer.render(TEMPLATE, report, report.language().locale());
    }
}
