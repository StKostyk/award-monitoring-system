package ua.edu.chnu.awards.analytics.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Renders a report template to PDF with the bundled Noto Sans font, so that Ukrainian renders without any font
 * installed on the server.
 */
final class PdfRenderer {

    private static final String FONT_FAMILY = "Noto Sans";
    private static final int REGULAR = 400;
    private static final int BOLD = 700;
    private static final String PRODUCER = "Award Monitoring";

    private final ITemplateEngine templates;
    private final byte[] regular;
    private final byte[] bold;

    PdfRenderer(ITemplateEngine templates) {
        this.templates = templates;
        this.regular = font("fonts/NotoSans-Regular.ttf");
        this.bold = font("fonts/NotoSans-Bold.ttf");
    }

    byte[] render(String template, Object report, Locale locale) {
        Context context = new Context(locale);
        context.setVariable("report", report);
        String html = templates.process(template, context);
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
