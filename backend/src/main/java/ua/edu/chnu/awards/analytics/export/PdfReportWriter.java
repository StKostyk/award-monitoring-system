package ua.edu.chnu.awards.analytics.export;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;

/**
 * Writes the report as an A4 portrait PDF from the template {@code templates/report/unit-analytics.html}.
 */
@Component
public final class PdfReportWriter implements ReportWriter {

    static final String TEMPLATE = "report/unit-analytics";

    private final PdfRenderer renderer;

    /**
     * Creates the writer and loads the bundled fonts.
     *
     * @param templates the template engine
     */
    public PdfReportWriter(ITemplateEngine templates) {
        this.renderer = new PdfRenderer(templates);
    }

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] write(UnitReport report) {
        return renderer.render(TEMPLATE, report, report.language().locale());
    }
}
