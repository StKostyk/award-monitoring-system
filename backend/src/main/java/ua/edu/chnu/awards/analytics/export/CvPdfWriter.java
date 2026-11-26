package ua.edu.chnu.awards.analytics.export;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;

/**
 * Renders the CV as an A4 PDF with the bundled fonts.
 */
@Component
public final class CvPdfWriter {

    static final String TEMPLATE = "report/cv";

    private final PdfRenderer renderer;

    /**
     * Creates the writer and loads the bundled fonts.
     *
     * @param templates the template engine
     */
    public CvPdfWriter(ITemplateEngine templates) {
        this.renderer = new PdfRenderer(templates);
    }

    /**
     * Renders the CV.
     *
     * @param report the CV
     * @return the PDF
     */
    public byte[] write(CvReport report) {
        return renderer.render(TEMPLATE, report, report.language().locale());
    }
}
