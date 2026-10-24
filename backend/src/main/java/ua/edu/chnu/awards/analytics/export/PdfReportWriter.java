package ua.edu.chnu.awards.analytics.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Writes the report as an A4 portrait PDF from the template {@code templates/report/unit-analytics.html}, with the
 * bundled Noto Sans font so that Ukrainian renders without any font installed on the server.
 */
@Component
public final class PdfReportWriter implements ReportWriter {

    static final String TEMPLATE = "report/unit-analytics";

    private static final String FONT_FAMILY = "Noto Sans";
    private static final int REGULAR = 400;
    private static final int BOLD = 700;
    private static final String PRODUCER = "Award Monitoring";

    private final ITemplateEngine templates;
    private final byte[] regular;
    private final byte[] bold;

    /**
     * Creates the writer and loads the bundled fonts.
     *
     * @param templates the template engine
     */
    public PdfReportWriter(ITemplateEngine templates) {
        this.templates = templates;
        this.regular = font("fonts/NotoSans-Regular.ttf");
        this.bold = font("fonts/NotoSans-Bold.ttf");
    }

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] write(UnitReport report) {
        Context context = new Context(report.language().locale());
        context.setVariable("report", report);
        String html = templates.process(TEMPLATE, context);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new PdfRendererBuilder()
                .useFastMode()
                .withProducer(PRODUCER)
                .useFont(() -> stream(regular), FONT_FAMILY, REGULAR, BaseRendererBuilder.FontStyle.NORMAL, true)
                .useFont(() -> stream(bold), FONT_FAMILY, BOLD, BaseRendererBuilder.FontStyle.NORMAL, true)
                .withHtmlContent(html, null)
                .toStream(out)
                .run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static InputStream stream(byte[] font) {
        return new ByteArrayInputStream(font);
    }

    private static byte[] font(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
